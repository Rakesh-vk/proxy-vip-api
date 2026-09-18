package com.rakesh.proxyvip.proxy_vip_api.exception;

public class VipPoolExhaustedException extends RuntimeException{
    public VipPoolExhaustedException (String message){
        super("VIP pool Exception : "+ message);
    }
}
