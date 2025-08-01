package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.model.Product;
import lombok.Getter;

@Getter
public class ProductStockDTO {
    private final Integer productId;
    private final String productOptionsDisplay;
    private final Long amount;

    public ProductStockDTO(Integer productId, Product product, Long amount) {
        this.productId = productId;
        this.productOptionsDisplay = product.getProductOptionsDisplay();
        this.amount = amount;
    }
}