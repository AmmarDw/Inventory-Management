package com.speedit.inventorysystem.dto.stockmonitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InventoryStockJsonDTO {
    private int inventoryStockId;
    private int inventoryId;
    private int productId;
    private Integer orderItemId; // Can be null
    private int amount;
    private Integer employeeId; // Can be null
}