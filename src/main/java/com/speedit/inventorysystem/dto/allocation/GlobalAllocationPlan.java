package com.speedit.inventorysystem.dto.allocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Global allocation plan across many orders / order items.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlobalAllocationPlan {

    @Builder.Default
    private List<OrderItemAllocationPlan> itemPlans = new ArrayList<>();

    /**
     * True if every OrderItem in the plan is fully allocated.
     */
    private boolean fullyAllocated;
}
