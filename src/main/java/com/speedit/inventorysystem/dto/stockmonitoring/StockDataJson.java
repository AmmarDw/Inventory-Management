package com.speedit.inventorysystem.dto.stockmonitoring;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class StockDataJson {
    private List<ProductJsonDTO> products;

    @JsonProperty("inventory-stocks") // Match the JSON key
    private List<InventoryStockJsonDTO> inventoryStocks;

    private List<UserJsonDTO> users;
    private List<OrderJsonDTO> orders;

    @JsonProperty("order-items") // Match the JSON key
    private List<OrderItemJsonDTO> orderItems;
}