package com.productsearch.product_service.dto;

import java.math.BigDecimal;
import java.util.List;

import com.productsearch.product_service.entity.Product;

public record ProductResponse(
        Long id,
        String name,
        String description,
        String sku,
        String brand,
        List<String> categories,
        BigDecimal price,
        String status
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getSku(),
                product.getBrand() != null ? product.getBrand().getName() : null,
                product.getCategories().stream()
                        .map(category -> category.getName())
                        .toList(),
                product.getPrice(),
                product.getStatus()
        );
    }
}