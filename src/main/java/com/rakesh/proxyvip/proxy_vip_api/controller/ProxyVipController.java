package com.rakesh.proxyvip.proxy_vip_api.controller;

import com.rakesh.proxyvip.proxy_vip_api.dto.AddVipRequest;
import com.rakesh.proxyvip.proxy_vip_api.dto.AllocateRequest;
import com.rakesh.proxyvip.proxy_vip_api.dto.AllocateResponse;
import com.rakesh.proxyvip.proxy_vip_api.dto.addVipResponse;
import com.rakesh.proxyvip.proxy_vip_api.service.ProxyVipService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/vip")
@RequiredArgsConstructor
public class ProxyVipController {
    private final ProxyVipService proxyVipService;

    @GetMapping
    public ResponseEntity<List<String>> getAllVips(){
        System.out.println("reading all vips");
        return new ResponseEntity<>(proxyVipService.getAll(),HttpStatus.OK);
    }


    @PostMapping("/allocate")
    public ResponseEntity<AllocateResponse> allocate(
            @RequestBody @Valid AllocateRequest request){
        String allocate = proxyVipService.allocate(
                request.sourceIP(), request.destinationIP());
        AllocateResponse response= new AllocateResponse(allocate);
        return new ResponseEntity<>(response,HttpStatus.OK);
    }
    @PostMapping("/add")
    public ResponseEntity<addVipResponse> addVIP(@RequestBody @Valid AddVipRequest vip){
        proxyVipService.addVip(vip.toString());
        addVipResponse response= new addVipResponse(true);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

}
