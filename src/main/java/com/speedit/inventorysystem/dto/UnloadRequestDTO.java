package com.speedit.inventorysystem.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UnloadRequestDTO {
    private Integer sourceInventoryId;
    private String destinationType;
    private Integer destinationInventoryId;
    private OrderDTO order;
    private List<ProductUnloadDTO> products;
}
