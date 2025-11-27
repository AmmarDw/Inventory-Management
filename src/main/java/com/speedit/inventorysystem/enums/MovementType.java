package com.speedit.inventorysystem.enums;

public enum MovementType {
    LOAD,       // from warehouse/local inventory to van
    UNLOAD,     // from van to warehouse or client
    TRANSFER    // warehouse -> warehouse, or other internal transfers
}
