// product-search-dropdown.js - Reusable product search dropdown component

class ProductSearchDropdown {
    constructor(selectElement, instanceId = null) {
        this.instanceId = instanceId || `product-dropdown-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        this.selectElement = selectElement;
        this.choicesInstance = null;
        this.currentSearchTerm = '';
        this.currentPage = 0;
        this.isLoading = false;
        this.hasMoreData = true;
        this.PAGE_SIZE = 10;
        this.searchDebounceTimer = null;
        this.SEARCH_DEBOUNCE_DELAY = 500;
        
        // Store the original attributes
        this.originalName = selectElement.getAttribute('name');
        this.originalId = selectElement.id;
        
        // Scroll handler reference for cleanup
        this.scrollHandler = null;
        this.scrollListbox = null;
        
        // Bind methods
        this.searchHandler = this.searchHandler.bind(this);
        this.showDropdownHandler = this.showDropdownHandler.bind(this);
        this.handleScroll = this.handleScroll.bind(this);
        
        this.initialize();
    }

    initialize() {
        console.log(`Initializing ProductSearchDropdown instance: ${this.instanceId}`);
        
        // Clean up any existing instance
        if (this.choicesInstance) {
            this.choicesInstance.destroy();
        }

        // Reset state
        this.currentSearchTerm = '';
        this.currentPage = 0;
        this.isLoading = false;
        this.hasMoreData = true;

        try {
            // Create Choices instance
            this.choicesInstance = new Choices(this.selectElement, {
                removeItemButton: true,
                searchEnabled: true,
                shouldSort: false,
                placeholder: true,
                placeholderValue: 'Search by ID or product details...',
                noResultsText: 'No products found',
                noChoicesText: 'No products available',
                itemSelectText: 'Select',
                searchPlaceholderValue: 'Type to search...',
                searchFloor: 1,
                searchResultLimit: 100,
                renderSelectedChoices: 'always',
                position: 'auto',
                resetScrollPosition: false,
                allowHTML: false
            });

            this.attachEventListeners();
            
        } catch (error) {
            console.error(`Error initializing Choices for instance ${this.instanceId}:`, error);
        }
    }

    attachEventListeners() {
        // Remove any existing listeners first
        this.selectElement.removeEventListener('showDropdown', this.showDropdownHandler);
        
        // Attach showDropdown listener
        this.selectElement.addEventListener('showDropdown', this.showDropdownHandler);

        // Setup search input listener
        this.setupSearchInputListener();
    }

    setupSearchInputListener() {
        const MAX_RETRIES = 5;
        let retries = 0;

        const tryAttachSearchListener = () => {
            const container = this.selectElement.closest('.choices');
            if (container) {
                const searchInput = container.querySelector('.choices__input.choices__input--cloned');
                if (searchInput) {
                    console.log(`Found search input for instance ${this.instanceId}`);
                    
                    // Remove any existing listener
                    searchInput.removeEventListener('input', this.searchHandler);
                    
                    // Attach input event listener
                    searchInput.addEventListener('input', this.searchHandler);
                    
                    return true;
                }
            }
            
            retries++;
            if (retries < MAX_RETRIES) {
                setTimeout(tryAttachSearchListener, 100);
            }
            return false;
        };

        tryAttachSearchListener();
    }

    showDropdownHandler(event) {
        console.log(`Dropdown opened for instance: ${this.instanceId}`);

        // Re-attach search input when dropdown opens
        setTimeout(() => this.setupSearchInputListener(), 50);

        // Setup scroll handler when dropdown opens
        this.setupScrollHandler();

        const currentChoices = this.choicesInstance.getValue(true);
        const isEmpty = !currentChoices || currentChoices.length === 0;

        if (this.currentSearchTerm === '' && isEmpty && !this.isLoading && this.hasMoreData) {
            console.log(`Loading initial data for instance ${this.instanceId}...`);
            this.loadChoicesData('', 0);
        }
    }

    setupScrollHandler() {
        // Remove existing scroll handler first
        if (this.scrollHandler && this.scrollListbox) {
            this.scrollListbox.removeEventListener('scroll', this.scrollHandler);
        }

        // Try to find the listbox and attach scroll handler
        const tryAttachScrollHandler = (retries = 0) => {
            const container = this.selectElement.closest('.choices');
            if (container) {
                // Try different selectors for the scrollable listbox
                let listbox = container.querySelector('.choices__list--dropdown [role="listbox"]');
                if (!listbox) {
                    listbox = container.querySelector('.choices__list--dropdown');
                }
                if (!listbox) {
                    listbox = container.querySelector('.choices__list.choices__list--dropdown');
                }
                
                if (listbox) {
                    console.log(`Found listbox for instance ${this.instanceId}, attaching scroll handler`, listbox);
                    
                    // Remove any existing listener and add new one
                    listbox.removeEventListener('scroll', this.handleScroll);
                    listbox.addEventListener('scroll', this.handleScroll);
                    
                    // Store the handler reference for cleanup
                    this.scrollHandler = this.handleScroll;
                    this.scrollListbox = listbox;
                    return true;
                }
            }
            
            if (retries < 10) {
                setTimeout(() => tryAttachScrollHandler(retries + 1), 50);
            } else {
                console.warn(`Could not find listbox for instance ${this.instanceId} after ${retries} retries`);
            }
            return false;
        };

        tryAttachScrollHandler();
    }

    searchHandler(event) {
        const newSearchTerm = event.target.value ? event.target.value.trim() : '';
        
        if (newSearchTerm === this.currentSearchTerm && newSearchTerm !== '') {
            return;
        }
        
        console.log(`Search input - Instance: ${this.instanceId}, New term: '${newSearchTerm}'`);
        
        this.currentSearchTerm = newSearchTerm;
        this.currentPage = 0;
        this.hasMoreData = true;
        
        clearTimeout(this.searchDebounceTimer);
        
        if (newSearchTerm !== '') {
            this.choicesInstance.clearStore();
            this.choicesInstance.setChoices([
                { value: '', label: 'Searching...', disabled: true }
            ], 'value', 'label', true);
        }
        
        if (newSearchTerm === '') {
            this.loadChoicesData('', 0);
        } else {
            this.searchDebounceTimer = setTimeout(() => {
                this.loadChoicesData(newSearchTerm, 0);
            }, this.SEARCH_DEBOUNCE_DELAY);
        }
    }

    handleScroll(event) {
        const listbox = event.target;
        const { scrollTop, scrollHeight, clientHeight } = listbox;
        
        console.log(`Scroll event - scrollTop: ${scrollTop}, clientHeight: ${clientHeight}, scrollHeight: ${scrollHeight}, hasMoreData: ${this.hasMoreData}, isLoading: ${this.isLoading}`);
        
        // Load more when near bottom (within 10px)
        const scrollThreshold = 10;
        const isNearBottom = scrollTop + clientHeight >= scrollHeight - scrollThreshold;
        
        if (isNearBottom && !this.isLoading && this.hasMoreData) {
            console.log(`Loading more data for instance ${this.instanceId}, page: ${this.currentPage + 1}`);
            this.loadChoicesData(this.currentSearchTerm, this.currentPage + 1);
        }
    }

    loadChoicesData(searchTerm, page) {
        if (this.isLoading) {
            console.log(`Instance ${this.instanceId} is already loading, skipping`);
            return;
        }
        
        this.isLoading = true;
        console.log(`Loading data for instance ${this.instanceId} - Search: '${searchTerm}', Page: ${page}`);

        const url = `/containers/products/searchable?q=${encodeURIComponent(searchTerm)}&page=${page}&size=${this.PAGE_SIZE}`;
        
        fetch(url)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.json();
            })
            .then(data => {
                console.log(`Received data for instance ${this.instanceId}:`, data);
                
                const choicesData = data.content.map(item => ({
                    value: item.productId.toString(),
                    label: item.displayText
                }));

                if (page === 0) {
                    // Clear existing choices for new search
                    this.choicesInstance.clearStore();
                }

                if (choicesData.length > 0) {
                    this.choicesInstance.setChoices(choicesData, 'value', 'label', false);
                } else if (page === 0) {
                    // No results found
                    this.choicesInstance.setChoices([
                        { value: '', label: 'No products found', disabled: true }
                    ], 'value', 'label', true);
                }

                this.currentPage = page;
                
                // FIX: Use the correct property name from backend response
                // Your logs show the backend returns 'hasMore', not 'hasNext'
                this.hasMoreData = data.hasMore !== undefined ? data.hasMore : 
                                 data.hasNext !== undefined ? data.hasNext : false;
                
                console.log(`Instance ${this.instanceId} - Page ${page}: hasMoreData=${this.hasMoreData}`);
                
            })
            .catch(error => {
                console.error(`Error fetching products for instance ${this.instanceId}:`, error);
                if (page === 0) {
                    this.choicesInstance.setChoices([
                        { value: '', label: 'Error loading results', disabled: true }
                    ], 'value', 'label', true);
                }
            })
            .finally(() => {
                this.isLoading = false;
            });
    }

    setInitialData(initialData) {
        if (initialData && initialData.content && initialData.content.length > 0) {
            const choicesData = initialData.content.map(item => ({
                value: item.productId.toString(),
                label: item.displayText
            }));
            
            setTimeout(() => {
                if (this.choicesInstance) {
                    this.choicesInstance.setChoices(choicesData, 'value', 'label', false);
                    console.log(`Set initial data for instance ${this.instanceId}`);
                }
            }, 100);
        }
    }

    getValue() {
        return this.choicesInstance ? this.choicesInstance.getValue(true) : null;
    }

    setValue(value) {
        if (this.choicesInstance) {
            this.choicesInstance.setChoiceByValue(value);
        }
    }

    destroy() {
        console.log(`Destroying ProductSearchDropdown instance: ${this.instanceId}`);
        
        // Clear timers
        if (this.searchDebounceTimer) {
            clearTimeout(this.searchDebounceTimer);
        }
        
        // Remove event listeners
        if (this.selectElement) {
            this.selectElement.removeEventListener('showDropdown', this.showDropdownHandler);
        }
        
        // Remove scroll handler
        if (this.scrollHandler && this.scrollListbox) {
            this.scrollListbox.removeEventListener('scroll', this.scrollHandler);
            this.scrollHandler = null;
            this.scrollListbox = null;
        }
        
        // Destroy Choices instance
        if (this.choicesInstance) {
            try {
                this.choicesInstance.destroy();
            } catch (error) {
                console.warn(`Error destroying Choices instance ${this.instanceId}:`, error);
            }
            this.choicesInstance = null;
        }
    }
}

// Global registry to manage multiple instances
const productDropdownInstances = new Map();

// Initialize function - maintains backward compatibility
function initializeChildProductChoices(selectElement, initialData = null) {
    if (!selectElement) {
        console.error("Select element not provided for initialization");
        return null;
    }

    // Check if this is a single instance (like in container management)
    const isSingleInstance = selectElement.id === 'childProductId' || 
                            selectElement.closest('#containerFormModal');
    
    if (isSingleInstance) {
        // For single instances, use the original global variable approach for backward compatibility
        if (window.childProductChoicesInstance) {
            window.childProductChoicesInstance.destroy();
        }
        
        window.childProductChoicesInstance = new ProductSearchDropdown(selectElement, 'childProductId');
        
        // Set initial data if provided
        if (initialData) {
            window.childProductChoicesInstance.setInitialData(initialData);
        }
        
        return window.childProductChoicesInstance;
    } else {
        // For multiple instances (like in load-new-products)
        const instanceId = selectElement.id || 
                          `product-dropdown-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        
        // Destroy existing instance if any
        if (productDropdownInstances.has(instanceId)) {
            productDropdownInstances.get(instanceId).destroy();
            productDropdownInstances.delete(instanceId);
        }

        // Ensure the select element has proper attributes
        if (!selectElement.id) {
            selectElement.id = instanceId;
        }

        // Create new instance
        const instance = new ProductSearchDropdown(selectElement, instanceId);
        productDropdownInstances.set(instanceId, instance);

        // Set initial data if provided
        if (initialData) {
            instance.setInitialData(initialData);
        }

        return instance;
    }
}

// Function to get instance by element
function getProductDropdownInstance(selectElement) {
    // Check single instance first
    if (window.childProductChoicesInstance && 
        window.childProductChoicesInstance.selectElement === selectElement) {
        return window.childProductChoicesInstance;
    }
    
    // Check multiple instances
    const instanceId = selectElement.id || 
                      Array.from(productDropdownInstances.entries())
                          .find(([key, instance]) => instance.selectElement === selectElement)?.[0];
    
    return instanceId ? productDropdownInstances.get(instanceId) : null;
}

// Function to destroy instance
function destroyProductDropdownInstance(selectElement) {
    // Check single instance first
    if (window.childProductChoicesInstance && 
        window.childProductChoicesInstance.selectElement === selectElement) {
        window.childProductChoicesInstance.destroy();
        window.childProductChoicesInstance = null;
        return;
    }
    
    // Check multiple instances
    const instance = getProductDropdownInstance(selectElement);
    if (instance) {
        const instanceId = instance.instanceId;
        instance.destroy();
        productDropdownInstances.delete(instanceId);
    }
}

// Get all instances (for debugging)
function getAllProductDropdownInstances() {
    const allInstances = new Map(productDropdownInstances);
    if (window.childProductChoicesInstance) {
        allInstances.set('childProductId', window.childProductChoicesInstance);
    }
    return allInstances;
}

// Clean up all instances
function destroyAllProductDropdownInstances() {
    if (window.childProductChoicesInstance) {
        window.childProductChoicesInstance.destroy();
        window.childProductChoicesInstance = null;
    }
    
    productDropdownInstances.forEach((instance, instanceId) => {
        instance.destroy();
    });
    productDropdownInstances.clear();
}