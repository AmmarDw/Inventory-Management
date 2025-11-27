package com.speedit.inventorysystem.dto.allocation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Phase A result for a single OrderItem.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateGenerationResultDto {

    private Integer orderId;
    private Integer orderItemId;
    private Integer productId;
    private Integer requestedQuantity;

    /** Top K candidates for this OrderItem. */
    private List<PathCandidateDto> candidates;
}

