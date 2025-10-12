package com.speedit.inventorysystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ProductSummaryDTO {
    private Integer productId;
    private String displayText; // "ID - Options/Hierarchy"
}