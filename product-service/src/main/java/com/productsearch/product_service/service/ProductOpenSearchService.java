package com.productsearch.product_service.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOptions;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.productsearch.product_service.dto.ProductResponse;
import com.productsearch.product_service.dto.ProductSearchRequest;
import com.productsearch.product_service.dto.ProductSuggestionsResponse;
import com.productsearch.product_service.dto.SuggestionItem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductOpenSearchService {

    private static final int MAX_SUGGESTIONS = 10;

    private final OpenSearchClient client;

    @Value("${opensearch.index:products}")
    private String indexName;

    public Page<ProductResponse> search(ProductSearchRequest request, Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), 100) : 20;
        int from = page * size;

        BoolQuery.Builder boolQuery = new BoolQuery.Builder();

        // 1. Keyword search with field boosts
        String kw = clean(request.keyword());
        if (kw != null) {
            boolQuery.must(m -> m
                    .multiMatch(mm -> mm
                            .query(kw)
                            .fields(
                                    "name^3.0",
                                    "sku^2.5",
                                    "brand^2.0",
                                    "categories^1.5",
                                    "attributes.value^1.2",
                                    "description^1.0"
                            )
                    )
            );
        }

        // 2. Specific field matches
        String name = clean(request.name());
        if (name != null) {
            boolQuery.must(m -> m.match(t -> t.field("name").query(FieldValue.of(name))));
        }

        String desc = clean(request.description());
        if (desc != null) {
            boolQuery.must(m -> m.match(t -> t.field("description").query(FieldValue.of(desc))));
        }

        // 3. Filters
        String brand = clean(request.brand());
        if (brand != null) {
            boolQuery.filter(f -> f.term(t -> t.field("brand").value(FieldValue.of(brand))));
        }

        if (request.brandId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("brandDetail.id").value(FieldValue.of(request.brandId()))));
        }

        String cat = clean(request.category());
        if (cat != null) {
            boolQuery.filter(f -> f.term(t -> t.field("categories").value(FieldValue.of(cat))));
        }

        if (request.categoryId() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("categoryDetails.id").value(FieldValue.of(request.categoryId()))));
        }

        String sku = clean(request.sku());
        if (sku != null) {
            boolQuery.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t.field("sku").value(FieldValue.of(sku))))
                    .should(s -> s.term(t -> t.field("variants.sku").value(FieldValue.of(sku))))
            ));
        }

        String attr = clean(request.attribute());
        if (attr != null) {
            boolQuery.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t.field("attributes.name").value(FieldValue.of(attr))))
                    .should(s -> s.match(m -> m.field("attributes.value").query(FieldValue.of(attr))))
            ));
        }

        if (request.minPrice() != null || request.maxPrice() != null) {
            boolQuery.filter(f -> f.range(r -> {
                r.field("price");
                if (request.minPrice() != null) {
                    r.gte(JsonData.of(request.minPrice().doubleValue()));
                }
                if (request.maxPrice() != null) {
                    r.lte(JsonData.of(request.maxPrice().doubleValue()));
                }
                return r;
            }));
        }

        if (request.available() != null) {
            boolQuery.filter(f -> f.term(t -> t.field("available").value(FieldValue.of(request.available()))));
        }

        boolean hasCriteria = kw != null || name != null || desc != null
                || brand != null || cat != null || sku != null || attr != null
                || request.brandId() != null || request.categoryId() != null
                || request.minPrice() != null || request.maxPrice() != null || request.available() != null;

        Query finalQuery;
        if (hasCriteria) {
            finalQuery = Query.of(q -> q.bool(boolQuery.build()));
        } else {
            finalQuery = Query.of(q -> q.matchAll(ma -> ma));
        }

        List<SortOptions> sortList = buildSort(pageable, kw != null);

        SearchRequest searchRequest = new SearchRequest.Builder()
                .index(indexName)
                .query(finalQuery)
                .from(from)
                .size(size)
                .sort(sortList)
                .build();

        try {
            SearchResponse<ProductResponse> response = client.search(searchRequest, ProductResponse.class);

            long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
            List<ProductResponse> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();

            return new PageImpl<>(products, pageable, totalHits);
        } catch (IOException e) {
            log.error("OpenSearch query error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to query OpenSearch: " + e.getMessage(), e);
        }
    }

    public ProductSuggestionsResponse getSuggestions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ProductSuggestionsResponse(Collections.emptyList());
        }

        String kw = keyword.trim().toLowerCase();
        String pattern = containsPattern(kw);

        SearchRequest searchRequest = new SearchRequest.Builder()
                .index(indexName)
                .size(20)
                .query(q -> q.bool(b -> b
                        .should(s -> s.wildcard(w -> w
                                .field("brand")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.wildcard(w -> w
                                .field("categories")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.wildcard(w -> w
                                .field("name")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.matchPhrasePrefix(m -> m.field("name").query(kw)))
                        .should(s -> s.wildcard(w -> w
                                .field("sku")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.wildcard(w -> w
                                .field("attributes.value")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.matchPhrasePrefix(m -> m.field("attributes.value").query(kw)))
                        .should(s -> s.wildcard(w -> w
                                .field("description")
                                .value(pattern)
                                .caseInsensitive(true)))
                        .should(s -> s.matchPhrasePrefix(m -> m.field("description").query(kw)))
                ))
                .build();

        try {
            SearchResponse<ProductResponse> response = client.search(searchRequest, ProductResponse.class);
            List<SuggestionItem> result = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            Pattern wordPattern = Pattern.compile("(?i)\\b([a-zA-Z0-9_-]*" + Pattern.quote(kw) + "[a-zA-Z0-9_-]*)\\b");

            for (Hit<ProductResponse> hit : response.hits().hits()) {
                ProductResponse p = hit.source();
                if (p == null) continue;

                // 1. Brand match
                if (p.brand() != null && p.brand().toLowerCase().contains(kw)) {
                    addSuggestion(result, seen, "brand", p.brand());
                }

                // 2. Category match
                if (p.categories() != null) {
                    for (String c : p.categories()) {
                        if (c.toLowerCase().contains(kw)) {
                            addSuggestion(result, seen, "category", c);
                        }
                    }
                }

                // 3. Name match
                if (p.name() != null && p.name().toLowerCase().contains(kw)) {
                    addSuggestion(result, seen, "name", p.name());
                }

                // 4. SKU match
                if (p.sku() != null && p.sku().toLowerCase().contains(kw)) {
                    addSuggestion(result, seen, "sku", p.sku());
                }

                // 5. Attribute match
                if (p.attributes() != null) {
                    for (var attr : p.attributes()) {
                        if (attr.value() != null && attr.value().toLowerCase().contains(kw)) {
                            addSuggestion(result, seen, "attribute", attr.value());
                        }
                    }
                }

                // 6. Description match (extract token, e.g. "jewellery")
                if (p.description() != null && p.description().toLowerCase().contains(kw)) {
                    Matcher matcher = wordPattern.matcher(p.description());
                    if (matcher.find()) {
                        String word = matcher.group(1).trim();
                        if (word.length() >= 2) {
                            addSuggestion(result, seen, "description", word);
                        }
                    }
                }

                if (result.size() >= MAX_SUGGESTIONS) {
                    break;
                }
            }

            return new ProductSuggestionsResponse(result);
        } catch (IOException e) {
            log.error("OpenSearch suggestion query failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to query OpenSearch suggestions", e);
        }
    }

    private void addSuggestion(List<SuggestionItem> list, Set<String> seen, String key, String value) {
        if (list.size() >= MAX_SUGGESTIONS) return;
        String val = value.trim();
        String dedupKey = key + ":" + val.toLowerCase();
        if (seen.add(dedupKey)) {
            list.add(SuggestionItem.of(key, val));
        }
    }

    private String containsPattern(String keyword) {
        String escaped = keyword
                .replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("?", "\\?");
        return "*" + escaped + "*";
    }

    private List<SortOptions> buildSort(Pageable pageable, boolean hasKeyword) {
        if (pageable.getSort().isSorted()) {
            List<SortOptions> options = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                SortOrder sortOrder = order.isAscending() ? SortOrder.Asc : SortOrder.Desc;
                String prop = order.getProperty();
                if ("name".equalsIgnoreCase(prop)) {
                    options.add(new SortOptions.Builder().field(f -> f.field("name.keyword").order(sortOrder)).build());
                } else if ("price".equalsIgnoreCase(prop)) {
                    options.add(new SortOptions.Builder().field(f -> f.field("price").order(sortOrder)).build());
                } else if ("createdAt".equalsIgnoreCase(prop)) {
                    options.add(new SortOptions.Builder().field(f -> f.field("createdAt").order(sortOrder)).build());
                }
            });
            if (!options.isEmpty()) return options;
        }

        if (hasKeyword) {
            return List.of(new SortOptions.Builder().score(s -> s.order(SortOrder.Desc)).build());
        }

        return List.of(new SortOptions.Builder().field(f -> f.field("createdAt").order(SortOrder.Desc)).build());
    }

    private String clean(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
