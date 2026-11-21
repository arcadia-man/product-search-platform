package com.productsearch.product_service.enums;

import java.util.Arrays;

public enum CatalogEntity {

    PRODUCTS,
    BRANDS,
    CATEGORIES,
    VARIANTS,
    PRICES,
    COMPONENTS,
    PRODUCT_COMPONENTS,
    ATTRIBUTES,
    INVENTORY,
    WAREHOUSES;

    public static CatalogEntity from(String value) {
        return Arrays.stream(values())
                .filter(entity -> entity.name().equalsIgnoreCase(
                        value.replace("-", "_")))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported catalog entity: " + value));
    }
}
