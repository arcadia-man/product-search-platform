# Performance Benchmark

## Status

No benchmark results or dedicated benchmark harness are present in the
repository. Do not infer a MySQL/OpenSearch speedup from functional tests.
There are no custom search-latency or synchronization-duration metrics, and no
Prometheus registry or Grafana dashboards are configured. Indexer progress and
errors are available in application logs.

## Comparison protocol

Compare the same catalog snapshot and equivalent search criteria using:

- MySQL: `GET /api/v1/products/search/db`
- OpenSearch: `GET /api/v1/products/search/os`

These separate routes establish the comparison baseline; they do not imply
that a performance result has already been measured.
Keep the query, filters, page size, result count, request rate, and response
handling identical. Record DB and OpenSearch versions, host resources, product
count, index state, page size, concurrency, warm-up, and test duration. Measure
search separately from initial sync and CDC lag.

Report at least:

| Metric | MySQL | OpenSearch |
|---|---:|---:|
| Requests and duration | Not measured | Not measured |
| Throughput (requests/second) | Not measured | Not measured |
| Latency p50 / p95 / p99 | Not measured | Not measured |
| Error rate | Not measured | Not measured |
| Result-count parity | Not measured | Not measured |

Run repeated trials after a warm-up and report the query set and environment
with the results. Test suggestions separately because OpenSearch uses
leading-wildcard queries. Include indexing/rebuild duration and CDC lag as
separate pipeline measurements; they are not search latency.
