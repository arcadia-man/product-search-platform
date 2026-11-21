package com.productsearch.product_service.dto;

import java.util.List;

public record CatalogUpdateResponse(
        boolean success,
        String entity,
        String table,
        Long id,
        List<String> updatedFields,
        String message
) {
}
