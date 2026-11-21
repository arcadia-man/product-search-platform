# Product Search Platform --- Database Schema

> Source of truth: MySQL `product_search` database metadata supplied on
> 21-Nov-2026. This document describes the implemented schema, not only
> the original logical design.

## 1. Scope and table count

The implemented catalog has **11 domain tables**. The original plan
listed 10 logical tables; the implementation also contains
`product_categories`, the junction table that represents the
many-to-many relationship between products and categories.

MySQL also contains `flyway_schema_history`. It is Flyway's migration
bookkeeping table, not a catalog/domain table, and is documented
separately below.

## 2. Entity relationship diagram

``` mermaid
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
        BIGINT product_id PK, FK
        BIGINT category_id PK, FK
    }

    PRODUCT_VARIANTS {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR_100 sku UK
        VARCHAR_100 color
        VARCHAR_100 size
        VARCHAR_100 material
        VARCHAR_30 status
        TIMESTAMP created_at
        TIMESTAMP updated_at
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
        TIMESTAMP updated_at
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

    PRODUCT_ATTRIBUTES {
        BIGINT id PK
        BIGINT product_id FK
        VARCHAR_150 attribute_name
        VARCHAR_500 attribute_value
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

    BRANDS o|--o{ PRODUCTS : "brand_id"
    CATEGORIES o|--o{ CATEGORIES : "parent_id"
    PRODUCTS ||--o{ PRODUCT_VARIANTS : "product_id"
    PRODUCTS ||--o{ PRODUCT_PRICES : "product_id"
    PRODUCT_VARIANTS o|--o{ PRODUCT_PRICES : "variant_id"
    PRODUCTS ||--o{ PRODUCT_CATEGORIES : "product_id"
    CATEGORIES ||--o{ PRODUCT_CATEGORIES : "category_id"
    PRODUCTS ||--o{ PRODUCT_COMPONENTS : "product_id"
    COMPONENTS ||--o{ PRODUCT_COMPONENTS : "component_id"
    PRODUCTS ||--o{ PRODUCT_ATTRIBUTES : "product_id"
    PRODUCT_VARIANTS ||--o{ INVENTORY : "variant_id"
    WAREHOUSES ||--o{ INVENTORY : "warehouse_id"
```

**Notation:** `PK` = primary key, `FK` = foreign key, `UK` = unique key.
In the diagram, `o|` indicates an optional one-side relationship and
`||` a mandatory one-side relationship.

## 3. Table and column dictionary

Types, nullability, defaults, and key markers below are transcribed from
the supplied `INFORMATION_SCHEMA` output. `NOT NULL` and `NULL` reflect
`IS_NULLABLE`. `AUTO_INCREMENT` and `ON UPDATE CURRENT_TIMESTAMP` are
shown where reported.

### 3.1 `products`

**Purpose:** Main product record and root entity for the product search
projection.

  -------------------------------------------------------------------------------------------------
  Column          MySQL type                     Null Key / default          Description
  --------------- ----------------- ----------------- ---------------------- ----------------------
  `id`            `BIGINT`                         No PK, AUTO_INCREMENT     Unique product
                                                                             identifier.

  `name`          `VARCHAR(255)`                   No ---                    Product display name;
                                                                             primary text users
                                                                             search.

  `slug`          `VARCHAR(280)`                   No UNIQUE                 URL-friendly product
                                                                             identifier.

  `description`   `TEXT`                          Yes ---                    Longer product
                                                                             description, useful
                                                                             for full-text search
                                                                             and details.

  `brand_id`      `BIGINT`                        Yes FK → `brands.id`       Brand associated with
                                                                             the product.

  `sku`           `VARCHAR(100)`                   No UNIQUE                 Product-level stock
                                                                             keeping unit.

  `price`         `DECIMAL(12,2)`                  No ---                    Product-level base
                                                                             price retained on the
                                                                             product row.

  `status`        `VARCHAR(30)`                    No `ACTIVE`               Product
                                                                             lifecycle/visibility
                                                                             status.

  `created_at`    `TIMESTAMP`                     Yes `CURRENT_TIMESTAMP`    Time the product row
                                                                             was created.

  `updated_at`    `TIMESTAMP`                     Yes `CURRENT_TIMESTAMP`,   Last update time for
                                                      auto-updates           the product row.
  -------------------------------------------------------------------------------------------------

### 3.2 `brands`

**Purpose:** Brand master data referenced by products.

  --------------------------------------------------------------------------------------
  Column         MySQL type                    Null Key / default         Description
  -------------- ---------------- ----------------- --------------------- --------------
  `id`           `BIGINT`                        No PK, AUTO_INCREMENT    Unique brand
                                                                          identifier.

  `name`         `VARCHAR(150)`                  No ---                   Brand display
                                                                          name.

  `slug`         `VARCHAR(180)`                  No UNIQUE                URL-friendly
                                                                          brand
                                                                          identifier.

  `created_at`   `TIMESTAMP`                    Yes `CURRENT_TIMESTAMP`   Time the brand
                                                                          row was
                                                                          created.
  --------------------------------------------------------------------------------------

### 3.3 `categories`

**Purpose:** Hierarchical category master data. A category can have a
parent category.

  --------------------------------------------------------------------------------------
  Column         MySQL type                    Null Key / default         Description
  -------------- ---------------- ----------------- --------------------- --------------
  `id`           `BIGINT`                        No PK, AUTO_INCREMENT    Unique
                                                                          category
                                                                          identifier.

  `name`         `VARCHAR(150)`                  No ---                   Category
                                                                          display name.

  `slug`         `VARCHAR(180)`                  No UNIQUE                URL-friendly
                                                                          category
                                                                          identifier.

  `parent_id`    `BIGINT`                       Yes FK → `categories.id`  Parent
                                                                          category; null
                                                                          for a
                                                                          top-level
                                                                          category.

  `created_at`   `TIMESTAMP`                    Yes `CURRENT_TIMESTAMP`   Time the
                                                                          category row
                                                                          was created.
  --------------------------------------------------------------------------------------

### 3.4 `product_categories`

**Purpose:** Junction table implementing the many-to-many relationship
between products and categories.

  ---------------------------------------------------------------------------------
  Column          MySQL type                 Null Key / default     Description
  --------------- ------------- ----------------- ----------------- ---------------
  `product_id`    `BIGINT`                     No Composite PK, FK  Product
                                                  → `products.id`   participating
                                                                    in the
                                                                    relationship.

  `category_id`   `BIGINT`                     No Composite PK, FK  Category
                                                  → `categories.id` assigned to the
                                                                    product.
  ---------------------------------------------------------------------------------

The pair (`product_id`, `category_id`) forms the composite primary key.

### 3.5 `product_variants`

**Purpose:** Sellable/configurable variants belonging to a product.

  ----------------------------------------------------------------------------------------
  Column         MySQL type                    Null Key / default          Description
  -------------- ---------------- ----------------- ---------------------- ---------------
  `id`           `BIGINT`                        No PK, AUTO_INCREMENT     Unique variant
                                                                           identifier.

  `product_id`   `BIGINT`                        No FK → `products.id`     Parent product.

  `sku`          `VARCHAR(100)`                  No UNIQUE                 Variant-level
                                                                           SKU.

  `color`        `VARCHAR(100)`                 Yes ---                    Variant color.

  `size`         `VARCHAR(100)`                 Yes ---                    Variant size or
                                                                           capacity.

  `material`     `VARCHAR(100)`                 Yes ---                    Variant
                                                                           material.

  `status`       `VARCHAR(30)`                   No `ACTIVE`               Variant status.

  `created_at`   `TIMESTAMP`                    Yes `CURRENT_TIMESTAMP`    Time the
                                                                           variant row was
                                                                           created.

  `updated_at`   `TIMESTAMP`                    Yes `CURRENT_TIMESTAMP`,   Last update
                                                    auto-updates           time for the
                                                                           variant row.
  ----------------------------------------------------------------------------------------

### 3.6 `product_prices`

**Purpose:** Product- or variant-specific prices, including currency,
discount, and validity window.

  ----------------------------------------------------------------------------------------------
  Column             MySQL type                     Null Key / default           Description
  ------------------ ----------------- ----------------- ----------------------- ---------------
  `id`               `BIGINT`                         No PK, AUTO_INCREMENT      Unique price
                                                                                 record
                                                                                 identifier.

  `product_id`       `BIGINT`                         No FK → `products.id`      Product to
                                                                                 which the price
                                                                                 belongs.

  `variant_id`       `BIGINT`                        Yes FK →                    Optional
                                                         `product_variants.id`   variant this
                                                                                 price applies
                                                                                 to; null can
                                                                                 represent
                                                                                 product-level
                                                                                 pricing.

  `currency`         `VARCHAR(10)`                    No `INR`                   Currency code
                                                                                 for the price.

  `price`            `DECIMAL(12,2)`                  No ---                     Original/base
                                                                                 price.

  `discount_price`   `DECIMAL(12,2)`                 Yes ---                     Optional
                                                                                 discounted
                                                                                 price.

  `valid_from`       `TIMESTAMP`                     Yes ---                     Start of the
                                                                                 price validity
                                                                                 period.

  `valid_to`         `TIMESTAMP`                     Yes ---                     End of the
                                                                                 price validity
                                                                                 period.

  `updated_at`       `TIMESTAMP`                     Yes `CURRENT_TIMESTAMP`,    Last update
                                                         auto-updates            time for the
                                                                                 price row.
  ----------------------------------------------------------------------------------------------

### 3.7 `components`

**Purpose:** Master list of components that can be associated with
products.

  ----------------------------------------------------------------------------------------
  Column        MySQL type                    Null Key / default    Description
  ------------- ---------------- ----------------- ---------------- ----------------------
  `id`          `BIGINT`                        No PK,              Unique component
                                                   AUTO_INCREMENT   identifier.

  `name`        `VARCHAR(150)`                  No ---              Component name.

  `type`        `VARCHAR(100)`                  No ---              Component
                                                                    classification/type.
  ----------------------------------------------------------------------------------------

### 3.8 `product_components`

**Purpose:** Associates products with components and records the
quantity of each component.

  ------------------------------------------------------------------------------------------
  Column           MySQL type                     Null Key / default     Description
  ---------------- ----------------- ----------------- ----------------- -------------------
  `id`             `BIGINT`                         No PK,               Unique
                                                       AUTO_INCREMENT    product-component
                                                                         relationship
                                                                         identifier.

  `product_id`     `BIGINT`                         No FK →              Product using the
                                                       `products.id`     component.

  `component_id`   `BIGINT`                         No FK →              Component
                                                       `components.id`   associated with the
                                                                         product.

  `quantity`       `DECIMAL(12,3)`                  No `1.000`           Quantity of the
                                                                         component used by
                                                                         the product.
  ------------------------------------------------------------------------------------------

### 3.9 `product_attributes`

**Purpose:** Stores flexible name/value attributes for products.

  -------------------------------------------------------------------------------------
  Column              MySQL type                    Null Key / default    Description
  ------------------- ---------------- ----------------- ---------------- -------------
  `id`                `BIGINT`                        No PK,              Unique
                                                         AUTO_INCREMENT   attribute row
                                                                          identifier.

  `product_id`        `BIGINT`                        No FK →             Product that
                                                         `products.id`    owns the
                                                                          attribute.

  `attribute_name`    `VARCHAR(150)`                  No ---              Attribute
                                                                          key/name,
                                                                          such as RAM
                                                                          or display.

  `attribute_value`   `VARCHAR(500)`                  No ---              Attribute
                                                                          value.
  -------------------------------------------------------------------------------------

### 3.10 `warehouses`

**Purpose:** Warehouse/location master data used by inventory records.

  -------------------------------------------------------------------------------
  Column        MySQL type                    Null Key / default    Description
  ------------- ---------------- ----------------- ---------------- -------------
  `id`          `BIGINT`                        No PK,              Unique
                                                   AUTO_INCREMENT   warehouse
                                                                    identifier.

  `name`        `VARCHAR(150)`                  No ---              Warehouse
                                                                    name.

  `city`        `VARCHAR(100)`                  No ---              City where
                                                                    the warehouse
                                                                    is located.
  -------------------------------------------------------------------------------

### 3.11 `inventory`

**Purpose:** Available quantity for a variant at a warehouse.

  --------------------------------------------------------------------------------------------
  Column                 MySQL type                 Null Key / default           Description
  ---------------------- ------------- ----------------- ----------------------- -------------
  `id`                   `BIGINT`                     No PK, AUTO_INCREMENT      Unique
                                                                                 inventory row
                                                                                 identifier.

  `variant_id`           `BIGINT`                     No FK →                    Variant whose
                                                         `product_variants.id`   stock is
                                                                                 recorded.

  `warehouse_id`         `BIGINT`                     No FK → `warehouses.id`    Warehouse
                                                                                 holding the
                                                                                 stock.

  `available_quantity`   `INT`                        No `0`                     Quantity
                                                                                 currently
                                                                                 available.

  `updated_at`           `TIMESTAMP`                 Yes `CURRENT_TIMESTAMP`,    Last update
                                                         auto-updates            time for the
                                                                                 inventory
                                                                                 row.
  --------------------------------------------------------------------------------------------

## 4. Flyway metadata table

### `flyway_schema_history`

This is an infrastructure table maintained by Flyway. It tracks which
database migrations have been applied. It is not part of the product
catalog and should not have catalog PATCH endpoints or be included in
the product search document.

  ---------------------------------------------------------------------------------------------
  Column             MySQL type                     Null Key / default         Description
  ------------------ ----------------- ----------------- --------------------- ----------------
  `installed_rank`   `INT`                            No PK                    Migration
                                                                               execution order.

  `version`          `VARCHAR(50)`                   Yes ---                   Version
                                                                               identifier of
                                                                               the migration;
                                                                               may be null for
                                                                               repeatable
                                                                               migrations.

  `description`      `VARCHAR(200)`                   No ---                   Human-readable
                                                                               migration
                                                                               description.

  `type`             `VARCHAR(20)`                    No ---                   Migration type.

  `script`           `VARCHAR(1000)`                  No ---                   Migration script
                                                                               name.

  `checksum`         `INT`                           Yes ---                   Checksum used by
                                                                               Flyway to
                                                                               validate
                                                                               migration
                                                                               consistency.

  `installed_by`     `VARCHAR(100)`                   No ---                   Database user
                                                                               that applied the
                                                                               migration.

  `installed_on`     `TIMESTAMP`                      No `CURRENT_TIMESTAMP`   Time the
                                                                               migration was
                                                                               installed.

  `execution_time`   `INT`                            No ---                   Migration
                                                                               execution
                                                                               duration
                                                                               recorded by
                                                                               Flyway.

  `success`          `TINYINT(1)`                     No ---                   Whether the
                                                                               migration
                                                                               succeeded.
  ---------------------------------------------------------------------------------------------

## 5. Relationship summary

  -------------------------------------------------------------------------------------------------
  Parent table         Child table            Relationship      Foreign key
  -------------------- ---------------------- ----------------- -----------------------------------
  `brands`             `products`             One brand can be  `products.brand_id`
                                              referenced by     
                                              many products;    
                                              product brand is  
                                              optional.         

  `categories`         `categories`           One category can  `categories.parent_id`
                                              have many child   
                                              categories.       

  `products`           `product_variants`     One product has   `product_variants.product_id`
                                              many variants.    

  `products`           `product_prices`       One product has   `product_prices.product_id`
                                              many price        
                                              records.          

  `product_variants`   `product_prices`       One variant can   `product_prices.variant_id`
                                              have many price   
                                              records; price's  
                                              variant is        
                                              optional.         

  `products`           `product_categories`   One product can   `product_categories.product_id`
                                              have many         
                                              category links.   

  `categories`         `product_categories`   One category can  `product_categories.category_id`
                                              have many product 
                                              links.            

  `products`           `product_components`   One product can   `product_components.product_id`
                                              have many         
                                              component links.  

  `components`         `product_components`   One component can `product_components.component_id`
                                              be used by many   
                                              product links.    

  `products`           `product_attributes`   One product can   `product_attributes.product_id`
                                              have many         
                                              attributes.       

  `product_variants`   `inventory`            One variant can   `inventory.variant_id`
                                              have inventory    
                                              rows across       
                                              warehouses.       

  `warehouses`         `inventory`            One warehouse can `inventory.warehouse_id`
                                              contain inventory 
                                              rows for many     
                                              variants.         
  -------------------------------------------------------------------------------------------------

## 6. Implications for PATCH APIs and search indexing

1.  **Keep MySQL as the source of truth.** PATCH requests update
    relational tables; OpenSearch is updated asynchronously later
    through CDC → Kafka → Search Indexer.
2.  **Use entity-specific API routes and request DTOs.** Do not accept
    arbitrary table names or column names from clients.
3.  **A row change does not always map to one product.** For example, a
    brand, category, or component update can affect multiple product
    search documents.
4.  **Resolve affected product IDs before rebuilding documents.** The
    indexer should use the changed entity and its relationships to find
    products, then load the latest complete product projection from
    MySQL.
5.  **Treat junction and dependent records as first-class update
    targets.** This includes product-category links, product-component
    links, prices, variants, attributes, and inventory.
6.  **Do not expose `flyway_schema_history` through catalog APIs.**

### Important schema observations

-   The implemented `products` table has **no `category_id` column**.
    Product-category membership is represented by `product_categories`.
-   Both `products` and `product_variants` have their own unique SKU
    columns.
-   A price can be linked to a product and optionally to a variant.
-   Inventory is variant-level and warehouse-specific.
-   The schema metadata supplied here reports the primary/unique markers
    and foreign keys shown above. It does not provide a complete
    index-definition listing, check constraints, or trigger definitions;
    those are intentionally not inferred in this document.
