package com.speedit.inventorysystem.dto;

import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.model.Inventory;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class InventoryDTO {
    private Integer inventoryId;
    private String inventoryType;  // Store as string
    private String inventoryTypeDisplay;  // Add display name
    private String location;
    private boolean status;
    private BigDecimal capacity;
    private LocalDateTime createdAt;
    private String createdBy;
    private LocalDateTime updatedAt;
    private String updatedBy;

    // Constructor from Inventory entity
    public InventoryDTO(Inventory inventory) {
        this.inventoryId = inventory.getInventoryId();
        this.inventoryType = inventory.getInventoryType().name();  // Store enum name
        this.inventoryTypeDisplay = inventory.getInventoryType().getDisplayName();  // Store display name
        this.location = inventory.getLocation();
        this.status = inventory.isStatus();
        this.capacity = inventory.getCapacity();
        this.createdAt = inventory.getCreatedAt();
        this.createdBy = inventory.getCreatedBy();
        this.updatedAt = inventory.getUpdatedAt();
        this.updatedBy = inventory.getUpdatedBy();
    }
}