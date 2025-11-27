package com.speedit.inventorysystem.service;

// Import the new DTO and Parsing service
import com.speedit.inventorysystem.dto.InventoryRequest;
import com.speedit.inventorysystem.dto.ParsedLocationData;
import com.speedit.inventorysystem.service.LocationParsingService;

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

    // 1. Inject the new LocationParsingService
    @Autowired
    private LocationParsingService locationParsingService;

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

    /**
     * Main logic function, now updated to handle location parsing.
     */
    private void updateInventoryFromRequest(Inventory inventory, InventoryRequest request) {

        // --- NEW LOCATION LOGIC ---

        // 2. Call the parser to get coordinates and a derived description
        ParsedLocationData locationData = locationParsingService.parseGoogleMapsLink(request.getGoogleMapsUrl());

        // 3. Decide which description to use
        String finalDescription;
        if (request.getLocationDescription() != null && !request.getLocationDescription().isBlank()) {
            // Use the user's preferred description if provided
            finalDescription = request.getLocationDescription();
        } else {
            // Otherwise, use the description derived from the coordinates
            finalDescription = locationData.getDescription();
        }

        // 4. Set all three location fields on the entity
        inventory.setLocation(finalDescription);
        inventory.setLatitude(BigDecimal.valueOf(locationData.getLatitude()));
        inventory.setLongitude(BigDecimal.valueOf(locationData.getLongitude()));

        // --- EXISTING LOGIC ---

        // Find the selected unit from the request
        MeasurementUnitEnum unit = MeasurementUnitEnum.valueOf(request.getCapacityUnit());

        // Use the enum to convert the input capacity to the base unit (cm³)
        BigDecimal capacityInCm3 = unit.convertToCubicCm(request.getCapacity());

        inventory.setInventoryType(InventoryTypeEnum.valueOf(request.getInventoryType()));
        inventory.setStatus(request.isStatus());
        inventory.setCapacity(capacityInCm3); // Save the converted value
    }
}