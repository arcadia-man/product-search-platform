package com.productsearch.product_service.dto;

public record SuggestionItem(
        String key,
        String value,
        String displayLabel
) {
    public static SuggestionItem of(String key, String value) {
        return new SuggestionItem(key, value, key + ": " + value);
    }
}
