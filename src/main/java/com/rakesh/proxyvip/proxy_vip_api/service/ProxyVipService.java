package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.exception.VipNotAllocated;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class ProxyVipService {

    private final ConcurrentHashMap<String, PerSourceState> sourceStates = new ConcurrentHashMap<>();
    private final List<String> vipPool;
    // the global pool — populated at startup, grown by add()

    public ProxyVipService() {
        List<String> preConfiguredVips = List.of("1.1.1.1", "1.1.1.2", "1.1.1.3", "1.1.1.4", "1.1.1.5", "1.1.1.6");
        this.vipPool = new CopyOnWriteArrayList<>(preConfiguredVips);
    }

    public String allocate(String sourceIp, String destinationIp) {
        log.info("VIP allocation requested: sourceIP={}, destinationIP={}",
                sourceIp, destinationIp);

        PerSourceState state = sourceStates.computeIfAbsent(sourceIp, key -> new PerSourceState());
        synchronized (state) {
            return state.getVIP(destinationIp)
                    .map(vip -> {
                        log.info(
                                "Existing VIP returned: sourceIP={}, destinationIP={}, vip={}",
                                sourceIp, destinationIp, vip
                        );
                        return vip;
                    })
                    .orElseGet(() -> {
                        String vip = pickUnusedVip(state,sourceIp);
                        state.assignVIP(destinationIp, vip);
                        log.info(
                                "New VIP allocated: sourceIP={}, destinationIP={}, vip={}",
                                sourceIp, destinationIp, vip
                        );
                        return vip;
                    });
        }
    }

    private String pickUnusedVip(PerSourceState state,String sourceIp) {
        List<String> candidates = new ArrayList<>();
        for (String vip : vipPool) {
            if (!state.checkIfVipUsed(vip)) {
                candidates.add(vip);
            }
        }
        if (candidates.isEmpty()) {
            log.warn("VIP pool exhausted for sourceIP={}", sourceIp);
            throw new VipPoolExhaustedException(
                    "No available VIPs remaining for source IP allocation"
            );
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }

    public void addVip(String newVip) {
        vipPool.add(newVip);   // immediately visible to all future allocate() calls
        log.info("VIP added to pool: vip={}, poolSize={}",
                newVip, vipPool.size());

    }

    public List<String> getAll() {
        return vipPool;
    }

        public String get(String sourceIp, String destinationIp) {
            log.info("VIP lookup requested: sourceIP={}, destinationIP={}",
                    sourceIp, destinationIp);

            PerSourceState state = sourceStates.get(sourceIp);
            if (state == null) {
                log.warn(
                        "VIP lookup failed - sourceIP has no allocations: sourceIP={}, destinationIP={}",
                        sourceIp, destinationIp
                );

                throw new VipNotAllocated("VIP is not allocated ");
            }
            synchronized (state) {
                return state.getVIP(destinationIp)
                        .map(vip -> {
                            log.info(
                                    "VIP lookup successful: sourceIP={}, destinationIP={}, vip={}",
                                    sourceIp, destinationIp, vip
                            );
                            return vip;
                        })
                        .orElseThrow(() -> {
                            log.warn(
                                    "VIP lookup failed - allocation not found: sourceIP={}, destinationIP={}",
                                    sourceIp, destinationIp
                            );

                            return new VipNotAllocated("VIP is not allocated");
                        });
            }
        }

}
