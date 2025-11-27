package com.speedit.inventorysystem.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.speedit.inventorysystem.dto.Coordinates;
import com.speedit.inventorysystem.dto.ParsedLocationData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LocationParsingService {

    @Autowired
    private WebClient webClient;
    @Value("${ors.api.key}")
    private String orsApiKey;

    // Regex to find @latitude,longitude,
    private static final Pattern COORD_PATTERN = Pattern.compile("/@(-?\\d+\\.\\d+),(-?\\d+\\.\\d+)");


    /**
     * Main function called by other services.
     */
    public ParsedLocationData parseGoogleMapsLink(String googleMapsUrl) {
        Coordinates coords = getCoordinatesFromUrl(googleMapsUrl);
        String description = getDescriptionFromCoordinates(coords);
        return new ParsedLocationData(description, coords.getLatitude(), coords.getLongitude());
    }

    /**
     * Uses Regex to get coordinates from a long URL.
     */
    private Coordinates getCoordinatesFromUrl(String googleMapsUrl) {
        try {
            String longUrl = googleMapsUrl;
            // Check if it's a short link, and if so, un-shorten it
            if (!longUrl.contains("/@")) {
                longUrl = getFinalRedirectedUrl(googleMapsUrl);
            }

            Matcher matcher = COORD_PATTERN.matcher(longUrl);
            if (matcher.find()) {
                double latitude = Double.parseDouble(matcher.group(1));
                double longitude = Double.parseDouble(matcher.group(2));
                return new Coordinates(latitude, longitude);
            } else {
                throw new IllegalArgumentException("Could not parse coordinates from URL: " + longUrl);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error processing Google Maps URL", e);
        }
    }

    /**
     * Follows redirects for short URLs (e.g., http://googleusercontent.com/maps/k/l/j/v)
     */
    private String getFinalRedirectedUrl(String shortUrl) throws Exception {
        URL url = new URL(shortUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setInstanceFollowRedirects(true); // This is key
        conn.setRequestMethod("GET");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.connect();
        return conn.getURL().toString();
    }

    /**
     * NEW FUNCTION: Calls ORS Reverse Geocoding API
     */
    private String getDescriptionFromCoordinates(Coordinates coords) {
        try {
            // Call ORS Reverse Geocoding API
            JsonNode response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.openrouteservice.org")
                            .path("/geocode/reverse")
                            .queryParam("api_key", orsApiKey)
                            .queryParam("point.lon", coords.getLongitude())
                            .queryParam("point.lat", coords.getLatitude())
                            .queryParam("layers", "address")
                            .queryParam("size", 1)
                            .build())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(); // Blocking for simplicity in this service

            if (response != null && response.has("features") && response.get("features").size() > 0) {
                // Get the "label" (full address)
                return response.get("features").get(0).get("properties").get("label").asText();
            }
            return "Unknown Location";
        } catch (Exception e) {
            // Log the error
            return "Location at " + coords.getLatitude() + "," + coords.getLongitude();
        }
    }
}