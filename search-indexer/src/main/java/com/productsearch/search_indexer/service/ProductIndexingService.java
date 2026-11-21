package com.productsearch.search_indexer.service;

import java.io.IOException;
import java.util.List;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.BulkRequest;
import org.opensearch.client.opensearch.core.BulkResponse;
import org.springframework.stereotype.Service;

import com.productsearch.search_indexer.client.ProductServiceClient;
import com.productsearch.search_indexer.document.ProductDocument;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductIndexingService {

    private final ProductServiceClient productServiceClient;
    private final OpenSearchClient openSearchClient;

    public void indexAllProducts() throws IOException {

        int page = 0;
        int size = 100;
        boolean lastPage = false;

        while (!lastPage) {

            var response = productServiceClient.getProducts(page, size);
            List<ProductDocument> products = response.content();

            if (!products.isEmpty()) {

                BulkRequest.Builder bulkRequest = new BulkRequest.Builder();

                for (ProductDocument product : products) {
                    bulkRequest.operations(operation -> operation
                            .index(index -> index
                                    .index("products")
                                    .id(product.id().toString())
                                    .document(product)
                            )
                    );
                }

                BulkResponse bulkResponse =
                        openSearchClient.bulk(bulkRequest.build());

                if (bulkResponse.errors()) {
                    throw new IllegalStateException(
                            "Bulk indexing failed: " + bulkResponse.items()
                    );
                }
            }

            lastPage = response.last();
            page++;
        }
    }
}