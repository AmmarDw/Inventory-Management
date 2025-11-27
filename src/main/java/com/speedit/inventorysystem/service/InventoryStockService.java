package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.controller.InventoryStockController;
import com.speedit.inventorysystem.dto.*;
import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.InventoryStock;
import com.speedit.inventorysystem.model.Product;
import com.speedit.inventorysystem.repository.InventoryRepository;
import com.speedit.inventorysystem.repository.InventoryStockRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class InventoryStockService {

    private static final Logger logger = LoggerFactory.getLogger(InventoryStockController.class);
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

    public List<OrderDTO> getOrdersForInventory(Integer inventoryId) {
        return stockRepo.findOrdersByInventory(inventoryId);
    }

    public List<ProductStockDTO> getProductsForInventoryAndOrder(
            Integer inventoryId, Integer orderId) {
        logger.info("Loading products for inventory: {}, orderId: {}", inventoryId, orderId);

        // Add debug logging for repository method
        List<ProductStockDTO> result = stockRepo.findProductsByInventoryAndOrder(inventoryId, orderId);
        logger.info("Query result: {}", result);

        // Check if any products have positive amounts
        boolean hasPositiveAmounts = result.stream()
                .anyMatch(dto -> dto.getAmount() > 0);

        logger.info("Found {} products with positive amounts: {}", result.size(), hasPositiveAmounts);
        return result;
    }

    @Transactional
    public void unloadProducts(UnloadRequestDTO request) {
        for (ProductUnloadDTO product : request.getProducts()) {
            // Find existing stock record
            InventoryStock stock;
            if (request.getOrder() == null) {
                // Available stock
                stock = stockRepo.findByInventoryInventoryIdAndProductProductIdAndOrderItemIsNull(
                                request.getSourceInventoryId(), product.getProductId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Available stock not found for product: " + product.getProductId()));
            } else {
                // Order-bound stock
                stock = stockRepo.findByInventoryInventoryIdAndProductProductIdAndOrderItemOrderOrderId(
                                request.getSourceInventoryId(), product.getProductId(), request.getOrder().getOrderId())
                        .orElseThrow(() -> new IllegalArgumentException(
                                "Stock for order #" + request.getOrder().getOrderId() +
                                        " not found for product: " + product.getProductId()));
            }

            int currentAmount = stock.getAmount();
            int transportAmount = product.getAmount();

            if (transportAmount > currentAmount) {
                throw new IllegalArgumentException("Transport amount (" + transportAmount +
                        ") exceeds available stock (" + currentAmount +
                        ") for product: " + product.getProductId());
            }

            if (transportAmount == currentAmount) {
                // Full unload
                handleFullUnload(request, stock);
            } else {
                // Partial unload
                handlePartialUnload(request, stock, transportAmount);
            }
        }
    }

    private void handleFullUnload(UnloadRequestDTO request, InventoryStock stock) {
        if ("inventory".equals(request.getDestinationType())) {
            // Transfer to another inventory
            Inventory destination = invRepo.findById(request.getDestinationInventoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid destination inventory"));
            stock.setInventory(destination);
            stockRepo.save(stock);
        } else {
            // Deliver to client - remove from inventory
            if (stock.getOrderItem() == null) {
                // TODO For available stock, we need to associate with order
                if (request.getOrder() == null) {
                    throw new IllegalArgumentException("Cannot deliver available stock to client without an order");
                }
                // Create order item association (implementation depends on your business logic)
                // This would require additional logic to associate with order
                throw new UnsupportedOperationException("Delivering available stock to client is not implemented");
            }
            // For order-bound stock, just remove from inventory
            stock.setInventory(null);
            stockRepo.save(stock);
        }
    }

    private void handlePartialUnload(UnloadRequestDTO request, InventoryStock stock, int transportAmount) {
        // Reduce existing stock
        stock.setAmount(stock.getAmount() - transportAmount);
        stockRepo.save(stock);

        // Create new stock entry
        InventoryStock newStock = new InventoryStock();
        newStock.setProduct(stock.getProduct());
        newStock.setOrderItem(stock.getOrderItem());
        newStock.setAmount(transportAmount);

        if ("inventory".equals(request.getDestinationType())) {
            Inventory destination = invRepo.findById(request.getDestinationInventoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Invalid destination inventory"));
            newStock.setInventory(destination);
        } else {
            // Deliver to client - no inventory
            newStock.setInventory(null);
        }
        stockRepo.save(newStock);
    }
}