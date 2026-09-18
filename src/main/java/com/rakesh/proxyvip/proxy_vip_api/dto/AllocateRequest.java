package com.rakesh.proxyvip.proxy_vip_api.dto;


import jakarta.validation.constraints.NotBlank;

public record AllocateRequest(
        @NotBlank String sourceIP,
        @NotBlank String destinationIP
) {}
