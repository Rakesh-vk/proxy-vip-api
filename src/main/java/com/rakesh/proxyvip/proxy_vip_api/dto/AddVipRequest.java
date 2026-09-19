package com.rakesh.proxyvip.proxy_vip_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record AddVipRequest(
        @Pattern(
                regexp = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$",
                message = "must be a valid IPv4 address"
        )
        @NotBlank
        String newVip
) {
}
