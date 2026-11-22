# Search Engineering

OpenSearch is a denormalized read model of the MySQL catalog. One document
represents one product; `_id` is the MySQL product ID. The document type is
[`ProductDocument.java`](../search-indexer/src/main/java/com/productsearch/search_indexer/document/ProductDocument.java).

## Product document model

```mermaid
classDiagram
    class ProductDocument {
        Long id
        String name
        String slug
        String description
        String sku
        String brand
        BrandDetail brandDetail
        List~String~ categories
        List~CategoryDetail~ categoryDetails
        BigDecimal price
        String status
        boolean available
        List~Variant~ variants
        List~Price~ prices
        List~Attribute~ attributes
        List~Component~ components
        LocalDateTime createdAt
        LocalDateTime updatedAt
    }
    class BrandDetail {
        Long id
        String name
        String slug
    }
    class CategoryDetail {
        Long id
        String name
        String slug
    }
    class Variant {
        Long id
        String sku
        String color
        String size
        String material
        String status
        List~Inventory~ inventory
    }
    class Inventory {
        Long id
        Long warehouseId
        String warehouseName
        String warehouseCity
        Integer availableQuantity
    }
    class Price {
        Long id
        Long variantId
        String variantSku
        String currency
        BigDecimal price
        BigDecimal discountPrice
        LocalDateTime validFrom
        LocalDateTime validTo
    }
    class Attribute {
        Long id
        String name
        String value
    }
    class Component {
        Long id
        String name
        String type
        BigDecimal quantity
    }
    ProductDocument "1" --> "0..1" BrandDetail
    ProductDocument "1" --> "0..*" CategoryDetail
    ProductDocument "1" --> "0..*" Variant
    ProductDocument "1" --> "0..*" Price
    ProductDocument "1" --> "0..*" Attribute
    ProductDocument "1" --> "0..*" Component
    Variant "1" --> "0..*" Inventory
```

`available` is true when any variant has positive inventory.
`ProductDocument.price` comes from `products.price`; `prices` contains the
separate currency- and optional variant-level rows. The full projection and
example document are in
[`ProductProjectionReader.java`](../search-indexer/src/main/java/com/productsearch/search_indexer/service/ProductProjectionReader.java)
and [`ProductDocument.java`](../search-indexer/src/main/java/com/productsearch/search_indexer/document/ProductDocument.java).

## Explicit mappings

| Field | Mapping | Use |
|---|---|---|
| `id` | `long` | Product identity |
| `name` | `text`, `keyword` subfield (`ignore_above: 256`) | Search, exact sort |
| `slug` | `keyword` | Exact lookup |
| `description` | `text` | Search and suggestions |
| `sku`, `brand`, `categories` | `keyword` | Exact filters and suggestions |
| `price` | `double` | Range filter and sort |
| `status` | `keyword` | Exact status |
| `available` | `boolean` | Availability filter |
| `createdAt`, `updatedAt` | `date` | Sort |
| `variants.sku` | `keyword` | Variant SKU filter |
| `attributes.name` | `keyword` | Attribute-name filter |
| `attributes.value` | `text` | Search and suggestions |

Other serialized fields, including `brandDetail`, `categoryDetails`, `prices`,
`components`, and `inventory`, are dynamically mapped. `variants` and
`attributes` are regular objects, not `nested` mappings. Add and test mappings
before querying dynamic fields as exact filters, sorts, or aggregations. The
explicit mappings are created in
[`ProductIndexingService.java`](../search-indexer/src/main/java/com/productsearch/search_indexer/service/ProductIndexingService.java).

No custom analyzer or edge-ngram mapping is configured. Text fields use
OpenSearch's default analyzer; keyword fields retain exact values.

## Query and ranking behavior

- `GET /api/v1/products/search/os` uses `keyword` for a boosted `multi_match`:
  `name^3`, `sku^2.5`, `brand^2`, `categories^1.5`,
  `attributes.value^1.2`, and `description^1`.
- Additional criteria are ANDed; brand/category/availability/SKU filters use
  exact terms, and price uses a numeric range.
- Supported filters include `brand`, `brandId`, `category`, `categoryId`,
  `sku`, `attribute`, `minPrice`, `maxPrice`, and `available`.
- `page` is zero-based; default page size is 20 and the service caps it at 100.
  The controller defaults to `createdAt` descending. Name and price sorting
  are supported; relevance scores are used when no recognized sort is present.
- Suggestions use case-insensitive contains wildcards and phrase-prefix
  matches across brand, category, name, SKU, attribute value, and description.
  The service reads up to 20 product hits and returns at most 10 deduplicated
  suggestions.

Contains suggestions use leading wildcards, which can be expensive on large
indices. Measure this query before scaling the catalog; no n-gram optimization
is currently configured.

## Index lifecycle

- Product-service queries the configured `products` index/alias.
- Full sync writes to `products-rebuild-<uuid>`, then switches the `products`
  alias after all bulk writes succeed.
- Mapping changes require a full rebuild into a new physical index.
- DB routes remain separate: `/api/v1/products/search/db` and
  `/api/v1/products/suggestions/db`.

See [database design](database-design.md),
[cdc-pipeline](cdc-pipeline.md), and
[failure handling](failure-handling.md).
