package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.entity.VipAllocationEntity;
import com.rakesh.proxyvip.proxy_vip_api.repository.VipAllocationRepository;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j

public class VipPersistenceService {

    private final VipAllocationRepository vipAllocationRepository;

    public VipPersistenceService(VipAllocationRepository vipAllocationRepository) {
        this.vipAllocationRepository = vipAllocationRepository;
    }

    @Async
    public void saveAllocationAsync(String sourceIp, String destinationIp, String vip) {
        try {
            VipAllocationEntity entity = new VipAllocationEntity(sourceIp, destinationIp, vip);
            vipAllocationRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist VIP allocation to database: sourceIP={}, destinationIP={}, vip={}",
                    sourceIp, destinationIp, vip, e);
        }
    }
}