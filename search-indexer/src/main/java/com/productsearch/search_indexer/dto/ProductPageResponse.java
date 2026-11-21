package com.productsearch.search_indexer.dto;

import java.util.List;

import com.productsearch.search_indexer.document.ProductDocument;

public record ProductPageResponse(
        List<ProductDocument> content,
        int totalPages,
        int number,
        boolean last
) {}