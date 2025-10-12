package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.controller.InventoryController.InventoryRequest;
import com.speedit.inventorysystem.enums.MeasurementUnitEnum;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.repository.InventoryRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class InventoryService {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Transactional
    public Inventory createInventory(InventoryRequest request) {
        Inventory inventory = new Inventory();
        updateInventoryFromRequest(inventory, request);
        return inventoryRepository.save(inventory);
    }

    @Transactional
    public Inventory updateInventory(Integer id, InventoryRequest request) {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found with id: " + id));
        updateInventoryFromRequest(inventory, request);
        return inventoryRepository.save(inventory);
    }

    private void updateInventoryFromRequest(Inventory inventory, InventoryRequest request) {
        // Find the selected unit from the request
        MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getCapacityUnit());

        // Use the enum to convert the input capacity to the base unit (cm³)
        BigDecimal capacityInCm3 = unit.convertToCubicCm(request.getCapacity());

        inventory.setInventoryType(InventoryTypeEnum.valueOf(request.getInventoryType()));
        inventory.setLocation(request.getLocation());
        inventory.setStatus(request.isStatus());
        inventory.setCapacity(capacityInCm3); // Save the converted value
    }
}