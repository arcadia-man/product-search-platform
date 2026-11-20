# Product Search Platform — Plan & Design

## 1. Project Overview

Build a production-grade product search platform using **Java, Spring Boot, MySQL, Kafka, CDC, and OpenSearch**.

The system represents a product catalog whose transactional data is distributed across multiple relational tables. The existing approach searches the relational database using joins across these tables.

The new system introduces a dedicated search/read model in OpenSearch.

The relational database remains the **source of truth**.

OpenSearch becomes the **search-optimized read model**.

The system must continuously synchronize relevant product changes from MySQL to OpenSearch using **CDC → Kafka → Search Indexer**.

The project must be runnable locally and demonstrate:

- Relational database search
- OpenSearch-based search
- Autocomplete/suggestions
- Filtering
- Sorting
- Pagination
- Search relevance
- CDC
- Kafka-based synchronization
- Failure handling
- Retry/DLQ
- Idempotent indexing
- Metrics and observability
- Automated tests
- CI pipeline
- Production-oriented project structure

---

# 2. Goals

## Primary Goals

1. Learn and demonstrate hands-on OpenSearch.
2. Build the backend using Spring Boot.
3. Understand how a search/read model differs from a transactional database model.
4. Build a realistic product search system.
5. Demonstrate why searching across many relational tables can become unsuitable for a high-volume search workload.
6. Build a denormalized product search document.
7. Keep OpenSearch synchronized with MySQL.
8. Use CDC and Kafka for near-real-time synchronization.
9. Implement production-grade failure handling.
10. Add measurable observability and performance comparison.
11. Build the project with proper testing and CI/CD practices.

---

# 3. Non-Goals

The following are intentionally outside the initial scope:

- Kubernetes deployment
- Cloud deployment
- User authentication/authorization
- Payment
- Order management
- Real production frontend
- Complex recommendation systems
- Machine-learning based ranking
- Multi-region deployment

These can be future extensions.

---

# 4. High-Level Architecture

```text
                         ┌──────────────────────┐
                         │      Dummy HTML      │
                         │      Search UI       │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │   Spring Boot API    │
                         │                      │
                         │  /search             │
                         │  /suggestions        │
                         │  /search/db          │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │      OpenSearch      │
                         │                      │
                         │  Product Search      │
                         │      Index           │
                         └──────────────────────┘


                  SOURCE OF TRUTH / WRITE MODEL

                         ┌──────────────────────┐
                         │        MySQL         │
                         │                      │
                         │ ~10 relational       │
                         │ tables               │
                         └──────────┬───────────┘
                                    │
                                    ▼
                                  CDC
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │        Kafka         │
                         │                      │
                         │ product-change       │
                         │ events               │
                         └──────────┬───────────┘
                                    │
                                    ▼
                         ┌──────────────────────┐
                         │    Search Indexer    │
                         │                      │
                         │ Build search         │
                         │ projection           │
                         └──────────┬───────────┘
                                    │
                                    ▼
                              OpenSearch


                    OBSERVABILITY

              ┌─────────────────┐
              │   Prometheus    │
              └────────┬────────┘
                       │
                       ▼
              ┌─────────────────┐
              │     Grafana     │
              └─────────────────┘
```

---

# 5. Core Architectural Principle

The system follows a **CQRS-style read model** for search.

MySQL and OpenSearch do not have the same responsibility.

### MySQL

Responsible for:

- transactional data
- relationships
- source-of-truth state
- writes
- relational consistency

### OpenSearch

Responsible for:

- full-text search
- autocomplete
- filtering
- sorting
- relevance
- fast product retrieval

OpenSearch is **not** the source of truth.

If OpenSearch is lost, it must be possible to rebuild it from MySQL.

---

# 6. Repository Structure

Initial repository:

```text
product-search-platform/
│
├── product-service/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
│
├── search-indexer/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   └── test/
│   └── pom.xml
│
├── database/
│   ├── migrations/
│   └── seed/
│
├── frontend/
│   └── index.html
│
├── infrastructure/
│   ├── docker/
│   ├── kafka/
│   ├── opensearch/
│   └── monitoring/
│
├── docs/
│   ├── architecture.md
│   ├── search.md
│   ├── indexing.md
│   └── cdc.md
│
├── .github/
│   └── workflows/
│
├── docker-compose.yml
├── README.md
├── Makefile
└── PLAN_AND_DESIGN.md
```

`PLAN_AND_DESIGN.md` is the primary project design document.

---

# 7. Technology Stack

## Backend

- Java 21
- Spring Boot 4.x
- Spring Web
- Spring Data JPA
- Spring Kafka
- Spring Boot Actuator
- Micrometer

## Database

- MySQL 8+
- Flyway

## Search

- OpenSearch

## Messaging

- Apache Kafka

## CDC

- Debezium

## Monitoring

- Prometheus
- Grafana

## Testing

- JUnit
- Mockito
- Spring Boot Test
- Testcontainers

## Infrastructure

- Docker
- Docker Compose

## Frontend

- HTML
- CSS
- Vanilla JavaScript

## CI

- GitHub Actions

---

# 8. Relational Data Model

The database intentionally contains multiple related tables to represent a realistic catalog.

## 8.1 products

```text
products
--------
id
sku
name
description
brand_id
category_id
status
created_at
updated_at
```

## 8.2 product_variants

```text
product_variants
----------------
id
product_id
sku
color
size
material
status
created_at
updated_at
```

## 8.3 product_prices

```text
product_prices
--------------
id
product_id
variant_id
currency
price
discount_price
valid_from
valid_to
updated_at
```

## 8.4 categories

```text
categories
----------
id
name
parent_id
status
```

## 8.5 brands

```text
brands
------
id
name
status
```

## 8.6 components

```text
components
----------
id
name
type
```

## 8.7 product_components

```text
product_components
------------------
id
product_id
component_id
quantity
```

## 8.8 product_attributes

```text
product_attributes
------------------
id
product_id
attribute_name
attribute_value
```

## 8.9 inventory

```text
inventory
---------
id
variant_id
warehouse_id
available_quantity
updated_at
```

## 8.10 warehouses

```text
warehouses
----------
id
name
city
```

The exact columns can be refined during implementation, but the relationships above are fixed unless a concrete implementation problem requires a change.

---

# 9. Seed Data

The application must include a deterministic seed-data generator.

Initial target:

```text
Brands:              100+
Categories:          100+
Components:          500+
Warehouses:          20+
Products:            100,000+
Variants:            200,000+
Attributes:          500,000+
Prices:              200,000+
Inventory records:   200,000+
```

The seed system should support smaller development datasets as well.

Example:

```bash
./seed.sh --products 1000
./seed.sh --products 10000
./seed.sh --products 100000
```

The exact final scale will be validated based on local machine performance.

---

# 10. Existing/Legacy Search

Before implementing OpenSearch search, build a relational search endpoint.

```http
GET /api/v1/products/search/db
```

This endpoint represents the existing approach.

It should search using MySQL and joins across relevant tables.

Supported operations:

- keyword search
- category filter
- brand filter
- price range
- availability
- pagination
- sorting

The purpose is not to make the SQL implementation perfect.

The purpose is to establish a **baseline** against which OpenSearch can be compared.

---

# 11. Search Document

OpenSearch will contain a denormalized document representing the searchable product.

Example:

```json
{
  "productId": 1001,
  "sku": "IPHONE-17-256-BLK",
  "name": "Apple iPhone 17",
  "description": "Latest Apple smartphone",

  "brand": {
    "id": 10,
    "name": "Apple"
  },

  "category": {
    "id": 20,
    "name": "Smartphones"
  },

  "variants": [
    {
      "id": 501,
      "sku": "IPHONE-17-256-BLK",
      "color": "Black",
      "size": "256GB"
    }
  ],

  "price": {
    "amount": 79999,
    "currency": "INR"
  },

  "attributes": {
    "ram": "8GB",
    "display": "6.3 inch",
    "processor": "A19"
  },

  "available": true,

  "updatedAt": "2026-11-21T00:30:00Z"
}
```

The search document is **not a database dump**.

Only fields required for:

- search
- filtering
- sorting
- autocomplete
- result presentation

should be indexed.

---

# 12. OpenSearch Index

Initial index:

```text
products-v1
```

Application-facing alias:

```text
products-search
```

The application should query the alias rather than directly depending on the physical index.

Example:

```text
products-search
       ↓
products-v1
```

Future mapping changes can create:

```text
products-v2
```

and the alias can be switched after reindexing.

---

# 13. Search API

Endpoint:

```http
GET /api/v1/products/search
```

Example:

```http
GET /api/v1/products/search?q=iphone
```

Supported parameters:

```text
q
category
brand
minPrice
maxPrice
available
sort
page
size
```

Example:

```http
GET /api/v1/products/search
    ?q=iphone
    &brand=apple
    &category=smartphones
    &minPrice=50000
    &maxPrice=150000
    &available=true
    &sort=relevance
    &page=0
    &size=20
```

Response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

The response contract should remain independent of the OpenSearch response format.

---

# 14. Suggestion API

Endpoint:

```http
GET /api/v1/products/suggestions?q=iph
```

Example response:

```json
{
  "suggestions": [
    "iPhone 17",
    "iPhone 17 Pro",
    "iPhone 17 Pro Max"
  ]
}
```

The implementation will use OpenSearch autocomplete/search-as-you-type capabilities.

The exact analyzer strategy will be finalized during the OpenSearch implementation phase.

---

# 15. Search Relevance

Search should not simply perform exact matching.

Initial relevance priority:

```text
Product name
    ↓
SKU
    ↓
Brand
    ↓
Category
    ↓
Description
    ↓
Attributes
```

Example:

Searching:

```text
iphone 17
```

should prioritize:

```text
iPhone 17
iPhone 17 Pro
iPhone 17 Pro Max
```

over products that merely mention "iPhone 17" deep inside a description.

The relevance configuration will be implemented using OpenSearch mappings, analyzers, and query boosts.

---

# 16. Pagination

Initial API pagination:

```text
page
size
```

OpenSearch implementation should avoid unnecessarily retrieving huge result sets.

Default:

```text
size = 20
```

Maximum:

```text
size = 100
```

The API must validate pagination parameters.

Deep-pagination behavior will be explicitly evaluated.

If required for large result traversal, `search_after` will be introduced rather than relying indefinitely on large offsets.

---

# 17. Initial Indexing

A separate indexing mechanism must support rebuilding the OpenSearch index from MySQL.

Flow:

```text
MySQL
   ↓
Read product data
   ↓
Build search document
   ↓
Bulk indexing
   ↓
OpenSearch
```

This mechanism is required because OpenSearch is not the source of truth.

It must be possible to recreate the search index from the database.

---

# 18. CDC Pipeline

Continuous synchronization:

```text
MySQL
  │
  ▼
Debezium CDC
  │
  ▼
Kafka
  │
  ▼
Search Indexer
  │
  ▼
OpenSearch
```

CDC is responsible for detecting relevant database changes.

Kafka provides:

- buffering
- durability
- replay
- decoupling
- scaling
- consumer groups

---

# 19. Kafka Topic

Initial topic:

```text
product-changes
```

The event must contain enough information to determine what changed.

Conceptually:

```json
{
  "eventId": "uuid",
  "entityType": "PRODUCT",
  "entityId": 1001,
  "operation": "UPDATE",
  "changedAt": "2026-11-21T00:30:00Z"
}
```

The actual Debezium event format may contain additional metadata.

The indexer should not blindly couple the entire Debezium payload to the OpenSearch document.

---

# 20. Related Entity Changes

A product search document may depend on multiple tables.

For example:

```text
products
   ├── brands
   ├── categories
   ├── variants
   ├── prices
   ├── attributes
   └── inventory
```

Therefore, changing a related entity can require re-indexing affected products.

Example:

```text
Brand 10 changes
      ↓
Find affected products
      ↓
Rebuild product projections
      ↓
Update OpenSearch
```

This behavior must be explicitly implemented for the relationships included in the final search document.

---

# 21. Search Indexer

The Search Indexer is responsible for:

1. Consume Kafka events.
2. Determine affected product IDs.
3. Read required current data from MySQL.
4. Build the latest search projection.
5. Upsert the document into OpenSearch.
6. Record processing metrics.
7. Handle failures.
8. Support retry.
9. Send permanently failed events to DLQ.

The indexer must not blindly reconstruct the search document from incomplete CDC events if the current database state is required.

The database remains the authoritative source for rebuilding the latest projection.

---

# 22. Eventual Consistency

The system intentionally uses eventual consistency between MySQL and OpenSearch.

Example:

```text
T0
MySQL updated

T0 + Δ1
CDC captures change

T0 + Δ2
Kafka receives event

T0 + Δ3
Indexer processes event

T0 + Δ4
OpenSearch updated
```

The system should expose metrics to measure this synchronization delay.

---

# 23. Idempotency

Kafka events may be delivered more than once.

The indexing operation must therefore be idempotent.

The OpenSearch document ID will be:

```text
productId
```

Example:

```text
product 1001
     ↓
OpenSearch document ID = 1001
```

Repeated processing should update the same document rather than create duplicates.

Event-level deduplication may be added if required, but document-level idempotency is mandatory.

---

# 24. Retry and DLQ

Indexer failures must not silently lose events.

Initial strategy:

```text
Kafka event
    ↓
Indexer
    ↓
Failure
    ↓
Retry with backoff
    ↓
Retry
    ↓
Retry
    ↓
DLQ
```

The following must be configurable:

```text
maximum retry attempts
backoff
DLQ topic
```

Initial DLQ:

```text
product-changes.DLT
```

The project should expose metrics for:

- retry count
- failed events
- DLQ events

---

# 25. Ordering

Kafka partitioning must preserve ordering for events that affect the same product.

Initial partition key:

```text
productId
```

This ensures events for the same product are routed consistently.

The design must also consider the case where different related entities affect the same product.

---

# 26. Failure Scenarios

The system must be tested against:

### OpenSearch unavailable

```text
Kafka
 ↓
Indexer
 ↓
OpenSearch unavailable
 ↓
Retry
 ↓
Recovery
 ↓
Index successfully
```

### Kafka consumer restart

Events should continue processing after restart.

### Duplicate event

The same event processed twice must not create duplicate product documents.

### Invalid event

The event should be rejected and handled according to the retry/DLQ policy.

### Database temporarily unavailable

Indexer should retry rather than lose the event.

### OpenSearch indexing failure

Retry and eventually DLQ.

---

# 27. Index Rebuild

The project must support rebuilding the search index.

Example:

```text
MySQL
  ↓
Initial indexing job
  ↓
products-v2
  ↓
Validation
  ↓
Alias switch
  ↓
products-search → products-v2
```

The existing index should remain available while the new index is being built.

---

# 28. Observability

Spring Boot Actuator must be enabled.

Endpoints:

```text
/actuator/health
/actuator/metrics
/actuator/prometheus
```

Important metrics:

### Search

```text
search_requests_total
search_errors_total
search_latency
```

### Suggestions

```text
suggestion_requests_total
suggestion_latency
```

### Indexing

```text
index_events_received
index_events_processed
index_events_failed
index_latency
```

### Kafka

```text
consumer_lag
retry_count
dlq_count
```

### Synchronization

```text
db_to_opensearch_sync_latency
```

---

# 29. Monitoring

Prometheus collects application metrics.

Grafana displays:

```text
Search request rate
Search latency
Search errors
Indexer throughput
Indexer failures
Kafka consumer lag
Retry count
DLQ count
DB → OpenSearch synchronization delay
```

---

# 30. Performance Benchmark

The project must compare:

```text
MySQL relational search
vs
OpenSearch search
```

Measurements:

- average latency
- p95 latency
- p99 latency
- throughput
- error rate

The project must not claim a performance improvement before measurements are performed.

Example final report:

```text
                    MySQL       OpenSearch

Average latency       TBD          TBD
P95 latency           TBD          TBD
P99 latency           TBD          TBD
Requests/sec          TBD          TBD
```

The numbers will come from actual local benchmarks.

---

# 31. Testing Strategy

## Unit Tests

Test:

- search service
- query builder
- document mapper
- event mapper
- validation
- retry logic

## Integration Tests

Test:

```text
Spring Boot
+
MySQL
+
Kafka
+
OpenSearch
```

## End-to-End Test

Full flow:

```text
MySQL update
    ↓
CDC
    ↓
Kafka
    ↓
Indexer
    ↓
OpenSearch
    ↓
Search API
```

## Failure Tests

Test:

- OpenSearch failure
- Kafka restart
- duplicate event
- invalid event
- database failure
- retry exhaustion

Testcontainers should be used where practical.

---

# 32. API Validation

Invalid requests must return appropriate HTTP responses.

Examples:

```text
size > 100
invalid page
negative price
invalid sort
empty suggestion query
unsupported filter
```

The API must not expose internal OpenSearch exceptions directly to clients.

---

# 33. Error Response

Use a consistent error format.

Example:

```json
{
  "timestamp": "2026-11-21T00:30:00Z",
  "status": 400,
  "code": "INVALID_REQUEST",
  "message": "size must be between 1 and 100",
  "path": "/api/v1/products/search"
}
```

---

# 34. Configuration

Environment-specific configuration must not be hardcoded.

Examples:

```text
MYSQL_URL
MYSQL_USERNAME
MYSQL_PASSWORD

KAFKA_BOOTSTRAP_SERVERS

OPENSEARCH_URL

OPENSEARCH_USERNAME
OPENSEARCH_PASSWORD

INDEX_NAME

RETRY_MAX_ATTEMPTS
RETRY_BACKOFF
```

Secrets must never be committed to Git.

---

# 35. Docker Environment

Local development should be runnable using Docker Compose.

Required infrastructure:

```text
MySQL
Kafka
Kafka UI
OpenSearch
OpenSearch Dashboards
Prometheus
Grafana
```

The application services can either run locally from IntelliJ/Maven or inside Docker.

---

# 36. CI Pipeline

Pull Request:

```text
Checkout
   ↓
Compile
   ↓
Unit Tests
   ↓
Integration Tests
   ↓
Static checks
   ↓
Build
```

Main branch:

```text
Checkout
   ↓
Build
   ↓
Tests
   ↓
Docker Image
   ↓
Security Scan
   ↓
Publish
```

The initial CI implementation should remain simple and reliable.

---

# 37. Frontend

A minimal HTML interface will demonstrate:

- search box
- suggestions
- filters
- sorting
- pagination
- product results
- search latency

The frontend is not the focus of the project.

It exists to demonstrate the complete system.

---

# 38. Development Phases

Development must happen in the following order.

## Phase 1 — Repository & Infrastructure

Deliver:

- repository
- Maven projects
- Java 21
- Spring Boot
- Docker Compose
- MySQL
- Kafka
- OpenSearch
- basic README
- CI skeleton

---

## Phase 2 — Database

Deliver:

- Flyway migrations
- relational schema
- relationships
- seed generator
- sample data

---

## Phase 3 — Legacy Search

Deliver:

```text
GET /api/v1/products/search/db
```

Implement:

- joins
- filters
- sorting
- pagination

This establishes the baseline.

---

## Phase 4 — OpenSearch

Deliver:

- OpenSearch connection
- index mapping
- analyzers
- product document
- index creation
- bulk indexing
- alias

---

## Phase 5 — Search API

Deliver:

```text
GET /api/v1/products/search
GET /api/v1/products/suggestions
```

Implement:

- full-text search
- relevance
- filters
- sorting
- pagination
- suggestions

---

## Phase 6 — CDC + Kafka

Deliver:

```text
MySQL
 ↓
Debezium
 ↓
Kafka
```

Implement relevant product-change events.

---

## Phase 7 — Search Indexer

Deliver:

```text
Kafka
 ↓
Search Indexer
 ↓
MySQL
 ↓
Search Projection
 ↓
OpenSearch
```

Implement:

- event consumption
- affected-product resolution
- document building
- upsert
- idempotency

---

## Phase 8 — Reliability

Implement:

- retries
- backoff
- DLQ
- error handling
- consumer restart handling
- failure recovery

---

## Phase 9 — Observability

Implement:

- Actuator
- Micrometer
- Prometheus
- Grafana
- search metrics
- indexing metrics
- Kafka lag
- synchronization latency

---

## Phase 10 — Testing & Benchmarking

Implement:

- unit tests
- integration tests
- E2E tests
- failure tests
- performance comparison

---

## Phase 11 — Frontend & Demo

Build:

- search UI
- autocomplete
- filters
- pagination
- latency display

---

## Phase 12 — Production Cleanup

Before considering the project complete:

- configuration cleanup
- README
- architecture diagram
- API documentation
- failure documentation
- benchmark results
- CI verification
- Docker verification
- code cleanup

---

# 39. Definition of Done

The project is complete when the following flow works locally:

```text
1. Start infrastructure

2. Start Spring Boot services

3. Seed MySQL

4. Build initial OpenSearch index

5. Search through OpenSearch

6. Change product data in MySQL

7. CDC detects the change

8. Kafka receives the event

9. Search Indexer processes it

10. OpenSearch gets updated

11. Search API returns the new data

12. Metrics show the processing

13. Failure/retry/DLQ behavior can be demonstrated

14. Tests pass

15. CI passes
```

The final demo should be able to show:

```text
Relational DB search
        ↓
Why it becomes difficult
        ↓
OpenSearch search
        ↓
Fast search/read model
        ↓
Database change
        ↓
CDC
        ↓
Kafka
        ↓
Indexer
        ↓
OpenSearch automatically updated
        ↓
Metrics / monitoring
```

---

# 40. Engineering Principles

Throughout development:

### 1. MySQL is the source of truth.

Never treat OpenSearch as the primary database.

### 2. Search documents are projections.

Do not copy the entire database into OpenSearch.

### 3. Events must be safely replayable.

A failed event should be recoverable.

### 4. Indexing must be idempotent.

Repeated events must not corrupt search data.

### 5. Measure instead of assuming.

Performance claims must be backed by benchmarks.

### 6. Prefer simple production-grade solutions.

Do not introduce unnecessary infrastructure merely to make the architecture look complicated.

### 7. Every major architectural decision must have a reason.

The project should be explainable in an interview.

### 8. Build incrementally.

Do not implement CDC, Kafka, OpenSearch, metrics and frontend simultaneously.

Each phase must produce a working system before the next phase begins.

---

# 41. Final Learning Outcome

After completing this project, we should be able to confidently explain and implement:

```text
Spring Boot
Java
MySQL
JPA
OpenSearch
Full-text search
Autocomplete
Search relevance
Denormalization
Read models
CQRS concepts
CDC
Debezium
Kafka
Kafka consumer groups
Partitioning
Ordering
Idempotency
Retries
DLQ
Eventual consistency
Index rebuilding
Index aliases
Pagination
Performance benchmarking
Actuator
Micrometer
Prometheus
Grafana
Testcontainers
Docker
GitHub Actions
```

The project should demonstrate not just that we know these technologies individually, but that we understand **how they work together in a real backend system**.