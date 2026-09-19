package com.rakesh.proxyvip.proxy_vip_api.repository;

import com.rakesh.proxyvip.proxy_vip_api.entity.VipAllocationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VipAllocationRepository extends JpaRepository<VipAllocationEntity,Long> {
}
