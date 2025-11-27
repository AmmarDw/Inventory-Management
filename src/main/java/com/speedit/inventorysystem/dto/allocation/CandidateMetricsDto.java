package com.speedit.inventorysystem.dto.allocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated metrics for a candidate path.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateMetricsDto {
    /** Total distance in km for the candidate path. */
    private double distanceKm;

    /** Total travel time in seconds. */
    private long travelTimeSec;

    /** Total handling time (load/unload etc.) in seconds. */
    private double handlingTimeSec;

    /**
     * Unified pressure metric (0..1) representing how squeezed the critical node
     * (typically the van) will be if we allocate this candidate.
     */
    private double maxPressure;
}

