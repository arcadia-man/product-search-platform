# Product Search Platform — OpenSearch Index Design

> **Baseline Architecture & Schema Specification**  
> **Source of Truth:** MySQL `product_search` database.  
> **Search Engine:** OpenSearch 3.10.x.  
> **Index Alias:** `products`.  
> **Target Consumer:** `product-service` (Read Model for Search & Suggestions).  
> **Target Producer:** `search-indexer` (Batch Pipeline & CDC Consumer).

---

## 1. Executive Summary & Design Goals

The OpenSearch search cluster acts as a high-performance **denormalized read model** designed to serve sub-50ms catalog searches, faceted filtering, and real-time multi-dimensional suggestions. 

### Why a Denormalized Read Model?
In MySQL, assembling a complete product search card requires joining across 11 relational tables (`products`, `brands`, `categories`, `product_categories`, `product_variants`, `inventory`, `warehouses`, `product_prices`, `product_attributes`, `components`, `product_components`). Even with indexed foreign keys and JPA `@BatchSize`, high concurrency search queries with free-text searches (`LIKE '%keyword%'`) cause severe database locks and high CPU utilization.

OpenSearch solves this by storing each product as a self-contained, pre-aggregated document:
1. **Single-Hop Document Retrieval:** All variant SKUs, warehouses, inventory counts, prices, attributes, and categories are embedded directly in the document.
2. **Inverted Indexes & BM25 Scoring:** Full-text matching across multiple fields with relevance boosting instead of expensive full table scans.
3. **Zero-Downtime Index Swapping:** OpenSearch aliases decouple read traffic from full re-indexing operations.

---

## 2. Document Hierarchy & Model Diagram

```mermaid
classDiagram
    class ProductDocument {
        +Long id
        +String name
        +String slug
        +String description
        +String sku
        +String brand
        +BigDecimal price
        +String status
        +Boolean available
        +LocalDateTime createdAt
        +LocalDateTime updatedAt
    }

    class BrandDetail {
        +Long id
        +String name
        +String slug
    }

    class CategoryDetail {
        +Long id
        +String name
        +String slug
    }

    class Variant {
        +Long id
        +String sku
        +String color
        +String size
        +String material
        +String status
    }

    class Inventory {
        +Long id
        +Long warehouseId
        +String warehouseName
        +String warehouseCity
        +Integer availableQuantity
    }

    class Price {
        +Long id
        +Long variantId
        +String variantSku
        +String currency
        +BigDecimal price
        +BigDecimal discountPrice
        +LocalDateTime validFrom
        +LocalDateTime validTo
    }

    class Attribute {
        +Long id
        +String name
        +String value
    }

    class Component {
        +Long id
        +String name
        +String type
        +BigDecimal quantity
    }

    ProductDocument *-- BrandDetail : "1:1 brandDetail"
    ProductDocument *-- "0..*" CategoryDetail : "1:N categoryDetails"
    ProductDocument *-- "0..*" Variant : "1:N variants"
    Variant *-- "0..*" Inventory : "1:N inventory"
    ProductDocument *-- "0..*" Price : "1:N prices"
    ProductDocument *-- "0..*" Attribute : "1:N attributes"
    ProductDocument *-- "0..*" Component : "1:N components"
```

---

## 3. OpenSearch Mapping Definition

Below is the definitive index mapping applied to concrete indices (e.g. `products-rebuild-<uuid>`) managed behind the `products` alias:

```json
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 1,
      "refresh_interval": "1s",
      "analysis": {
        "analyzer": {
          "catalog_text_analyzer": {
            "type": "standard",
            "stopwords": "_english_"
          }
        }
      }
    }
  },
  "mappings": {
    "properties": {
      "id": { "type": "long" },
      "name": {
        "type": "text",
        "analyzer": "catalog_text_analyzer",
        "fields": {
          "keyword": {
            "type": "keyword",
            "ignore_above": 256
          }
        }
      },
      "slug": { "type": "keyword" },
      "description": {
        "type": "text",
        "analyzer": "catalog_text_analyzer"
      },
      "sku": { "type": "keyword" },
      "brand": {
        "type": "keyword",
        "fields": {
          "text": { "type": "text" }
        }
      },
      "brandDetail": {
        "properties": {
          "id": { "type": "long" },
          "name": { "type": "keyword" },
          "slug": { "type": "keyword" }
        }
      },
      "categories": { "type": "keyword" },
      "categoryDetails": {
        "properties": {
          "id": { "type": "long" },
          "name": { "type": "keyword" },
          "slug": { "type": "keyword" }
        }
      },
      "price": { "type": "double" },
      "status": { "type": "keyword" },
      "available": { "type": "boolean" },
      "variants": {
        "properties": {
          "id": { "type": "long" },
          "sku": { "type": "keyword" },
          "color": { "type": "keyword" },
          "size": { "type": "keyword" },
          "material": { "type": "keyword" },
          "status": { "type": "keyword" },
          "inventory": {
            "properties": {
              "id": { "type": "long" },
              "warehouseId": { "type": "long" },
              "warehouseName": { "type": "keyword" },
              "warehouseCity": { "type": "keyword" },
              "availableQuantity": { "type": "integer" }
            }
          }
        }
      },
      "prices": {
        "properties": {
          "id": { "type": "long" },
          "variantId": { "type": "long" },
          "variantSku": { "type": "keyword" },
          "currency": { "type": "keyword" },
          "price": { "type": "double" },
          "discountPrice": { "type": "double" },
          "validFrom": { "type": "date" },
          "validTo": { "type": "date" }
        }
      },
      "attributes": {
        "properties": {
          "id": { "type": "long" },
          "name": { "type": "keyword" },
          "value": {
            "type": "text",
            "fields": {
              "keyword": { "type": "keyword", "ignore_above": 256 }
            }
          }
        }
      },
      "components": {
        "properties": {
          "id": { "type": "long" },
          "name": { "type": "keyword" },
          "type": { "type": "keyword" },
          "quantity": { "type": "double" }
        }
      },
      "createdAt": { "type": "date" },
      "updatedAt": { "type": "date" }
    }
  }
}
```

---

## 4. MySQL to OpenSearch Field Mapping Dictionary

| OpenSearch Field | OS Field Type | Source MySQL Table(s) | Source Column(s) | Transformation / Aggregation Rule |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `long` | `products` | `id` | Direct primary key mapping. Used as OpenSearch Document `_id`. |
| `name` | `text` + `keyword` | `products` | `name` | Analyzed for full-text search; `name.keyword` used for alphabetical sorting. |
| `slug` | `keyword` | `products` | `slug` | Direct string token. Exact match. |
| `description` | `text` | `products` | `description` | Analyzed full text. Used in multi-match and snippet suggestion extraction. |
| `sku` | `keyword` | `products` | `sku` | Base product SKU. Exact match. |
| `brand` | `keyword` | `brands` | `name` | Denormalized via `products.brand_id = brands.id`. Filter & aggregation. |
| `brandDetail` | `object` | `brands` | `id`, `name`, `slug` | Nested object with brand ID and slug for metadata display and ID filtering. |
| `categories` | `keyword[]` | `categories` | `name` | Denormalized array via `product_categories` junction table. |
| `categoryDetails`| `object[]` | `categories` | `id`, `name`, `slug` | Nested array with category ID, name, slug for structured category filtering. |
| `price` | `double` | `products` | `price` | Numerical value for range filtering (`minPrice`, `maxPrice`) and sorting. |
| `status` | `keyword` | `products` | `status` | Direct enum/string (`ACTIVE`, `INACTIVE`, etc.). |
| `available` | `boolean` | `inventory` | `available_quantity` | Calculated flag: `true` if any inventory item has `available_quantity > 0`. |
| `variants` | `object[]` | `product_variants` | `id`, `sku`, `color`, `size`, `material`, `status` | Embedded array of variants belonging to the product. |
| `variants.inventory` | `object[]` | `inventory`, `warehouses` | `available_quantity`, warehouse details | Embedded per-variant stock locations and warehouse counts. |
| `prices` | `object[]` | `product_prices` | `id`, `price`, `discount_price`, `currency`, validity dates | Embedded active and historical pricing schedules. |
| `attributes` | `object[]` | `product_attributes` | `attribute_name`, `attribute_value` | Embedded key-value specs (e.g. `Color: Space Gray`, `RAM: 16GB`). |
| `components` | `object[]` | `product_components`, `components` | `name`, `type`, `quantity` | Embedded bill of materials and component ingredients. |
| `createdAt` | `date` | `products` | `created_at` | ISO-8601 timestamp. Default sorting field. |
| `updatedAt` | `date` | `products` | `updated_at` | ISO-8601 timestamp. Used for CDC ordering and freshness tracking. |

---

## 5. Query Strategy & UX Parity

To ensure seamless feature parity with the MySQL backend (`demo-db.html`), the OpenSearch query engine (`ProductOpenSearchService`) implements the following search strategies:

### 5.1 Multi-Match Relevance Query
When a free-text `keyword` is supplied, OpenSearch evaluates relevance scores using BM25 across weighted fields:
- `name^3`: Highest boost on product title.
- `brand^2`: High boost on manufacturer/brand.
- `categories^2`: High boost on category names.
- `sku^2`: High boost on exact product or variant SKU.
- `attributes.value`: Matches specifications (e.g. "Titanium").
- `description`: Matches full body description text.

### 5.2 Structured Filter Criteria
Filters are executed as bool filter clauses (cached and score-neutral):
- **Brand Filter:** Term filter on `brand` or `brandDetail.id`.
- **Category Filter:** Term filter on `categories` or `categoryDetails.id`.
- **SKU Filter:** Should clause on `sku` and `variants.sku`.
- **Attribute Filter:** Match query on `attributes.value`.
- **Price Range Filter:** Range query on `price` (`gte` minPrice, `lte` maxPrice).
- **In-Stock Filter:** Term filter on `available: true`.

### 5.3 Multi-Dimension Suggestions
Autocomplete suggestions (`/api/v1/products/suggestions/os?q=...`) query OpenSearch with wildcard and prefix operators, aggregating up to 10 suggestions grouped into:
1. `brand`: `Brand: <Name>`
2. `category`: `Category: <Name>`
3. `name`: `Product: <Name>`
4. `sku`: `SKU: <SKU>`
5. `attribute`: `Attribute: <Value>`
6. `description`: `Description: <Snippet>`

---

## 6. Data Synchronization Pipeline Architecture

```mermaid
flowchart TD
    subgraph Persistence [MySQL 9.6 Single Source of Truth]
        DB[(product_search)]
        Binlog[MySQL Binlog (Row-based CDC)]
    end

    subgraph Pipeline [search-indexer Service (Port 8081)]
        Runner[PipelineStartupRunner (Config-driven)]
        SyncAPI[POST /api/v1/sync/full (API-driven)]
        Reader[ProductProjectionReader (Batch JDBC)]
        Indexer[ProductIndexingService (Bulk OpenSearch Client)]
        Consumer[ProductChangeConsumer (Kafka Listener)]
    end

    subgraph Streaming [Message Streaming Engine]
        Kafka[Kafka Topic: product-changes]
        DLT[Kafka Topic: product-changes.DLT]
    end

    subgraph SearchEngine [OpenSearch 3.10.x]
        Alias[Alias: products]
        IndexA[(products-rebuild-uuid)]
        IndexLive[(products-live)]
    end

    subgraph ClientLayer [product-service (Port 8080)]
        UI[Frontend UI (demo-os.html / index.html)]
        SearchSvc[ProductOpenSearchService (Read-Only)]
    end

    %% Full sync flow
    Runner -.->|1. Startup Trigger| Indexer
    SyncAPI -->|1. REST Trigger| Indexer
    Indexer -->|2. Batch Read| Reader
    Reader -->|3. Query Projection| DB
    Indexer -->|4. Bulk Index| IndexA
    Indexer -->|5. Atomic Alias Swap| Alias
    Alias -->|Points to| IndexA

    %% Real-time sync flow
    Binlog -.->|CDC Events| Kafka
    Kafka -->|CDC Records| Consumer
    Consumer -->|Lookup Projection| Reader
    Consumer -->|Upsert/Delete Document| Indexer
    Consumer -.->|Dead-Letter Queue| DLT

    %% Read queries
    UI -->|Search & Suggestions| SearchSvc
    SearchSvc -->|Query Inverted Index| Alias
```

### 6.1 Full Historical Sync
- **Trigger:** Either via `POST /api/v1/sync/full` or startup config `search-indexer.full-sync-on-startup=true`.
- **Implementation:** `search-indexer` creates a fresh index `products-rebuild-<uuid>`, applies the full mapping, streams batches of 100 products from MySQL via `ProductProjectionReader`, performs bulk indexing, and atomically switches the `products` alias to point to the new index while dropping the old index.
- **Zero Downtime:** Search traffic to `products` alias continues uninterrupted throughout the rebuild process.

### 6.2 Real-Time CDC Sync
- **Trigger:** Relational data updates (e.g. `PATCH /api/v1/catalog/{entity}`).
- **Mechanism:** Debezium captures binlog changes from any catalog table (`products`, `inventory`, `brands`, `prices`, etc.) and produces events to Kafka topic `product-changes`.
- **Consumer:** `ProductChangeConsumer` resolves affected product IDs, queries the latest projection from MySQL via `ProductProjectionReader`, and updates or deletes the document in OpenSearch.
- **Reliability:** Manual Kafka offset commit only after OpenSearch document write succeeds; failures route to `product-changes.DLT`.

---

## 7. Sample Document Payload

```json
{
  "id": 1,
  "name": "Apple iPhone 15 Pro",
  "slug": "apple-iphone-15-pro",
  "description": "Apple flagship smartphone with A17 Pro chip, titanium design, and advanced camera system.",
  "sku": "IPHONE-15-PRO-BASE",
  "brand": "Apple",
  "brandDetail": {
    "id": 1,
    "name": "Apple",
    "slug": "apple"
  },
  "categories": [
    "Electronics",
    "Smartphones"
  ],
  "categoryDetails": [
    { "id": 1, "name": "Electronics", "slug": "electronics" },
    { "id": 2, "name": "Smartphones", "slug": "smartphones" }
  ],
  "price": 129900.00,
  "status": "ACTIVE",
  "available": true,
  "variants": [
    {
      "id": 101,
      "sku": "IPH15P-128-NAT",
      "color": "Natural Titanium",
      "size": "128GB",
      "material": "Titanium",
      "status": "ACTIVE",
      "inventory": [
        {
          "id": 1001,
          "warehouseId": 1,
          "warehouseName": "North Mumbai Fulfilment Center",
          "warehouseCity": "Mumbai",
          "availableQuantity": 25
        }
      ]
    }
  ],
  "prices": [
    {
      "id": 201,
      "variantId": 101,
      "variantSku": "IPH15P-128-NAT",
      "currency": "INR",
      "price": 129900.00,
      "discountPrice": 124900.00,
      "validFrom": "2026-01-01T00:00:00",
      "validTo": "2026-12-31T23:59:59"
    }
  ],
  "attributes": [
    { "id": 301, "name": "Processor", "value": "A17 Pro" },
    { "id": 302, "name": "Display", "value": "6.1-inch Super Retina XDR" }
  ],
  "components": [
    { "id": 401, "name": "Titanium Enclosure", "type": "CHASSIS", "quantity": 1.000 },
    { "id": 402, "name": "OLED Panel", "type": "DISPLAY", "quantity": 1.000 }
  ],
  "createdAt": "2026-01-10T10:00:00",
  "updatedAt": "2026-11-21T08:30:00"
}
```

---

## 8. Summary of Responsibilities

- **`product-service` (Port 8080):**
  - Serves static UI (`index.html`, `demo-db.html`, `demo-os.html`).
  - Implements relational search (`/api/v1/products/search/db`, `/api/v1/products/suggestions`).
  - Implements OpenSearch search (`/api/v1/products/search/os`, `/api/v1/products/suggestions/os`).
  - Exposes catalog testing update endpoint (`PATCH /api/v1/catalog/{entity}`).
  - **No pipeline or sync responsibilities.**

- **`search-indexer` (Port 8081):**
  - Pipeline & synchronization engine only.
  - Implements full sync from MySQL to OpenSearch (`POST /api/v1/sync/full` and startup runner).
  - Handles zero-downtime index alias creation and swapping.
  - Implements CDC consumer for real-time synchronization.
  - **No user-facing search or UI responsibilities.**
