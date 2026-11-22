# Database Design

MySQL database: `product_search`. DDL and seed data are managed by Flyway in
[`product-service/src/main/resources/db/migration`](../product-service/src/main/resources/db/migration).

## Entity relationships

```mermaid
erDiagram
    BRANDS {
        BIGINT id PK
        VARCHAR_150 name
        VARCHAR_180 slug UK
        TIMESTAMP created_at
    }
    CATEGORIES {
        BIGINT id PK
        VARCHAR_150 name
        VARCHAR_180 slug UK
        BIGINT parent_id FK
        TIMESTAMP created_at
    }
    PRODUCTS {
        BIGINT id PK
        VARCHAR_255 name
        VARCHAR_280 slug UK
        TEXT description
        BIGINT brand_id FK
        VARCHAR_100 sku UK
        DECIMAL_12_2 price
        VARCHAR_30 status
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
    PRODUCT_CATEGORIES {
        BIGINT product_id PK
        BIGINT category_id PK
    }
    PRODUCT_VARIANTS {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR_100 sku UK
        VARCHAR_100 color
        VARCHAR_100 size
        VARCHAR_100 material
        VARCHAR_30 status
    }
    PRODUCT_PRICES {
        BIGINT id PK
        BIGINT product_id FK
        BIGINT variant_id FK
        VARCHAR_10 currency
        DECIMAL_12_2 price
        DECIMAL_12_2 discount_price
        TIMESTAMP valid_from
        TIMESTAMP valid_to
    }
    PRODUCT_ATTRIBUTES {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR_150 attribute_name
        VARCHAR_500 attribute_value
    }
    COMPONENTS {
        BIGINT id PK
        VARCHAR_150 name
        VARCHAR_100 type
    }
    PRODUCT_COMPONENTS {
        BIGINT id PK
        BIGINT product_id FK
        BIGINT component_id FK
        DECIMAL_12_3 quantity
    }
    WAREHOUSES {
        BIGINT id PK
        VARCHAR_150 name
        VARCHAR_100 city
    }
    INVENTORY {
        BIGINT id PK
        BIGINT variant_id FK
        BIGINT warehouse_id FK
        INT available_quantity
        TIMESTAMP updated_at
    }

    BRANDS o|--o{ PRODUCTS : brand_id
    CATEGORIES o|--o{ CATEGORIES : parent_id
    PRODUCTS ||--o{ PRODUCT_CATEGORIES : product_id
    CATEGORIES ||--o{ PRODUCT_CATEGORIES : category_id
    PRODUCTS ||--o{ PRODUCT_VARIANTS : product_id
    PRODUCTS ||--o{ PRODUCT_PRICES : product_id
    PRODUCT_VARIANTS o|--o{ PRODUCT_PRICES : variant_id
    PRODUCTS ||--o{ PRODUCT_ATTRIBUTES : product_id
    PRODUCTS ||--o{ PRODUCT_COMPONENTS : product_id
    COMPONENTS ||--o{ PRODUCT_COMPONENTS : component_id
    PRODUCT_VARIANTS ||--o{ INVENTORY : variant_id
    WAREHOUSES ||--o{ INVENTORY : warehouse_id
```

## Tables and constraints

| Table | Key columns and constraints |
|---|---|
| `brands` | `id` PK; `name VARCHAR(150)`; unique `slug VARCHAR(180)` |
| `categories` | `id` PK; unique `slug`; nullable `parent_id` self-FK |
| `products` | `id` PK; unique `slug` and product `sku`; nullable `brand_id`; `price DECIMAL(12,2)`; `status` |
| `product_categories` | Composite PK `(product_id, category_id)`; both columns are FKs |
| `product_variants` | `id` PK; `product_id` FK; unique variant `sku`; color, size, material, status |
| `product_prices` | `id` PK; `product_id` FK; nullable `variant_id` FK; currency, price, discount, validity dates |
| `product_attributes` | `id` PK; `product_id` FK; `attribute_name`, `attribute_value` |
| `components` | `id` PK; name and type |
| `product_components` | `id` PK; product/component FKs; unique `(product_id, component_id)`; quantity |
| `warehouses` | `id` PK; name and city |
| `inventory` | `id` PK; variant/warehouse FKs; unique `(variant_id, warehouse_id)`; available quantity |

## Schema decisions

- Products and categories are many-to-many through `product_categories`; there
  is no `products.category_id`.
- Product and variant SKUs are independently unique.
- `products.price` is the product-level price. `product_prices` stores
  currency- and optional variant-specific prices.
- Inventory is variant/warehouse-specific. Search availability is derived from
  whether any variant has positive inventory.
- `flyway_schema_history` is migration metadata, not a catalog entity.
- The schema source of truth is the Flyway migration set, not this summary.

The OpenSearch projection is documented in
[search-engineering](search-engineering.md).
