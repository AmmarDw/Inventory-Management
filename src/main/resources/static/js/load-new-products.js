// load-new-products.js - Properly manages multiple product dropdown instances

document.addEventListener('DOMContentLoaded', () => {
    const container = document.getElementById('stock-groups-container');
    const form = document.querySelector('form');
    let stockGroupCounter = 0;
    
    // Store the original group HTML from the template in the HTML
    const originalGroup = container.querySelector('.stock-group');
    if (!originalGroup) {
        console.error('No initial stock group found in container');
        return;
    }
    
    const originalGroupHTML = originalGroup.outerHTML;

    function showError(msg) {
        let err = form.querySelector('.error-message');
        if (!err) {
            err = document.createElement('div');
            err.className = 'error-message';
            form.insertBefore(err, form.querySelector('button[type="submit"]'));
        }
        err.textContent = msg;
        err.style.display = 'block';
    }

    function hideError() {
        const err = form.querySelector('.error-message');
        if (err) {
            err.style.display = 'none';
        }
    }

    // Generate unique IDs for form elements
    function generateUniqueId(prefix) {
        return `${prefix}-${++stockGroupCounter}-${Date.now()}`;
    }

    // Fix label association for a group
    function fixLabelAssociation(group) {
        const select = group.querySelector('select.product-select');
        const labels = group.querySelectorAll('label');
        const input = group.querySelector('input[type="number"]');

        if (select && labels.length > 0) {
            const selectId = select.id || generateUniqueId('product-select');
            select.id = selectId;
            // The first label is for the product select
            labels[0].setAttribute('for', selectId);
        }

        if (input && labels.length > 1) {
            const inputId = input.id || generateUniqueId('amount-input');
            input.id = inputId;
            // The second label is for the amount input
            labels[1].setAttribute('for', inputId);
        }
    }

    // Initialize a stock group
    function initializeStockGroup(group) {
        // Fix label associations first
        fixLabelAssociation(group);
        
        const select = group.querySelector('select.product-select');
        
        if (select) {
            // Set proper attributes for form submission - MUST match DTO field names
            select.setAttribute('name', 'productIds');
            select.setAttribute('required', 'required');
            
            // Initialize the dropdown with initial data
            initializeChildProductChoices(select, initialProductData);
        }

        // Initialize the amount input - MUST match DTO field names
        const amountInput = group.querySelector('input[type="number"]');
        if (amountInput) {
            amountInput.setAttribute('name', 'amounts');
            amountInput.setAttribute('required', 'required');
            amountInput.setAttribute('min', '0'); // matches @PositiveOrZero in DTO
        }

        console.log(`Initialized stock group`);
    }

    // Remove stock group
    function removeStockGroup(group) {
        const select = group.querySelector('select.product-select');
        if (select) {
            destroyProductDropdownInstance(select);
        }
        group.remove();
        hideError();
    }

    // Create a new stock group
    function createNewStockGroup() {
        const temp = document.createElement('div');
        temp.innerHTML = originalGroupHTML;
        const newGroup = temp.firstElementChild;
        
        // Reset form values
        const select = newGroup.querySelector('select.product-select');
        select.innerHTML = '<option value="" disabled selected>Select product</option>';
        select.value = '';
        
        const amountInput = newGroup.querySelector('input[type="number"]');
        amountInput.value = '';
        
        container.appendChild(newGroup);
        initializeStockGroup(newGroup);
        
        return newGroup;
    }

    // Initialize existing stock groups on page load
    container.querySelectorAll('.stock-group').forEach(group => {
        initializeStockGroup(group);
    });

    // Removing groups
    container.addEventListener('click', e => {
        if (!e.target.matches('.remove-stock-group')) return;
        
        e.preventDefault();
        e.stopPropagation();
        
        const groups = container.querySelectorAll('.stock-group');
        if (groups.length <= 1) {
            showError('You must have at least one product.');
            return;
        }
        
        const group = e.target.closest('.stock-group');
        removeStockGroup(group);
        
        console.log(`Removed stock group, remaining: ${container.querySelectorAll('.stock-group').length}`);
    });

    // Adding new groups
    document.querySelector('.add-stock-group').addEventListener('click', (e) => {
        e.preventDefault();
        createNewStockGroup();
        console.log(`Added new stock group, total: ${container.querySelectorAll('.stock-group').length}`);
    });

    // Form submission validation
    form.addEventListener('submit', (e) => {
        let isValid = true;
        const groups = container.querySelectorAll('.stock-group');
        const errorMessages = [];
        
        groups.forEach((group, index) => {
            const select = group.querySelector('select.product-select');
            const amountInput = group.querySelector('input[type="number"]');
            
            if (!select || !select.value) {
                errorMessages.push(`Please select a product for item ${index + 1}`);
                isValid = false;
            } else if (!amountInput || !amountInput.value || parseInt(amountInput.value) < 0) {
                errorMessages.push(`Please enter a valid amount (≥ 0) for item ${index + 1}`);
                isValid = false;
            }
        });
        
        if (!isValid) {
            e.preventDefault();
            showError(errorMessages.join(', '));
        } else {
            hideError();
            console.log('Form submitted with valid data');
        }
    });

    console.log('Load New Products page initialized');
});