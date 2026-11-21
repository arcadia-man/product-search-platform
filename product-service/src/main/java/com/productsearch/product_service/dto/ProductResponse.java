package com.productsearch.product_service.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import com.productsearch.product_service.entity.Category;
import com.productsearch.product_service.entity.Product;

public record ProductResponse(
        Long id,
        String name,
        String slug,
        String description,
        String sku,
        String brand,
        BrandDto brandDetail,
        List<String> categories,
        List<CategoryDto> categoryDetails,
        BigDecimal price,
        String status,
        boolean available,
        List<VariantDto> variants,
        List<PriceDto> prices,
        List<AttributeDto> attributes,
        List<ComponentDto> components,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record BrandDto(Long id, String name, String slug) {}

    public record CategoryDto(Long id, String name, String slug) {}

    public record InventoryDto(
            Long id,
            Long warehouseId,
            String warehouseName,
            String warehouseCity,
            Integer availableQuantity
    ) {}

    public record VariantDto(
            Long id,
            String sku,
            String color,
            String size,
            String material,
            String status,
            List<InventoryDto> inventory
    ) {}

    public record PriceDto(
            Long id,
            Long variantId,
            String variantSku,
            String currency,
            BigDecimal price,
            BigDecimal discountPrice,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {}

    public record AttributeDto(
            Long id,
            String name,
            String value
    ) {}

    public record ComponentDto(
            Long id,
            String name,
            String type,
            BigDecimal quantity
    ) {}

    public static ProductResponse from(Product product) {
        boolean isAvailable = product.getVariants().stream()
                .flatMap(v -> v.getInventory().stream())
                .anyMatch(i -> i.getAvailableQuantity() != null && i.getAvailableQuantity() > 0);

        List<VariantDto> variantDtos = product.getVariants().stream()
                .map(v -> new VariantDto(
                        v.getId(),
                        v.getSku(),
                        v.getColor(),
                        v.getSize(),
                        v.getMaterial(),
                        v.getStatus(),
                        v.getInventory().stream()
                                .map(inv -> new InventoryDto(
                                        inv.getId(),
                                        inv.getWarehouse() != null ? inv.getWarehouse().getId() : null,
                                        inv.getWarehouse() != null ? inv.getWarehouse().getName() : null,
                                        inv.getWarehouse() != null ? inv.getWarehouse().getCity() : null,
                                        inv.getAvailableQuantity()))
                                .sorted(Comparator.comparing(i -> i.warehouseName() != null ? i.warehouseName() : ""))
                                .toList()))
                .sorted(Comparator.comparing(VariantDto::sku))
                .toList();

        List<PriceDto> priceDtos = product.getPrices().stream()
                .map(p -> new PriceDto(
                        p.getId(),
                        p.getVariant() != null ? p.getVariant().getId() : null,
                        p.getVariant() != null ? p.getVariant().getSku() : null,
                        p.getCurrency(),
                        p.getPrice(),
                        p.getDiscountPrice(),
                        p.getValidFrom(),
                        p.getValidTo()))
                .sorted(Comparator.comparing(p -> p.variantSku() != null ? p.variantSku() : ""))
                .toList();

        List<AttributeDto> attributeDtos = product.getAttributes().stream()
                .map(a -> new AttributeDto(
                        a.getId(),
                        a.getAttributeName(),
                        a.getAttributeValue()))
                .sorted(Comparator.comparing(AttributeDto::name))
                .toList();

        List<ComponentDto> componentDtos = product.getComponents().stream()
                .map(pc -> new ComponentDto(
                        pc.getId(),
                        pc.getComponent() != null ? pc.getComponent().getName() : null,
                        pc.getComponent() != null ? pc.getComponent().getType() : null,
                        pc.getQuantity()))
                .sorted(Comparator.comparing(c -> c.name() != null ? c.name() : ""))
                .toList();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getSlug(),
                product.getDescription(),
                product.getSku(),
                product.getBrand() != null ? product.getBrand().getName() : null,
                product.getBrand() != null ? new BrandDto(product.getBrand().getId(), product.getBrand().getName(), product.getBrand().getSlug()) : null,
                product.getCategories().stream().map(Category::getName).sorted().toList(),
                product.getCategories().stream()
                        .map(c -> new CategoryDto(c.getId(), c.getName(), c.getSlug()))
                        .sorted(Comparator.comparing(CategoryDto::name))
                        .toList(),
                product.getPrice(),
                product.getStatus(),
                isAvailable,
                variantDtos,
                priceDtos,
                attributeDtos,
                componentDtos,
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
