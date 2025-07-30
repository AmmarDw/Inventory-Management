package com.speedit.inventorysystem.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter
public class LoadStockDto {
    @NotNull(message="Please select an inventory")
    private Integer inventoryId;

    @NotEmpty(message="Add at least one product")
    private List<@NotNull(message="Select a product") Integer> productIds;

    @NotEmpty
    private List<@PositiveOrZero(message="Amount must be ≥ 0") Integer> amounts;
}
