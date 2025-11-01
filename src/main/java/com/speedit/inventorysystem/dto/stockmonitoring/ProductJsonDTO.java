package com.speedit.inventorysystem.dto.stockmonitoring;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ProductJsonDTO {
    private int productId;
    private String productInfo;
    private BigDecimal price;
}