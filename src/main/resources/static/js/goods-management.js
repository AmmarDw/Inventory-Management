// goods-management.js - Shared functionality for product and container management

// --- Global Data (Injected by Thymeleaf in HTML) ---
// These variables are expected to be defined in the HTML template
// e.g., <script th:inline="javascript">const categories = /*[[${categories}]]*/ [];</script>
// const categories = []; // Placeholder, actual value comes from Thymeleaf
// const categoryOptionsMap = {}; // Placeholder, actual value comes from Thymeleaf

// --- Store delegation function references to prevent duplicates ---
// MOVE THESE DECLARATIONS OUTSIDE OF ANY FUNCTION OR EVENT LISTENER
let filterModalOptionDelegationFn = null;
// --- End delegation references ---

// Conversion factors and symbols for converting from the base unit (cm).
const CONVERSION_FACTORS_FROM_CM = {
    METER:      { factor: 1 / 100,  symbol: 'm' },
    CENTIMETER: { factor: 1,        symbol: 'cm' },
    MILLIMETER: { factor: 10,       symbol: 'mm' },
    INCH:       { factor: 1 / 2.54, symbol: 'in' }
};

function filterModalEventBinding() {
    // Setup delegation for the Filter Modal *once* on page load
    const filterModalElement = document.getElementById('filterModal');
    if (filterModalElement) {
        // Use a single listener on the filter modal for Add/Remove Clicks
        // Store the function to allow potential removal/re-adding
        filterModalOptionDelegationFn = function (e) {
            if (e.target && e.target.classList.contains('add-option-group')) {
                e.preventDefault();
                addFilterOptionGroup();
            }
            if (e.target && e.target.classList.contains('remove-option-group')) {
                e.preventDefault();
                const allFilterGroups = filterModalElement.querySelectorAll('#filter-option-groups-container .option-group');
                if (allFilterGroups.length <= 1) {
                    alert("Cannot remove the last filter option set.");
                    return;
                }
                const filterGroupToRemove = e.target.closest('.option-group');
                if (filterGroupToRemove) {
                    filterGroupToRemove.remove();
                } else {
                    console.warn("Could not find the filter option group to remove.");
                }
            }
        };
        filterModalElement.addEventListener('click', filterModalOptionDelegationFn);

        // --- Delegate the 'change' event for .filter-category ---
        filterModalElement.addEventListener('change', function (e) {
            if (e.target && e.target.classList.contains('filter-category')) {
                updateFilterOptionSelect(e.target);
            }
        });

    } else {
        console.error("Filter modal (#filterModal) not found during DOMContentLoaded setup.");
    }
}

function addFilterOptionGroup() {
    const container = document.getElementById('filter-option-groups-container');
    if (!container) {
        console.error("Cannot add filter option group: Container (#filter-option-groups-container) not found.");
        return;
    }
    const templateGroup = container.querySelector('.option-group');
    if (!templateGroup) {
        console.error("Cannot add filter option group: No template group found.");
        alert("Unable to add a new filter option set. Please try reloading the page.");
        return;
    }
    const newGroup = templateGroup.cloneNode(true);
    // Reset values for the cloned filter group
    const categorySelect = newGroup.querySelector('.filter-category');
    const optionSelect = newGroup.querySelector('.filter-option');
    if (categorySelect) categorySelect.value = '';
    if (optionSelect) optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';
    // No need to rebind events for clicks/remove, delegation handles them.
    // No need to rebind change listener for category, delegation handles it.
    // Append the new group
    container.appendChild(newGroup);
}

function updateFilterOptionSelect(categorySelect) {
    if (!categorySelect) {
        console.error("updateFilterOptionSelect called with invalid categorySelect");
        return;
    }

    const categoryId = parseInt(categorySelect.value, 10);
    const group = categorySelect.closest('.option-group');

    if (!group) {
        console.error("Could not find option-group for category select");
        return;
    }

    const optionSelect = group.querySelector('.filter-option');
    if (!optionSelect) {
        console.error("Could not find filter-option select within group");
        return;
    }

    optionSelect.innerHTML = '<option value="" disabled selected>Select an option</option>';

    if (!isNaN(categoryId) && categoryId > 0) {
        const options = categoryOptionsMap[categoryId];
        if (options && Array.isArray(options) && options.length > 0) {
            options.forEach(option => {
                if (option && option.optionId !== undefined && option.optionValue) {
                    const optElement = document.createElement('option');
                    optElement.value = option.optionId;
                    optElement.textContent = option.optionValue;
                    optionSelect.appendChild(optElement);
                } else {
                    console.warn("Skipping invalid option ", option);
                }
            });
        } else {
            console.warn(`No options found in categoryOptionsMap for categoryId: ${categoryId}`, options);
        }
    }
}


/**
 * REUSABLE: Initializes a dynamic unit converter for a view modal.
 * @param {object} config - An object containing the element IDs for the modal.
 * e.g., {
 * gridId: '#viewContainerModal .detail-grid',
 * selectorId: 'viewContainerUnitSelector',
 * volumeId: 'viewContainerVolume',
 * heightId: 'viewContainerHeight',
 * widthId: 'viewContainerWidth',
 * lengthId: 'viewContainerLength'
 * }
 * @param {object} data - The DTO data containing base values (volume, height, etc. in cm).
 */
function initializeUnitConverter(config, data) {
    const detailGrid = document.querySelector(config.gridId);
    const unitSelector = document.getElementById(config.selectorId);

    if (!detailGrid || !unitSelector) {
        console.error("Unit converter initialization failed: Missing required elements.");
        return;
    }

    // 1. Store base values from the DTO onto the grid element
    detailGrid.dataset.baseVolume = data.volume;
    detailGrid.dataset.baseHeight = data.height;
    detailGrid.dataset.baseWidth = data.width;
    detailGrid.dataset.baseLength = data.length;

    // 2. Define the update function specific to this converter instance
    const updateFunction = () => {
        const base = {
            volume: parseFloat(detailGrid.dataset.baseVolume),
            height: parseFloat(detailGrid.dataset.baseHeight),
            width: parseFloat(detailGrid.dataset.baseWidth),
            length: parseFloat(detailGrid.dataset.baseLength)
        };
        
        const selectedUnit = unitSelector.value;
        const conversion = CONVERSION_FACTORS_FROM_CM[selectedUnit];
        if (!conversion) return;

        const lengthFactor = conversion.factor;
        const volumeFactor = lengthFactor * lengthFactor * lengthFactor;

        const display = {
            volume: base.volume * volumeFactor,
            height: base.height * lengthFactor,
            width: base.width * lengthFactor,
            length: base.length * lengthFactor
        };

        const formatOptions = { minimumFractionDigits: 2, maximumFractionDigits: 4 };

        document.getElementById(config.volumeId).textContent = `${display.volume.toLocaleString(undefined, formatOptions)} ${conversion.symbol}³`;
        document.getElementById(config.heightId).textContent = `${display.height.toLocaleString(undefined, formatOptions)} ${conversion.symbol}`;
        document.getElementById(config.widthId).textContent = `${display.width.toLocaleString(undefined, formatOptions)} ${conversion.symbol}`;
        document.getElementById(config.lengthId).textContent = `${display.length.toLocaleString(undefined, formatOptions)} ${conversion.symbol}`;
    };

    // 3. Remove any old listener and attach the new one
    unitSelector.removeEventListener('change', detailGrid.updateListener);
    unitSelector.addEventListener('change', updateFunction);
    detailGrid.updateListener = updateFunction; // Store reference for removal

    // 4. Set default state and perform initial update
    unitSelector.value = 'CENTIMETER';
    updateFunction();
}