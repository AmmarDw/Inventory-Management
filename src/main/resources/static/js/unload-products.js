document.addEventListener('DOMContentLoaded', () => {
  const steps = document.querySelectorAll('.form-step');
  const progressSteps = document.querySelectorAll('.progress-indicator .step');
  const prevBtn = document.getElementById('prevBtn');
  const nextBtn = document.getElementById('nextBtn');
  const submitBtn = document.getElementById('submitBtn');
  const sourceInventorySelect = document.getElementById('sourceInventory');
  const orderSelection = document.getElementById('orderSelection');
  const productsContainer = document.getElementById('products-container');
  const destinationTypeSelect = document.getElementById('destinationType');
  const inventoryDestinationGroup = document.getElementById('inventoryDestinationGroup');
  const clientDestinationGroup = document.getElementById('clientDestinationGroup');
  const clientNameSpan = document.getElementById('clientName');
  const orderIdSpan = document.getElementById('orderIdDisplay');
  const deliveryLocationSpan = document.getElementById('deliveryLocation');

  let currentStep = 0;
  let selectedOrder = null;
  let selectedProducts = [];
  let sourceInventoryId = null;

  let csrfToken = '';
  let csrfHeader = '';

  try {
    const csrfMeta = document.querySelector('meta[name="_csrf"]');
    const headerMeta = document.querySelector('meta[name="_csrf_header"]');
    
    if (csrfMeta && headerMeta) {
      csrfToken = csrfMeta.content;
      csrfHeader = headerMeta.content;
    }
  } catch (e) {
    console.error('Error getting CSRF token:', e);
  }

  // Initialize Choices.js
  const initChoices = (element) => {
    return new Choices(element, {
      searchEnabled: true,
      shouldSort: false,
      removeItemButton: true
    });
  };

  // Initialize selects
  const sourceInventoryChoices = initChoices(sourceInventorySelect);
  const orderSelectionChoices = initChoices(orderSelection);

  // Handle destination type change
  destinationTypeSelect.addEventListener('change', (e) => {
    inventoryDestinationGroup.style.display = 'none';
    clientDestinationGroup.style.display = 'none';
    
    if (e.target.value === 'inventory') {
      inventoryDestinationGroup.style.display = 'block';
      
      // Filter out source inventory from destination options
      const destSelect = document.getElementById('destinationInventory');
      Array.from(destSelect.options).forEach(option => {
        if (option.value === sourceInventoryId) {
          option.style.display = 'none';
        } else {
          option.style.display = 'block';
        }
      });
      destSelect.value = '';
      
    } else if (e.target.value === 'client') {
      clientDestinationGroup.style.display = 'block';
      
      if (selectedOrder) {
        clientNameSpan.textContent = selectedOrder.clientName;
        orderIdSpan.textContent = `#${selectedOrder.orderId}`;
        deliveryLocationSpan.textContent = selectedOrder.deliveryLocation;
      }
    }
  });

  // Navigation functions
  function showStep(stepIndex) {
    steps.forEach((step, index) => {
      step.classList.toggle('active', index === stepIndex);
      step.style.display = index === stepIndex ? 'block' : 'none';
    });
    
    progressSteps.forEach((step, index) => {
      step.classList.toggle('active', index <= stepIndex);
    });
    
    prevBtn.style.display = stepIndex > 0 ? 'block' : 'none';
    nextBtn.style.display = stepIndex < steps.length - 1 ? 'block' : 'none';
    submitBtn.style.display = stepIndex === steps.length - 1 ? 'block' : 'none';
    
    currentStep = stepIndex;
  }

  // Next button click handler - updated validation
  nextBtn.addEventListener('click', async () => {
    // Validate current step
    if (currentStep === 0) {
      if (!sourceInventoryChoices.getValue(true)) {
        alert('Please select a source inventory');
        return;
      }
    }
    
    if (currentStep === 1) {
      if (!orderSelectionChoices.getValue(true)) {
        alert('Please select an order or available stock');
        return;
      }
    }
    
    if (currentStep === 2) {
      let valid = true;
      const errors = [];
      
      document.querySelectorAll('.transport-amount').forEach(input => {
        const rawValue = input.value.trim();
        let amount = parseInt(rawValue);
        
        // Handle empty/NaN values
        if (rawValue === '' || isNaN(amount)) {
          amount = 0;
          input.value = '0'; // Set to zero for clarity
        }
        
        const max = parseInt(input.max);
        
        if (amount < 0) {
          errors.push(`Amount must be ≥ 0 for product: ${input.dataset.productId}`);
          valid = false;
        } else if (amount > max) {
          errors.push(`Amount cannot exceed available stock (${max}) for product: ${input.dataset.productId}`);
          valid = false;
        }
      });
      
      if (errors.length > 0) {
        alert(errors.join('\n'));
      }
      
      if (!valid) return;
    }
    
    if (currentStep === 3) {
      if (!destinationTypeSelect.value) {
        alert('Please select a destination type');
        return;
      }
      
      if (destinationTypeSelect.value === 'inventory' && 
          !document.getElementById('destinationInventory').value) {
        alert('Please select a destination inventory');
        return;
      }
    }
    
    // Load data for next step if needed
    if (currentStep === 0) {
      sourceInventoryId = sourceInventoryChoices.getValue(true);
      await loadOrderOptions();
    } else if (currentStep === 1) {
      const selectedValue = orderSelectionChoices.getValue(true);
      if (selectedValue === 'available') {
        selectedOrder = null;
      } else {
        selectedOrder = JSON.parse(selectedValue);
      }
      await loadProducts();
    }
    
    // Move to next step
    showStep(currentStep + 1);
  });

  // Previous button click handler
  prevBtn.addEventListener('click', () => {
    showStep(currentStep - 1);
  });

  // Load order options via AJAX
  async function loadOrderOptions() {
    try {
      orderSelectionChoices.clearChoices();
      orderSelectionChoices.setChoices([{
        value: '',
        label: 'Loading...',
        disabled: true
      }]);
      
      const response = await fetch(`${BASE_URL}inventory-stock/orders?inventoryId=${sourceInventoryId}`);
      if (!response.ok) throw new Error('Failed to load orders');
      
      const data = await response.json();
      
      const choices = [{
        value: 'available',
        label: 'Available Stock (Not assigned to any order)'
      }];
      
      data.forEach(order => {
        choices.push({
          value: JSON.stringify(order),
          label: `Order #${order.orderId} - ${order.clientName} - ${order.deliveryLocation}`
        });
      });
      
      orderSelectionChoices.clearChoices();
      orderSelectionChoices.setChoices(choices, 'value', 'label', true);
    } catch (error) {
      console.error('Error loading orders:', error);
      alert('Failed to load orders. Please try again.');
    }
  }

  // Load products via AJAX
  async function loadProducts() {
    try {
      productsContainer.innerHTML = '<p>Loading products...</p>';
      
      let url = `${BASE_URL}inventory-stock/products?inventoryId=${sourceInventoryId}`;
      if (selectedOrder) {
        url += `&orderId=${selectedOrder.orderId}`;
      }
  
      console.log("Loading products from:", url);
      const response = await fetch(url);
      console.log("Response status:", response.status);
      
      if (!response.ok) {
        const errorText = await response.text();
        console.error("Error response:", errorText);
        throw new Error('Failed to load products: ' + errorText);
      }
      
      const products = await response.json();
      console.log("Received products:", products);
      
      selectedProducts = products;
      productsContainer.innerHTML = '';
      
      if (products.length === 0) {
        productsContainer.innerHTML = `
          <div class="alert alert-info">
            No products found in this inventory for the selected criteria.
            <br>Available stock might be zero or not exist.
          </div>
        `;
        return;
      }
      
      products.forEach(product => {
        const productDiv = document.createElement('div');
        productDiv.className = 'product-item';
        productDiv.innerHTML = `
          <h4>${product.productId} – ${product.productOptionsDisplay || 'No options'}</h4>
          <div class="form-group">
            <label>Current Amount: ${product.amount}</label>
          </div>
          <div class="form-group">
            <label for="amount-${product.productId}">Amount to Unload</label>
            <input type="number" id="amount-${product.productId}" 
                   class="transport-amount" 
                   min="0" max="${product.amount}" 
                   value="0"
                   data-product-id="${product.productId}">
          </div>
        `;
        productsContainer.appendChild(productDiv);
      });
    } catch (error) {
      console.error('Error loading products:', error);
      productsContainer.innerHTML = `
        <div class="alert alert-danger">
          Failed to load products: ${error.message}
        </div>
      `;
    }
  }

  // Form submission handler - updated
  document.getElementById('unloadForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    
    // Prepare data
    const unloadData = {
      sourceInventoryId: sourceInventoryId,
      destinationType: destinationTypeSelect.value,
      destinationInventoryId: destinationTypeSelect.value === 'inventory' 
        ? document.getElementById('destinationInventory').value 
        : null,
      order: selectedOrder,
      products: []
    };
    
    // Add product amounts
    document.querySelectorAll('.transport-amount').forEach(input => {
      const amount = parseInt(input.value);
      if (amount > 0) {
        unloadData.products.push({
          productId: parseInt(input.dataset.productId),
          amount: amount
        });
      }
    });
    
    try {
      const response = await fetch(`${BASE_URL}inventory-stock/unload`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          [csrfHeader]: csrfToken  // Use dynamic header name
        },
        body: JSON.stringify(unloadData)
      });
      
      if (response.ok) {
        const result = await response.json();
        if (result.success) {
          alert('Products unloaded successfully!');
          window.location.href = `${BASE_URL}inventory-stock`;
        } else {
          throw new Error(result.message);
        }
      } else {
        const error = await response.text();
        throw new Error(error);
      }
    } catch (error) {
      console.error('Unload error:', error);
      alert(`Failed to unload products: ${error.message}`);
    }
  });

  // Initialize first step
  showStep(0);
});