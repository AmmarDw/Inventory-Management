package com.speedit.inventorysystem.dto.ors;
import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;

@Data
@AllArgsConstructor
public class OptimizationRequest {
    private List<Job> jobs;
    private List<Vehicle> vehicles;

    @Data
    @AllArgsConstructor
    public static class Job {
        private int id;
        private double[] location; // [longitude, latitude]
    }

    @Data
    @AllArgsConstructor
    public static class Vehicle {
        private int id;
        private String profile = "driving-car";
        private double[] start; // [longitude, latitude]
        private double[] end;   // [longitude, latitude]
    }
}