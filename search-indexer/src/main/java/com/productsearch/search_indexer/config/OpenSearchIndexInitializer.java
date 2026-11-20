package com.productsearch.search_indexer.config;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OpenSearchIndexInitializer implements CommandLineRunner {

    private final OpenSearchClient client;

    @Override
    public void run(String... args) throws Exception {

        String indexName = "products";

        boolean exists = client.indices().exists(e -> e.index(indexName)).value();

        if (!exists) {
            client.indices().create(c -> c
                    .index(indexName)
                    .mappings(m -> m
                            .properties("id", p -> p.long_(v -> v))
                            .properties("name", p -> p.text(t -> t))
                            .properties("description", p -> p.text(t -> t))
                            .properties("brand", p -> p.keyword(k -> k))
                            .properties("categories", p -> p.keyword(k -> k))
                            .properties("sku", p -> p.keyword(k -> k))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("status", p -> p.keyword(k -> k))));

            System.out.println("Created OpenSearch index: " + indexName);
        } else {
            System.out.println("OpenSearch index already exists: " + indexName);
        }
    }
}