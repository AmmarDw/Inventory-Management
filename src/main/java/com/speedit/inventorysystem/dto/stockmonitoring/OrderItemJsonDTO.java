package com.speedit.inventorysystem.dto.stockmonitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OrderItemJsonDTO {
    private int orderItemId;
    private int orderId;
    private int productId;
    private int quantity;
    private long discount;
}