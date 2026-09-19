package com.rakesh.proxyvip.proxy_vip_api.service;

import com.rakesh.proxyvip.proxy_vip_api.entity.VipAllocationEntity;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipNotAllocated;
import com.rakesh.proxyvip.proxy_vip_api.exception.VipPoolExhaustedException;
import com.rakesh.proxyvip.proxy_vip_api.repository.VipAllocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Slf4j
class ProxyVipServiceTest {


}