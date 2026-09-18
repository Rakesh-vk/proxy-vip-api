package com.rakesh.proxyvip.proxy_vip_api.service;

import java.util.*;

public class PerSourceState {

    private Map<String, String> destinationToVip = new HashMap<>();
    private Set<String> usedVips = new HashSet<>();
    public Optional<String> getVIP(String destinationIP) {
        return Optional.ofNullable(destinationToVip.get(destinationIP));
    }
    public void assignVIP(String destinationIP, String vip) {
        usedVips.add(vip);
        destinationToVip.put(destinationIP, vip);
    }
    public boolean checkIfVipUsed(String vip ){
       return usedVips.contains(vip);}


}