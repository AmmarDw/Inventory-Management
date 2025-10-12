// product-management.js - Product management functionality
// Relies on management.js for shared modal/CSRF logic

// --- Store delegation function references to prevent duplicates ---
// MOVE THESE DECLARATIONS OUTSIDE OF ANY FUNCTION OR EVENT LISTENER
let optionGroupsDelegationFn = null;
// --- End delegation references ---

document.addEventListener('DOMContentLoaded', function () {
    // Specific modals for product
    const productFormModal = registerModal('productFormModal'); // Register with shared logic
    // Filter modal registration and #filterBtn handling is done in management.js

    // --- Event Listeners ---

    // Open Create/Edit Modal
    document.getElementById('createProductBtn').addEventListener('click', () => {

        openModal(productFormModal); // Use shared function

        // --- CRITICAL FIX: Setup event delegation AFTER modal is opened ---
        setupOptionGroupsDelegation(); // <-- Setup delegation for Create/Edit form
        // --- END CRITICAL FIX ---

        document.getElementById('modalTitle').textContent = 'Create New Product';
        document.getElementById('productForm').reset();
        document.getElementById('productId').value = '';
        resetOptionGroups();
    });

    // Action buttons (View, Edit, Delete)
    rebindProductActionButtons();

    // Delete confirmation
    document.getElementById('confirmDelete').addEventListener('click', function () {
        const productId = this.getAttribute('data-id');
        deleteProduct(productId);
    });

    // Form submission
    document.getElementById('productForm').addEventListener('submit', function (e) {
        e.preventDefault();
        saveProduct();
    });

    // Search functionality
    document.getElementById('productSearch').addEventListener('input', function () {
        const searchTerm = this.value.toLowerCase();
        const rows = document.querySelectorAll('.management-table tbody tr');
        rows.forEach(row => {
            const id = row.cells[0].textContent.toLowerCase();
            const options = row.cells[1].textContent.toLowerCase();
            row.style.display = (id.includes(searchTerm) || options.includes(searchTerm))
                ? ''
                : 'none';
        });
    });

    // Filter functionality (Apply/Reset buttons)
    const applyFiltersBtn = document.getElementById('applyFilters');
    const resetFiltersBtn = document.getElementById('resetFilters');
    if (applyFiltersBtn) {
        applyFiltersBtn.addEventListener('click', applyFilters);
    } else {
        console.error("Apply Filters button (#applyFilters) not found in DOM.");
    }
    if (resetFiltersBtn) {
        resetFiltersBtn.addEventListener('click', resetFilters);
    } else {
        console.error("Reset Filters button (#resetFilters) not found in DOM.");
    }

    // --- Filter Modal Event Binding (Using Event Delegation) ---
    // Setup delegation for the Filter Modal *once* on page load
    filterModalEventBinding();
    // --- End Filter Modal Event Binding ---


    // Initialize option groups functionality for *creation/editing* form
    initOptionGroups();

    // Initialize option groups functionality for *filtering* modal
    initFilterOptionGroups();
});

// --- CRITICAL FIX FUNCTION: Setup Event Delegation for Create/Edit Form ---
// This function MUST be called AFTER the #productFormModal is opened and its content is in the DOM.
function setupOptionGroupsDelegation() {
    const container = document.getElementById('productFormModal');
    if (!container) {
        console.error("Cannot setup option group delegation: Container (#option-groups-container) not found.");
        return;
    }

    bindOptionGroupEvents(container.querySelector('.option-group'));

    // If a previous listener exists, remove it to prevent duplicates
    // Ensure optionGroupsDelegationFn is accessible here (it should be if declared globally above)
    if (optionGroupsDelegationFn) {
        container.removeEventListener('click', optionGroupsDelegationFn);
    }

    // Define the delegation function for clicks
    optionGroupsDelegationFn = function (e) {
        if (e.target && e.target.classList.contains('add-option-group')) { 
            e.preventDefault();
            addOptionGroup();
        }
        if (e.target && e.target.classList.contains('remove-option-group')) {
            e.preventDefault();
            const allGroups = container.querySelectorAll('.option-group');
            if (allGroups.length <= 1) {
                alert("You must have at least one option group.");
                return;
            }
            const groupToRemove = e.target.closest('.option-group');
            if (groupToRemove) {
                groupToRemove.remove();
            } else {
                console.warn("Could not find the option group to remove (Create/Edit - dynamic).");
            }
        }
    };

    // Attach the new listener
    container.addEventListener('click', optionGroupsDelegationFn);
}
// --- END CRITICAL FIX FUNCTION ---

// --- Option Groups for Creation/Edit Form ---

function initOptionGroups() {
    // Ensure the template group inside the modal is clean on initial load
    const container = document.getElementById('option-groups-container');
    if (container) {
        const firstGroup = container.querySelector('.option-group');
        if (firstGroup) {
            // Ensure the template group is in a clean, default state
            resetOptionGroup(firstGroup); // <-- Ensure template is pristine
        }
    }
}

function addOptionGroup() {
    const container = document.getElementById('option-groups-container');
    if (!container) {
        console.error("Cannot add option group: Container (#option-groups-container) not found.");
        return;
    }
    const templateGroup = container.querySelector('.option-group'); // Find the first group as template
    if (!templateGroup) {
        console.error("Cannot add option group: No template group found.");
        alert("Unable to add a new option group. Please try reloading the page.");
        return;
    }
    const newGroup = templateGroup.cloneNode(true); // Clone the template
    // Reset the cloned group's values
    resetOptionGroup(newGroup);
    // Bind events for the *new* group's selects.
    bindOptionGroupEvents(newGroup);
    // Append the new group to the container
    container.appendChild(newGroup);
}

function resetOptionGroups() {
    const container = document.getElementById('option-groups-container');
    if (!container) return;
    const groups = container.querySelectorAll('.option-group');
    // Keep the first group, remove the rest
    for (let i = groups.length - 1; i > 0; i--) {
        groups[i].remove();
    }
    // Reset the first (and now only) group
    if (groups.length > 0) {
        resetOptionGroup(groups[0]);
    }
}

function resetOptionGroup(groupElement) {
    const categorySelect = groupElement.querySelector('.category-select');
    const optionSelect = groupElement.querySelector('.option-select');
    const newCatInput = groupElement.querySelector('.new-category-input');
    const newOptInput = groupElement.querySelector('.new-option-input');

    if (categorySelect) categorySelect.value = ''; // TODO why isn't it the same as optionSelect?
    if (optionSelect) optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';
    if (newCatInput) {
        newCatInput.style.display = 'none';
        const input = newCatInput.querySelector('input');
        if (input) input.value = '';
    }
    if (newOptInput) {
        newOptInput.style.display = 'none';
        const input = newOptInput.querySelector('input');
        if (input) input.value = '';
    }
}

function bindOptionGroupEvents(groupElement) {  
    if (!groupElement) {
        console.error("bindOptionGroupEvents called with invalid groupElement");
        return;
    }
    const categorySelect = groupElement.querySelector('.category-select');
    const optionSelect = groupElement.querySelector('.option-select');

    // Add listeners for the new group's selects
    if (categorySelect) {
        categorySelect.addEventListener('change', function () {
            updateOptionSelect(this);
        });
    }
    if (optionSelect) {
        optionSelect.addEventListener('change', function () {
            const newOptIn = this.closest('.option-group').querySelector('.new-option-input');
            if (newOptIn) {
                newOptIn.style.display = this.value === 'new' ? 'block' : 'none';
            }
        });
    }
}

function updateOptionSelect(categorySelect) {
    const group = categorySelect.closest('.option-group');
    const optionSelect = group.querySelector('.option-select');
    const newCatInput = group.querySelector('.new-category-input');
    const newOptInput = group.querySelector('.new-option-input');

    if (newCatInput) newCatInput.style.display = categorySelect.value === 'new' ? 'block' : 'none';
    if (optionSelect) {
        optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';
        if (categorySelect.value !== 'new' && categorySelect.value !== '') {
            const catId = parseInt(categorySelect.value);
            const options = categoryOptionsMap[catId] || [];
            options.forEach(opt => {
                const option = document.createElement('option');
                option.value = opt.optionId;
                option.textContent = opt.optionValue;
                optionSelect.appendChild(option);
            });
        }
        const newOpt = document.createElement('option');
        newOpt.value = 'new';
        newOpt.textContent = '+ New Option';
        optionSelect.appendChild(newOpt);
        if (newOptInput) newOptInput.style.display = 'none';
    }
}


// --- Option Groups for Filter Modal ---

function initFilterOptionGroups() {
     // Ensure the template filter group is clean on initial load
     const container = document.getElementById('filter-option-groups-container');
     if (container) {
         const firstGroup = container.querySelector('.option-group');
         if (firstGroup) {
             // Reset first filter group template
             const categorySelect = firstGroup.querySelector('.filter-category');
             const optionSelect = firstGroup.querySelector('.filter-option');
             if (categorySelect) categorySelect.value = '';
             if (optionSelect) optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';
         }
     }
}

// --- Data Interaction Functions ---

function fetchProductDetails(productId, action) {
    fetch(`/products/${productId}/details`)
        .then(response => response.json())
        .then(data => {
            if (action === 'view') {
                populateViewModal(data);
                openModal(document.getElementById('viewModal'));
            } else if (action === 'edit') {
                populateEditForm(data);
                openModal(document.getElementById('productFormModal'));
                // --- CRITICAL FIX: Setup event delegation AFTER Edit modal is opened/populated ---
                setupOptionGroupsDelegation(); // <-- Ensure delegation works for Edit too
                // --- END CRITICAL FIX ---
            }
        })
        .catch(error => {
            console.error('Error fetching product details:', error);
            // Also log if setupOptionGroupsDelegation itself failed due to the ReferenceError
            if (error instanceof ReferenceError && error.message.includes('optionGroupsDelegationFn')) {
                 console.error("Specifically, setupOptionGroupsDelegation failed due to ReferenceError. Check variable scope.");
            }
        });
}

function populateViewModal(data) {
    document.getElementById('viewId').textContent = data.productId;
    document.getElementById('viewPrice').textContent = `$${data.price}`;
    document.getElementById('viewStock').textContent = data.totalStock;
    document.getElementById('viewCreatedAt').textContent = new Date(data.createdAt).toLocaleString();
    document.getElementById('viewCreatedBy').textContent = data.createdBy || 'System';
    document.getElementById('viewUpdatedAt').textContent = new Date(data.updatedAt).toLocaleString();
    document.getElementById('viewUpdatedBy').textContent = data.updatedBy || 'System';

    // This function now just calls the shared initializer
    initializeUnitConverter({
        gridId: '#viewModal .detail-grid', // Note: This uses the product modal's ID
        selectorId: 'viewProductUnitSelector',
        volumeId: 'viewVolume',
        heightId: 'viewHeight',
        widthId: 'viewWidth',
        lengthId: 'viewLength'
    }, data); // Pass the ProductDTO data
    
    // Populate options list
    const optionsContainer = document.getElementById('viewOptions');
    optionsContainer.innerHTML = '';
    data.productOptions.forEach(option => {
        const optionDiv = document.createElement('div');
        optionDiv.className = 'option-display';
        const categorySpan = document.createElement('span');
        categorySpan.className = 'option-category';
        categorySpan.textContent = option.categoryName + ': ';
        const valueSpan = document.createElement('span');
        valueSpan.className = 'option-value';
        valueSpan.textContent = option.optionValue;
        optionDiv.appendChild(categorySpan);
        optionDiv.appendChild(valueSpan);
        optionsContainer.appendChild(optionDiv);
    });

    document.getElementById('barcodeText').textContent = data.fullBarcode;
    JsBarcode("#barcodeImage", data.fullBarcode, {
        format: "CODE128",
        displayValue: false,
        width: 2,
        height: 60
    });

    document.getElementById('inventoryStockLink').href =
        `/inventory-stock/manage?productId=${data.productId}`;
    document.getElementById('ordersLink').href =
        `/orders/manage?productId=${data.productId}`;
}

function populateEditForm(data) {
    document.getElementById('modalTitle').textContent = 'Edit Product';
    document.getElementById('productId').value = data.productId;
    document.getElementById('modalPrice').value = data.price;

    // Populate dimension fields from the DTO
    // The backend sends dimensions in the base unit (cm), so we set the form to match.
    document.getElementById('modalHeight').value = data.height;
    document.getElementById('modalWidth').value = data.width;
    document.getElementById('modalLength').value = data.length;
    document.getElementById('modalDistanceUnit').value = 'CENTIMETER'; // Default to cm

    resetOptionGroups(); // Clear any extra groups, reset the first one
    const container = document.getElementById('option-groups-container');
    if (!container) return;

    // Add groups to match the number of existing options
    while (container.querySelectorAll('.option-group').length < data.productOptions.length) {
        addOptionGroup(); // This will now work due to setupOptionGroupsDelegation being called in fetchProductDetails
    }

    // Populate the groups with existing option data
    const groups = container.querySelectorAll('.option-group');
    data.productOptions.forEach((option, index) => {
        const group = groups[index];
        if (!group) return;
        const categorySelect = group.querySelector('.category-select');
        if (categorySelect) {
            const categoryOption = Array.from(categorySelect.options).find(
                opt => parseInt(opt.value) === option.categoryId
            );
            if (categoryOption) {
                categoryOption.selected = true;
                updateOptionSelect(categorySelect); // Populate options for this category
                // Delay setting the option select to allow options to populate
                setTimeout(() => {
                    const optionSelect = group.querySelector('.option-select');
                    if (optionSelect) {
                        const optionOption = Array.from(optionSelect.options).find(
                            opt => parseInt(opt.value) === option.optionId
                        );
                        if (optionOption) {
                            optionOption.selected = true;
                        }
                    }
                }, 10);
            }
        }
    });
}

// TODO add volume related fields
function saveProduct() {
    const csrf = getCSRFToken();
    const productId = document.getElementById('productId').value;
    const url = productId ? `/products/${productId}/update` : '/products/create';
    const method = productId ? 'PUT' : 'POST';

    const formData = {
        price: parseFloat(document.getElementById('modalPrice').value),
        height: parseFloat(document.getElementById('modalHeight').value),
        width: parseFloat(document.getElementById('modalWidth').value),
        length: parseFloat(document.getElementById('modalLength').value),
        distanceUnit: document.getElementById('modalDistanceUnit').value,
        categoryIds: [],
        optionIds: [],
        newCategoryNames: [],
        newOptionValues: []
    };

    document.querySelectorAll('#option-groups-container .option-group').forEach(group => {
        const categorySelect = group.querySelector('.category-select');
        const optionSelect = group.querySelector('.option-select');
        const newCatInput = group.querySelector('.new-category-input input');
        const newOptInput = group.querySelector('.new-option-input input');
        if (categorySelect && optionSelect) {
            if (categorySelect.value === 'new' && newCatInput && newCatInput.value.trim() !== '') {
                formData.newCategoryNames.push(newCatInput.value.trim());
                formData.categoryIds.push('new');
            } else if (categorySelect.value !== '' && categorySelect.value !== 'new') {
                formData.categoryIds.push(categorySelect.value);
            } else {
                console.warn("Invalid category selection in a group");
                return;
            }

            if (optionSelect.value === 'new' && newOptInput && newOptInput.value.trim() !== '') {
                formData.newOptionValues.push(newOptInput.value.trim());
                formData.optionIds.push('new');
            } else if (optionSelect.value !== '' && optionSelect.value !== 'new') {
                formData.optionIds.push(optionSelect.value);
            } else {
                console.warn("Invalid option selection in a group");
                return;
            }
        }
    });

    // Basic validation before sending
    if (isNaN(formData.price) || formData.price <= 0) {
        alert("Please enter a valid price.");
        return;
    }
    if (formData.optionIds.length === 0 && formData.newOptionValues.length === 0) {
        alert("Please select or add at least one product option.");
        return;
    }
    if (isNaN(formData.height) || formData.height <= 0 ||
        isNaN(formData.width) || formData.width <= 0 ||
        isNaN(formData.length) || formData.length <= 0) {
        alert("Please enter valid, positive numbers for all dimensions.");
        return;
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
            if (response.ok) {
                location.reload();
            } else if (response.status === 400) {
                return response.json().then(errors => {
                    displayFormErrors(errors, {
                        price: 'priceError'
                    });
                });
            } else {
                alert("An error occurred while saving the product. Please try again.");
                console.error("Save product error:", response.status, response.statusText);
            }
        })
        .catch(error => {
            console.error('Network error saving product:', error);
            alert("A network error occurred. Please check your connection and try again.");
        });
}

function deleteProduct(productId) {
    const csrf = getCSRFToken();
    fetch(`/products/${productId}/delete`, {
        method: 'DELETE',
        headers: {
            [csrf.header]: csrf.token
        }
    })
        .then(response => {
            if (response.ok) {
                location.reload();
            } else {
                console.error('Error deleting product');
                alert("An error occurred while deleting the product. Please try again.");
            }
        })
        .catch(error => {
            console.error('Network error deleting product:', error);
            alert("A network error occurred. Please check your connection and try again.");
        });
}

// --- Combined Filter Functions ---

function applyFilters() {
    // --- Collect Price/Stock Filters ---
    const minPriceInput = document.getElementById('filterPriceMin').value.trim();
    const maxPriceInput = document.getElementById('filterPriceMax').value.trim();
    const minStockInput = document.getElementById('filterStockMin').value.trim();

    const minPrice = minPriceInput !== '' ? parseFloat(minPriceInput) : null;
    const maxPrice = maxPriceInput !== '' ? parseFloat(maxPriceInput) : null;
    const minStock = minStockInput !== '' ? parseInt(minStockInput, 10) : null;

    // --- Collect Option Filters ---
    const optionIds = [];
    document.querySelectorAll('#filter-option-groups-container .filter-option').forEach(select => {
        if (select.value && select.value !== '') {
            const optionId = parseInt(select.value, 10);
            if (!isNaN(optionId)) {
                optionIds.push(optionId);
            }
        }
    });

    // --- Prepare Request Data ---
    // Send the collected data. An empty object {} or one with nulls/empty list
    // will be handled by the backend to return ALL products.
    const filterData = {};
    if (minPrice !== null) filterData.minPrice = minPrice;
    if (maxPrice !== null) filterData.maxPrice = maxPrice;
    if (minStock !== null) filterData.minStock = minStock;
    if (optionIds.length > 0) filterData.optionIds = optionIds;

    // --- Send Filter Request to Backend ---
    const csrf = getCSRFToken();
    fetch('/products/filter', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrf.header]: csrf.token
        },
        body: JSON.stringify(filterData) // Send the data (could be empty {})
    })
        .then(response => {
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            return response.json();
        })
        .then(products => {
            updateProductTable(products);
            closeAllModals();
        })
        .catch(error => {
            console.error('Error filtering products:', error);
            alert("An error occurred while filtering products. Please try again.");
        });
}

function resetFilters() {
    // --- UI Reset ---
    document.getElementById('filterPriceMin').value = '';
    document.getElementById('filterPriceMax').value = '';
    document.getElementById('filterStockMin').value = '';

    const container = document.getElementById('filter-option-groups-container');
    if (container) {
        const groups = container.querySelectorAll('.option-group');
        for (let i = groups.length - 1; i > 0; i--) {
            groups[i].remove();
        }
        if (groups.length > 0) {
            const firstGroup = groups[0];
            const categorySelect = firstGroup.querySelector('.filter-category');
            const optionSelect = firstGroup.querySelector('.filter-option');
            if (categorySelect) categorySelect.value = '';
            if (optionSelect) optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';
        }
    }
    // --- End UI Reset ---

    // --- Fetch All Products to Reset Table ---
    const csrf = getCSRFToken();
    fetch('/products/filter', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            [csrf.header]: csrf.token
        },
        body: JSON.stringify({}) // Send empty object to get all products
    })
    .then(response => {
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.json();
    })
    .then(products => {
        updateProductTable(products);
        closeAllModals();
    })
    .catch(error => {
        console.error('Error resetting filters / fetching all products:', error);
        alert("An error occurred while resetting filters. Please try again.");
        closeAllModals(); // Close modal even on error for better UX
    });
    // --- End Fetch All Products ---
}

// --- Table Update and Rebinding ---

function updateProductTable(products) {
    const tbody = document.querySelector('.management-table tbody');
    if (!tbody) return;
    tbody.innerHTML = '';

    if (products.length === 0) {
        const emptyRow = document.createElement('tr');
        const emptyCell = document.createElement('td');
        emptyCell.colSpan = 4;
        emptyCell.textContent = 'No products found matching the filters.';
        emptyCell.style.textAlign = 'center';
        emptyRow.appendChild(emptyCell);
        tbody.appendChild(emptyRow);
        rebindProductActionButtons(); // TODO What the hell!
        return;
    }

    products.forEach(product => {
        const row = document.createElement('tr');
        row.setAttribute('data-price', product.price);
        row.setAttribute('data-stock', product.totalStock !== undefined ? product.totalStock : 'unknown');

        const idCell = document.createElement('td');
        idCell.textContent = product.productId;
        row.appendChild(idCell);

        const optionsCell = document.createElement('td');
        if (product.productOptions && Array.isArray(product.productOptions) && product.productOptions.length > 0) {
            optionsCell.textContent = product.productOptions.map(opt => opt.optionValue).join(', ');
        } else {
            optionsCell.textContent = '';
        }
        row.appendChild(optionsCell);

        const priceCell = document.createElement('td');
        priceCell.textContent = '$' + product.price;
        row.appendChild(priceCell);

        const actionsCell = document.createElement('td');
        actionsCell.className = 'actions-cell';
        actionsCell.innerHTML = `
            <button class="btn-icon view-btn" data-id="${product.productId}">
                <i data-lucide="eye"></i>
            </button>
            <button class="btn-icon edit-btn" data-id="${product.productId}">
                <i data-lucide="pencil"></i>
            </button>
            <button class="btn-icon delete-btn" data-id="${product.productId}">
                <i data-lucide="trash-2"></i>
            </button>
        `;
        row.appendChild(actionsCell);

        tbody.appendChild(row);
    });

    rebindProductActionButtons();
    if (typeof lucide !== 'undefined' && lucide.createIcons) {
        lucide.createIcons();
    }
}

function rebindProductActionButtons() {
    const newViewButtons = document.querySelectorAll('.view-btn:not([data-listener-added])');
    const newEditButtons = document.querySelectorAll('.edit-btn:not([data-listener-added])');
    const newDeleteButtons = document.querySelectorAll('.delete-btn:not([data-listener-added])');

    newViewButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            const productId = this.getAttribute('data-id');
            fetchProductDetails(productId, 'view');
        });
        btn.setAttribute('data-listener-added', 'true');
    });

    newEditButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            const productId = this.getAttribute('data-id');
            fetchProductDetails(productId, 'edit');
        });
        btn.setAttribute('data-listener-added', 'true');
    });

    newDeleteButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            const productId = this.getAttribute('data-id');
            const row = this.closest('tr');
            let optionsText = '';
            if (row && row.cells[1]) {
                optionsText = ` (${row.cells[1].textContent})`;
            }
            const productName = `#${productId}${optionsText}`;
            document.getElementById('deleteProductName').textContent = productName;
            document.getElementById('confirmDelete').setAttribute('data-id', productId);
            openModal(document.getElementById('deleteModal'));
        });
        btn.setAttribute('data-listener-added', 'true');
    });
}