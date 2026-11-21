
package com.productsearch.product_service.seed;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "seed", name = "enabled", havingValue = "true")
public class CatalogSeedRunner implements CommandLineRunner {

    private static final int BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        int target = Integer.parseInt(
                System.getProperty("seed.products", "1000"));

        if (target < 4) {
            throw new IllegalArgumentException(
                    "Target must be at least 4");
        }

        System.out.println("Preparing catalog dimensions...");

        ensureDimensions();

        List<Long> brands = ids("brands");
        List<Long> categories = ids("categories");
        List<Long> components = ids("components");
        List<Long> warehouses = ids("warehouses");

        int currentCount = count("products");

        if (currentCount >= target) {
            System.out.println(
                    "Product target already reached: " + currentCount);
            return;
        }

        Integer lastGenerated = jdbc.queryForObject("""
                SELECT COALESCE(
                    MAX(CAST(SUBSTRING(sku, 10) AS UNSIGNED)), 0
                )
                FROM products
                WHERE sku LIKE 'GEN-PROD-%'
                """, Integer.class);

        int sequence = lastGenerated == null ? 0 : lastGenerated;

        System.out.println(
                "Generating " + (target - currentCount) + " products...");

        Random random = new Random(42);

        while (currentCount < target) {
            int batchCount = Math.min(
                    BATCH_SIZE, target - currentCount);

            List<ProductSeed> batch = new ArrayList<>();

            for (int i = 0; i < batchCount; i++) {
                int number = ++sequence;

                long brandId = brands.get(
                        (number - 1) % brands.size());

                long categoryId = categories.get(
                        (number - 1) % categories.size());

                long componentId = components.get(
                        (number - 1) % components.size());

                long warehouseId = warehouses.get(
                        (number - 1) % warehouses.size());

                BigDecimal price = BigDecimal.valueOf(
                        1000 + random.nextInt(99000));

                batch.add(new ProductSeed(
                        number,
                        brandId,
                        categoryId,
                        componentId,
                        warehouseId,
                        price));
            }

            insertProducts(batch);
            Map<String, Long> productIds = findProductIds(batch);

            insertProductCategories(batch, productIds);
            insertVariants(batch, productIds);

            Map<String, Long> variantIds = findVariantIds(batch);

            insertPricesAndInventory(batch, productIds, variantIds);
            insertAttributes(batch, productIds);
            insertComponents(batch, productIds);

            currentCount += batchCount;

            System.out.println(
                    "Products generated: " + currentCount + "/" + target);
        }

        System.out.println("Catalog seed completed successfully.");
    }

    private void ensureDimensions() {
        ensureSimpleTable(
                "brands", 100,
                "INSERT INTO brands(name, slug) VALUES (?, ?)",
                "Generated Brand ");

        ensureSimpleTable(
                "categories", 100,
                "INSERT INTO categories(name, slug) VALUES (?, ?)",
                "Generated Category ");

        ensureSimpleTable(
                "components", 500,
                "INSERT INTO components(name, type) VALUES (?, ?)",
                "Generated Component ");

        ensureSimpleTable(
                "warehouses", 20,
                "INSERT INTO warehouses(name, city) VALUES (?, ?)",
                "Generated Warehouse ");
    }

    private void ensureSimpleTable(
            String table,
            int target,
            String sql,
            String prefix) {
        int current = count(table);

        for (int i = current + 1; i <= target; i++) {
            String name = prefix + String.format("%03d", i);

            if (table.equals("components")) {
                jdbc.update(sql, name, "GENERAL");
            } else if (table.equals("warehouses")) {
                jdbc.update(sql, name, "City " + i);
            } else {
                String slug = name.toLowerCase()
                        .replace(" ", "-");

                jdbc.update(sql, name, slug);
            }
        }
    }

    private int count(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table,
                Integer.class);
    }

    private List<Long> ids(String table) {
        return jdbc.queryForList(
                "SELECT id FROM " + table + " ORDER BY id",
                Long.class);
    }

    private void insertProducts(List<ProductSeed> batch) {
        List<Object[]> args = batch.stream()
                .map(p -> {
                    String suffix = String.format("%06d", p.number());

                    return new Object[] {
                            "Generated Product " + suffix,
                            "gen-product-" + suffix,
                            "Generated catalog product number " + suffix,
                            p.brandId(),
                            "GEN-PROD-" + suffix,
                            p.price(),
                            "ACTIVE"
                    };
                })
                .toList();

        jdbc.batchUpdate("""
                INSERT IGNORE INTO products
                (name, slug, description, brand_id, sku, price, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, args);
    }

    private Map<String, Long> findProductIds(
            List<ProductSeed> batch) {
        List<String> skus = batch.stream()
                .map(p -> productSku(p.number()))
                .toList();

        return findIds("products", "sku", skus);
    }

    private void insertProductCategories(
            List<ProductSeed> batch,
            Map<String, Long> productIds) {
        List<Object[]> args = batch.stream()
                .map(p -> new Object[] {
                        productIds.get(productSku(p.number())),
                        p.categoryId()
                })
                .toList();

        jdbc.batchUpdate("""
                INSERT IGNORE INTO product_categories
                (product_id, category_id)
                VALUES (?, ?)
                """, args);
    }

    private void insertVariants(
            List<ProductSeed> batch,
            Map<String, Long> productIds) {
        List<Object[]> args = new ArrayList<>();

        for (ProductSeed p : batch) {
            long productId = productIds.get(productSku(p.number()));

            for (int v = 1; v <= 2; v++) {
                args.add(new Object[] {
                        productId,
                        variantSku(p.number(), v),
                        v == 1 ? "Black" : "Blue",
                        v == 1 ? "128GB" : "256GB",
                        "Aluminium",
                        "ACTIVE"
                });
            }
        }

        jdbc.batchUpdate("""
                INSERT IGNORE INTO product_variants
                (product_id, sku, color, size, material, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, args);
    }

    private Map<String, Long> findVariantIds(
            List<ProductSeed> batch) {
        List<String> skus = new ArrayList<>();

        for (ProductSeed p : batch) {
            skus.add(variantSku(p.number(), 1));
            skus.add(variantSku(p.number(), 2));
        }

        return findIds("product_variants", "sku", skus);
    }

    private Map<String, Long> findIds(
            String table,
            String column,
            List<String> values) {
        String placeholders = values.stream()
                .map(v -> "?")
                .collect(Collectors.joining(","));

        String sql = "SELECT id, " + column + " FROM " + table
                + " WHERE " + column + " IN (" + placeholders + ")";

        return jdbc.query(
                sql,
                rs -> {
                    Map<String, Long> result = new HashMap<>();

                    while (rs.next()) {
                        result.put(
                                rs.getString(column),
                                rs.getLong("id"));
                    }

                    return result;
                },
                values.toArray());
    }

    private void insertPricesAndInventory(
            List<ProductSeed> batch,
            Map<String, Long> productIds,
            Map<String, Long> variantIds) {
        List<Object[]> prices = new ArrayList<>();
        List<Object[]> inventory = new ArrayList<>();

        for (ProductSeed p : batch) {
            long productId = productIds.get(productSku(p.number()));

            for (int v = 1; v <= 2; v++) {
                long variantId = variantIds.get(
                        variantSku(p.number(), v));

                BigDecimal price = p.price().add(
                        BigDecimal.valueOf((v - 1) * 5000L));

                BigDecimal discount = price.multiply(
                        BigDecimal.valueOf(0.90)).setScale(2, java.math.RoundingMode.HALF_UP);

                prices.add(new Object[] {
                        productId, variantId, price, discount, variantId
                });

                inventory.add(new Object[] {
                        variantId,
                        p.warehouseId(),
                        10 + (p.number() % 100)
                });
            }
        }

        jdbc.batchUpdate("""
                INSERT INTO product_prices
                (product_id, variant_id, currency, price,
                 discount_price, valid_from, valid_to)
                SELECT ?, ?, 'INR', ?, ?, CURRENT_TIMESTAMP, NULL
                WHERE NOT EXISTS (
                    SELECT 1 FROM product_prices WHERE variant_id = ?
                )
                """, prices);

        jdbc.batchUpdate("""
                INSERT IGNORE INTO inventory
                (variant_id, warehouse_id, available_quantity)
                VALUES (?, ?, ?)
                """, inventory);
    }

    private void insertAttributes(
            List<ProductSeed> batch,
            Map<String, Long> productIds) {
        List<Object[]> args = new ArrayList<>();

        for (ProductSeed p : batch) {
            long id = productIds.get(productSku(p.number()));

            String suffix = String.format("%06d", p.number());

            args.add(new Object[] { id, "processor", "Processor " + suffix, id, "processor" });
            args.add(new Object[] { id, "display", "Display " + suffix, id, "display" });
            args.add(new Object[] { id, "storage", "128GB", id, "storage" });
            args.add(new Object[] { id, "connectivity", "Bluetooth 5.3", id, "connectivity" });
            args.add(new Object[] { id, "material", "Aluminium", id, "material" });
        }

        jdbc.batchUpdate("""
                INSERT INTO product_attributes
                (product_id, attribute_name, attribute_value)
                SELECT ?, ?, ?
                WHERE NOT EXISTS (
                    SELECT 1 FROM product_attributes
                    WHERE product_id = ? AND attribute_name = ?
                )
                """, args);
    }

    private void insertComponents(
            List<ProductSeed> batch,
            Map<String, Long> productIds) {
        List<Object[]> args = batch.stream()
                .map(p -> new Object[] {
                        productIds.get(productSku(p.number())),
                        p.componentId()
                })
                .toList();

        jdbc.batchUpdate("""
                INSERT INTO product_components
                (product_id, component_id, quantity)
                SELECT ?, ?, 1
                WHERE NOT EXISTS (
                    SELECT 1 FROM product_components
                    WHERE product_id = ? AND component_id = ?
                )
                """, args.stream()
                .map(a -> new Object[] {
                        a[0], a[1], a[0], a[1]
                })
                .toList());
    }

    private String productSku(int number) {
        return "GEN-PROD-" + String.format("%06d", number);
    }

    private String variantSku(int number, int variant) {
        return productSku(number) + "-V" + variant;
    }

    private record ProductSeed(
            int number,
            long brandId,
            long categoryId,
            long componentId,
            long warehouseId,
            BigDecimal price) {
    }
}
