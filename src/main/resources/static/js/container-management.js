// container-management.js - Container management functionality
// Relies on management.js for shared modal/CSRF logic

// --- Store Choices instance and state for Create/Edit Modal ---
let childProductChoicesInstance = null;
let currentSearchTerm = '';
let currentPage = 0;
let isLoading = false; // Flag to prevent multiple simultaneous requests
let hasMoreData = true; // Flag to indicate if more data might exist
const PAGE_SIZE = 20; // Should match backend default or be configurable
let searchDebounceTimer = null;
const SEARCH_DEBOUNCE_DELAY = 800; // milliseconds

// --- Store state for filtering and pagination ---
let isFilterActive = false;
let lastFilterRequest = null; // Stores the last successful filter request for pagination
let currentPaginationContext = {
    pageNumber: 0,
    pageSize: PAGE_SIZE
};

// --- Store the ID of the container being edited ---
let currentEditContainerId = null;

document.addEventListener('DOMContentLoaded', function () {
    // --- Shared logic reference ---
    // This script relies on functions defined in management.js:
    // - getCSRFToken() for CSRF handling
    // - registerModal(), openModal(), closeAllModals()
    // - displayFormErrors()
    // - getCSRFTokenHeaders() (custom helper if needed)

    // --- Initialization ---
    console.log("Container Management JS loaded.");
    loadContainerTable(); // Load initial page using Thymeleaf-injected data

    // --- Register Modals ---
    const containerFormModal = registerModal('containerFormModal');
    const viewContainerModal = registerModal('viewContainerModal');
    const deleteContainerModal = registerModal('deleteContainerModal');
    const filterModal = registerModal('filterModal');

    // --- Ensure close buttons inside modals also work ---
    const closeModalButtons = document.querySelectorAll('.modal .close-modal');
    closeModalButtons.forEach(button => {
        button.addEventListener('click', closeAllModals);
    });

    // --- Event Listeners for Main Buttons ---
    document.getElementById('createContainerBtn')?.addEventListener('click', openCreateContainerModal);
    document.getElementById('filterBtn')?.addEventListener('click', openFilterModal);
    document.getElementById('applyContainerFilters')?.addEventListener('click', applyFilters);
    document.getElementById('resetContainerFilters')?.addEventListener('click', resetFilters);
    document.getElementById('saveContainerBtn')?.addEventListener('click', saveContainer); // Save/Create button

    // Find the confirm delete button and attach the listener
    const confirmDeleteBtn = document.getElementById('confirmDeleteContainer');
    if (confirmDeleteBtn) {
        // Remove any existing listener to prevent duplicates, then add the new one
        confirmDeleteBtn.removeEventListener('click', handleConfirmDelete); // Remove potential old listener
        confirmDeleteBtn.addEventListener('click', handleConfirmDelete);   // Add the new listener
        console.log("Attached click listener to #confirmDeleteContainer button.");
    } else {
        console.error("Confirm delete button (#confirmDeleteContainer) not found in DOM.");
    }

    // --- Event Listeners for Pagination Buttons ---
    document.getElementById('nextPageBtn')?.addEventListener('click', () => navigatePage('NEXT'));
    document.getElementById('prevPageBtn')?.addEventListener('click', () => navigatePage('PREVIOUS'));

    // --- Event Delegation for Action Buttons (View, Edit, Delete) ---
    const tableBody = document.querySelector('.management-table tbody');
    if (tableBody) {
        tableBody.addEventListener('click', function(event) {
            const viewBtn = event.target.closest('.view-btn');
            const editBtn = event.target.closest('.edit-btn');
            const deleteBtn = event.target.closest('.delete-btn');

            if (viewBtn) {
                const containerId = parseInt(viewBtn.getAttribute('data-id'), 10);
                if (!isNaN(containerId)) {
                    console.log("View button clicked for container ID:", containerId);
                    fetchAndShowViewModal(containerId); // Call the function to fetch and show view modal
                } else {
                    console.error("Invalid container ID for view:", viewBtn.getAttribute('data-id'));
                }
            } else if (editBtn) {
                const containerId = parseInt(editBtn.getAttribute('data-id'), 10);
                if (!isNaN(containerId)) {
                    console.log("Edit button clicked for container ID:", containerId);
                    openEditContainerModal(containerId); // Call the function to open edit modal
                } else {
                    console.error("Invalid container ID for edit:", editBtn.getAttribute('data-id'));
                }
            } else if (deleteBtn) {
                const containerId = parseInt(deleteBtn.getAttribute('data-id'), 10);
                if (!isNaN(containerId)) {
                    console.log("Delete button clicked for container ID:", containerId);
                    openDeleteConfirmationModal(containerId); // Call the function to open delete confirmation
                } else {
                    console.error("Invalid container ID for delete:", deleteBtn.getAttribute('data-id'));
                }
            }
        });
    } else {
        console.warn("Could not find table body for action button delegation.");
    }

    // --- Function to Open Create Container Modal ---
    function openCreateContainerModal() {
        console.log("Opening Create Container Modal");
        currentEditContainerId = null; // Clear edit state
        document.getElementById('containerForm').reset();
        clearContainerFormErrors();
        document.getElementById('containerModalTitle').textContent = 'Create New Container';
        currentSearchTerm = '';
        currentPage = 0;
        isLoading = false;
        hasMoreData = true;
        // Destroy and re-initialize Choices.js for a clean state in create mode
        if (childProductChoicesInstance) {
            childProductChoicesInstance.destroy();
            childProductChoicesInstance = null;
        }
        initializeChildProductChoices();
        openModal(containerFormModal);
    }

    // --- Function to Open Filter Modal ---
    function openFilterModal() {
        console.log("Opening Filter Container Modal");
        // Reset filter form errors if any
        const filterErrorContainers = filterModal.querySelectorAll('.error-message');
        filterErrorContainers.forEach(el => el.textContent = '');
        openModal(filterModal);
    }

    // --- Function to Apply Filters ---
    function applyFilters() {
        console.log("Applying container filters...");
        const filterData = collectFilterData();
        console.log("Collected Filter Data:", filterData);

        // Set initial pagination for filtered results
        filterData.pageSize = currentPaginationContext.pageSize;
        filterData.direction = null; // First page of filtered results
        filterData.lastContainerId = null;
        filterData.firstContainerId = null;

        // Store filter request for pagination
        lastFilterRequest = filterData;
        isFilterActive = true;

        // Send request to backend filter endpoint
        sendFilterRequest(filterData)
            .then(data => {
                console.log("Filter request successful, updating table...");
                updateContainerTable(data); // Use filtered data structure { containersPage, containersMap, hasNext, hasPrevious }
                closeAllModals(); // Close the filter modal
            })
            .catch(error => {
                console.error('Error applying filters:', error);
                alert("An error occurred while applying filters. Please try again.");
                // Optionally, keep modal open to show specific errors if backend provides them
            });
    }

    // --- Function to Reset Filters ---
    function resetFilters() {
        console.log("Resetting container filters...");
        // Reset filter form fields
        const filterForm = document.getElementById('filterModal');
        if (filterForm) {
            filterForm.querySelectorAll('input, select, textarea').forEach(element => {
                if (element.type === 'checkbox' || element.type === 'radio') {
                    element.checked = false;
                } else {
                    element.value = '';
                }
            });
            const unitSelect = document.getElementById('filterUnit');
            if (unitSelect) {
                unitSelect.selectedIndex = 0; // Assuming first option is 'All' or blank
            }
        }

        // Clear stored filter state
        lastFilterRequest = null;
        isFilterActive = false;

        // Reload the initial unfiltered page state via AJAX
        currentPaginationContext.pageNumber = 0;
        loadUnfilteredPage(currentPaginationContext.pageNumber, currentPaginationContext.pageSize)
            .then(data => {
                console.log("Filters reset and unfiltered page loaded via AJAX.");
                updateContainerTable(data); // Update table with data from /containers/data
                closeAllModals(); // Close the filter modal
            })
            .catch(error => {
                console.error('Error resetting filters and loading unfiltered page:', error);
                alert("An error occurred while resetting filters. Please try again.");
                // Fallback to full reload if AJAX fails
                // location.reload();
            });
    }

    // --- Function to Navigate Pages (Handles Filtered vs. Unfiltered) ---
    function navigatePage(direction) {
        console.log(`Navigating ${direction}...`);
        if (!direction || (direction !== 'NEXT' && direction !== 'PREVIOUS')) {
            console.warn("Invalid navigation direction:", direction);
            return;
        }

        if (isFilterActive && lastFilterRequest) {
            // --- Filtered Pagination ---
            console.log("Using filtered pagination logic (/containers/filter)...");
            const requestData = JSON.parse(JSON.stringify(lastFilterRequest)); // Deep copy

            const currentRows = document.querySelectorAll('.management-table tbody tr');
            if (currentRows.length > 0) {
                if (direction === 'NEXT') {
                    const lastRow = currentRows[currentRows.length - 1];
                    const lastIdText = lastRow.cells[0]?.textContent?.trim();
                    const lastId = lastIdText ? parseInt(lastIdText, 10) : null;
                    if (lastId && !isNaN(lastId)) {
                        requestData.lastContainerId = lastId;
                        requestData.firstContainerId = null;
                        requestData.direction = 'NEXT';
                    } else {
                        console.error("Could not determine last container ID for NEXT pagination.");
                        alert("Error determining pagination context. Please reload the page.");
                        return;
                    }
                } else if (direction === 'PREVIOUS') {
                    const firstRow = currentRows[0];
                    const firstIdText = firstRow.cells[0]?.textContent?.trim();
                    const firstId = firstIdText ? parseInt(firstIdText, 10) : null;
                    if (firstId && !isNaN(firstId)) {
                        requestData.firstContainerId = firstId;
                        requestData.lastContainerId = null;
                        requestData.direction = 'PREVIOUS';
                    } else {
                        console.error("Could not determine first container ID for PREVIOUS pagination.");
                        alert("Error determining pagination context. Please reload the page.");
                        return;
                    }
                }
                requestData.pageSize = currentPaginationContext.pageSize;

                sendFilterRequest(requestData)
                    .then(data => {
                        console.log(`${direction} page (filtered) request successful, updating table...`);
                        updateContainerTable(data); // Update table with filtered data
                    })
                    .catch(error => {
                        console.error(`Error fetching ${direction} page (filtered):`, error);
                        alert(`An error occurred while loading the ${direction.toLowerCase()} page. Please try again.`);
                    });

            } else {
                console.warn("No table rows found for filtered pagination context.");
                requestData.lastContainerId = null;
                requestData.firstContainerId = null;
                requestData.direction = direction;
                requestData.pageSize = currentPaginationContext.pageSize;

                 sendFilterRequest(requestData)
                    .then(data => {
                        console.log(`${direction} page (filtered fallback) request successful, updating table...`);
                        updateContainerTable(data); // Update table with filtered data
                    })
                    .catch(error => {
                        console.error(`Error fetching ${direction} page (filtered fallback):`, error);
                        alert(`An error occurred while loading the ${direction.toLowerCase()} page. Please try again.`);
                    });
            }

        } else {
            // --- Unfiltered Pagination (AJAX to /containers/data) ---
            console.log("Using unfiltered pagination logic (/containers/data)...");
            const currentPageNumber = currentPaginationContext.pageNumber;
            let targetPageNumber = currentPageNumber;

            if (direction === 'NEXT') {
                targetPageNumber = currentPageNumber + 1;
            } else if (direction === 'PREVIOUS') {
                targetPageNumber = Math.max(0, currentPageNumber - 1); // Don't go below page 0
            }

            if (targetPageNumber !== currentPageNumber) {
                currentPaginationContext.pageNumber = targetPageNumber;
                loadUnfilteredPage(targetPageNumber, currentPaginationContext.pageSize)
                    .then(data => {
                        console.log(`${direction} page (unfiltered) loaded successfully via AJAX, updating table...`);
                        updateContainerTable(data); // Update table with data from /containers/data
                    })
                    .catch(error => {
                        console.error(`Error loading ${direction} page (unfiltered) via AJAX:`, error);
                        alert(`An error occurred while loading the ${direction.toLowerCase()} page. Please try again.`);
                        currentPaginationContext.pageNumber = currentPageNumber; // Revert on error
                    });
            } else {
                console.log(`Already at the ${direction === 'PREVIOUS' ? 'first' : 'last'} page (unfiltered).`);
            }
        }
    }

    // --- NEW: Helper Function to Load Unfiltered Page via AJAX ---
    function loadUnfilteredPage(page, size) {
        console.log(`Loading unfiltered page via AJAX: page=${page}, size=${size}`);
        const url = `/containers/data?page=${page}&size=${size}`;
        return fetch(url)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log("Unfiltered page data fetched via AJAX:", data);
                // Ensure hasNext/hasPrevious are present in the AJAX response
                // The backend /containers/data endpoint should provide these
                data.hasNext = data.hasNext ?? false;
                data.hasPrevious = data.hasPrevious ?? false;
                return data;
            });
    }

    // --- NEW: Helper Function to Collect Filter Data from Modal ---
    function collectFilterData() {
        const filterModal = document.getElementById('filterModal');
        if (!filterModal) {
            console.error("Filter modal (#filterModal) not found.");
            return {};
        }

        // --- Container-related filters ---
        const minQuantity = parseInt(document.getElementById('filterMinQuantity')?.value, 10);
        const maxQuantity = parseInt(document.getElementById('filterMaxQuantity')?.value, 10);
        const unitSelect = document.getElementById('filterUnit');
        const unit = unitSelect && unitSelect.value ? unitSelect.value : null; // Assumes UnitType enum values as option values
        const minVolume = parseFloat(document.getElementById('filterMinVolume')?.value);
        const maxVolume = parseFloat(document.getElementById('filterMaxVolume')?.value);

        // --- Base Product-related filters ---
        const minPriceInput = parseFloat(document.getElementById('filterStockMin')?.value);
        const maxPriceInput = parseFloat(document.getElementById('filterPriceMax')?.value);
        // Assuming backend expects price in cents if sent as integer
        const minPrice = (!isNaN(minPriceInput) && minPriceInput >= 0) ? minPriceInput : null;
        const maxPrice = (!isNaN(maxPriceInput) && maxPriceInput >= 0) ? maxPriceInput : null;
        const minStock = parseInt(document.getElementById('filterMinStock')?.value, 10);

        // --- Base Product Options (simplified collection) ---
        // This assumes option filters are structured similarly to products.
        // You'll need to adapt this logic based on how option filters are implemented in the HTML.
        // Example: Collecting selected option IDs from checkboxes/selects within filter groups.
        const optionIds = [];
        // Example logic (adjust selectors based on your actual HTML structure for option filters):
        const optionFilterGroups = filterModal.querySelectorAll('#filter-option-groups-container .option-group');
        optionFilterGroups.forEach(group => {
            const optionSelect = group.querySelector('.filter-option'); // Adjust selector
            if (optionSelect && optionSelect.value && optionSelect.value !== '') {
                const optionId = parseInt(optionSelect.value, 10);
                if (!isNaN(optionId)) {
                    if (!optionIds.includes(optionId)) {
                        optionIds.push(optionId);
                    }
                }
            }
        });
        // For now, assuming a simpler structure or that option filtering is handled differently on the backend
        // if only IDs are sent. If you have a complex option filter UI, implement the collection logic here.

        return {
            minQuantity: (!isNaN(minQuantity) && minQuantity >= 0) ? minQuantity : null,
            maxQuantity: (!isNaN(maxQuantity) && maxQuantity >= 0) ? maxQuantity : null,
            unit: unit, // Can be null if not selected
            minVolume: (!isNaN(minVolume) && minVolume > 0) ? minVolume : null,
            maxVolume: (!isNaN(maxVolume) && maxVolume > 0) ? maxVolume : null,
            minPrice: minPrice, // Adjusted for cents if needed
            maxPrice: maxPrice, // Adjusted for cents if needed
            minStock: (!isNaN(minStock) && minStock >= 0) ? minStock : null,
            optionIds: optionIds.length > 0 ? optionIds : null // Send null if no options selected
            // Pagination fields (lastContainerId, firstContainerId, direction, pageSize) will be set by navigatePage or applyFilters
        };
    }

    // --- NEW: Helper Function to Send Filter Request ---
    function sendFilterRequest(filterData) {
        console.log("Sending filter request with ", filterData);
        const csrf = getCSRFToken(); // From management.js
        // Use the endpoint you defined in ContainerController for filtering
        return fetch('/containers/filter', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrf.header]: csrf.token
            },
            body: JSON.stringify(filterData)
        })
        .then(response => {
            console.log("Raw filter response received:", response);
            if (!response.ok) {
                if (response.status === 400) {
                    // Handle validation errors if backend sends them
                    return response.json().then(errors => {
                        console.error("Backend validation errors:", errors);
                        // Display errors in filter modal if UI elements exist
                        // Example: displayFormErrors(errors, errorFieldMapping);
                        throw new Error("Validation failed on backend.");
                    });
                }
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            console.log("Filter response JSON parsed:", data);
            // Validate expected structure
            if (data && typeof data === 'object' && data.hasOwnProperty('containersPage') && data.hasOwnProperty('containersMap')) {
                // Backend returns hasNext/hasPrevious flags directly
                data.hasNext = data.hasNext ?? false;
                data.hasPrevious = data.hasPrevious ?? false;
                return data; // Return the validated data structure
            } else {
                console.error("Invalid data structure received from filter endpoint:", data);
                throw new Error("Invalid response structure from server.");
            }
        });
    }

    // --- Initialize Child Product Choices for Create/Edit Modal ---
    function initializeChildProductChoices() {
        const childProductSelect = document.querySelector('#childProductId.child-product-select');
        console.log("Selected childProductSelect element:", childProductSelect);
        if (!childProductSelect) {
            console.error("Child product select element (#childProductId.child-product-select) not found for Choices.js initialization.");
            return;
        }

        if (childProductChoicesInstance) {
            childProductChoicesInstance.destroy();
            childProductChoicesInstance = null;
        }

        console.log("Initializing Choices.js for child product dropdown.");
        childProductChoicesInstance = new Choices(childProductSelect, {
            searchEnabled: true,
            shouldSort: false,
            placeholderValue: 'Search by ID or product details...',
            noResultsText: 'No products/containers found',
            searchPlaceholderValue: 'Type to search...',
            searchFloor: 0,
            searchResultLimit: 50,
            searchChoices: false,
            allowHTML: true,
            callbackOnInit: function () {
                console.log("Choices.js initialized for child product dropdown.");
            }
        });

        if (childProductChoicesInstance && childProductChoicesInstance.passedElement) {
            childProductChoicesInstance.passedElement.element.addEventListener('showDropdown', function (event) {
                console.log("Choices.js 'showDropdown' event triggered.");
                if (currentPage === 0 && childProductChoicesInstance.config.choices.length <= 1) {
                    console.log("Dropdown opened and appears empty, loading initial data (Page 0).");
                    currentSearchTerm = '';
                    currentPage = 0;
                    hasMoreData = true;
                    childProductChoicesInstance.clearStore();
                    childProductChoicesInstance.setChoices([{ value: '', label: 'Loading...', disabled: true }], 'value', 'label', true);
                    loadChoicesData(currentSearchTerm, 0);
                } else {
                    console.log("Dropdown opened, data might already be present or a search was performed.");
                }
            });
            console.log("Attached 'showDropdown' event listener.");

            childProductChoicesInstance.passedElement.element.addEventListener('search', function (event) {
                const newSearchTerm = event.detail.value ? event.detail.value.trim() : '';
                console.log("Choices.js 'search' event triggered. Search Term:", `'${newSearchTerm}'`);

                if (newSearchTerm !== currentSearchTerm) {
                    currentSearchTerm = newSearchTerm;
                    currentPage = 0;
                    hasMoreData = true;
                    childProductChoicesInstance.clearStore();
                    if (newSearchTerm !== '') {
                        childProductChoicesInstance.setChoices([{ value: '', label: 'Searching...', disabled: true }], 'value', 'label', true);
                    } else {
                        childProductChoicesInstance.setChoices([{ value: '', label: 'Loading...', disabled: true }], 'value', 'label', true);
                    }
                }

                clearTimeout(searchDebounceTimer);
                searchDebounceTimer = setTimeout(() => {
                    console.log("Debounce timer finished. Searching for:", currentSearchTerm);
                    loadChoicesData(currentSearchTerm, 0);
                }, SEARCH_DEBOUNCE_DELAY);
            });
            console.log("Attached 'search' event listener.");
        } else {
            console.error("Could not attach 'search' event listener.");
        }

        const choicesList = childProductSelect.closest('.choices')?.querySelector('.choices__list--dropdown');
        console.log("Found choicesList for scroll listener:", choicesList);
        if (choicesList) {
            let scrollTimeout;
            choicesList.addEventListener('scroll', function () {
                console.log("DEBUG: Scroll event on choicesList detected.");
                window.clearTimeout(scrollTimeout);
                scrollTimeout = window.setTimeout(function () {
                    const { scrollTop, scrollHeight, clientHeight } = choicesList;
                    console.log(`DEBUG: ScrollTop: ${scrollTop}, ClientHeight: ${clientHeight}, ScrollHeight: ${scrollHeight}`);
                    const isNearBottom = scrollTop + clientHeight >= scrollHeight - 5; // 5px threshold
                    console.log("DEBUG: Is near bottom?", isNearBottom, "isLoading?", isLoading, "hasMoreData?", hasMoreData);

                    if (isNearBottom && !isLoading && hasMoreData) {
                        console.log("Near bottom of dropdown, loading more data...");
                        loadChoicesData(currentSearchTerm, currentPage + 1);
                    } else if (isNearBottom && !hasMoreData) {
                         console.log("Near bottom, but no more data available (hasMoreData is false).");
                    } else if (isNearBottom && isLoading) {
                         console.log("Near bottom, but a load request is already in progress (isLoading is true).");
                    }
                }, 100);
            });
            console.log("Attached 'scroll' event listener to choicesList.");
        } else {
            console.warn("Could not find Choices dropdown list for scroll listener.");
        }
    }

    // --- Load Data for Choices.js ---
    function loadChoicesData(searchTerm, page) {
        if (isLoading) {
            console.log("Load request ignored, already loading.");
            return;
        }

        isLoading = true;
        console.log(`Loading data - Search: '${searchTerm}', Page: ${page}`);

        fetch(`/containers/products/searchable?q=${encodeURIComponent(searchTerm)}&page=${page}&size=${PAGE_SIZE}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log("Data fetched for page", page, ":", data);
                const choicesData = data.content.map(item => ({
                    value: item.productId.toString(),
                    label: item.displayText
                }));

                if (page === 0) {
                    childProductChoicesInstance.clearStore();
                    if (choicesData.length > 0) {
                        childProductChoicesInstance.setChoices(choicesData, 'value', 'label', false);
                    } else {
                        childProductChoicesInstance.setChoices([{ value: '', label: 'No products/containers found', disabled: true }], 'value', 'label', true);
                    }
                } else {
                    if (choicesData.length > 0) {
                        // Choices.js setChoices with false as last arg should append
                        // But behavior might depend on version/options. Check docs.
                        // If append doesn't work reliably, might need to get current choices,
                        // merge arrays, clear, and setChoices with merged array.
                        // Let's try simple append first.
                        childProductChoicesInstance.setChoices(choicesData, 'value', 'label', false);
                        console.log(`Appended ${choicesData.length} items to dropdown.`);
                    } else {
                         console.log("No more data found for page", page);
                    }
                }

                currentPage = page; // Update current page to the one just loaded
                if (data.content.length < PAGE_SIZE) {
                     hasMoreData = false;
                     console.log("Reached end of available data.");
                } else {
                    hasMoreData = true; // Assume more unless proven otherwise
                }

                isLoading = false;
                console.log("Data loading finished. Current Page:", currentPage, "Has More:", hasMoreData);
            })
            .catch(error => {
                console.error('Error fetching searchable products for page', page, ':', error);
                isLoading = false;
                if (page === 0) {
                    childProductChoicesInstance.clearStore();
                    childProductChoicesInstance.setChoices([{ value: '', label: 'Error loading results', disabled: true }], 'value', 'label', true);
                } else {
                    // For subsequent pages, maybe just log or show a brief message if Choices allows
                    // Adding an error item to the end might be tricky.
                    console.error("Error appending data to dropdown.");
                }
            });
    }

    // --- Save Container Function (Handles Create & Edit) ---
    function saveContainer() {
        console.log("Save Container button clicked");
        clearContainerFormErrors();

        const csrf = getCSRFToken();
        let url, method;
        let formData = {
            parentProductPrice: parseFloat(document.getElementById('parentProductPrice').value),
            childProductId: parseInt(document.getElementById('childProductId').value, 10),
            quantity: parseInt(document.getElementById('quantity').value, 10),
            unit: document.getElementById('unit').value,
            height: parseFloat(document.getElementById('containerHeight').value),
            width: parseFloat(document.getElementById('containerWidth').value),
            length: parseFloat(document.getElementById('containerLength').value),
            distanceUnit: document.getElementById('containerDistanceUnit').value
        };

        console.log("Collected Form Data:", formData);

        let isValid = true;
        if (isNaN(formData.parentProductPrice) || formData.parentProductPrice <= 0) {
            displayFormErrors({ parentProductPrice: "Please enter a valid price." }, { parentProductPrice: 'parentProductPriceError' });
            isValid = false;
        }
        if (isNaN(formData.childProductId) || formData.childProductId <= 0 || !document.getElementById('childProductId').value) {
            displayFormErrors({ childProductId: "Please select a child product." }, { childProductId: 'childProductIdError' });
            isValid = false;
        }
        if (isNaN(formData.quantity) || formData.quantity <= 0) {
            displayFormErrors({ quantity: "Please enter a valid quantity." }, { quantity: 'quantityError' });
            isValid = false;
        }
        if (!formData.unit) {
            displayFormErrors({ unit: "Please select a unit." }, { unit: 'unitError' });
            isValid = false;
        }

        // Add validation for dimension fields
        if (isNaN(formData.height) || formData.height <= 0 ||
            isNaN(formData.width) || formData.width <= 0 ||
            isNaN(formData.length) || formData.length <= 0) {
            displayFormErrors({ dimensions: "Please enter valid, positive numbers for all dimensions." }, { dimensions: 'dimensionsError' });
            isValid = false;
        }

        if (!isValid) {
            console.log("Form validation failed.");
            return;
        }

        if (currentEditContainerId) {
            // --- Edit Mode ---
            url = `/containers/${currentEditContainerId}/update`;
            method = 'PUT';
        } else {
            // --- Create Mode ---
            url = '/containers/create';
            method = 'POST';
        }

        fetch(url, {
            method: method,
            headers: {
                'Content-Type': 'application/json',
                [csrf.header]: csrf.token
            },
            body: JSON.stringify(formData)
        })
        .then(response => {
            console.log("Fetch response received:", response);
            if (response.ok) {
                return response.json();
            } else if (response.status === 400) {
                return response.json().then(errors => {
                    displayFormErrors(errors, {
                        parentProductPrice: 'parentProductPriceError',
                        childProductId: 'childProductIdError',
                        quantity: 'quantityError',
                        unit: 'unitError',
                        height: 'dimensionsError',
                        width: 'dimensionsError',
                        length: 'dimensionsError'
                    });
                    throw new Error("Backend validation error");
                });
            } else if (response.status === 404) {
                return response.json().then(data => {
                    alert(data.error || "Child product not found.");
                    throw new Error("Not Found");
                });
            } else if (response.status === 409) {
                return response.json().then(data => {
                    alert(data.error || "Conflict: Container might already exist for this parent product.");
                    throw new Error("Conflict");
                });
            } else {
                alert("An error occurred while saving the container. Please try again.");
                throw new Error(`HTTP error! status: ${response.status}`);
            }
        })
        .then(data => {
            const action = currentEditContainerId ? "updated" : "created";
            console.log(`Container ${action} successfully:`, data);
            alert(`Container ${action} successfully!`);
            closeAllModals();
            location.reload();
        })
        .catch(error => {
            if (error.message !== "Backend validation error" &&
                error.message !== "Not Found" &&
                error.message !== "Conflict") {
                console.error(`Network or unexpected error ${currentEditContainerId ? 'updating' : 'saving'} container:`, error);
                alert("A network error occurred. Please check your connection and try again.");
            }
        });
    }

    // --- NEW: Load Initial Container Table (Uses Thymeleaf-injected unified data) ---
    /**
     * Loads initial container data using the unified structure injected by Thymeleaf.
     * This function does NOT make an AJAX call for the initial data.
     */
    function loadContainerTable() {
        console.log("Loading initial container table with Thymeleaf-injected unified data.");
        // The containerData is expected to be injected by Thymeleaf
        // e.g., <script th:inline="javascript">const containerData = /*[[${containerData}]]*/ {};</script>
        if (typeof containerData === 'undefined') {
            console.error("containerData is not defined. Check Thymeleaf injection.");
            displayContainerTableError("Failed to load container data.");
            return;
        }
        console.log("Using injected unified containerData:", containerData);
        // Pass the unified data structure directly to updateContainerTable
        updateContainerTable(containerData);
    }

    // --- UPDATED: Update Container Table with Data (Handles Unified Structure Only) ---
    /**
     * Updates the container table body with data.
     * Expects data to be the unified structure:
     * { containersPage: Page object, containersMap: Map of strain data, hasNext: boolean, hasPrevious: boolean }
     * @param {Object} data - The unified data object containing containersPage, containersMap, hasNext, hasPrevious.
     */
    function updateContainerTable(data) {
        console.log("Updating container table with unified data structure:", data);
        const tbody = document.querySelector('.management-table tbody');
        if (!tbody) {
            console.error("Could not find table body (.management-table tbody)");
            displayContainerTableError("Table body not found.");
            return;
        }

        // Clear existing table rows
        tbody.innerHTML = '';

        // Check if the data structure is valid
        if (!data || !data.hasOwnProperty('containersPage') || !data.hasOwnProperty('containersMap') ||
            !data.hasOwnProperty('hasNext') || !data.hasOwnProperty('hasPrevious')) {
            console.error("Unexpected data structure for table update. Expected { containersPage, containersMap, hasNext, hasPrevious }:", data);
            displayContainerTableError("Received unexpected data format.");
            return;
        }

        // Extract data from the unified structure
        const containersPage = data.containersPage;
        const containersMap = data.containersMap;
        const hasNext = data.hasNext;
        const hasPrevious = data.hasPrevious;

        // Check if containersPage has content
        if (!containersPage || !Array.isArray(containersPage.content) || containersPage.content.length === 0) {
            const emptyRow = document.createElement('tr');
            const emptyCell = document.createElement('td');
            emptyCell.colSpan = 4; // ID, Hierarchy, Price, Actions
            emptyCell.textContent = 'No containers found.';
            emptyCell.style.textAlign = 'center';
            emptyRow.appendChild(emptyCell);
            tbody.appendChild(emptyRow);
            updatePaginationControls(false, false); // No next/prev for empty
            return;
        }

        // Iterate through the containersPage content (which contains container/page info)
        containersPage.content.forEach(containerData => {
             // Assuming containerData represents the parent product ID or has a direct link
             // The key in containersMap is usually the parent product ID.
             // Adjust this logic based on your actual `containerData` structure from the backend.
             const parentProductId = containerData.parentProduct?.productId || containerData.productId || containerData.id; // Adapt key access

             if (parentProductId === undefined) {
                 console.warn("Could not determine parent product ID for container data:", containerData);
                 return; // Skip this row if ID is missing
             }

             const strainData = containersMap[parentProductId];
             if (!strainData) {
                 console.warn(`Strain data missing for container parent product ID: ${parentProductId}`);
                 // You might choose to skip or display an error for this row
                 // For now, let's create a row with placeholder/error info
                 const errorRow = document.createElement('tr');
                 errorRow.innerHTML = `<td colspan="4" style="color:red;">Error: Strain data missing for ID ${parentProductId}</td>`;
                 tbody.appendChild(errorRow);
                 return;
             }

            // Create a new table row for this container using strain data
            const row = createContainerTableRow(parentProductId, strainData); // Pass strain data
            if (row) {
                tbody.appendChild(row);
            }
        });

        // Re-initialize Lucide icons for any newly added icons
        if (typeof lucide !== 'undefined' && lucide.createIcons) {
            lucide.createIcons();
        }

        // --- Update Pagination Controls based on backend flags ---
        console.log(`Updating pagination controls: hasNext=${hasNext}, hasPrevious=${hasPrevious}`);
        updatePaginationControls(hasNext, hasPrevious);
    }

     // --- NEW: Function to Update Pagination Control States ---
     /**
      * Enables or disables the Next and Previous page buttons based on flags.
      * @param {boolean} hasNext - Whether a next page is available.
      * @param {boolean} hasPrevious - Whether a previous page is available.
      */
     function updatePaginationControls(hasNext, hasPrevious) {
         const nextBtn = document.getElementById('nextPageBtn');
         const prevBtn = document.getElementById('prevPageBtn');

         if (nextBtn) {
             nextBtn.disabled = !hasNext;
             // Optional: Add/remove a class for styling disabled state
             if (hasNext) {
                 nextBtn.classList.remove('disabled');
             } else {
                 nextBtn.classList.add('disabled');
             }
             console.log("Next button enabled:", hasNext);
         } else {
             console.warn("Next page button (#nextPageBtn) not found.");
         }

         if (prevBtn) {
             prevBtn.disabled = !hasPrevious;
             if (hasPrevious) {
                 prevBtn.classList.remove('disabled');
             } else {
                 prevBtn.classList.add('disabled');
             }
             console.log("Previous button enabled:", hasPrevious);
         } else {
             console.warn("Previous page button (#prevPageBtn) not found.");
         }
     }


    // --- Create Table Row (using strain data) ---
    /**
     * Creates a single table row (<tr>) for a container using strain data.
     * @param {number} parentProductId - The ID of the parent product (the container itself).
     * @param {Object} containerData - The pre-fetched strain data for this container from containersMap.
     * @returns {HTMLTableRowElement|null} The created table row, or null on error.
     */
    function createContainerTableRow(parentProductId, containerData) { // Renamed containerData to containerData for clarity inside function
         // Use containerData (strainData) directly instead of fetching it again
        try {
            const row = document.createElement('tr');
            // Store price for potential future client-side sorting/filtering
            const price = containerData.parentProductPrice || 0; // Assuming strainData contains parentProductPrice
            row.setAttribute('data-price', price);

            // --- ID Cell ---
            const idCell = document.createElement('td');
            idCell.textContent = parentProductId; // Container ID is the Parent Product ID
            row.appendChild(idCell);

            // --- Hierarchy Cell (using strainData) ---
            const hierarchyCell = document.createElement('td');
            const hierarchyDiv = document.createElement('div');
            hierarchyDiv.className = 'container-hierarchy';

            // Add levels (Container -> Container -> ...) from strainData.levels
            if (containerData.levels && Array.isArray(containerData.levels)) {
                containerData.levels.forEach((level, index) => {
                    const levelDiv = document.createElement('div');
                    levelDiv.className = 'container-level';
                    // Use data from the level object provided by strainData
                    levelDiv.textContent = `${level.unit} contains ${level.quantity}`;
                    levelDiv.setAttribute('data-container-parent-id', level.parentProductId);
                    levelDiv.setAttribute('data-container-child-id', level.childProductId);

                    // Add hover effect via JS if needed
                    // levelDiv.addEventListener('mouseenter', () => levelDiv.style.backgroundColor = '#f0f4f8');
                    // levelDiv.addEventListener('mouseleave', () => levelDiv.style.backgroundColor = '');

                    // Add click handler for container level (placeholder)
                    levelDiv.addEventListener('click', function() {
                        console.log(`Clicked on container level: Parent ID ${level.parentProductId}`);
                        // TODO: Implement navigation/request to /containers/${level.parentProductId}/details
                        alert(`View details for container product ID: ${level.parentProductId}`); // Placeholder
                    });

                    hierarchyDiv.appendChild(levelDiv);

                    // Add icon if it's NOT the last level
                    if (index < containerData.levels.length) {
                        const iconElement = document.createElement('i');
                        iconElement.setAttribute('data-lucide', 'corner-right-down');
                        iconElement.className = 'corner-right-down-icon';
                        levelDiv.appendChild(iconElement);
                    }
                });
            }

            // Add final product options
            const finalProductDiv = document.createElement('div');
            finalProductDiv.className = 'product-options-display';
            // Use the options string provided by the backend in strainData
            finalProductDiv.textContent = containerData.finalProductOptions || 'N/A';
            finalProductDiv.setAttribute('data-final-product-id', containerData.finalProductId || '');

            // Add click handler for final product (placeholder)
            finalProductDiv.addEventListener('click', function() {
                console.log(`Clicked on final product: ID ${containerData.finalProductId}`);
                // TODO: Implement navigation/request to /products/${containerData.finalProductId}/details
                alert(`View details for base product ID: ${containerData.finalProductId}`); // Placeholder
            });
            hierarchyDiv.appendChild(finalProductDiv);

            hierarchyCell.appendChild(hierarchyDiv);
            row.appendChild(hierarchyCell);

            // --- Price Cell ---
            const priceCell = document.createElement('td');
             // Format price assuming it's in the smallest currency unit (e.g., cents)
             // Adjust divisor (100) based on your backend price unit (e.g., 1000 if price is in mills)
             // Assuming price is already in dollars.cents (no division by 100 needed if backend sends dollars.cents)
             const formattedPrice = (price ).toFixed(2); // Removed division by 100 as per your note
            priceCell.textContent = `$${formattedPrice}`;
            row.appendChild(priceCell);

            // --- Actions Cell (Placeholder) ---
            const actionsCell = document.createElement('td');
            actionsCell.className = 'actions-cell';
            // Use Thymeleaf-like structure for actions, but they are placeholders for now
            actionsCell.innerHTML = `
                <button class="btn-icon view-btn" data-id="${parentProductId}">
                    <i data-lucide="eye"></i>
                </button>
                <button class="btn-icon edit-btn" data-id="${parentProductId}">
                    <i data-lucide="pencil"></i>
                </button>
                <button class="btn-icon delete-btn" data-id="${parentProductId}">
                    <i data-lucide="trash-2"></i>
                </button>
            `;
            // --- Action button event listeners are now handled by delegation ---
            // See the event delegation setup at the top of DOMContentLoaded
            row.appendChild(actionsCell);

            return row;
        } catch (error) {
            console.error(`Error creating table row for container ID ${parentProductId}:`, error);
            return null; // Indicate failure to create row
        }
    }

    // --- Display Table Error ---
    function displayContainerTableError(message) {
        const tbody = document.querySelector('.management-table tbody');
        if (tbody) {
            tbody.innerHTML = `<tr><td colspan="4" style="text-align: center; color: red;">${message}</td></tr>`;
        }
    }

    // --- Clear Form Errors ---
    function clearContainerFormErrors() {
        const errorContainers = document.querySelectorAll('#containerForm .error-message');
        errorContainers.forEach(container => container.textContent = '');
    }

    // --- Ensure Choices.js library is loaded ---
    if (typeof Choices === 'undefined') {
        console.error("Choices.js library is not loaded. Searchable dropdowns will not work.");
    }

    // --- Placeholder for Future Action Button Rebinding ---
    // function rebindContainerActionButtons() {
    //     // Use event delegation similar to product-management.js
    //     // e.g., listen on tbody for clicks on .view-btn, .edit-btn, .delete-btn
    //     // and call respective handler functions like viewContainer, editContainer, deleteContainer
    // }

    // --- Placeholder for Future Search/Filter Handlers ---
    // function handleContainerSearch(event) {
    //     const searchTerm = event.target.value.toLowerCase();
    //     // Implement search logic, potentially filtering the DOM rows
    //     // or requesting filtered data from the backend
    // }

    // --- Potential Code for goods-management.js ---
    // The following functions or patterns are common between product and container management
    // and could be candidates for a shared `goods-management.js` file:

    // 1. Generic Table Update Error Display (used in updateContainerTable)
    // function displayGenericTableError(tbodySelector, message, colSpan) {
    //     const tbody = document.querySelector(tbodySelector);
    //     if (tbody) {
    //         tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align: center; color: red;">${message}</td></tr>`;
    //     }
    // }

    // 2. Generic Price Formatting (used in price cell creation)
    // function formatPrice(priceInCents) {
    //     return `$${(priceInCents / 100).toFixed(2)}`;
    // }

    // 3. Generic Icon Re-initialization (used in updateContainerTable)
    // function reinitializeIcons() {
    //     if (typeof lucide !== 'undefined' && lucide.createIcons) {
    //         lucide.createIcons();
    //     }
    // }

    // 4. Generic Action Button Rebinding Pattern (conceptual)
    // function setupActionButtons(tbodySelector, viewHandler, editHandler, deleteHandler) {
    //     const tbody = document.querySelector(tbodySelector);
    //     if (!tbody) return;
    //     tbody.addEventListener('click', function(e) {
    //         if (e.target.closest('.view-btn')) {
    //             const id = e.target.closest('.view-btn').getAttribute('data-id');
    //             viewHandler(id);
    //         } else if (e.target.closest('.edit-btn')) {
    //             const id = e.target.closest('.edit-btn').getAttribute('data-id');
    //             editHandler(id);
    //         } else if (e.target.closest('.delete-btn')) {
    //             const id = e.target.closest('.delete-btn').getAttribute('data-id');
    //             deleteHandler(id);
    //         }
    //     });
    // }

    // --- NEW: Function to Open Edit Container Modal ---
    function openEditContainerModal(containerId) {
        console.log("Opening Edit Container Modal for ID:", containerId);
        currentEditContainerId = containerId; // Set edit state
        document.getElementById('containerForm').reset();
        clearContainerFormErrors();
        document.getElementById('containerModalTitle').textContent = 'Edit Container';

        // Reset Choices.js state
        currentSearchTerm = '';
        currentPage = 0;
        isLoading = false;
        hasMoreData = true;

        // --- Fetch Container Edit Data ---
        fetch(`/containers/${containerId}/edit`) // Use the endpoint for fetching edit data
            .then(response => {
                if (!response.ok) {
                    if (response.status === 404) {
                        throw new Error("Container not found for editing.");
                    }
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(editData => { // Expecting { containerData: ContainerRequestDTO, childProductSummary: ProductSummaryDTO }
                console.log("Fetched container edit data:", editData);

                if (!editData || !editData.containerData) {
                    throw new Error("Invalid data received from server for edit.");
                }

                const containerData = editData.containerData;
                const childProductSummary = editData.childProductSummary;

                // --- Populate Form Fields ---
                document.getElementById('parentProductPrice').value = containerData.parentProductPrice || '';
                // --- Populate Child Product Dropdown ---
                const childProductId = containerData.childProductId;
                const childProductSelect = document.getElementById('childProductId');
                if (childProductSelect && childProductId) {
                    // Ensure Choices.js is initialized
                    if (!childProductChoicesInstance) {
                         initializeChildProductChoices(); // Initialize if not already done
                    }

                    // After ensuring Choices.js is initialized, add the specific item and select it
                    setTimeout(() => {
                         if (childProductChoicesInstance) {
                             try {
                                 // Add the fetched child product summary to Choices
                                 childProductChoicesInstance.setChoices([{
                                     value: childProductSummary.productId.toString(),
                                     label: childProductSummary.displayText
                                 }], 'value', 'label', false); // Append
                                 console.log("Added child product summary to dropdown for edit:", childProductSummary);

                                 // Now set the selected value
                                 childProductChoicesInstance.setChoiceByValue(childProductId.toString());
                                 console.log("Set child product choice for edit:", childProductId);
                             } catch (e) {
                                 console.error("Error setting choice by value in edit mode:", e);
                                 // Fallback handled within setChoiceByValue try/catch
                             }
                         }
                    }, 100); // Small delay

                } else {
                    console.warn("Could not set child product in dropdown for edit. Select element or ID missing.");
                    if (!childProductChoicesInstance) {
                         initializeChildProductChoices();
                    }
                }

                document.getElementById('quantity').value = containerData.quantity || '';
                document.getElementById('unit').value = containerData.unit || ''; // Assumes unit is the enum string value
                document.getElementById('containerHeight').value = containerData.height || '';
                document.getElementById('containerWidth').value = containerData.width || '';
                document.getElementById('containerLength').value = containerData.length || '';
                // The DTO for edit doesn't include the unit, so default to cm
                document.getElementById('containerDistanceUnit').value = 'CENTIMETER';

                // --- Open Modal ---
                const containerFormModal = document.getElementById('containerFormModal');
                openModal(containerFormModal);

            })
            .catch(error => {
                console.error('Error fetching container details for edit:', error);
                alert("Failed to load container details for editing: " + (error.message || "Unknown error"));
            });
    }

    /**
     * Opens the Delete Confirmation modal and prepares it for the specified container.
     * Displays the container's parent product ID in the confirmation message.
     *
     * @param {number} containerId The ID of the parent product of the container to be deleted.
     */
    function openDeleteConfirmationModal(containerId) {
        console.log("Opening Delete Confirmation Modal for container ID:", containerId);
        if (isNaN(containerId) || containerId <= 0) {
            console.error("Invalid container ID provided for deletion:", containerId);
            alert("Error preparing delete confirmation: Invalid container ID.");
            return;
        }

        // --- Update the confirmation message with the container ID ---
        const confirmationMessage = document.querySelector('#deleteContainerModal .modal-body p');
        if (confirmationMessage) {
            confirmationMessage.textContent = `Are you sure you want to delete container with ID ${containerId}? This action cannot be undone.`;
        } else {
            console.warn("Could not find confirmation message paragraph in delete modal.");
            // Fallback: Maybe update a different element or add the paragraph dynamically
        }

        // --- Store the container ID on the confirm button (or a hidden field) ---
        const confirmDeleteBtn = document.getElementById('confirmDeleteContainer');
        if (confirmDeleteBtn) {
            confirmDeleteBtn.setAttribute('data-container-id', containerId);
            console.log("Stored container ID", containerId, "on confirm delete button.");
        } else {
            console.error("Confirm delete button (#confirmDeleteContainer) not found when trying to store ID.");
            alert("Error preparing delete confirmation. Please try again.");
            return;
        }

        // --- Open the modal ---
        const deleteContainerModal = document.getElementById('deleteContainerModal');
        if (deleteContainerModal) {
            openModal(deleteContainerModal);
            console.log("Delete confirmation modal opened.");
        } else {
            console.error("Delete Container Modal element (#deleteContainerModal) not found.");
            alert("Error opening delete confirmation modal.");
        }
    }

    /**
     * Handles the click event on the confirm delete button.
     * Retrieves the container ID and calls the deleteContainer function.
     */
    function handleConfirmDelete() {
        console.log("Confirm delete button clicked.");
        const confirmDeleteBtn = document.getElementById('confirmDeleteContainer');
        if (!confirmDeleteBtn) {
            console.error("Confirm delete button (#confirmDeleteContainer) not found in handleConfirmDelete.");
            alert("Error processing delete confirmation.");
            return;
        }

        const containerId = parseInt(confirmDeleteBtn.getAttribute('data-container-id'), 10);
        if (isNaN(containerId) || containerId <= 0) {
            console.error("Invalid container ID retrieved from confirm button:", confirmDeleteBtn.getAttribute('data-container-id'));
            alert("Error determining container to delete. Please try again.");
            return;
        }

        console.log("Confirmed deletion for container ID:", containerId);
        // Call the main delete function
        deleteContainer(containerId);
    }

    // --- NEW/UPDATED: Function to Perform Container Deletion (AJAX Call) ---
    /**
     * Performs the actual deletion of a container via an AJAX DELETE request.
     *
     * @param {number} containerId The ID of the parent product of the container to delete.
     */
    function deleteContainer(containerId) {
        console.log("Initiating deletion for container ID:", containerId);
        if (isNaN(containerId) || containerId <= 0) {
            console.error("Invalid container ID provided to deleteContainer:", containerId);
            alert("Error initiating deletion: Invalid container ID.");
            return;
        }

        // --- Get CSRF Token ---
        const csrf = getCSRFToken(); // Assuming this function exists in management.js
        if (!csrf || !csrf.token || !csrf.header) {
            console.error("CSRF token/header not found. Cannot proceed with deletion.");
            alert("Security token missing. Please reload the page and try again.");
            return;
        }

        // --- Construct the DELETE URL ---
        const url = `/containers/${containerId}/delete`;
        console.log("Sending DELETE request to:", url);

        // --- Send the AJAX DELETE Request ---
        fetch(url, {
            method: 'DELETE',
            headers: {
                [csrf.header]: csrf.token // Include CSRF token in headers
            }
        })
        .then(response => {
            console.log("Raw DELETE response received:", response);
            if (response.ok) {
                // --- Success (200 OK) ---
                console.log(`Container with ID ${containerId} deleted successfully.`);
                alert(`Container with ID ${containerId} deleted successfully!`);
                closeAllModals(); // Close the delete confirmation modal
                location.reload(); // Reload the page to reflect changes
                // --- Alternative (Better UX, but requires more JS): ---
                // Instead of reloading, you could:
                // 1. Remove the deleted row from the table dynamically:
                //    const rowToRemove = document.querySelector(`tr[data-container-id="${containerId}"]`); // Adjust selector if needed
                //    if (rowToRemove) rowToRemove.remove();
                // 2. Update pagination controls if necessary (e.g., if it was the last item on the page)
                //    This is complex without full server-side state in JS.
                // 3. Show a success message briefly.
                // For now, full reload is simpler and guarantees consistency.
            } else if (response.status === 404) {
                // --- Not Found (404) ---
                console.warn(`Container with ID ${containerId} not found on server.`);
                alert(`Container with ID ${containerId} was not found. It might have already been deleted.`);
                closeAllModals();
                location.reload(); // Reload to sync state
            } else if (response.status === 409) {
                 // --- Conflict (409) - Data Integrity Issue ---
                 return response.json().then(data => {
                     const errorMsg = data.error || "Cannot delete container due to existing references.";
                     console.warn(`Conflict deleting container ID ${containerId}:`, errorMsg);
                     alert(errorMsg);
                     closeAllModals();
                     // Do not reload, let user see the error
                     throw new Error("Conflict"); // To be caught by .catch
                 });
            } else {
                // --- Other HTTP Errors (500, etc.) ---
                console.error(`HTTP error deleting container ID ${containerId}! status: ${response.status}`);
                alert(`An error occurred while deleting the container (Status: ${response.status}). Please try again.`);
                closeAllModals();
                // Do not reload on generic error
                throw new Error(`HTTP error! status: ${response.status}`);
            }
        })
        .catch(error => {
            // --- Network Errors or Errors Thrown Above ---
            if (error.message !== "Conflict") { // Conflict was already handled
                 console.error('Network error or unexpected error deleting container ID', containerId, ':', error);
                 alert("A network error occurred. Please check your connection and try again.");
                 // Keep modal closed or open? Let's close on network error.
                 closeAllModals();
            }
            // If it was a Conflict, the modal is already closed, and alert shown.
        });
    }

    // --- NEW: Function to Fetch and Show View Modal ---
    function fetchAndShowViewModal(containerId) {
        console.log("Fetching view data for Container ID:", containerId);

        fetch(`/containers/${containerId}/details`) // Use the endpoint for fetching view data
            .then(response => {
                if (!response.ok) {
                    if (response.status === 404) {
                        throw new Error("Container details not found.");
                    }
                    throw new Error(`HTTP error! status: ${response.status}`);
                }
                return response.json();
            })
            .then(viewData => { // <-- Expect ContainerViewDTO JSON
                console.log("Received view ", viewData);
                populateContainerViewModal(viewData); // <-- Call new populate function
                const viewModal = document.getElementById('viewContainerModal');
                if (viewModal) {
                    openModal(viewModal);
                } else {
                    console.error("View Container Modal element (#viewContainerModal) not found.");
                }
            })
            .catch(error => {
                console.error('Error fetching container view ', error);
                alert("Failed to load container details. Please try again.");
            });
    }

    // Function to Populate the View Modal ---
    function populateContainerViewModal(data) {
        // --- Populate Basic Fields ---
        document.getElementById('viewContainerId').textContent = data.containerId || 'N/A';
        document.getElementById('viewParentProductId').textContent = data.parentProductId || 'N/A';

        // --- Populate Hierarchy ---
        const hierarchyContainer = document.getElementById('viewHierarchy');
        if (hierarchyContainer) {
            hierarchyContainer.innerHTML = ''; // Clear previous content
            if (data.hierarchyLevels && Array.isArray(data.hierarchyLevels)) {
                const hierarchyList = document.createElement('ul');
                data.hierarchyLevels.forEach((level, index) => {
                    const listItem = document.createElement('li');
                    listItem.textContent = `${level.unit} contains ${level.quantity}`;
                    hierarchyList.appendChild(listItem);
                });
                // Add final product options
                if (data.finalProductOptions) {
                     const finalItem = document.createElement('li');
                     finalItem.textContent = data.finalProductOptions;
                     hierarchyList.appendChild(finalItem);
                }
                hierarchyContainer.appendChild(hierarchyList);
            } else {
                hierarchyContainer.textContent = 'Hierarchy data unavailable';
            }
        }

        // --- Populate Price ---
        const priceElement = document.getElementById('viewPrice');
        if (priceElement) {
             const formattedPrice = data.price ? `$${(data.price ).toFixed(2)}` : 'N/A'; // No division by 100
             priceElement.textContent = formattedPrice;
        }

        // --- Populate Total Stock ---
        document.getElementById('viewStock').textContent = data.totalStock !== undefined ? data.totalStock : 'N/A';

        // --- Populate Barcode ---
        const barcodeTextElement = document.getElementById('viewBarcodeText');
        const barcodeImageElement = document.getElementById('viewBarcodeImage');
        if (barcodeTextElement && barcodeImageElement) {
            barcodeTextElement.textContent = data.fullBarcode || 'N/A';
            if (data.fullBarcode) {
                try {
                    if (typeof JsBarcode !== 'undefined') {
                         JsBarcode(barcodeImageElement, data.fullBarcode, {
                            format: "CODE128",
                            displayValue: false,
                            width: 2,
                            height: 60
                         });
                    } else {
                        console.warn("JsBarcode library not found. Cannot generate barcode image.");
                        barcodeImageElement.alt = "Barcode library not loaded";
                        barcodeImageElement.src = "";
                    }
                } catch (e) {
                    console.error("Error generating barcode image:", e);
                    barcodeImageElement.alt = "Error generating barcode";
                    barcodeImageElement.src = "";
                }
            } else {
                 barcodeImageElement.alt = "No barcode data";
                 barcodeImageElement.src = "";
            }
        }

        // --- Populate Audit Fields ---
        document.getElementById('viewCreatedAt').textContent =
            data.createdAt ? new Date(data.createdAt).toLocaleString() : 'N/A';
        document.getElementById('viewCreatedBy').textContent = data.createdBy || 'System';
        document.getElementById('viewUpdatedAt').textContent =
            data.updatedAt ? new Date(data.updatedAt).toLocaleString() : 'N/A';
        document.getElementById('viewUpdatedBy').textContent = data.updatedBy || 'System';

        // --- Populate Action Links ---
        const inventoryStockLink = document.getElementById('viewInventoryStockLink');
        const ordersLink = document.getElementById('viewOrdersLink');
        if (inventoryStockLink && ordersLink) {
            inventoryStockLink.href = `/inventory-stock/manage?productId=${data.parentProductId}`;
            ordersLink.href = `/orders/manage?productId=${data.parentProductId}`;
        }

        // Call the shared unit converter from goods-management.js
        initializeUnitConverter({
            gridId: '#viewContainerModal .detail-grid',
            selectorId: 'viewContainerUnitSelector',
            volumeId: 'viewContainerVolume',
            heightId: 'viewContainerHeight',
            widthId: 'viewContainerWidth',
            lengthId: 'viewContainerLength'
        }, data); // Pass the DTO data
    }


    // --- Potential Code for goods-management.js ---
    // The following functions or patterns are common between product and container management
    // and could be candidates for a shared `goods-management.js` file:

    // 1. Generic Table Update Error Display (used in updateContainerTable)
    // function displayGenericTableError(tbodySelector, message, colSpan) {
    //     const tbody = document.querySelector(tbodySelector);
    //     if (tbody) {
    //         tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align: center; color: red;">${message}</td></tr>`;
    //     }
    // }

    // 2. Generic Price Formatting (used in price cell creation)
    // function formatPrice(priceInCents) {
    //     return `$${(priceInCents / 100).toFixed(2)}`;
    // }

    // 3. Generic Icon Re-initialization (used in updateContainerTable)
    // function reinitializeIcons() {
    //     if (typeof lucide !== 'undefined' && lucide.createIcons) {
    //         lucide.createIcons();
    //     }
    // }

    // 4. Generic Action Button Rebinding Pattern (conceptual)
    // function setupActionButtons(tbodySelector, viewHandler, editHandler, deleteHandler) {
    //     const tbody = document.querySelector(tbodySelector);
    //     if (!tbody) return;
    //     tbody.addEventListener('click', function(e) {
    //         if (e.target.closest('.view-btn')) {
    //             const id = e.target.closest('.view-btn').getAttribute('data-id');
    //             viewHandler(id);
    //         } else if (e.target.closest('.edit-btn')) {
    //             const id = e.target.closest('.edit-btn').getAttribute('data-id');
    //             editHandler(id);
    //         } else if (e.target.closest('.delete-btn')) {
    //             const id = e.target.closest('.delete-btn').getAttribute('data-id');
    //             deleteHandler(id);
    //         }
    //     });
    // }

    // --- Filter Modal Event Binding (Using Event Delegation) ---
    // Setup delegation for the Filter Modal *once* on page load
    filterModalEventBinding();
    // --- End Filter Modal Event Binding ---
});