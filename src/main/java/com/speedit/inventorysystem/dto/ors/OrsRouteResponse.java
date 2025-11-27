package com.speedit.inventorysystem.dto.ors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrsRouteResponse {
    private List<RouteFeature> features;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteFeature {
        private RouteGeometry geometry;
        private RouteProperties properties;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteGeometry {
        private List<List<Double>> coordinates; // [[lon, lat], [lon, lat], ...]
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RouteProperties {
        private Extras extras;
        private Summary summary;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Extras {
        private TimeInfo time;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TimeInfo {
        private List<List<Double>> values; // [[0, 1, 60.5], [1, 2, 69.7]] -> We parse this
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Summary {
        private double distance; // in meters
        private double duration; // in seconds
    }
}