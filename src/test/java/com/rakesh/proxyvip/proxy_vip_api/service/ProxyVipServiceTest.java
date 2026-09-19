package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class ProxyVipServiceTest {

    private ProxyVipService proxyVipService;

    @BeforeEach
    void setUp() {
        proxyVipService = new ProxyVipService();
    }

    @Test
    void shouldReturnSameVipForSameSourceAndDestinationOnRepeatedCalls() {
        // Arrange: pick a source and destination
        String sourceIp = "10.10.10.10";
        String destIp = "127.0.0.1";

        // Act: call allocate twice with the SAME arguments
        String firstResult = proxyVipService.allocate(sourceIp, destIp);
        String secondResult = proxyVipService.allocate(sourceIp, destIp);

        // Assert: both calls must return the identical VIP
        assertEquals(firstResult, secondResult);
    }
    @Test
    void shouldReturnDifferentVipsForSameSourceWithDifferentDestinations() {
        String sourceIp = "10.10.10.10";
        String destIp = "127.0.0.1";
        String destIp1 = "127.0.0.2";

        String firstResult = proxyVipService.allocate(sourceIp, destIp);
        String secondResult = proxyVipService.allocate(sourceIp, destIp1);

        assertNotEquals(firstResult, secondResult);
    }

    @Test
    void shouldAllowDifferentSourceToAllocateEvenWhenAnotherSourceIsExhausted(){
        // Arrange: exhaust ALL 6 VIPs for sourceA, by allocating 6 different destinations
        String sourceA = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            String destination = "127.0.0." + i;
            proxyVipService.allocate(sourceA, destination);
        }
        // Act + Assert: sourceB should still be able to allocate successfully,
        // even though sourceA has used every VIP in the pool
        String sourceB = "20.20.20.20";
        assertDoesNotThrow(() -> {
            proxyVipService.allocate(sourceB, "127.0.0.100");
        });
    }

    @Test
    void shouldThrowExceptionWhenAllVipsExhaustedForSource(){
        String sourceA = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            String destination = "127.0.0." + i;
            proxyVipService.allocate(sourceA, destination);
        }
        assertThrows(VipPoolExhaustedException.class,()-> {
            proxyVipService.allocate(sourceA, "127.0.0.109");
        });
    }
    @Test
    void shouldMakeNewlyAddedVipImmediatelyAvailable() {
        String sourceA = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            String destination = "127.0.0." + i;
            proxyVipService.allocate(sourceA, destination);
        }

        // Confirm exhaustion, as a self-contained check within this test
        assertThrows(VipPoolExhaustedException.class, () -> {
            proxyVipService.allocate(sourceA, "127.0.0.200");
        });

        // Act: grow the pool
        proxyVipService.addVip("1.1.1.7");

        // Assert: the previously-failing allocation now succeeds, AND returns
        // specifically 1.1.1.7 — since it's the ONLY unused VIP available now
        String result = proxyVipService.allocate(sourceA, "127.0.0.200");
        assertEquals("1.1.1.7", result);
    }
    @Test
    void shouldNotAlwaysAssignVipsInSequentialOrder() {
        // Arrange: one source, 6 destinations, matching the 6 pre-configured VIPs
        String sourceA = "10.10.10.10";
        List<String> allocatedVips = new ArrayList<>();

        // Act: allocate for all 6 destinations, collecting results IN ORDER
        for (int i = 0; i < 6; i++) {
            String destination = "127.0.0." + i;
            String vip = proxyVipService.allocate(sourceA, destination);
            allocatedVips.add(vip);
        }

        // The pool's original, sequential insertion order
        List<String> sequentialOrder = List.of(
                "1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6"
        );

        // Assert: the randomly-allocated order should NOT match the original sequential order.
        // NOTE: there is a theoretical 1-in-720 chance random selection reproduces this exact
        // order by coincidence, which would cause a rare false failure. Documented as a known
        // limitation of testing true randomness deterministically.
        assertNotEquals(sequentialOrder, allocatedVips);
    }


    @Test
    void shouldNotAssignDuplicateVipUnderConcurrentRequestsForSameSource() throws InterruptedException {
        String sourceA = "10.10.10.10";
        int numberOfThreads = 6;  // matches the 6 pre-configured VIPs

        // Thread-safe collection to gather results from multiple threads safely
        List<String> allocatedVips = new CopyOnWriteArrayList<>();

        // Lets the main test thread wait until ALL worker threads finish
        CountDownLatch latch = new CountDownLatch(numberOfThreads);

        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        // Fire off 6 concurrent allocate() calls, same source, different destinations
        for (int i = 0; i < numberOfThreads; i++) {
            String destination = "127.0.0." + i;
            executor.submit(() -> {
                try {
                    String vip = proxyVipService.allocate(sourceA, destination);
                    allocatedVips.add(vip);
                } finally {
                    latch.countDown();  // signal this thread is done, whether it succeeded or threw
                }
            });
        }

        // Wait for all 6 threads to finish, with a safety timeout so the test can't hang forever
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Assert: 6 requests went in, 6 VIPs came out
        assertEquals(numberOfThreads, allocatedVips.size());

        // Assert: no duplicates — converting to a Set removes duplicates,
        // so if the Set is smaller than the List, a duplicate existed
        long distinctCount = allocatedVips.stream().distinct().count();
        assertEquals(numberOfThreads, distinctCount);
    }
}