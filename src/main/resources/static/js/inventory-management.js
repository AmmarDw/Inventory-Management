// inventory-management.js

document.addEventListener('DOMContentLoaded', function() {
  // Modal elements
  const inventoryFormModal = document.getElementById('inventoryFormModal');
  const viewModal = document.getElementById('viewModal');
  const deleteModal = document.getElementById('deleteModal');
  const filterModal = document.getElementById('filterModal');
  const modals = [inventoryFormModal, viewModal, deleteModal, filterModal];
  
  // Open modals
  document.getElementById('createInventoryBtn').addEventListener('click', () => {
      openModal(inventoryFormModal);
      document.getElementById('modalTitle').textContent = 'Create New Inventory';
      document.getElementById('inventoryForm').reset();
      document.getElementById('inventoryId').value = '';
  });
  
  document.getElementById('filterBtn').addEventListener('click', () => {
      openModal(filterModal);
  });
  
  // Close modals
  document.querySelectorAll('.close-modal').forEach(button => {
      button.addEventListener('click', () => {
          modals.forEach(modal => modal.style.display = 'none');
      });
  });
  
  // Click outside modal to close
  window.addEventListener('click', (event) => {
      modals.forEach(modal => {
          if (event.target === modal) {
              modal.style.display = 'none';
          }
      });
  });
  
  // Action buttons
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
          
          document.getElementById('deleteInventoryName').textContent = inventoryName;
          document.getElementById('confirmDelete').setAttribute('data-id', inventoryId);
          openModal(deleteModal);
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

  // search
  document.getElementById('inventorySearch').addEventListener('input', function() {
    const searchTerm = this.value.toLowerCase();
    const rows = document.querySelectorAll('.management-table tbody tr');
    
    rows.forEach(row => {
        const id = row.cells[0].textContent.toLowerCase();
        const location = row.cells[2].textContent.toLowerCase();
        row.style.display = (id.includes(searchTerm) || location.includes(searchTerm)) 
            ? '' 
            : 'none';
    });
  });

  const searchInput = document.getElementById('inventorySearch');
  if (searchInput) {
      searchInput.addEventListener('input', function() {
          const searchTerm = this.value.toLowerCase().trim();
          const rows = document.querySelectorAll('.management-table tbody tr');
          
          rows.forEach(row => {
              const id = row.cells[0].textContent.toLowerCase();
              const location = row.cells[2].textContent.toLowerCase();
              
              // Show row if search term matches ID or location
              row.style.display = (id.includes(searchTerm) || location.includes(searchTerm)) 
                  ? '' 
                  : 'none';
          });
      });
  }

  document.getElementById('applyFilters').addEventListener('click', applyFilters);
  document.getElementById('resetFilters').addEventListener('click', resetFilters);
});

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
  
  document.getElementById('filterModal').style.display = 'none';
}

function resetFilters() {
    document.getElementById('filterType').value = '';
    document.getElementById('filterStatus').value = '';
    
    // Clear search input and reset table
    document.getElementById('inventorySearch').value = '';
    document.querySelectorAll('.management-table tbody tr').forEach(row => {
        row.style.display = '';
    });

    document.getElementById('filterModal').style.display = 'none';
}

function openModal(modal) {
  modal.style.display = 'flex';
}

function fetchInventoryDetails(inventoryId, action) {
  fetch(`/inventory/${inventoryId}/details`)
      .then(response => response.json())
      .then(data => {
          if (action === 'view') {
              populateViewModal(data);
              openModal(document.getElementById('viewModal'));
          } else if (action === 'edit') {
              populateEditForm(data);
              openModal(document.getElementById('inventoryFormModal'));
          }
      })
      .catch(error => console.error('Error fetching inventory details:', error));
}

// Update view modal population
function populateViewModal(data) {
  document.getElementById('viewId').textContent = data.inventoryId;
  document.getElementById('viewType').textContent = data.inventoryTypeDisplay; // Use display name
  document.getElementById('viewLocation').textContent = data.location;
  document.getElementById('viewStatus').textContent = data.status ? 'Active' : 'Inactive';
  document.getElementById('viewCreatedAt').textContent = new Date(data.createdAt).toLocaleString();
  document.getElementById('viewCreatedBy').textContent = data.createdBy || 'System';
  document.getElementById('viewUpdatedAt').textContent = new Date(data.updatedAt).toLocaleString();
  document.getElementById('viewUpdatedBy').textContent = data.updatedBy || 'System';
  
  const stockLink = document.getElementById('stockLink');
  stockLink.href = `/inventory-stock/manage?inventoryId=${data.inventoryId}`;
}

// Update edit form population
function populateEditForm(data) {
  document.getElementById('modalTitle').textContent = 'Edit Inventory';
  document.getElementById('inventoryId').value = data.inventoryId;
  document.getElementById('modalInventoryType').value = data.inventoryType; // Use enum name
  document.getElementById('modalLocation').value = data.location;
  document.getElementById('modalStatus').value = data.status.toString();
}

function saveInventory() {
  // Get CSRF token from meta tags
  const token = document.querySelector('meta[name="_csrf"]').content;
  const header = document.querySelector('meta[name="_csrf_header"]').content;
  
  const formData = {
      inventoryType: document.getElementById('modalInventoryType').value,
      location: document.getElementById('modalLocation').value,
      status: document.getElementById('modalStatus').value === 'true'
  };
  
  const inventoryId = document.getElementById('inventoryId').value;
  const url = inventoryId ? `/inventory/${inventoryId}/update` : '/inventory/create';
  const method = inventoryId ? 'PUT' : 'POST';
  
  fetch(url, {
      method: method,
      body: JSON.stringify(formData),
      headers: {
          'Content-Type': 'application/json',
          [header]: token  // Add CSRF token
      }
  })
  .then(response => {
      if (response.ok) {
          location.reload();
      } else if (response.status === 400) {
          return response.json().then(errors => displayFormErrors(errors));
      }
  })
  .catch(error => console.error('Error saving inventory:', error));
}

// Add similar CSRF handling to deleteInventory function
function deleteInventory(inventoryId) {
  const token = document.querySelector('meta[name="_csrf"]').content;
  const header = document.querySelector('meta[name="_csrf_header"]').content;
  
  fetch(`/inventory/${inventoryId}/delete`, {
      method: 'DELETE',
      headers: {
          [header]: token  // Add CSRF token
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

function displayFormErrors(errors) {
  // Reset errors
  document.querySelectorAll('.error-message').forEach(el => el.textContent = '');
  
  // Display new errors
  if (errors.inventoryType) {
      document.getElementById('typeError').textContent = errors.inventoryType;
  }
  if (errors.location) {
      document.getElementById('locationError').textContent = errors.location;
  }
}