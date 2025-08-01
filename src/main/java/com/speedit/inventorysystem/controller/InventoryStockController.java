package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.LoadStockDto;
import com.speedit.inventorysystem.dto.OrderDTO;
import com.speedit.inventorysystem.dto.ProductStockDTO;
import com.speedit.inventorysystem.dto.UnloadRequestDTO;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import com.speedit.inventorysystem.service.InventoryStockService;
import com.speedit.inventorysystem.service.ProductService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/inventory-stock")
public class InventoryStockController {

    private static final Logger logger = LoggerFactory.getLogger(InventoryStockController.class);
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

    // Serve HTML form
    @GetMapping("/unload")
    public String showUnloadForm(Model model) {
        model.addAttribute("inventories", inventoryRepository.findAll());
        return "unload-products";
    }

    // AJAX endpoint: Get orders for inventory
    @GetMapping("/orders")
    @ResponseBody
    public List<OrderDTO> getOrdersForInventory(
            @RequestParam Integer inventoryId) {
        return stockService.getOrdersForInventory(inventoryId);
    }

    // AJAX endpoint: Get products for inventory/order
    @GetMapping("/products")
    @ResponseBody
    public List<?> getProductsForInventoryAndOrder(
            @RequestParam Integer inventoryId,
            @RequestParam(required = false) Integer orderId) {
        try {
            return ResponseEntity.ok(stockService.getProductsForInventoryAndOrder(inventoryId, orderId)).getBody();
        } catch (Exception e) {
            logger.error("Error loading products", e);
            return Collections.singletonList(ResponseEntity.badRequest().body("Error loading products: " + e.getMessage()));
        }
    }

    // Process unload request
    @PostMapping("/unload")
    @ResponseBody
    public ResponseEntity<?> unloadProducts(
            @RequestBody UnloadRequestDTO unloadRequest) {
        try {
            stockService.unloadProducts(unloadRequest);
            return ResponseEntity.ok(new SimpleResponse(true, "Products unloaded successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(new SimpleResponse(false, e.getMessage()));
        }
    }

    // Simple response DTO
    @Getter
    @AllArgsConstructor
    private static class SimpleResponse {
        private boolean success;
        private String message;
    }
}
