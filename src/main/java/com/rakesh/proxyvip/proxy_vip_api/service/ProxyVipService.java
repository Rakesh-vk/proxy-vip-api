package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class ProxyVipService {

    private final ConcurrentHashMap<String, PerSourceState> sourceStates = new ConcurrentHashMap<>();
    private final List<String> vipPool;
    // the global pool — populated at startup, grown by add()
    List<String> initialVips= Arrays.asList
            ("1.1.1.1","1.1.1.2","1.1.1.3","1.1.1.4","1.1.1.5","1.1.1.6");
    public ProxyVipService(List<String> initialVips) {
        System.out.println("initializing initial vips");
        this.vipPool = new CopyOnWriteArrayList<>(initialVips);
    }

    public String allocate(String sourceIp, String destinationIp) {
        PerSourceState state = sourceStates.computeIfAbsent(sourceIp, key -> new PerSourceState());
        System.out.println(state);
        synchronized (state) {
            return state.getVIP(destinationIp)
                    .orElseGet(() -> {
                        String vip = pickUnusedVip(state);
                        state.assignVIP(destinationIp, vip);
                        return vip;
                    });
        }
    }

    private String pickUnusedVip(PerSourceState state) {
        List<String> candidates = new ArrayList<>();
        for (String vip : vipPool) {
            if (!state.checkIfVipUsed(vip)) {
                candidates.add(vip);
            }
        }
        if (candidates.isEmpty()) {
            throw new VipPoolExhaustedException(
                    "No available VIPs remaining for source IP allocation"
            );
        }
        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        return candidates.get(randomIndex);
    }

    public boolean addVip(String newVip) {
        vipPool.add(newVip);   // immediately visible to all future allocate() calls
        System.out.println("available VIPs are : "+ vipPool);
        return true;
    }

    public List<String> getAll() {
        for(String vip:vipPool){
            System.out.println(vip);
        }
        return vipPool;
    }
}
