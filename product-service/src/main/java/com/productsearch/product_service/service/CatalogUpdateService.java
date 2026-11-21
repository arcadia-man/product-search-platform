package com.productsearch.product_service.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.productsearch.product_service.dto.CatalogUpdateRequest;
import com.productsearch.product_service.dto.CatalogUpdateResponse;
import com.productsearch.product_service.enums.CatalogEntity;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogUpdateService {

    private static final Map<CatalogEntity, String> ENTITY_TABLE_MAP = Map.of(
            CatalogEntity.PRODUCTS, "products",
            CatalogEntity.BRANDS, "brands",
            CatalogEntity.CATEGORIES, "categories",
            CatalogEntity.VARIANTS, "product_variants",
            CatalogEntity.PRICES, "product_prices",
            CatalogEntity.COMPONENTS, "components",
            CatalogEntity.PRODUCT_COMPONENTS, "product_components",
            CatalogEntity.ATTRIBUTES, "product_attributes",
            CatalogEntity.INVENTORY, "inventory",
            CatalogEntity.WAREHOUSES, "warehouses"
    );

    private static final Map<String, Set<String>> TABLE_UPDATABLE_COLUMNS = Map.of(
            "products", Set.of("name", "slug", "description", "brand_id", "sku", "price", "status"),
            "brands", Set.of("name", "slug"),
            "categories", Set.of("name", "slug", "parent_id"),
            "product_variants", Set.of("sku", "color", "size", "material", "status", "product_id"),
            "product_prices", Set.of("currency", "price", "discount_price", "valid_from", "valid_to", "variant_id", "product_id"),
            "components", Set.of("name", "type"),
            "product_components", Set.of("quantity", "component_id", "product_id"),
            "product_attributes", Set.of("attribute_name", "attribute_value", "product_id"),
            "inventory", Set.of("available_quantity", "variant_id", "warehouse_id"),
            "warehouses", Set.of("name", "city")
    );

    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public CatalogUpdateResponse update(String entityName, Long explicitId, CatalogUpdateRequest request) {
        CatalogEntity entity = CatalogEntity.from(entityName);
        String table = ENTITY_TABLE_MAP.get(entity);

        Long recordId = explicitId != null ? explicitId : request.id();
        if (recordId == null || recordId <= 0) {
            throw new IllegalArgumentException("Record ID must be provided and positive");
        }

        Set<String> allowedColumns = TABLE_UPDATABLE_COLUMNS.getOrDefault(table, Collections.emptySet());
        Map<String, Object> updates = request.updates();

        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("Updates map cannot be empty");
        }

        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        List<String> updatedFields = new ArrayList<>();

        for (Map.Entry<String, Object> entry : updates.entrySet()) {
            String columnName = toSnakeCase(entry.getKey());

            // Update only relative updatable fields from the table
            if (allowedColumns.contains(columnName)) {
                setClauses.add(columnName + " = ?");
                params.add(entry.getValue());
                updatedFields.add(columnName);
            }
        }

        if (setClauses.isEmpty()) {
            throw new IllegalArgumentException(
                    "No valid updatable fields provided for table " + table
                            + ". Allowed updatable fields: " + allowedColumns
            );
        }

        params.add(recordId);

        String sql = "UPDATE " + table + " SET " + String.join(", ", setClauses) + " WHERE id = ?";
        int rows = jdbcTemplate.update(sql, params.toArray());

        if (rows == 0) {
            throw new NoSuchElementException("Record with ID " + recordId + " not found in " + table);
        }

        return new CatalogUpdateResponse(
                true,
                entity.name(),
                table,
                recordId,
                updatedFields,
                "Successfully updated " + updatedFields.size() + " field(s) in " + table
        );
    }

    private String toSnakeCase(String str) {
        if (str == null) {
            return "";
        }
        return str.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }
}
