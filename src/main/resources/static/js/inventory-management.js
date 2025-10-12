// inventory-management.js - Inventory management functionality
// Relies on management.js for shared modal/CSRF logic

// Conversion factors from our base unit (cm³) to other units.
// This mirrors the logic from the Java Enum.
const CONVERSION_FACTORS_FROM_CM3 = {
    // To get the target unit, multiply the cm³ value by the factor
    CENTIMETER: 1,
    LITER:      1 / 1000,
    METER:      1 / 1000000,
    MILLIMETER: 1000,
    INCH:       1 / (2.54 * 2.54 * 2.54)
};


document.addEventListener('DOMContentLoaded', function() {
  // Specific modals for inventory
  const inventoryFormModal = registerModal('inventoryFormModal'); // Register with shared logic
  // const filterModal = registerModal('filterModal'); // Register with shared logic

  // --- Event Listeners ---

  // Open Create/Edit Modal
  document.getElementById('createInventoryBtn').addEventListener('click', () => {
    openModal(inventoryFormModal); // Use shared function
    document.getElementById('modalTitle').textContent = 'Create New Inventory';
    document.getElementById('inventoryForm').reset();
    document.getElementById('inventoryId').value = '';
  });

  // Open Filter Modal
  // document.getElementById('filterBtn').addEventListener('click', () => {
  //   openModal(filterModal); // Use shared function
  // });

  // Action buttons (View, Edit, Delete)
  document.querySelectorAll('.view-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const inventoryId = this.getAttribute('data-id');
      fetchInventoryDetails(inventoryId, 'view');
    });
  });

  document.querySelectorAll('.edit-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const inventoryId = this.getAttribute('data-id');
      fetchInventoryDetails(inventoryId, 'edit');
    });
  });

  document.querySelectorAll('.delete-btn').forEach(btn => {
    btn.addEventListener('click', function() {
      const inventoryId = this.getAttribute('data-id');
      const row = this.closest('tr');
      const inventoryName = `#${inventoryId} (${row.cells[1].textContent})`;
      document.getElementById('deleteProductName').textContent = inventoryName; // Reuse delete modal element ID
      document.getElementById('confirmDelete').setAttribute('data-id', inventoryId);
      openModal(document.getElementById('deleteModal')); // Use shared function
    });
  });

  // Delete confirmation
  document.getElementById('confirmDelete').addEventListener('click', function() {
    const inventoryId = this.getAttribute('data-id');
    deleteInventory(inventoryId);
  });

  // Form submission
  document.getElementById('inventoryForm').addEventListener('submit', function(e) {
    e.preventDefault();
    saveInventory();
  });

  // Search functionality
  const searchInput = document.getElementById('inventorySearch');
  if (searchInput) {
    searchInput.addEventListener('input', function() {
      const searchTerm = this.value.toLowerCase().trim();
      const rows = document.querySelectorAll('.management-table tbody tr');
      rows.forEach(row => {
        const id = row.cells[0].textContent.toLowerCase();
        const location = row.cells[2].textContent.toLowerCase();
        row.style.display = (id.includes(searchTerm) || location.includes(searchTerm))
          ? ''
          : 'none';
      });
    });
  }

  // Filter functionality
  document.getElementById('applyFilters').addEventListener('click', applyFilters);
  document.getElementById('resetFilters').addEventListener('click', resetFilters);

  // Capacity unit change in the view modal
  document.getElementById('viewCapacityUnit').addEventListener('change', updateDisplayedCapacity);
});

// --- Filter Functions ---

function applyFilters() {
  const typeFilter = document.getElementById('filterType').value;
  const statusFilter = document.getElementById('filterStatus').value;
  document.querySelectorAll('.management-table tbody tr').forEach(row => {
    const type = row.getAttribute('data-type');
    const status = row.getAttribute('data-status');
    const typeMatch = !typeFilter || type === typeFilter;
    const statusMatch = !statusFilter || status === statusFilter;
    row.style.display = (typeMatch && statusMatch) ? '' : 'none';
  });
  // Use shared function to close
  closeAllModals(); // This will close the filter modal
}

function resetFilters() {
  document.getElementById('filterType').value = '';
  document.getElementById('filterStatus').value = '';
  // Clear search input and reset table
  document.getElementById('inventorySearch').value = '';
  document.querySelectorAll('.management-table tbody tr').forEach(row => {
    row.style.display = '';
  });
  // Use shared function to close
  closeAllModals(); // This will close the filter modal
}

// --- Data Interaction Functions ---

function fetchInventoryDetails(inventoryId, action) {
  fetch(`/inventory/${inventoryId}/details`)
    .then(response => response.json())
    .then(data => {
      if (action === 'view') {
        populateViewModal(data);
        openModal(document.getElementById('viewModal')); // Use shared function
      } else if (action === 'edit') {
        populateEditForm(data);
        openModal(document.getElementById('inventoryFormModal')); // Use shared function
      }
    })
    .catch(error => console.error('Error fetching inventory details:', error));
}

// Update view modal population
function populateViewModal(data) {
  document.getElementById('viewId').textContent = data.inventoryId;
  document.getElementById('viewType').textContent = data.inventoryTypeDisplay;
  document.getElementById('viewLocation').textContent = data.location;
  document.getElementById('viewStatus').textContent = data.status ? 'Active' : 'Inactive';
  
  // ✨ NEW logic for capacity display
  const capacityValueSpan = document.getElementById('viewCapacityValue');
  const capacityUnitSelect = document.getElementById('viewCapacityUnit');

  // Store the raw cm³ value in a data attribute for later conversions
  capacityValueSpan.dataset.baseCapacity = data.capacity;

  // Set a user-friendly default view (e.g., cubic meters) and update the display
  capacityUnitSelect.value = 'METER'; // Default to m³
  updateDisplayedCapacity(); // Call the new function to calculate and show the initial value
  
  document.getElementById('viewCreatedAt').textContent = new Date(data.createdAt).toLocaleString();
  document.getElementById('viewCreatedBy').textContent = data.createdBy || 'System';
  document.getElementById('viewUpdatedAt').textContent = new Date(data.updatedAt).toLocaleString();
  document.getElementById('viewUpdatedBy').textContent = data.updatedBy || 'System';
  const stockLink = document.getElementById('stockLink');
  stockLink.href = `/inventory-stock/manage?inventoryId=${data.inventoryId}`;
}


function saveInventory() {
  const csrf = getCSRFToken(); 
  const formData = {
    inventoryType: document.getElementById('modalInventoryType').value,
    location: document.getElementById('modalLocation').value,
    status: document.getElementById('modalStatus').value === 'true',
    capacity: document.getElementById('modalCapacity').value,
    capacityUnit: document.getElementById('modalCapacityUnit').value // ✨ NEW: Get the selected unit
  };
  const inventoryId = document.getElementById('inventoryId').value;
  const url = inventoryId ? `/inventory/${inventoryId}/update` : '/inventory/create';
  const method = inventoryId ? 'PUT' : 'POST';

  fetch(url, {
    method: method,
    body: JSON.stringify(formData),
    headers: {
      'Content-Type': 'application/json',
      [csrf.header]: csrf.token
    }
  })
  .then(response => {
      if (response.ok) {
        location.reload();
      } else if (response.status === 400) {
        return response.json().then(errors => displayFormErrors(errors, {
          inventoryType: 'typeError',
          location: 'locationError',
          capacity: 'capacityError' // ✨ NEW
        })); 
      }
    })
    .catch(error => console.error('Error saving inventory:', error));
}


// Update edit form population
function populateEditForm(data) {
  document.getElementById('modalTitle').textContent = 'Edit Inventory';
  document.getElementById('inventoryId').value = data.inventoryId;
  document.getElementById('modalInventoryType').value = data.inventoryType; // Use enum name
  document.getElementById('modalLocation').value = data.location;
  
  // ✨ NEW: When editing, we show the stored cm³ value and default the unit to cm³.
  // The user can then change it if they wish to re-calculate.
  document.getElementById('modalCapacity').value = data.capacity; 
  document.getElementById('modalCapacityUnit').value = 'CENTIMETER';

  document.getElementById('modalStatus').value = data.status.toString();
}

// Add similar CSRF handling to deleteInventory function
function deleteInventory(inventoryId) {
  // Get CSRF token using shared function
  const csrf = getCSRFToken(); // Use shared function
  fetch(`/inventory/${inventoryId}/delete`, {
    method: 'DELETE',
    headers: {
      [csrf.header]: csrf.token // Add CSRF token using shared data
    }
  })
    .then(response => {
      if (response.ok) {
        location.reload();
      } else {
        console.error('Error deleting inventory');
      }
    })
    .catch(error => console.error('Error deleting inventory:', error));
}


// A reusable function to update the displayed capacity
function updateDisplayedCapacity() {
    const valueSpan = document.getElementById('viewCapacityValue');
    const unitSelect = document.getElementById('viewCapacityUnit');
    
    // Retrieve the base capacity stored in the data attribute
    const baseCapacityCm3 = parseFloat(valueSpan.dataset.baseCapacity);
    if (isNaN(baseCapacityCm3)) return;

    const targetUnit = unitSelect.value;
    const factor = CONVERSION_FACTORS_FROM_CM3[targetUnit];

    if (factor) {
        const convertedValue = baseCapacityCm3 * factor;
        // Format to a reasonable number of decimal places
        const displayValue = convertedValue.toLocaleString(undefined, { 
            minimumFractionDigits: 2, 
            maximumFractionDigits: 6 
        });
        valueSpan.textContent = displayValue;
    }
}

// Note: displayFormErrors is now in management.js and requires a mapping object
// function displayFormErrors(errors, errorContainerIds) - handled in management.js