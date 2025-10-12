package com.speedit.inventorysystem.enums;

import java.math.BigDecimal;
import java.math.MathContext;

public enum MeasurementUnitEnum {
    // Length Units
    METER("m", UnitType.LENGTH, new BigDecimal("100.0")),
    CENTIMETER("cm", UnitType.LENGTH, new BigDecimal("1.0")),
    MILLIMETER("mm", UnitType.LENGTH, new BigDecimal("0.1")),
    INCH("in", UnitType.LENGTH, new BigDecimal("2.54")),

    // Volume Units
    LITER("L", UnitType.VOLUME, new BigDecimal("1000.0")); // Directly converts to cm³

    private final String symbol;
    private final UnitType type;
    private final BigDecimal toBaseFactor; // Factor to convert to base unit (cm for length, cm³ for volume)

    MeasurementUnitEnum(String symbol, UnitType type, BigDecimal toBaseFactor) {
        this.symbol = symbol;
        this.type = type;
        this.toBaseFactor = toBaseFactor;
    }

    public String getSymbol() { return symbol; }
    public UnitType getType() { return type; }
    public BigDecimal getToBaseFactor() { return toBaseFactor; }

    /**
     * Converts a given value to the base volume unit (cubic centimeters).
     * @param value The numerical value to convert.
     * @return The value converted to cubic centimeters.
     */
    public BigDecimal convertToCubicCm(BigDecimal value) {
        if (this.type == UnitType.VOLUME) {
            // For volume units like Liter, just multiply by the factor.
            return value.multiply(this.toBaseFactor);
        } else {
            // For length units, cube the conversion factor.
            // (e.g., for meters, it's value * 100 * 100 * 100)
            BigDecimal volumeFactor = this.toBaseFactor.pow(3);
            return value.multiply(volumeFactor);
        }
    }

    public enum UnitType {
        LENGTH, VOLUME
    }
}
