package com.speedit.inventorysystem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RouteDetails {
    private double distanceInMeters;
    private double durationInSeconds;
}