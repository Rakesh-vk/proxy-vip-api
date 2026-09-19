package com.rakesh.proxyvip.proxy_vip_api.controller;

import com.rakesh.proxyvip.proxy_vip_api.dto.AddVipRequest;
import com.rakesh.proxyvip.proxy_vip_api.dto.AllocateRequest;
import com.rakesh.proxyvip.proxy_vip_api.dto.AllocateResponse;
import com.rakesh.proxyvip.proxy_vip_api.dto.addVipResponse;
import com.rakesh.proxyvip.proxy_vip_api.entity.VipAllocationEntity;
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

    @GetMapping("/getAllVips")
    public ResponseEntity<List<String>> getAllVips(){
        return new ResponseEntity<>(proxyVipService.getAll(),HttpStatus.OK);
    }
    @GetMapping("/getVip/{sourceIP}/{destinationIP}")
    public ResponseEntity<String> getVip(@PathVariable String sourceIP,@PathVariable String destinationIP){
        return new ResponseEntity<>(proxyVipService.get(sourceIP,destinationIP),HttpStatus.OK);
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
        proxyVipService.addVip(vip.newVip());
        addVipResponse response = new addVipResponse(true);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @GetMapping("/allocations")
    public ResponseEntity<List<VipAllocationEntity>> getAllAllocations() {

        List<VipAllocationEntity> allocations =
                proxyVipService.getAllAllocations();

        return  ResponseEntity.ok(allocations);
    }
    @DeleteMapping("/removeAllVips")
    public ResponseEntity<Void> deleteAllMapping(){
        proxyVipService.removeRecords();
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
