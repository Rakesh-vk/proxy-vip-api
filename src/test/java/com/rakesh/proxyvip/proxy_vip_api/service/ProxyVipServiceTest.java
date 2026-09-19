package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.exception.VipNotAllocated;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

        String sourceIp = "10.10.10.10";
        String destinationIp = "127.0.0.1";

        String firstResult =
                proxyVipService.allocate(sourceIp, destinationIp);

        String secondResult =
                proxyVipService.allocate(sourceIp, destinationIp);

        assertEquals(firstResult, secondResult);
    }

    @Test
    void shouldReturnDifferentVipsForSameSourceWithDifferentDestinations() {

        String sourceIp = "10.10.10.10";
        String firstDestination = "127.0.0.1";
        String secondDestination = "127.0.0.2";

        String firstVip =
                proxyVipService.allocate(
                        sourceIp,
                        firstDestination
                );

        String secondVip =
                proxyVipService.allocate(
                        sourceIp,
                        secondDestination
                );

        assertNotEquals(firstVip, secondVip);
    }

    @Test
    void shouldAllowDifferentSourceToAllocateEvenWhenAnotherSourceIsExhausted() {

        String sourceA = "10.10.10.10";

        // Exhaust all 6 VIPs for source A.
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(
                    sourceA,
                    "127.0.0." + i
            );
        }

        // Source B should still have its own independent VIP pool.
        String sourceB = "20.20.20.20";

        String vip =
                proxyVipService.allocate(
                        sourceB,
                        "127.0.0.100"
                );

        assertNotNull(vip);
    }

    @Test
    void shouldThrowExceptionWhenAllVipsExhaustedForSource() {

        String sourceIp = "10.10.10.10";

        // Use all 6 VIPs for the same source.
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(
                    sourceIp,
                    "127.0.0." + i
            );
        }

        // No VIP should remain for another destination.
        assertThrows(
                VipPoolExhaustedException.class,
                () -> proxyVipService.allocate(
                        sourceIp,
                        "127.0.0.100"
                )
        );
    }

    @Test
    void shouldMakeNewlyAddedVipImmediatelyAvailable() {

        String sourceIp = "10.10.10.10";

        // Exhaust all configured VIPs.
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(
                    sourceIp,
                    "127.0.0." + i
            );
        }

        // Confirm that the source is exhausted.
        assertThrows(
                VipPoolExhaustedException.class,
                () -> proxyVipService.allocate(
                        sourceIp,
                        "127.0.0.200"
                )
        );

        // Add a new VIP.
        proxyVipService.addVip("1.1.1.7");

        // The newly added VIP should now be available.
        String result =
                proxyVipService.allocate(
                        sourceIp,
                        "127.0.0.200"
                );

        assertEquals("1.1.1.7", result);
    }

    @Test
    void shouldReturnAllocatedVipForSourceAndDestination() {

        String sourceIp = "10.10.10.10";
        String destinationIp = "127.0.0.1";

        String allocatedVip =
                proxyVipService.allocate(
                        sourceIp,
                        destinationIp
                );

        String retrievedVip =
                proxyVipService.getBySourceAndDestination(
                        sourceIp,
                        destinationIp
                );

        assertEquals(allocatedVip, retrievedVip);
    }

    @Test
    void shouldThrowExceptionWhenVipIsNotAllocated() {

        assertThrows(
                VipNotAllocated.class,
                () -> proxyVipService.getBySourceAndDestination(
                        "10.10.10.10",
                        "127.0.0.1"
                )
        );
    }

    @Test
    void shouldAllocateVipFromConfiguredPool() {

        String sourceIp = "10.10.10.10";

        List<String> configuredVips = List.of(
                "1.1.1.1",
                "1.1.1.2",
                "1.1.1.3",
                "1.1.1.4",
                "1.1.1.5",
                "1.1.1.6"
        );

        // Every allocated VIP must come from the configured pool.
        for (int i = 0; i < 6; i++) {

            String vip =
                    proxyVipService.allocate(
                            sourceIp,
                            "127.0.0." + i
                    );

            assertTrue(
                    configuredVips.contains(vip),
                    "Allocated VIP should belong to the configured pool"
            );
        }
    }

    @Test
    void shouldNotAssignDuplicateVipUnderConcurrentRequestsForSameSource()
            throws InterruptedException {

        String sourceIp = "10.10.10.10";
        int numberOfThreads = 6;

        List<String> allocatedVips =
                new CopyOnWriteArrayList<>();

        CountDownLatch latch =
                new CountDownLatch(numberOfThreads);

        ExecutorService executor =
                Executors.newFixedThreadPool(numberOfThreads);

        // Submit 6 concurrent requests for the same source
        // but different destinations.
        for (int i = 0; i < numberOfThreads; i++) {

            String destinationIp =
                    "127.0.0." + i;

            executor.submit(() -> {
                try {
                    String vip =
                            proxyVipService.allocate(
                                    sourceIp,
                                    destinationIp
                            );

                    allocatedVips.add(vip);

                } finally {
                    latch.countDown();
                }
            });
        }

        // Make sure every worker actually completed.
        boolean completed =
                latch.await(5, TimeUnit.SECONDS);

        assertTrue(
                completed,
                "All worker threads should complete within 5 seconds"
        );

        executor.shutdown();

        // Make sure the executor itself terminates.
        assertTrue(
                executor.awaitTermination(
                        5,
                        TimeUnit.SECONDS
                ),
                "Executor should terminate within 5 seconds"
        );

        // All 6 requests should have received a VIP.
        assertEquals(
                numberOfThreads,
                allocatedVips.size()
        );

        // No two different destinations for the same source
        // should receive the same VIP.
        long distinctCount =
                allocatedVips.stream()
                        .distinct()
                        .count();

        assertEquals(
                numberOfThreads,
                distinctCount
        );
    }

}