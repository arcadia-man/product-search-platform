package com.productsearch.product_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;

public record ProductSearchRequest(
        String keyword,

        String name,

        String brand,

        @Positive Long brandId,

        String category,

        @Positive Long categoryId,

        String sku,

        String attribute,

        String description,

        @DecimalMin("0.0") BigDecimal minPrice,

        @DecimalMin("0.0") BigDecimal maxPrice,

        Boolean available
) {
    @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null
                || minPrice.compareTo(maxPrice) <= 0;
    }
}
