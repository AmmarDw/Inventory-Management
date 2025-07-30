package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.dto.LoadStockDto;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.InventoryStock;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryStockService {

    @Autowired private InventoryStockRepository stockRepo;
    @Autowired private InventoryRepository invRepo;
    @Autowired private ProductRepository prodRepo;

    @Transactional
    public void loadNewStocks(LoadStockDto dto) {
        Inventory inv = invRepo.findById(dto.getInventoryId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid inventory"));

        for (int i = 0; i < dto.getProductIds().size(); i++) {
            Product p = prodRepo.findById(dto.getProductIds().get(i))
                    .orElseThrow(() -> new IllegalArgumentException("Invalid product"));
            Integer amount = dto.getAmounts().get(i);

            // Check for existing stock record
            Optional<InventoryStock> existingStock = stockRepo.findByInventoryAndProductAndOrderItemIsNull(inv, p);

            if (existingStock.isPresent()) {
                // Update existing record
                InventoryStock stock = existingStock.get();
                stock.setAmount(stock.getAmount() + amount);
                stockRepo.save(stock);
            } else {
                // Create new record
                InventoryStock st = new InventoryStock();
                st.setInventory(inv);
                st.setProduct(p);
                st.setOrderItem(null);
                st.setAmount(amount);
                stockRepo.save(st);
            }
        }
    }
}