package com.speedit.inventorysystem.dto.allocation;

import com.speedit.inventorysystem.model.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Allocation plan for one OrderItem.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemAllocationPlan {

    private OrderItem orderItem;
    private int requestedQuantity;
    private int allocatedQuantity;

    @Builder.Default
    private List<AllocationChunk> chunks = new ArrayList<>();

    public boolean isFullyAllocated() {
        return allocatedQuantity >= requestedQuantity;
    }
}
