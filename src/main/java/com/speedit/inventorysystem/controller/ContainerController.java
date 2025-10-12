package com.speedit.inventorysystem.controller;

import com.speedit.inventorysystem.dto.ContainerFilterRequest;
import com.speedit.inventorysystem.dto.ContainerRequestDTO;
import com.speedit.inventorysystem.dto.ContainerViewDTO;
import com.speedit.inventorysystem.dto.ProductSummaryDTO;
import com.speedit.inventorysystem.model.Container;
import com.speedit.inventorysystem.repository.ContainerRepository;
import com.speedit.inventorysystem.service.ContainerService;
import com.speedit.inventorysystem.service.ProductService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/containers")
public class ContainerController {

    @Autowired
    private ContainerService containerService;

    @Autowired
    private ProductService productService;

    /**
     * Displays the Container Management page.
     * Fetches a page of containers with their hierarchical strain data.
     * Supports future pagination via request parameters.
     *
     * @param model The Spring Model to pass data to the Thymeleaf template.
     * @param page  The page number (0-indexed, default 0).
     * @param size  The number of items per page (default 20).
     * @return The name of the Thymeleaf template to render.
     */
    @GetMapping("/manage")
    public String manageContainers(
            Model model,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { // Adjust default size as needed

        try {
            // Define sorting (e.g., by containerId) - adjust as needed
            // Consistent sorting with /containers/data endpoint
            Sort sort = Sort.by(Sort.Direction.ASC, "containerId");
            Pageable pageable = PageRequest.of(page, size, sort);

            // --- Call the service to get the paginated container data and strain ---
            // This returns a Map like { "containersPage": Page<Container>, "containersMap": Map<...> }
            Map<String, Object> containerDataFromService = containerService.getContainersPageWithStrain(pageable);

            // --- NEW: Prepare the unified containerData structure for Thymeleaf ---
            Map<String, Object> unifiedContainerData = new HashMap<>();

            // Extract the Page object and the strain map from the service result
            Page<Container> containersPage = (Page<Container>) containerDataFromService.get("containersPage");
            Map<Integer, Map<String, Object>> containersMap = (Map<Integer, Map<String, Object>>) containerDataFromService.get("containersMap");

            // Add the page object and strain map to the unified structure
            unifiedContainerData.put("containersPage", containersPage);
            unifiedContainerData.put("containersMap", containersMap);

            // --- Add pagination flags (hasNext, hasPrevious) ---
            // Derive these directly from the Page object returned by the service
            unifiedContainerData.put("hasNext", containersPage.hasNext());
            unifiedContainerData.put("hasPrevious", containersPage.hasPrevious());

            // --- Add the unified data structure to the model ---
            // This will be injected into the Thymeleaf template
            model.addAttribute("containerData", unifiedContainerData); // <-- Single attribute

            // --- Prepare and add formData attributes (needed for modals/forms) ---
            // Reuse the logic from ProductService, assuming it provides categories and categoryOptionsMap
            Map<String, Object> formData = productService.prepareAddProductData();
            // Add formData attributes individually to the model, as expected by Thymeleaf/JS
            // These are typically used for dropdowns in create/edit modals
            model.addAttribute("categories", formData.get("categories"));
            model.addAttribute("categoryOptionsMap", formData.get("categoryOptionsMap"));
            // Add other formData elements as needed (e.g., units if you have a static list)
            // model.addAttribute("units", formData.get("units")); // If applicable

        } catch (Exception e) {
            System.err.println("Error fetching container data for management page: " + e.getMessage());
            e.printStackTrace();
            model.addAttribute("errorMessage", "Failed to load container data.");
            // Consider returning an error view or the page with limited data
        }

        return "manage-container"; // Return the Thymeleaf template name
    }

    /**
     * Provides container data as JSON for AJAX requests (e.g., initial table load, pagination).
     * This separates data fetching from page rendering.
     * This endpoint already implements the unified structure.
     *
     * @param page The page number (0-indexed, default 0).
     * @param size The number of items per page (default 20).
     * @return ResponseEntity containing the page of container data and strain map as JSON.
     */
    @GetMapping("/data") // Maps to /containers/data
    @ResponseBody // This annotation is key: it serializes the return value to JSON
    public ResponseEntity<Map<String, Object>> getContainersData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            // Define sorting (e.g., by containerId) - should match manageContainers
            Sort sort = Sort.by(Sort.Direction.ASC, "containerId");
            Pageable pageable = PageRequest.of(page, size, sort);

            // Call the service to get the paginated container data and strain
            // This reuses the existing logic, returning the Map structure
            Map<String, Object> containerData = new HashMap<>(containerService.getContainersPageWithStrain(pageable));

            // --- Ensure the returned map has top-level hasNext/hasPrevious flags ---
            // The service method getContainersPageWithStrain should ideally return these,
            // or we derive them here from the containersPage object within containerData.
            if (containerData.containsKey("containersPage") && containerData.get("containersPage") instanceof Page) {
                Page<Container> pageObj = (Page<Container>) containerData.get("containersPage");
                // Add flags directly to the top level of the map for easy access by JS
                containerData.put("hasNext", pageObj.hasNext());
                containerData.put("hasPrevious", pageObj.hasPrevious());
                // Optionally, add totalElements, totalPages if needed by frontend
                // containerData.put("totalElements", pageObj.getTotalElements());
                // containerData.put("totalPages", pageObj.getTotalPages());
            } else {
                // Handle case where containersPage is not as expected
                containerData.put("hasNext", false);
                containerData.put("hasPrevious", false);
            }

            return ResponseEntity.ok(containerData); // Return the map directly, @ResponseBody handles JSON conversion

        } catch (Exception e) {
            System.err.println("Error fetching container data for AJAX request (/data): " + e.getMessage());
            e.printStackTrace();
            // Return an error structure or a 500 status
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load container data via AJAX."));
        }
    }

    /**
     * Creates a new container.
     *
     * @param request The container creation request data.
     * @return ResponseEntity with the created Container or an error.
     */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createContainer(@Valid @RequestBody ContainerRequestDTO request) {
        try {
            Container createdContainer = containerService.createContainer(request);
            // Return the created container ID or the full object
            // Returning the ID is often sufficient for a create operation
            // You might want to return a DTO later if the full entity structure is complex
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("containerId", createdContainer.getContainerId()));
            // Or, return the full container if needed immediately:
            // return ResponseEntity.status(HttpStatus.CREATED).body(createdContainer);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            // Handle potential constraint violations (e.g., unique parent product ID if applicable)
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Container creation failed due to a conflict (e.g., parent product might already be defined)."));
        } catch (Exception ex) {
            // Log the exception for debugging
            ex.printStackTrace(); // Use a logger in production
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred while creating the container."));
        }
    }

    /**
     * Endpoint for fetching paginated product summaries for the child product dropdown.
     * Supports server-side search and pagination.
     *
     * @param q The search query string (optional).
     * @param page The page number (0-indexed, default 0).
     * @param size The number of items per page (default 20).
     * @return ResponseEntity containing the page of ProductSummaryDTOs and pagination info.
     */
    @GetMapping("/products/searchable")
    public ResponseEntity<Map<String, Object>> getSearchableProducts(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        try {
            // Define default sorting (e.g., by product ID)
            Sort sort = Sort.by(Sort.Direction.ASC, "productId"); // Adjust field name if different in DB/Entity
            Pageable pageable = PageRequest.of(page, size, sort);

            // Call the service method
            Page<ProductSummaryDTO> productPage = containerService.getProductSummariesForDropdown(q, pageable);

            // Prepare the response data
            Map<String, Object> response = new HashMap<>();
            response.put("content", productPage.getContent());
            response.put("page", productPage.getNumber());
            response.put("size", productPage.getSize());
            response.put("totalElements", productPage.getTotalElements());
            response.put("totalPages", productPage.getTotalPages());
            // Add 'lastPage' if backend handles searching subsequent pages internally
            // response.put("lastPageChecked", lastPageChecked); // If applicable

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            System.err.println("Error in /containers/products/searchable: " + e.getMessage());
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to fetch product data.");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Updates an existing container.
     *
     * @param id      The ID of the container to update.
     * @param request The container update request data.
     * @return ResponseEntity with the updated Container or an error.
     */
    @PutMapping("/{id}/update")
    @ResponseBody
    public ResponseEntity<?> updateContainer(@PathVariable Integer id, @Valid @RequestBody ContainerRequestDTO request) {
        try {
            Container updatedContainer = containerService.updateContainer(id, request);
            // Return the updated container ID or the full object
            // Returning the ID is often sufficient, or you could return a DTO
            return ResponseEntity.ok(Map.of("containerId", updatedContainer.getContainerId()));
            // Or return the full updated container if needed immediately:
            // return ResponseEntity.ok(updatedContainer);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Update failed due to a conflict (e.g., data integrity issue)."));
        } catch (Exception ex) {
            // Log the exception for debugging
            ex.printStackTrace(); // Use a logger in production
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "An unexpected error occurred while updating the container."));
        }
    }

    /**
     * Fetches data for pre-populating the Container Edit form.
     * Returns the container details and a summary of its child product.
     *
     * @param id The ID of the container's parent product.
     * @return ResponseEntity containing ContainerRequestDTO and child ProductSummaryDTO, or 404/500.
     */
    @GetMapping("/{id}/edit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getContainerEditData(@PathVariable Integer id) {
        try {
            // Call the service method to get both DTOs
            Map<String, Object> editData = containerService.getContainerEditData(id);

            if (editData != null && !editData.isEmpty()) {
                return ResponseEntity.ok(editData);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (EntityNotFoundException ex) { // Assuming you import this
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            // Log the exception
            ex.printStackTrace(); // Use a logger in production
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching container edit data"));
        }
    }

    /**
     * Fetches detailed information for a specific container to display in the View modal.
     *
     * @param id The ID of the container's parent product.
     * @return ResponseEntity containing the ContainerViewDTO or a 404 Not Found.
     */
    @GetMapping("/{id}/details") // <-- Use /details for view, consistent with ProductController
    @ResponseBody
    public ResponseEntity<?> getContainerViewDetails(@PathVariable Integer id) {
        try {
            ContainerViewDTO viewData = containerService.getContainerViewData(id);
            return ResponseEntity.ok(viewData);
        } catch (EntityNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            // Log the exception
            ex.printStackTrace(); // Use a logger in production
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error fetching container view data"));
        }
    }

    /**
     * Filters containers based on provided criteria and handles keyset pagination.
     * Returns both the filtered container page and their strain data.
     *
     * @param request The container filter and pagination request data.
     * @return ResponseEntity containing a map with "containersPage" and "containersMap".
     */
    @PostMapping("/filter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> filterContainers(@RequestBody ContainerFilterRequest request) {
        try {
            // Call the new service method that handles filtering and strain building
            Map<String, Object> filterResult = containerService.filterContainersWithStrain(request);

            return ResponseEntity.ok(filterResult);

        } catch (Exception e) {
            System.err.println("Error filtering containers: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An error occurred while filtering containers."));
        }
    }

    /**
     * Deletes an existing container by its parent product ID.
     *
     * @param id The ID of the parent product identifying the container.
     * @return ResponseEntity indicating success (200 OK) or failure (404 Not Found, 500 Internal Server Error).
     */
    @DeleteMapping("/{id}/delete") // Maps to DELETE /containers/{id}/delete
    @ResponseBody // Ensures the return value is serialized (though void ResponseEntity doesn't need it much)
    public ResponseEntity<?> deleteContainer(@PathVariable Integer id) {
        try {
            // Call the service method to perform the deletion
            ResponseEntity<?> response = containerService.deleteContainer(id);
            return response; // Return the response directly from the service
        } catch (Exception e) {
            // Log the unexpected exception
            System.err.println("Unexpected error in ContainerController.deleteContainer for ID " + id + ": " + e.getMessage());
            e.printStackTrace();
            // Return a 500 Internal Server Error response
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "An unexpected error occurred while deleting the container."));
        }
    }
}
