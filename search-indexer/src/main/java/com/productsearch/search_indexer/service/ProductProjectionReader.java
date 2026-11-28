package com.productsearch.search_indexer.service;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.productsearch.search_indexer.document.ProductDocument;
import com.productsearch.search_indexer.document.ProductDocument.Attribute;
import com.productsearch.search_indexer.document.ProductDocument.BrandDetail;
import com.productsearch.search_indexer.document.ProductDocument.CategoryDetail;
import com.productsearch.search_indexer.document.ProductDocument.Component;
import com.productsearch.search_indexer.document.ProductDocument.Inventory;
import com.productsearch.search_indexer.document.ProductDocument.Price;
import com.productsearch.search_indexer.document.ProductDocument.Variant;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductProjectionReader {

    private static final RowMapper<BaseProduct> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new BaseProduct(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("slug"),
            rs.getString("description"),
            rs.getString("sku"),
            rs.getString("brand"),
            nullableLong(rs, "brand_id"),
            rs.getString("brand_slug"),
            rs.getBigDecimal("price"),
            rs.getString("status"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at")
    );

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public List<ProductDocument> findProductsAfterId(long lastId, int limit) {
        List<BaseProduct> products = jdbcTemplate.query(
                baseProductSql() + " WHERE p.id > ? ORDER BY p.id LIMIT ?",
                PRODUCT_ROW_MAPPER,
                lastId,
                limit
        );
        return buildDocuments(products);
    }

    public Optional<ProductDocument> findProduct(long productId) {
        List<BaseProduct> products = jdbcTemplate.query(
                baseProductSql() + " WHERE p.id = ? LIMIT 1",
                PRODUCT_ROW_MAPPER,
                productId
        );
        return buildDocuments(products).stream().findFirst();
    }

    public Set<Long> findAffectedProductIds(String table, JsonNode row) {
        Long rowId;
        switch (table) {
            case "products" -> {
                rowId = readLong(row, "id");
                return rowId == null ? Set.of() : Set.of(rowId);
            }
            case "product_variants", "product_prices", "product_categories",
                    "product_components", "product_attributes" -> {
                rowId = readLong(row, "product_id");
                return rowId == null ? Set.of() : Set.of(rowId);
            }
            case "inventory" -> {
                rowId = readLong(row, "variant_id");
                return rowId == null ? Set.of() : queryProductIds(
                        "SELECT product_id FROM product_variants WHERE id = ?", rowId);
            }
            case "brands" -> {
                rowId = readLong(row, "id");
                return rowId == null ? Set.of() : queryProductIds(
                        "SELECT id FROM products WHERE brand_id = ?", rowId);
            }
            case "categories" -> {
                rowId = readLong(row, "id");
                return rowId == null ? Set.of() : queryProductIds(
                        "SELECT product_id FROM product_categories WHERE category_id = ?", rowId);
            }
            case "components" -> {
                rowId = readLong(row, "id");
                return rowId == null ? Set.of() : queryProductIds(
                        "SELECT product_id FROM product_components WHERE component_id = ?", rowId);
            }
            case "warehouses" -> {
                rowId = readLong(row, "id");
                return rowId == null ? Set.of() : queryProductIds("""
                        SELECT DISTINCT v.product_id
                        FROM inventory i
                        JOIN product_variants v ON v.id = i.variant_id
                        WHERE i.warehouse_id = ?
                        """, rowId);
            }
            default -> throw new IllegalArgumentException("Unsupported CDC table: " + table);
        }
    }

    private String baseProductSql() {
        return """
                SELECT p.id, p.name, p.slug, p.description, p.sku, p.price, p.status,
                       p.created_at, p.updated_at, b.id AS brand_id, b.name AS brand,
                       b.slug AS brand_slug
                FROM products p
                LEFT JOIN brands b ON b.id = p.brand_id
                """;
    }

    private List<ProductDocument> buildDocuments(List<BaseProduct> products) {
        if (products.isEmpty()) {
            return List.of();
        }

        List<Long> productIds = products.stream().map(BaseProduct::id).toList();
        MapSqlParameterSource parameters = new MapSqlParameterSource("productIds", productIds);
        Map<Long, List<CategoryDetail>> categories = readCategories(parameters);
        Map<Long, List<Variant>> variants = readVariants(parameters);
        Map<Long, List<Price>> prices = readPrices(parameters);
        Map<Long, List<Attribute>> attributes = readAttributes(parameters);
        Map<Long, List<Component>> components = readComponents(parameters);

        return products.stream().map(product -> {
            List<CategoryDetail> productCategories = categories.getOrDefault(product.id(), List.of());
            List<Variant> productVariants = variants.getOrDefault(product.id(), List.of());
            boolean available = productVariants.stream()
                    .flatMap(variant -> variant.inventory().stream())
                    .anyMatch(inventory -> inventory.availableQuantity() != null
                            && inventory.availableQuantity() > 0);

            return new ProductDocument(
                    product.id(),
                    product.name(),
                    product.slug(),
                    product.description(),
                    product.sku(),
                    product.brand(),
                    product.brandId() == null ? null : new BrandDetail(
                            product.brandId(), product.brand(), product.brandSlug()),
                    productCategories.stream().map(CategoryDetail::name).toList(),
                    productCategories,
                    product.price(),
                    product.status(),
                    available,
                    productVariants,
                    prices.getOrDefault(product.id(), List.of()),
                    attributes.getOrDefault(product.id(), List.of()),
                    components.getOrDefault(product.id(), List.of()),
                    product.createdAt(),
                    product.updatedAt()
            );
        }).toList();
    }

    private Map<Long, List<CategoryDetail>> readCategories(MapSqlParameterSource parameters) {
        Map<Long, List<CategoryDetail>> result = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query("""
                SELECT pc.product_id, c.id, c.name, c.slug
                FROM product_categories pc
                JOIN categories c ON c.id = pc.category_id
                WHERE pc.product_id IN (:productIds)
                ORDER BY pc.product_id, c.name
                """, parameters, rs -> {
            result.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                    .add(new CategoryDetail(rs.getLong("id"), rs.getString("name"), rs.getString("slug")));
        });
        return result;
    }

    private Map<Long, List<Variant>> readVariants(MapSqlParameterSource parameters) {
        Map<Long, LinkedHashMap<Long, VariantBuilder>> byProduct = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query("""
                SELECT v.product_id, v.id AS variant_id, v.sku, v.color, v.size, v.material, v.status,
                       i.id AS inventory_id, i.available_quantity, w.id AS warehouse_id,
                       w.name AS warehouse_name, w.city AS warehouse_city
                FROM product_variants v
                LEFT JOIN inventory i ON i.variant_id = v.id
                LEFT JOIN warehouses w ON w.id = i.warehouse_id
                WHERE v.product_id IN (:productIds)
                ORDER BY v.product_id, v.sku, w.name
                """, parameters, rs -> {
            long productId = rs.getLong("product_id");
            long variantId = rs.getLong("variant_id");
            VariantBuilder variant = byProduct
                    .computeIfAbsent(productId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(variantId, ignored -> new VariantBuilder(
                            variantId,
                            rsString(rs, "sku"),
                            rsString(rs, "color"),
                            rsString(rs, "size"),
                            rsString(rs, "material"),
                            rsString(rs, "status")));

            Long inventoryId = nullableLong(rs, "inventory_id");
            if (inventoryId != null) {
                variant.inventory.add(new Inventory(
                        inventoryId,
                        nullableLong(rs, "warehouse_id"),
                        rsString(rs, "warehouse_name"),
                        rsString(rs, "warehouse_city"),
                        nullableInteger(rs, "available_quantity")));
            }
        });

        Map<Long, List<Variant>> result = new LinkedHashMap<>();
        byProduct.forEach((productId, productVariants) -> result.put(
                productId,
                productVariants.values().stream().map(VariantBuilder::build).toList()));
        return result;
    }

    private Map<Long, List<Price>> readPrices(MapSqlParameterSource parameters) {
        Map<Long, List<Price>> result = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query("""
                SELECT p.product_id, p.id, p.variant_id, v.sku AS variant_sku, p.currency,
                       p.price, p.discount_price, p.valid_from, p.valid_to
                FROM product_prices p
                LEFT JOIN product_variants v ON v.id = p.variant_id
                WHERE p.product_id IN (:productIds)
                ORDER BY p.product_id, v.sku
                """, parameters, rs -> {
            result.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                    .add(new Price(
                        rs.getLong("id"),
                        nullableLong(rs, "variant_id"),
                        rs.getString("variant_sku"),
                        rs.getString("currency"),
                        rs.getBigDecimal("price"),
                        rs.getBigDecimal("discount_price"),
                        localDateTime(rs, "valid_from"),
                        localDateTime(rs, "valid_to")));
        });
        return result;
    }

    private Map<Long, List<Attribute>> readAttributes(MapSqlParameterSource parameters) {
        Map<Long, List<Attribute>> result = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query("""
                SELECT product_id, id, attribute_name, attribute_value
                FROM product_attributes
                WHERE product_id IN (:productIds)
                ORDER BY product_id, attribute_name
                """, parameters, rs -> {
            result.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                    .add(new Attribute(
                        rs.getLong("id"),
                        rs.getString("attribute_name"),
                        rs.getString("attribute_value")));
        });
        return result;
    }

    private Map<Long, List<Component>> readComponents(MapSqlParameterSource parameters) {
        Map<Long, List<Component>> result = new LinkedHashMap<>();
        namedParameterJdbcTemplate.query("""
                SELECT pc.product_id, pc.id, c.name, c.type, pc.quantity
                FROM product_components pc
                JOIN components c ON c.id = pc.component_id
                WHERE pc.product_id IN (:productIds)
                ORDER BY pc.product_id, c.name
                """, parameters, rs -> {
            result.computeIfAbsent(rs.getLong("product_id"), ignored -> new ArrayList<>())
                    .add(new Component(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("type"),
                        rs.getBigDecimal("quantity")));
        });
        return result;
    }

    private Set<Long> queryProductIds(String sql, Long id) {
        return new LinkedHashSet<>(jdbcTemplate.queryForList(sql, Long.class, id));
    }

    private static Long readLong(JsonNode row, String field) {
        if (row == null || row.isNull() || !row.hasNonNull(field)) {
            return null;
        }
        try {
            return Long.parseLong(row.get(field).asText());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid numeric CDC field: " + field, e);
        }
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).longValue();
    }

    private static Integer nullableInteger(ResultSet rs, String column) throws SQLException {
        Object value = rs.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static String rsString(ResultSet rs, String column) {
        try {
            return rs.getString(column);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read product projection column " + column, e);
        }
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private record BaseProduct(
            Long id,
            String name,
            String slug,
            String description,
            String sku,
            String brand,
            Long brandId,
            String brandSlug,
            BigDecimal price,
            String status,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    private static final class VariantBuilder {
        private final Long id;
        private final String sku;
        private final String color;
        private final String size;
        private final String material;
        private final String status;
        private final List<Inventory> inventory = new ArrayList<>();

        private VariantBuilder(Long id, String sku, String color, String size, String material, String status) {
            this.id = id;
            this.sku = sku;
            this.color = color;
            this.size = size;
            this.material = material;
            this.status = status;
        }

        private Variant build() {
            return new Variant(id, sku, color, size, material, status, inventory);
        }
    }
}
