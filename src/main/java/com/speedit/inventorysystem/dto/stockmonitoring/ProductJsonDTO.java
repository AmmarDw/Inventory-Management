package com.speedit.inventorysystem.dto.stockmonitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductJsonDTO {
    private int productId;
    private String productInfo;
    private double price; // Use double for decimal format like 1.50
}