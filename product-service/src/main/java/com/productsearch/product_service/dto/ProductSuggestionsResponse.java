package com.productsearch.product_service.dto;

import java.util.List;

public record ProductSuggestionsResponse(
        List<SuggestionItem> suggestions
) {
}
