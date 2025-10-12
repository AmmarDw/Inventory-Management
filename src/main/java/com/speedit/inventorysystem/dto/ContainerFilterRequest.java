package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.enums.UnitType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/**
 * Data Transfer Object for container filtering criteria.
 */
@Getter
@Setter
@NoArgsConstructor
public class ContainerFilterRequest {

    // --- Container-related filters ---
    private Integer minQuantity;
    private Integer maxQuantity;
    private UnitType unit; // Filters for a specific unit type
    private BigDecimal minVolume;
    private BigDecimal maxVolume;

    // --- Base Product-related filters (applied to the final product in the strain) ---
    private Long minPrice; // Price of the final base product
    private Long maxPrice; // Price of the final base product
    private Integer minStock; // Total stock of the final base product
    private List<Integer> optionIds; // Option IDs that the final base product must have

    // --- Pagination ---
    // For Keyset Pagination (efficient for Next/Prev)
    private Integer lastContainerId;  // ID of the last container on the current page (for 'Next')
    private Integer firstContainerId; // ID of the first container on the current page (for 'Previous')
    private String direction;         // "NEXT" or "PREVIOUS"
    private int pageSize = 20;        // Desired page size
}