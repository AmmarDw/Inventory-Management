package com.speedit.inventorysystem.dto.allocation;

import com.speedit.inventorysystem.model.OrderItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single allocated piece from a candidate to an OrderItem.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AllocationChunk {

    private OrderItem orderItem;

    /**
     * The candidate chosen for this chunk. The candidate already holds
     * primaryInventoryStock and movements to apply.
     */
    private PathCandidateDto candidate;

    /**
     * How many units of the product we allocate from this candidate to the order item.
     */
    private int quantity;
}
