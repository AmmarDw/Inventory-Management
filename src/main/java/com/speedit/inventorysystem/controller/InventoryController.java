package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.InventoryDTO;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.service.InventoryService; // ✨ NEW import
import jakarta.validation.Valid;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @GetMapping("/manage")
    public String manageInventory(Model model) {
        List<Inventory> inventories = inventoryRepository.findAll();
        model.addAttribute("inventories", inventories);
        model.addAttribute("types", InventoryTypeEnum.values());
        return "manage-inventory";
    }

    @GetMapping("/{id}/details")
    @ResponseBody
    public ResponseEntity<InventoryDTO> getInventoryDetails(@PathVariable Integer id) {
        return inventoryRepository.findById(id)
                .map(InventoryDTO::new)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createInventory(@Valid @RequestBody InventoryRequest request,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
        }
        inventoryService.createInventory(request);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateInventory(@PathVariable Integer id,
                                             @Valid @RequestBody InventoryRequest request,
                                             BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return ResponseEntity.badRequest().body(bindingResult.getAllErrors());
        }
        inventoryService.updateInventory(id, request);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<?> deleteInventory(@PathVariable Integer id) {
        if (inventoryRepository.existsById(id)) {
            inventoryRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Data
    public static class InventoryRequest {
        private String inventoryType;
        private String location;
        private boolean status;
        private BigDecimal capacity;
        private String capacityUnit;
    }
}