package com.productsearch.search_indexer.document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDocument(
        Long id,
        String name,
        String slug,
        String description,
        String sku,
        String brand,
        BrandDetail brandDetail,
        List<String> categories,
        List<CategoryDetail> categoryDetails,
        BigDecimal price,
        String status,
        boolean available,
        List<Variant> variants,
        List<Price> prices,
        List<Attribute> attributes,
        List<Component> components,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record BrandDetail(Long id, String name, String slug) {}

    public record CategoryDetail(Long id, String name, String slug) {}

    public record Inventory(
            Long id,
            Long warehouseId,
            String warehouseName,
            String warehouseCity,
            Integer availableQuantity
    ) {}

    public record Variant(
            Long id,
            String sku,
            String color,
            String size,
            String material,
            String status,
            List<Inventory> inventory
    ) {}

    public record Price(
            Long id,
            Long variantId,
            String variantSku,
            String currency,
            BigDecimal price,
            BigDecimal discountPrice,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {}

    public record Attribute(Long id, String name, String value) {}

    public record Component(Long id, String name, String type, BigDecimal quantity) {}
}
