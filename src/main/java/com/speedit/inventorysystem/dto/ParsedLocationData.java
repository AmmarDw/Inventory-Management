package com.speedit.inventorysystem.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ParsedLocationData {
    private String description;
    private double latitude;
    private double longitude;
}