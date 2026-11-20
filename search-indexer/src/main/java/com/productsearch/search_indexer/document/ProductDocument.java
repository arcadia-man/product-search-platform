package com.productsearch.search_indexer.document;

import java.math.BigDecimal;
import java.util.List;

public record ProductDocument(
        Long id,
        String name,
        String description,
        String brand,
        List<String> categories,
        String sku,
        BigDecimal price,
        String status
) {}