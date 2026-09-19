package com.rakesh.proxyvip.proxy_vip_api.entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "vip_allocation", uniqueConstraints = @UniqueConstraint(columnNames = {"source_ip", "destination_ip"}))
public class VipAllocationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_ip", nullable = false)
    private String sourceIp;

    @Column(name = "destination_ip", nullable = false)
    private String destinationIp;

    @Column(name = "vip", nullable = false)
    private String vip;


    public VipAllocationEntity(String sourceIp, String destinationIp, String vip) {
    }
}