package com.speedit.inventorysystem.dto.stockmonitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderJsonDTO {
    private int orderId;
    private String status;
    private int clientId;
    private int supervisorId;
    private String deliveryLocation;
    private long totalOrderItems;
    private int totalOrderItemsQuantities;
}