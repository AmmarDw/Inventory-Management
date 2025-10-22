package com.speedit.inventorysystem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.speedit.inventorysystem.dto.StockMonitorDTO;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.service.StockMonitoringService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
@RequestMapping("/monitor-stock")
public class StockMonitoringController {
    @Autowired private InventoryRepository inventoryRepository;
    @Autowired private StockMonitoringService stockMonitoringService;
    @Autowired private ObjectMapper objectMapper; // Spring Boot provides this bean

    @GetMapping("/inventory/{id}")
    public String monitorInventoryStock(@PathVariable Integer id,
                                        @RequestParam(defaultValue = "product") String viewBy,
                                        Model model) throws Exception {
        Inventory inventory = inventoryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Inventory not found with id: " + id));

        Map<String, Object> dataMap = stockMonitoringService.prepareStockMonitorPageData(inventory);

        // Data for server-side rendering (Thymeleaf)
        model.addAttribute("pageData", dataMap.get("pageData"));
        model.addAttribute("viewBy", viewBy);

        // Full dataset, serialized to a JSON string for client-side JavaScript
        model.addAttribute("jsonData", objectMapper.writeValueAsString(dataMap.get("jsonData")));

        return "stock-monitoring/inventory-view";
    }

    // @GetMapping("/product/{id}")
    // public String monitorProductStock(...) { /* To be implemented later */ }

    // @GetMapping("/order/{id}")
    // public String monitorOrderStock(...) { /* To be implemented later */ }
}