package com.productsearch.product_service.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.productsearch.product_service.dto.ProductSuggestionsResponse;
import com.productsearch.product_service.dto.SuggestionItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSuggestionService {

    private static final int MAX_TOTAL_SUGGESTIONS = 10;

    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public ProductSuggestionsResponse getSuggestions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ProductSuggestionsResponse(List.of());
        }

        String kw = keyword.trim();
        String pattern = "%" + kw.toLowerCase() + "%";

        List<SuggestionItem> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        // 1. Brands
        addDistinctSuggestions(
                result, seen, "brand",
                "SELECT DISTINCT name FROM brands WHERE LOWER(name) LIKE ? ORDER BY name ASC LIMIT 3",
                pattern
        );

        // 2. Categories
        if (result.size() < MAX_TOTAL_SUGGESTIONS) {
            addDistinctSuggestions(
                    result, seen, "category",
                    "SELECT DISTINCT name FROM categories WHERE LOWER(name) LIKE ? ORDER BY name ASC LIMIT 3",
                    pattern
            );
        }

        // 3. Product Names
        if (result.size() < MAX_TOTAL_SUGGESTIONS) {
            addDistinctSuggestions(
                    result, seen, "name",
                    "SELECT DISTINCT name FROM products WHERE LOWER(name) LIKE ? ORDER BY name ASC LIMIT 5",
                    pattern
            );
        }

        // 4. Product SKUs
        if (result.size() < MAX_TOTAL_SUGGESTIONS) {
            addDistinctSuggestions(
                    result, seen, "sku",
                    "SELECT DISTINCT sku FROM products WHERE LOWER(sku) LIKE ? ORDER BY sku ASC LIMIT 2",
                    pattern
            );
        }

        // 5. Product Attributes
        if (result.size() < MAX_TOTAL_SUGGESTIONS) {
            addDistinctSuggestions(
                    result, seen, "attribute",
                    "SELECT DISTINCT attribute_value FROM product_attributes WHERE LOWER(attribute_value) LIKE ? ORDER BY attribute_value ASC LIMIT 3",
                    pattern
            );
        }

        // 6. Descriptions - extract matching token/snippet
        if (result.size() < MAX_TOTAL_SUGGESTIONS) {
            addDescriptionSuggestions(result, seen, kw, pattern);
        }

        return new ProductSuggestionsResponse(result);
    }

    private void addDistinctSuggestions(
            List<SuggestionItem> result,
            Set<String> seen,
            String key,
            String sql,
            String pattern) {
        int remaining = MAX_TOTAL_SUGGESTIONS - result.size();
        if (remaining <= 0) {
            return;
        }

        List<String> values = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> rs.getString(1),
                pattern
        );

        for (String val : values) {
            if (val == null || val.isBlank()) {
                continue;
            }
            String clean = val.trim();
            String dedupKey = key + ":" + clean.toLowerCase();
            if (seen.add(dedupKey)) {
                result.add(SuggestionItem.of(key, clean));
                if (result.size() >= MAX_TOTAL_SUGGESTIONS) {
                    break;
                }
            }
        }
    }

    private void addDescriptionSuggestions(
            List<SuggestionItem> result,
            Set<String> seen,
            String kw,
            String pattern) {
        int remaining = MAX_TOTAL_SUGGESTIONS - result.size();
        if (remaining <= 0) {
            return;
        }

        List<String> descriptions = jdbcTemplate.query(
                "SELECT description FROM products WHERE LOWER(description) LIKE ? LIMIT 10",
                (rs, rowNum) -> rs.getString("description"),
                pattern
        );

        Pattern wordPattern = Pattern.compile("(?i)\\b([a-zA-Z0-9_-]*" + Pattern.quote(kw) + "[a-zA-Z0-9_-]*)\\b");

        for (String desc : descriptions) {
            if (desc == null || desc.isBlank()) {
                continue;
            }
            Matcher matcher = wordPattern.matcher(desc);
            while (matcher.find()) {
                String token = matcher.group(1).trim();
                if (token.length() >= 2) {
                    String dedupKey = "description:" + token.toLowerCase();
                    if (seen.add(dedupKey)) {
                        result.add(SuggestionItem.of("description", token));
                        if (result.size() >= MAX_TOTAL_SUGGESTIONS) {
                            return;
                        }
                    }
                }
            }
        }
    }
}
