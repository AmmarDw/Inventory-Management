package com.speedit.inventorysystem.dto.ors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OptimizationResponse {
    private Summary summary;
    private List<Route> routes;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        private double distance; // in meters
        private double duration; // in seconds
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Route {
        private List<Step> steps;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        private String type;
        private double[] location; // [longitude, latitude]
    }
}