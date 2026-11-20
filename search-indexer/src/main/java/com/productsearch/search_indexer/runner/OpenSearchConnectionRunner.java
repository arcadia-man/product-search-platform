package com.productsearch.search_indexer.runner;

import lombok.RequiredArgsConstructor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenSearchConnectionRunner implements CommandLineRunner {

    private final OpenSearchClient openSearchClient;

    @Override
    public void run(String... args) throws Exception {
        var info = openSearchClient.info();

        System.out.println("================================");
        System.out.println("OpenSearch connected successfully");
        System.out.println("Cluster: " + info.clusterName());
        System.out.println("Version: " + info.version().number());
        System.out.println("================================");
    }
}