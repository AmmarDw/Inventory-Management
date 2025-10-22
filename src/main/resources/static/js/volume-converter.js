// js/volume-converter.js - A reusable module for volume unit conversion

const CONVERSION_FACTORS_FROM_CM3 = {
    METER:      { factor: 1 / 1000000,          symbol: 'm³' },
    LITER:      { factor: 1 / 1000,             symbol: 'L' },
    CENTIMETER: { factor: 1,                    symbol: 'cm³' },
    MILLIMETER: { factor: 1000,                 symbol: 'mm³' },
    INCH:       { factor: 1 / (2.54 * 2.54 * 2.54), symbol: 'in³' }
};

/**
 * Initializes a dynamic volume unit converter for a set of display elements.
 * @param {object} config - Configuration object.
 * @param {string} config.unitSelectorId - The ID of the <select> dropdown element.
 * @param {Array<object>} config.valueElements - An array of objects to convert.
 * @param {string} item.valueSpanId - The ID of the <span> that displays the value.
 * @param {number} item.baseValueCm3 - The raw value in cubic centimeters.
 * @param {string} [item.defaultUnit='METER'] - The unit to display initially.
 */
function initializeVolumeConverter(config) {
    const unitSelector = document.getElementById(config.unitSelectorId);
    if (!unitSelector) {
        console.error(`Volume converter init failed: Selector with ID #${config.unitSelectorId} not found.`);
        return;
    }

    const updateDisplay = () => {
        const targetUnit = unitSelector.value;
        const conversion = CONVERSION_FACTORS_FROM_CM3[targetUnit];
        if (!conversion) return;

        config.valueElements.forEach(item => {
            const valueSpan = document.getElementById(item.valueSpanId);
            if (valueSpan) {
                const baseValue = parseFloat(item.baseValueCm3);
                if (isNaN(baseValue)) {
                    valueSpan.textContent = "N/A";
                    return;
                }

                const convertedValue = baseValue * conversion.factor;

                // 🔄 CORRECTED: Using a more robust number formatting method
                // This formats to a max of 4 decimal places and removes trailing zeros.
                let displayValue = Number(convertedValue.toFixed(4)).toLocaleString();
                
                valueSpan.textContent = `${displayValue} ${conversion.symbol}`;
            }
        });
    };

    // Store the update function so we can remove the listener if needed, preventing memory leaks
    if (unitSelector.updateListener) {
        unitSelector.removeEventListener('change', unitSelector.updateListener);
    }
    unitSelector.addEventListener('change', updateDisplay);
    unitSelector.updateListener = updateDisplay;

    // Set initial state
    const defaultUnit = config.valueElements[0]?.defaultUnit || 'METER';
    unitSelector.value = defaultUnit;
    updateDisplay();
}