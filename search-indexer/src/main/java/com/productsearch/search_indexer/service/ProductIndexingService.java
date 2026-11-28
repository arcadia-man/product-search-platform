package com.productsearch.search_indexer.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.opensearch.client.opensearch.indices.update_aliases.Action;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.productsearch.search_indexer.document.ProductDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductIndexingService {

    private final ProductProjectionReader productProjectionReader;
    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index:products}")
    private String indexName;

    @Value("${search-indexer.batch-size:100}")
    private int batchSize;

    public synchronized int rebuildAllProducts() throws IOException {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalStateException("search-indexer.batch-size must be between 1 and 1000");
        }

        String rebuildIndex = indexName + "-rebuild-" + UUID.randomUUID().toString().replace("-", "");
        log.info("Starting full database sync into temporary OpenSearch index '{}'", rebuildIndex);

        int syncedProducts;
        try {
            createProductIndex(rebuildIndex);
            syncedProducts = loadAllProducts(rebuildIndex);
        } catch (IOException | RuntimeException e) {
            deleteTemporaryIndex(rebuildIndex, e);
            throw e;
        }

        Set<String> previousIndices;
        try {
            previousIndices = replaceSearchAlias(rebuildIndex);
        } catch (IOException | RuntimeException e) {
            deleteTemporaryIndex(rebuildIndex, e);
            throw e;
        }

        for (String previousIndex : previousIndices) {
            openSearchClient.indices().delete(delete -> delete.index(previousIndex));
        }

        log.info("Full database sync completed. Indexed {} products into '{}'", syncedProducts, indexName);
        return syncedProducts;
    }

    public void index(ProductDocument product) throws IOException {
        openSearchClient.index(request -> request
                .index(indexName)
                .id(product.id().toString())
                .document(product));
    }

    public void delete(long productId) throws IOException {
        openSearchClient.delete(request -> request
                .index(indexName)
                .id(Long.toString(productId)));
    }

    public void requireExistingSearchIndex() throws IOException {
        if (!openSearchClient.indices().exists(exists -> exists.index(indexName)).value()) {
            throw new IllegalStateException(
                    "OpenSearch index '" + indexName + "' is missing; enable a full sync before CDC consumption");
        }
    }

    private int loadAllProducts(String targetIndex) throws IOException {
        long lastId = 0;
        int totalSynced = 0;

        while (true) {
            List<ProductDocument> products = productProjectionReader.findProductsAfterId(lastId, batchSize);
            if (products.isEmpty()) {
                return totalSynced;
            }

            BulkRequest.Builder bulkRequest = new BulkRequest.Builder();
            for (ProductDocument product : products) {
                bulkRequest.operations(operation -> operation
                        .index(index -> index
                                .index(targetIndex)
                                .id(product.id().toString())
                                .document(product)));
            }

            BulkResponse response = openSearchClient.bulk(bulkRequest.build());
            if (response.errors()) {
                throw new IllegalStateException("Bulk indexing failed: " + response.items());
            }

            totalSynced += products.size();
            lastId = products.getLast().id();
            log.info("Indexed {} products into '{}'", totalSynced, targetIndex);
        }
    }

    private void createProductIndex(String targetIndex) throws IOException {
        openSearchClient.indices().create(create -> create
                .index(targetIndex)
                .mappings(mappings -> mappings
                        .properties("id", property -> property.long_(value -> value))
                        .properties("name", property -> property.text(text -> text
                                .fields("keyword", field -> field.keyword(keyword -> keyword.ignoreAbove(256)))))
                        .properties("slug", property -> property.keyword(keyword -> keyword))
                        .properties("description", property -> property.text(text -> text))
                        .properties("sku", property -> property.keyword(keyword -> keyword))
                        .properties("brand", property -> property.keyword(keyword -> keyword))
                        .properties("categories", property -> property.keyword(keyword -> keyword))
                        .properties("price", property -> property.double_(value -> value))
                        .properties("status", property -> property.keyword(keyword -> keyword))
                        .properties("available", property -> property.boolean_(value -> value))
                        .properties("createdAt", property -> property.date(date -> date))
                        .properties("updatedAt", property -> property.date(date -> date))
                        .properties("variants", property -> property.object(object -> object
                                .properties("sku", field -> field.keyword(keyword -> keyword))))
                        .properties("attributes", property -> property.object(object -> object
                                .properties("name", field -> field.keyword(keyword -> keyword))
                                .properties("value", field -> field.text(text -> text))))
                )
        );
    }

    private Set<String> replaceSearchAlias(String newIndex) throws IOException {
        Set<String> previousIndices = new HashSet<>();
        boolean resourceExists = openSearchClient.indices().exists(exists -> exists.index(indexName)).value();
        boolean existingConcreteIndex = false;

        if (resourceExists) {
            try {
                previousIndices.addAll(openSearchClient.indices()
                        .getAlias(alias -> alias.name(indexName))
                        .result()
                        .keySet());
            } catch (OpenSearchException e) {
                if (e.status() != 404) {
                    throw e;
                }
                existingConcreteIndex = true;
            }
        }

        List<Action> actions = new ArrayList<>();
        if (existingConcreteIndex) {
            actions.add(new Action.Builder()
                    .removeIndex(remove -> remove.index(indexName))
                    .build());
        } else {
            for (String previousIndex : previousIndices) {
                actions.add(new Action.Builder()
                        .remove(remove -> remove.index(previousIndex).alias(indexName))
                        .build());
            }
        }

        actions.add(new Action.Builder()
                .add(add -> add.index(newIndex).alias(indexName).isWriteIndex(true))
                .build());
        var response = openSearchClient.indices().updateAliases(update -> update.actions(actions));
        if (!response.acknowledged()) {
            throw new IllegalStateException("OpenSearch did not acknowledge the product index alias switch");
        }

        if (existingConcreteIndex) {
            previousIndices.clear();
        }
        return previousIndices;
    }

    private void deleteTemporaryIndex(String index, Exception originalFailure) {
        try {
            openSearchClient.indices().delete(delete -> delete.index(index));
        } catch (IOException | RuntimeException cleanupFailure) {
            originalFailure.addSuppressed(cleanupFailure);
        }
    }
}
