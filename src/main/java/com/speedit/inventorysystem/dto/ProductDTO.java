package com.speedit.inventorysystem.dto;

import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Getter
public class ProductDTO {
    private final Integer productId;
    private final String fullBarcode;
    private final Long price;
    private final List<ProductOptionDTO> productOptions;
    private final Integer totalStock;
    private final BigDecimal volume;
    private final BigDecimal height;
    private final BigDecimal width;
    private final BigDecimal length;
    private final LocalDateTime createdAt;
    private final String createdBy;
    private final LocalDateTime updatedAt;
    private final String updatedBy;
    private final byte[] barcodeImage;

    public ProductDTO(Integer productId, String fullBarcode, Long price,
                      List<ProductOptionDTO> productOptions, Integer totalStock,
                      BigDecimal volume, BigDecimal height, BigDecimal width, BigDecimal length,
                      LocalDateTime createdAt, String createdBy,
                      LocalDateTime updatedAt, String updatedBy,
                      byte[] barcodeImage) {
        this.productId = productId;
        this.fullBarcode = fullBarcode;
        this.price = price;
        this.productOptions = productOptions;
        this.totalStock = totalStock;
        this.volume = volume;
        this.height = height;
        this.width = width;
        this.length = length;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
        this.barcodeImage = barcodeImage;
    }

    @Getter
    public static class ProductOptionDTO {
        private final Integer optionId;
        private final String optionValue;
        private final Integer categoryId;
        private final String categoryName;

        public ProductOptionDTO(Integer optionId, String optionValue,
                                Integer categoryId, String categoryName) {
            this.optionId = optionId;
            this.optionValue = optionValue;
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}