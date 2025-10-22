package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.model.Inventory;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;


@Getter
@Setter
public class StockMonitorDTO {
    // Level 1 Data
    private Inventory inventory;
    private double fillLevelPercentage;
    private BigDecimal totalVolumeInStock;
}