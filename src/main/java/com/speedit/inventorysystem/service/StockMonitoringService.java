package com.speedit.inventorysystem.service;

import com.speedit.inventorysystem.dto.StockMonitorDTO;
import com.speedit.inventorysystem.dto.stockmonitoring.*;
import com.speedit.inventorysystem.model.*;
import com.speedit.inventorysystem.repository.OrderItemRepository;
import com.speedit.inventorysystem.repository.OrderRepository;
import com.speedit.inventorysystem.repository.ProductRepository;
import com.speedit.inventorysystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class StockMonitoringService {

    @Autowired private ContainerService containerService;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;

    public Map<String, Object> prepareStockMonitorPageData(Inventory inventory) {
        List<InventoryStock> stockRecords = inventory.getInventoryStocks();

        // 1. Prepare the simple DTO for Thymeleaf
        StockMonitorDTO pageData = new StockMonitorDTO();
        pageData.setInventory(inventory);
        calculateFillLevel(pageData, inventory, stockRecords);

        // 2. Prepare the large, flat JSON data object for JavaScript
        StockDataJson jsonData = buildJsonData(stockRecords);

        // 3. Return both objects in a map
        return Map.of(
                "pageData", pageData,
                "jsonData", jsonData
        );
    }

    private void calculateFillLevel(StockMonitorDTO pageData, Inventory inventory, List<InventoryStock> stockRecords) {
        if (inventory.getCapacity() == null || inventory.getCapacity().compareTo(BigDecimal.ZERO) == 0) {
            pageData.setTotalVolumeInStock(BigDecimal.ZERO);
            pageData.setFillLevelPercentage(0.0);
            return;
        }

        BigDecimal totalVolume = stockRecords.stream()
                .map(stock -> stock.getProduct().getVolume().multiply(new BigDecimal(stock.getAmount())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Set the total volume on the DTO
        pageData.setTotalVolumeInStock(totalVolume);

        BigDecimal percentage = totalVolume.divide(inventory.getCapacity(), 4, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        pageData.setFillLevelPercentage(percentage.doubleValue());
    }

    private StockDataJson buildJsonData(List<InventoryStock> stockRecords) {
        // --- Step 1: Collect all unique IDs from the stock records ---
        Set<Integer> productIds = stockRecords.stream().map(s -> s.getProduct().getProductId()).collect(Collectors.toSet());
        Set<Integer> employeeIds = stockRecords.stream().map(InventoryStock::getEmployee).filter(Objects::nonNull).map(User::getUserId).collect(Collectors.toSet());
        Set<Integer> orderItemIds = stockRecords.stream().map(InventoryStock::getOrderItem).filter(Objects::nonNull).map(OrderItem::getOrderItemId).collect(Collectors.toSet());

        // --- Step 2: Fetch related entities in bulk ---
        List<OrderItem> orderItems = orderItemIds.isEmpty() ? List.of() : orderItemRepository.findAllById(orderItemIds);
        Set<Integer> orderIds = orderItems.stream().map(oi -> oi.getOrder().getOrderId()).collect(Collectors.toSet());
        List<Order> orders = orderIds.isEmpty() ? List.of() : orderRepository.findAllById(orderIds);
        Set<Integer> clientIds = orders.stream().map(o -> o.getClient().getUserId()).collect(Collectors.toSet());
        Set<Integer> supervisorIds = orders.stream().map(o -> o.getSupervisor().getUserId()).collect(Collectors.toSet());

        Set<Integer> allUserIds = Stream.of(employeeIds, clientIds, supervisorIds).flatMap(Set::stream).collect(Collectors.toSet());
        List<User> users = allUserIds.isEmpty() ? List.of() : userRepository.findAllById(allUserIds);
        List<Product> products = productIds.isEmpty() ? List.of() : productRepository.findAllById(productIds);

        // --- Step 3: Map entities to their corresponding JSON DTOs ---
        StockDataJson data = new StockDataJson();

        data.setProducts(products.stream().map(p -> new ProductJsonDTO(
                p.getProductId(),
                containerService.buildProductInfoString(p),
                p.getPrice() // Assuming price is stored in dollars
        )).collect(Collectors.toList()));

        data.setInventoryStocks(stockRecords.stream().map(s -> new InventoryStockJsonDTO(
                s.getInventoryStockId(),
                s.getInventory().getInventoryId(),
                s.getProduct().getProductId(),
                s.getOrderItem() != null ? s.getOrderItem().getOrderItemId() : null,
                s.getAmount(),
                s.getEmployee() != null ? s.getEmployee().getUserId() : null
        )).collect(Collectors.toList()));

        data.setUsers(users.stream().map(u -> new UserJsonDTO(
                u.getUserId(),
                u.getName()
        )).collect(Collectors.toList()));

        // Create a map of OrderItem lists per Order for quick lookup
        Map<Integer, List<OrderItem>> itemsByOrderId = orderItems.stream().collect(Collectors.groupingBy(oi -> oi.getOrder().getOrderId()));

        data.setOrders(orders.stream().map(o -> {
            List<OrderItem> itemsForThisOrder = itemsByOrderId.getOrDefault(o.getOrderId(), List.of());
            int totalQuantities = itemsForThisOrder.stream().mapToInt(OrderItem::getQuantity).sum();
            return new OrderJsonDTO(
                    o.getOrderId(),
                    o.getOrderStatus().name(),
                    o.getClient().getUserId(),
                    o.getSupervisor().getUserId(),
                    o.getDeliveryLocation(),
                    itemsForThisOrder.size(),
                    totalQuantities
            );
        }).collect(Collectors.toList()));

        data.setOrderItems(orderItems.stream().map(oi -> new OrderItemJsonDTO(
                oi.getOrderItemId(),
                oi.getOrder().getOrderId(),
                oi.getProduct().getProductId(),
                oi.getQuantity(),
                oi.getDiscount()
        )).collect(Collectors.toList()));

        return data;
    }
}