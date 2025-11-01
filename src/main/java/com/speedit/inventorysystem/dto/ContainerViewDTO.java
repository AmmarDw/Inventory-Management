package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.enums.UnitType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map; // If you need to pass the hierarchy map directly, or create a specific DTO for it

@Getter
@Setter
@NoArgsConstructor
public class ContainerViewDTO {

    private Integer containerId;
    private Integer parentProductId; // The ID of the product this container represents
    // Hierarchy data - You can pass the Map structure directly or flatten it
    // Option 1: Pass the Map (e.g., List of levels, final product options)
    // Option 2: Create a flat string representation (like in the table)
    // Let's go with Option 1 for flexibility, similar to how it's used in the table
    private List<Map<String, Object>> hierarchyLevels; // From containersMap['levels']
    private String finalProductOptions; // From containersMap['finalProductOptions']
    private Integer finalProductId;    // From containersMap['finalProductId']

    private BigDecimal volume;
    private BigDecimal height;
    private BigDecimal width;
    private BigDecimal length;
    private BigDecimal price; // Price of the parent product
    private Integer totalStock; // Calculated stock for the parent product
    private String fullBarcode; // Full barcode string for the parent product

    // Audit fields from BaseEntity (of Container or Parent Product?)
    // Let's assume from Parent Product for consistency with Product Details
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;
}
