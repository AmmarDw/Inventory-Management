package com.speedit.inventorysystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.speedit.inventorysystem.dto.Coordinates;
import com.speedit.inventorysystem.dto.RouteDetails;
import com.speedit.inventorysystem.dto.ors.OptimizationRequest;
import com.speedit.inventorysystem.dto.ors.OptimizationResponse;
import com.speedit.inventorysystem.dto.ors.OrsRouteResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RoutingService {

    @Autowired
    private WebClient webClient;
    @Value("${ors.api.key}")
    private String orsApiKey;

    /**
     * Gets simple distance and duration.
     */
    public RouteDetails getRouteDetails(Coordinates start, Coordinates end) {
        JsonNode response = webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.openrouteservice.org")
                        .path("/v2/directions/driving-car")
                        .queryParam("api_key", orsApiKey)
                        .queryParam("start", start.getLongitude() + "," + start.getLatitude())
                        .queryParam("end", end.getLongitude() + "," + end.getLatitude())
                        .build())
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        JsonNode summary = response.get("features").get(0).get("properties").get("summary");
        double distance = summary.get("distance").asDouble();
        double duration = summary.get("duration").asDouble();
        return new RouteDetails(distance, duration);
    }

    /**
     * Gets the full route data needed for interpolation.
     */
    public OrsRouteResponse getFullRouteData(Coordinates start, Coordinates end) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .scheme("https")
                        .host("api.openrouteservice.org")
                        .path("/v2/directions/driving-car")
                        .queryParam("api_key", orsApiKey)
                        .queryParam("start", start.getLongitude() + "," + start.getLatitude())
                        .queryParam("end", end.getLongitude() + "," + end.getLatitude())
                        .queryParam("extras", "[\"time\"]") // Request time for each point
                        .queryParam("geometry_format", "geojson")
                        .build())
                .retrieve()
                .bodyToMono(OrsRouteResponse.class)
                .block();
    }

    /**
     * Finds the driver's theoretical location after a certain amount of time.
     */
    public Coordinates findLocationAfterDuration(OrsRouteResponse routeData, double elapsedTimeInSeconds) {
        List<List<Double>> coordinates = routeData.getFeatures().get(0).getGeometry().getCoordinates();

        // ORS returns time values in a complex [[index_start, index_end, time], ...] format.
        // We need to flatten this into a simple list of time values.
        List<Double> timeValues = routeData.getFeatures().get(0).getProperties().getExtras().getTime().getValues()
                .stream()
                .map(val -> val.get(2)) // Get the 3rd element (time)
                .collect(Collectors.toList());

        // Add the start time (0 seconds)
        timeValues.add(0, 0.0);

        // 1. Handle edge cases
        if (elapsedTimeInSeconds <= 0) {
            return new Coordinates(coordinates.get(0).get(1), coordinates.get(0).get(0)); // At start
        }
        double totalDuration = timeValues.get(timeValues.size() - 1);
        if (elapsedTimeInSeconds >= totalDuration) {
            List<Double> lastCoord = coordinates.get(coordinates.size() - 1);
            return new Coordinates(lastCoord.get(1), lastCoord.get(0)); // At end
        }

        // 2. Find the two points the driver is between
        for (int i = 1; i < timeValues.size(); i++) {
            double timeAtPointB = timeValues.get(i);

            if (timeAtPointB >= elapsedTimeInSeconds) {
                // The driver is between point i-1 and point i
                double timeAtPointA = timeValues.get(i - 1);
                List<Double> coordA = coordinates.get(i - 1); // [lon, lat]
                List<Double> coordB = coordinates.get(i);     // [lon, lat]

                // 3. Interpolate the exact location
                double segmentTime = timeAtPointB - timeAtPointA;
                double timeIntoSegment = elapsedTimeInSeconds - timeAtPointA;

                // Avoid division by zero if segmentTime is 0
                double factor = (segmentTime == 0) ? 0 : (timeIntoSegment / segmentTime);

                double currentLon = coordA.get(0) + (coordB.get(0) - coordA.get(0)) * factor;
                double currentLat = coordA.get(1) + (coordB.get(1) - coordA.get(1)) * factor;

                return new Coordinates(currentLat, currentLon);
            }
        }

        // Fallback
        List<Double> lastCoord = coordinates.get(coordinates.size() - 1);
        return new Coordinates(lastCoord.get(1), lastCoord.get(0));
    }

    /**
     * Checks if two coordinates are in different cities by reverse-geocoding both.
     *
     * @param coord1 The first location's coordinates.
     * @param coord2 The second location's coordinates.
     * @return true if cities are different, false if they are the same or if city data is unavailable.
     */
    public boolean isInDifferentCities(Coordinates coord1, Coordinates coord2) {
        String city1 = getCityFromCoordinates(coord1);
        String city2 = getCityFromCoordinates(coord2);

        // If we can't find a city name for either one, we can't confirm
        // they are different, so we conservatively return false.
        if (city1 == null || city1.isEmpty() || city2 == null || city2.isEmpty()) {
            return false;
        }

        // Return true if the city names are NOT equal (case-insensitive)
        return !city1.equalsIgnoreCase(city2);
    }

    /**
     * Helper function to call the ORS API and get the city (locality) for a coordinate.
     */
    private String getCityFromCoordinates(Coordinates coords) {
        try {
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openrouteservice.org")
                            .path("/geocode/reverse")
                            .queryParam("api_key", orsApiKey)
                            .queryParam("point.lon", coords.getLongitude())
                            .queryParam("point.lat", coords.getLatitude())
                            .queryParam("layers", "locality") // <-- Ask specifically for the city
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(); // Blocking for simplicity, consistent with other methods

            if (response != null && response.has("features") && response.get("features").size() > 0) {
                JsonNode properties = response.get("features").get(0).get("properties");

                // Check if the "locality" field (city) exists and return it
                if (properties.has("locality")) {
                    return properties.get("locality").asText();
                }
            }
            return null; // No city found
        } catch (Exception e) {
            // Log the error (e.g., e.printStackTrace())
            return null; // Return null on API error
        }
    }

    /**
     * Calculates the shortest path (TSP) for a list of locations
     * and returns a Google Maps link for the optimized route.
     * The first location in the list is the start/end point.
     */
    public String getShortestPathGoogleMapsLink(List<Coordinates> locations) {
        if (locations == null || locations.size() < 2) {
            throw new IllegalArgumentException("At least two locations are required for optimization.");
        }

        // Call the private helper to get the optimized route data
        OptimizationResponse optimizedRoute = getOptimizedRoute(locations);

        // Build the Google Maps URL from the ordered steps
        StringBuilder googleUrl = new StringBuilder("http://googleusercontent.com/maps/dir/");

        List<OptimizationResponse.Step> steps = optimizedRoute.getRoutes().get(0).getSteps();

        for (OptimizationResponse.Step step : steps) {
            // Google Maps uses [lat,lng], ORS gives [lon,lat]
            double latitude = step.getLocation()[1];
            double longitude = step.getLocation()[0];
            googleUrl.append(latitude).append(",").append(longitude).append("/");
        }

        // Remove the trailing "/"
        googleUrl.setLength(googleUrl.length() - 1);

        return googleUrl.toString();
    }


    // --- 2. CALCULATE OVERHEAD ---

    /**
     * Calculates the overhead (extra distance/duration) of adding
     * a new location to an existing route.
     *
     * @param originalLocations The base list of locations.
     * @param addedLocation     The single new location to add.
     * @return A RouteDetails object where distance/duration represent the OVERHEAD.
     */
    public RouteDetails calculateShortestPathOverhead(List<Coordinates> originalLocations, Coordinates addedLocation) {

        // 1. Get the summary for the original route
        OptimizationResponse.Summary originalSummary = getOptimizedRoute(originalLocations).getSummary();

        // 2. Create the combined list of locations
        List<Coordinates> combinedLocations = new ArrayList<>(originalLocations);
        combinedLocations.add(addedLocation);

        // 3. Get the summary for the new (combined) route
        OptimizationResponse.Summary combinedSummary = getOptimizedRoute(combinedLocations).getSummary();

        // 4. Calculate the overhead
        double overheadDistance = combinedSummary.getDistance() - originalSummary.getDistance();
        double overheadDuration = combinedSummary.getDuration() - originalSummary.getDuration();

        // 5. Return the overhead using the existing RouteDetails DTO
        return new RouteDetails(overheadDistance, overheadDuration);
    }


    // --- 3. PRIVATE HELPER (Required by ORS) ---

    /**
     * Private helper to build the request and call the ORS Optimization API.
     * The first location in the list is always the start and end point.
     */
    private OptimizationResponse getOptimizedRoute(List<Coordinates> locations) {
        if (locations == null || locations.isEmpty()) {
            throw new IllegalArgumentException("Locations list cannot be empty.");
        }

        // The first location is the start/end point for the vehicle
        Coordinates startPoint = locations.get(0);
        double[] startEndLocation = new double[]{startPoint.getLongitude(), startPoint.getLatitude()};

        // Create one vehicle starting and ending at the first location
        List<OptimizationRequest.Vehicle> vehicles = List.of(
                new OptimizationRequest.Vehicle(0, "driving-car", startEndLocation, startEndLocation)
        );

        // Create a "job" for every *other* location in the list
        List<OptimizationRequest.Job> jobs = new ArrayList<>();
        for (int i = 1; i < locations.size(); i++) {
            Coordinates jobCoord = locations.get(i);
            jobs.add(new OptimizationRequest.Job(
                    i, // Job ID
                    new double[]{jobCoord.getLongitude(), jobCoord.getLatitude()}
            ));
        }

        // Build the final request body
        OptimizationRequest requestBody = new OptimizationRequest(jobs, vehicles);

        // Call the ORS Optimization API
        return webClient.post()
                .uri("https://api.openrouteservice.org/v2/optimization")
                .header("Authorization", orsApiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(OptimizationResponse.class)
                .block();
    }
}