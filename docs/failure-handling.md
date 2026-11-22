# Failure Handling

## CDC consumer

| Condition | Current behavior |
|---|---|
| OpenSearch or projection reload fails | Listener throws; record is not acknowledged |
| Retryable processing failure | Fixed 1-second delay; 3 retries by default |
| Retries exhausted | Publish original record to `product-changes.DLT`, partition 0 |
| DLQ publish fails | Error is surfaced; recovered record is not committed |
| Duplicate/replayed event | Product ID is the document `_id`; upsert is idempotent |
| Deleted product | Reload finds no current row; indexer deletes its document |
| Null-value tombstone | Acknowledged without a projection update |

Consumer auto-commit is disabled. Acknowledgment mode is `MANUAL_IMMEDIATE`;
the listener acknowledges only after processing succeeds. Listener concurrency
defaults to 2, `max.poll.records` to 50, and `max.poll.interval.ms` to 600000.
Processing is synchronous, so a slow OpenSearch write slows consumption rather
than growing an unbounded in-memory queue.

The consumer logs the topic, partition, and offset for processing failures.
Full-sync batch progress/completion and product-service OpenSearch
search/suggestion errors are also logged. Actuator is a dependency, but
custom retry counts, synchronization-latency, and search-latency metrics are
not configured.

The dead-letter topic must have partition 0 available. A failed DLT publish is
not treated as successful recovery. The repository does not include an
automated DLT replay command.

## Full rebuild and alias cutover

- Index creation or bulk-index failure: the indexer attempts to remove the
  temporary rebuild index; the alias is not switched.
- Alias update failure: the rebuild fails and temporary-index cleanup is
  attempted. If the request outcome is ambiguous (for example, OpenSearch
  applied the action but the response was lost), inspect the alias before
  retrying; cleanup may have removed the index the alias now references.
- Successful alias update: the new index is active. The indexer then deletes
  the previous physical index. If that cleanup fails, the new alias is already
  active and an old index may remain.
- First rebuild from a concrete index named `products`: the alias operation
  replaces that concrete index with the alias.

Failures propagate from the runner; full sync does not silently report
success. Fix the source/cluster issue and run the full sync again.

## Recovery

1. Check indexer logs, Kafka consumer group lag, and the DLT for the failed
   record.
2. Correct the underlying MySQL, connector, Kafka, or OpenSearch issue.
3. Use the existing operational tooling to replay a DLT record if the recovery
   is isolated to that event; replay is not automated by this repository.
4. If index completeness is uncertain, run a full sync from MySQL. The rebuild
   replaces the active alias only after indexing succeeds.

Full sync and CDC are controlled by indexer configuration, not HTTP endpoints.
See [cdc-pipeline](cdc-pipeline.md) for startup settings and
[search-engineering](search-engineering.md) for index mapping and alias
details.
