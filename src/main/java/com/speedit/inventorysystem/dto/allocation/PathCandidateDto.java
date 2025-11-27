package com.speedit.inventorysystem.dto.allocation;

import com.speedit.inventorysystem.model.Inventory;
import com.speedit.inventorysystem.model.InventoryStock;
import com.speedit.inventorysystem.model.StockMovement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A candidate path to fulfill (part of) an OrderItem from one primary InventoryStock.
 * Multi-source / multi-order consolidation happens later in Phase B on top of these.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PathCandidateDto {

    /** The primary stock row this candidate is built from (warehouse or van). */
    private InventoryStock primaryInventoryStock;

    /** Product id (shortcut). */
    private Integer productId;

    /** The van that will deliver to the client (if any). */
    private Inventory deliveringVan;

    /**
     * Maximum total units this candidate could deliver for its OrderItem,
     * given source stock and van capacity constraints.
     */
    private int maxFeasibleAmount;

    /**
     * List of planned movements (legs) that this candidate would require.
     * These are NOT persisted in Phase A; they are just in-memory.
     */
    private List<StockMovement> movements;

    /** Raw and derived metrics used for scoring. */
    private CandidateMetricsDto metrics;

    /** Normalized weighted score – lower is better. */
    private double provisionalScore;

    /** Pattern label: e.g. "VAN->CLIENT", "WH->VAN->CLIENT". */
    private String pattern;
}

