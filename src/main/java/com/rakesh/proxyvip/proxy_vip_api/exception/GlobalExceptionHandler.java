package com.rakesh.proxyvip.proxy_vip_api.exception;

import com.rakesh.proxyvip.proxy_vip_api.dto.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(VipPoolExhaustedException.class)
    public ResponseEntity<ErrorResponse> vipPoolExhaustedExceptionHandler(
            VipPoolExhaustedException ex){
        log.warn("VIP pool exhausted: {}", ex.getMessage());
        ErrorResponse errorResponseDTO =
                new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponseDTO);
    }
    @ExceptionHandler(VipNotAllocated.class)
    public ResponseEntity<ErrorResponse> VipNotAllocatedHandler(
            VipNotAllocated ex){
        log.warn("VIP allocation lookup failed: {}", ex.getMessage());
        ErrorResponse errorResponseDTO =
                new ErrorResponse(
                        HttpStatus.NOT_FOUND.value(),
                        ex.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponseDTO);
    }

}
