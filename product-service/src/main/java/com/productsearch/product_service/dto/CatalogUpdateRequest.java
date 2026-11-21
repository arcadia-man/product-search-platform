package com.productsearch.product_service.dto;

import java.util.Map;

import jakarta.validation.constraints.NotEmpty;

public record CatalogUpdateRequest(
        Long id,

        @NotEmpty(message = "At least one field must be provided for update")
        Map<String, Object> updates
) {
}
