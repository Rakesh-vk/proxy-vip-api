package com.rakesh.proxyvip.proxy_vip_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ProxyVipApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(ProxyVipApiApplication.class, args);
	}

}
