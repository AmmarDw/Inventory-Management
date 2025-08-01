package com.speedit.inventorysystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class OrderDTO {
    private Integer orderId;
    private String clientName;
    private String deliveryLocation;
}