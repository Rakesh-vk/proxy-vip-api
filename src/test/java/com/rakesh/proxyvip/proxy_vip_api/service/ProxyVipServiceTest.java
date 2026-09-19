package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
    void shouldNotAlwaysAssignVipsInSequentialOrder(){

    }
}