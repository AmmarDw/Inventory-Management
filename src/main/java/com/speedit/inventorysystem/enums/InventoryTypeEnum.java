package com.speedit.inventorysystem.enums;

public enum InventoryTypeEnum {
    VAN("Van"), WAREHOUSE("Warehouse"), LOCAL_STORE("Local Store");
    private final String displayName;
    InventoryTypeEnum(String displayName){ this.displayName = displayName; }
    public String getDisplayName(){ return displayName; }
}
