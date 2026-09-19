package com.rakesh.proxyvip.proxy_vip_api.exception;

import com.rakesh.proxyvip.proxy_vip_api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(VipPoolExhaustedException.class)
    public ResponseEntity<ErrorResponse> vipPoolExhaustedExceptionHandler(
            VipPoolExhaustedException ex){
        ErrorResponse errorResponseDTO =
                new ErrorResponse(
                        HttpStatus.CONFLICT.value(),
                        ex.getMessage()
                );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponseDTO);
    }
}
