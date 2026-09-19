package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.entity.VipAllocationEntity;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipNotAllocated;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import com.rakesh.proxyvip.proxy_vip_api.repository.VipAllocationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProxyVipServiceTest {

    private ProxyVipService proxyVipService;

    @BeforeEach
    void setUp() {
        // Mocked — tests should not require a real Postgres connection.
        VipAllocationRepository mockRepository = mock(VipAllocationRepository.class);
        VipPersistenceService mockPersistenceService = mock(VipPersistenceService.class);

        proxyVipService = new ProxyVipService(mockRepository, mockPersistenceService);
    }

    @Test
    void shouldReturnSameVipForSameSourceAndDestinationOnRepeatedCalls() {

        String sourceIp = "10.10.10.10";
        String destinationIp = "127.0.0.1";

        String firstResult = proxyVipService.allocate(sourceIp, destinationIp);
        String secondResult = proxyVipService.allocate(sourceIp, destinationIp);

        assertEquals(firstResult, secondResult);
    }

    @Test
    void shouldReturnDifferentVipsForSameSourceWithDifferentDestinations() {

        String sourceIp = "10.10.10.10";

        String firstVip = proxyVipService.allocate(sourceIp, "127.0.0.1");
        String secondVip = proxyVipService.allocate(sourceIp, "127.0.0.2");

        assertNotEquals(firstVip, secondVip);
    }

    @Test
    void shouldAllowDifferentSourceToAllocateEvenWhenAnotherSourceIsExhausted() {

        String sourceA = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(sourceA, "127.0.0." + i);
        }

        String sourceB = "20.20.20.20";
        assertDoesNotThrow(() -> proxyVipService.allocate(sourceB, "127.0.0.100"));
    }

    @Test
    void shouldThrowExceptionWhenAllVipsExhaustedForSource() {

        String sourceIp = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(sourceIp, "127.0.0." + i);
        }

        assertThrows(
                VipPoolExhaustedException.class,
                () -> proxyVipService.allocate(sourceIp, "127.0.0.100")
        );
    }

    @Test
    void shouldMakeNewlyAddedVipImmediatelyAvailable() {

        String sourceIp = "10.10.10.10";
        for (int i = 0; i < 6; i++) {
            proxyVipService.allocate(sourceIp, "127.0.0." + i);
        }

        assertThrows(
                VipPoolExhaustedException.class,
                () -> proxyVipService.allocate(sourceIp, "127.0.0.200")
        );

        proxyVipService.addVip("1.1.1.7");

        String result = proxyVipService.allocate(sourceIp, "127.0.0.200");
        assertEquals("1.1.1.7", result);
    }

    @Test
    void shouldReturnAllocatedVipForSourceAndDestination() {

        String sourceIp = "10.10.10.10";
        String destinationIp = "127.0.0.1";

        String allocatedVip = proxyVipService.allocate(sourceIp, destinationIp);
        String retrievedVip = proxyVipService.get(sourceIp, destinationIp);

        assertEquals(allocatedVip, retrievedVip);
    }

    @Test
    void shouldThrowExceptionWhenVipIsNotAllocated() {

        assertThrows(
                VipNotAllocated.class,
                () -> proxyVipService.get("10.10.10.10", "127.0.0.1")
        );
    }

    @Test
    void shouldAllocateVipFromConfiguredPool() {

        String sourceIp = "10.10.10.10";
        List<String> configuredVips = List.of(
                "1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6"
        );

        for (int i = 0; i < 6; i++) {
            String vip = proxyVipService.allocate(sourceIp, "127.0.0." + i);
            assertTrue(configuredVips.contains(vip), "Allocated VIP should belong to the configured pool");
        }
    }

    @Test
    void shouldNotAlwaysAssignVipsInSequentialOrder() {

        String sourceA = "10.10.10.10";
        List<String> allocatedVips = new ArrayList<>();

        for (int i = 0; i < 6; i++) {
            allocatedVips.add(proxyVipService.allocate(sourceA, "127.0.0." + i));
        }

        List<String> sequentialOrder = List.of(
                "1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6"
        );

        // NOTE: theoretical 1-in-720 chance of false failure if random selection
        // happens to reproduce this exact order by coincidence.
        assertNotEquals(sequentialOrder, allocatedVips);
    }

    @Test
    void shouldNotAssignDuplicateVipUnderConcurrentRequestsForSameSource() throws InterruptedException {

        String sourceIp = "10.10.10.10";
        int numberOfThreads = 6;

        List<String> allocatedVips = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(numberOfThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);

        for (int i = 0; i < numberOfThreads; i++) {
            String destinationIp = "127.0.0." + i;
            executor.submit(() -> {
                try {
                    allocatedVips.add(proxyVipService.allocate(sourceIp, destinationIp));
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "All worker threads should complete within 5 seconds");
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Executor should terminate within 5 seconds");

        assertEquals(numberOfThreads, allocatedVips.size());
        assertEquals(numberOfThreads, allocatedVips.stream().distinct().count());
    }

    @Test
    void shouldHandleOneHundredThousandConcurrentSourcesWithoutErrorsOrDuplicateStateCorruption() throws InterruptedException {

        int numberOfUsers = 100_000;

        // Each "user" is a distinct source IP allocating once for the same destination —
        // this reflects the realistic high-scale scenario (many independent subscribers),
        // not 100K threads fighting over one exhausted 6-VIP pool.
        ExecutorService executor = Executors.newFixedThreadPool(200);
        CountDownLatch latch = new CountDownLatch(numberOfUsers);
        ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        long startTime = System.nanoTime();

        for (int i = 0; i < numberOfUsers; i++) {
            String sourceIp = "192.168." + (i / 256) + "." + (i % 256);
            executor.submit(() -> {
                try {
                    String vip = proxyVipService.allocate(sourceIp, "127.0.0.1");
                    results.put(sourceIp, vip);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;

        assertTrue(completed, "All 100,000 requests should complete within 60 seconds");
        assertTrue(errors.isEmpty(), "No unexpected exceptions should occur under load: " + errors);
        assertEquals(numberOfUsers, results.size(), "Every distinct source should receive exactly one allocation");

        // Sanity check: every returned VIP must come from the configured pool.
        Set<String> validVips = Set.of("1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6");
        assertTrue(results.values().stream().allMatch(validVips::contains),
                "Every allocated VIP must come from the configured pool");

        System.out.println("Stress test: " + numberOfUsers + " concurrent allocations completed in " + durationMs + "ms");
    }
    @Test
    void shouldRebuildInMemoryStateFromDatabaseOnStartup() {

        // Arrange: simulate a database that already has 2 allocations,
        // as if the service had run before and these were persisted.
        VipAllocationRepository mockRepository = mock(VipAllocationRepository.class);
        VipPersistenceService mockPersistenceService = mock(VipPersistenceService.class);

        List<VipAllocationEntity> existingAllocations = List.of(
                new VipAllocationEntity("10.10.10.10", "127.0.0.1", "1.1.1.1"),
                new VipAllocationEntity("10.10.10.10", "127.0.0.2", "1.1.1.2")
        );

        when(mockRepository.findAll()).thenReturn(existingAllocations);

        // Act: construct a NEW service instance — this simulates a fresh
        // application restart, since @PostConstruct runs once per instance,
        // right after the constructor.
        ProxyVipService freshService = new ProxyVipService(mockRepository, mockPersistenceService);
        freshService.loadAllocationsFromDatabase();

        // Assert: the "restarted" service should already know about both
        // allocations, without ever calling allocate() again for them —
        // proving the reload correctly rebuilt PerSourceState from the DB rows.
        assertEquals("1.1.1.1", freshService.get("10.10.10.10", "127.0.0.1"));
        assertEquals("1.1.1.2", freshService.get("10.10.10.10", "127.0.0.2"));
    }
}