package com.productsearch.product_service.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;

public record ProductSearchRequest(
        String keyword,
        Long brandId,
        Long categoryId,
        @DecimalMin("0.0") BigDecimal minPrice,
        @DecimalMin("0.0") BigDecimal maxPrice) {
    @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null
                || minPrice.compareTo(maxPrice) <= 0;
    }
}