package com.speedit.inventorysystem.dto;

import lombok.Data;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

// TODO make sure that the controller and service are following the update fully
@Data
public class InventoryRequest {

    @NotBlank(message = "Inventory type is required")
    private String inventoryType;

    /**
     * The mandatory, shareable Google Maps link.
     * e.g., "http://googleusercontent.com/maps/k/l/j/v"
     */
    @NotBlank(message = "A Google Maps link is required")
    private String googleMapsUrl;

    /**
     * An optional, user-preferred description for the location.
     * If left blank, a description will be generated from the Google Maps link.
     */
    private String locationDescription;

    private boolean status;

    @NotNull(message = "Capacity is required")
    private BigDecimal capacity;

    @NotBlank(message = "Capacity unit is required")
    private String capacityUnit;
}