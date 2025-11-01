package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.enums.UnitType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ContainerRequestDTO {

    // --- Fields for Parent Product Creation ---
    @NotNull
    @Positive
    private BigDecimal parentProductPrice; // Price of the new container product
    @NotNull private String distanceUnit;
    @NotNull private BigDecimal height;
    @NotNull private BigDecimal width;
    @NotNull private BigDecimal length;


    // --- Fields for Container Creation ---
    @NotNull private Integer childProductId; // ID of the existing product that this container holds

    @NotNull
    @Positive
    private Integer quantity; // How many child items are in this container

    @NotNull private UnitType unit; // The unit type (e.g., PACK, BOX)
}