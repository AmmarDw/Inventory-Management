package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.enums.InventoryTypeEnum;
import com.speedit.inventorysystem.repository.InventoryRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    @Autowired
    private InventoryRepository inventoryRepository;

    @GetMapping("/add")
    public String showAddInventoryForm(Model model) {
        model.addAttribute("inventory", new Inventory());
        model.addAttribute("types", InventoryTypeEnum.values());
        return "add-inventory";
    }

    @PostMapping("/add")
    public String addInventory(
            @Valid @ModelAttribute("inventory") Inventory inventory,
            BindingResult bindingResult,
            Model model
    ) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("types", InventoryTypeEnum.values());
            return "add-inventory";
        }

        inventory.setStatus(true);
        inventoryRepository.save(inventory);
        return "redirect:/inventory/list";
    }
}