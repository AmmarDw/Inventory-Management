package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.LoadStockDto;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import com.speedit.inventorysystem.service.InventoryStockService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/inventory-stock")
public class InventoryStockController {

    @Autowired private InventoryStockService stockService;
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private ProductRepository productRepository;

    @GetMapping("/load")
    public String showLoadForm(Model model) {
        model.addAttribute("inventories", inventoryRepository.findAll());
        model.addAttribute("products", productRepository.findAll());
        model.addAttribute("loadDto", new LoadStockDto());
        return "load-new-products";
    }

    @PostMapping("/load")
    public String loadStocks(
            @ModelAttribute("loadDto") @Valid LoadStockDto dto,
            BindingResult br,
            Model model) {

        if (br.hasErrors()) {
            model.addAttribute("inventories", inventoryRepository.findAll());
            model.addAttribute("products", productRepository.findAll());
            return "load-new-products";
        }

        try {
            stockService.loadNewStocks(dto);
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            model.addAttribute("inventories", inventoryRepository.findAll());
            model.addAttribute("products", productRepository.findAll());
            return "load-new-products";
        }
        return "redirect:/inventory/manage";
    }
}
