package com.productsearch.search_indexer.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.productsearch.search_indexer.dto.ProductPageResponse;

@Component
public class ProductServiceClient {

    private final RestClient restClient;

    public ProductServiceClient(
            RestClient.Builder builder,
            @Value("${product-service.base-url}") String baseUrl) {

        this.restClient = builder
                .baseUrl(baseUrl)
                .build();
    }

    public ProductPageResponse getProducts(int page, int size) {
        return restClient.get()
                .uri("/api/v1/products?page={page}&size={size}", page, size)
                .retrieve()
                .body(ProductPageResponse.class);
    }
}