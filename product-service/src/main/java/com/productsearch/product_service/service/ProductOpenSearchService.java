package com.productsearch.product_service.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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
    private static final int MAX_PAGE_SIZE = 100;

    private final OpenSearchClient openSearchClient;

    @Value("${opensearch.index:products}")
    private String indexName;

    public Page<ProductResponse> search(ProductSearchRequest request, Pageable pageable) {
        try {
            Query query = buildSearchQuery(request);
            List<SortOptions> sortOptions = buildSortOptions(request, pageable);

            int page = Math.max(0, pageable.getPageNumber());
            int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : 20;
            int from = page * size;

            SearchRequest searchRequest = new SearchRequest.Builder()
                    .index(indexName)
                    .query(query)
                    .from(from)
                    .size(size)
                    .sort(sortOptions)
                    .build();

            SearchResponse<ProductResponse> response = openSearchClient.search(searchRequest, ProductResponse.class);

            List<ProductResponse> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();

            long totalHits = response.hits().total() != null ? response.hits().total().value() : products.size();
            return new PageImpl<>(products, pageable, totalHits);
        } catch (IOException e) {
            log.error("Failed to execute OpenSearch search", e);
            throw new RuntimeException("OpenSearch query failed: " + e.getMessage(), e);
        }
    }

    public ProductSuggestionsResponse getSuggestions(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ProductSuggestionsResponse(List.of());
        }

        String kw = keyword.trim().toLowerCase();
        try {
            SearchRequest request = new SearchRequest.Builder()
                    .index(indexName)
                    .query(q -> q.bool(b -> b
                            .should(s -> s.wildcard(w -> w.field("brand").value("*" + kw + "*")))
                            .should(s -> s.wildcard(w -> w.field("categories").value("*" + kw + "*")))
                            .should(s -> s.matchPhrasePrefix(m -> m.field("name").query(keyword.trim())))
                            .should(s -> s.wildcard(w -> w.field("sku").value("*" + kw.toUpperCase() + "*")))
                            .should(s -> s.matchPhrasePrefix(m -> m.field("attributes.value").query(keyword.trim())))
                            .should(s -> s.match(m -> m.field("description").query(FieldValue.of(keyword.trim()))))
                    ))
                    .size(30)
                    .build();

            SearchResponse<ProductResponse> response = openSearchClient.search(request, ProductResponse.class);

            List<ProductResponse> hits = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .toList();

            List<SuggestionItem> items = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            // 1. Brands
            for (ProductResponse p : hits) {
                if (items.size() >= MAX_SUGGESTIONS) break;
                if (p.brand() != null && p.brand().toLowerCase().contains(kw)) {
                    addSuggestion(items, seen, "brand", p.brand(), "Brand: " + p.brand());
                }
            }

            // 2. Categories
            for (ProductResponse p : hits) {
                if (items.size() >= MAX_SUGGESTIONS) break;
                if (p.categories() != null) {
                    for (String cat : p.categories()) {
                        if (items.size() >= MAX_SUGGESTIONS) break;
                        if (cat.toLowerCase().contains(kw)) {
                            addSuggestion(items, seen, "category", cat, "Category: " + cat);
                        }
                    }
                }
            }

            // 3. Names
            for (ProductResponse p : hits) {
                if (items.size() >= MAX_SUGGESTIONS) break;
                if (p.name() != null && p.name().toLowerCase().contains(kw)) {
                    addSuggestion(items, seen, "name", p.name(), "Product: " + p.name());
                }
            }

            // 4. SKUs
            for (ProductResponse p : hits) {
                if (items.size() >= MAX_SUGGESTIONS) break;
                if (p.sku() != null && p.sku().toLowerCase().contains(kw)) {
                    addSuggestion(items, seen, "sku", p.sku(), "SKU: " + p.sku());
                }
            }

            // 5. Attributes
            for (ProductResponse p : hits) {
                if (items.size() >= MAX_SUGGESTIONS) break;
                if (p.attributes() != null) {
                    for (ProductResponse.AttributeDto attr : p.attributes()) {
                        if (items.size() >= MAX_SUGGESTIONS) break;
                        if (attr.value() != null && attr.value().toLowerCase().contains(kw)) {
                            addSuggestion(items, seen, "attribute", attr.value(), "Attribute: " + attr.value());
                        }
                    }
                }
            }

            // 6. Descriptions
            if (items.size() < MAX_SUGGESTIONS) {
                for (ProductResponse p : hits) {
                    if (items.size() >= MAX_SUGGESTIONS) break;
                    if (p.description() != null && p.description().toLowerCase().contains(kw)) {
                        String snippet = extractSnippet(p.description(), kw);
                        if (snippet != null) {
                            addSuggestion(items, seen, "description", snippet, "Description: " + snippet);
                        }
                    }
                }
            }

            return new ProductSuggestionsResponse(items);
        } catch (IOException e) {
            log.warn("Failed to get suggestions from OpenSearch", e);
            return new ProductSuggestionsResponse(List.of());
        }
    }

    private Query buildSearchQuery(ProductSearchRequest request) {
        BoolQuery.Builder bool = new BoolQuery.Builder();
        boolean hasClause = false;

        if (request.keyword() != null && !request.keyword().isBlank()) {
            String kw = request.keyword().trim();
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(kw)
                    .fields("name^3", "brand^2", "categories^2", "sku^2", "description", "attributes.value", "variants.sku")
            ));
            hasClause = true;
        }

        if (request.name() != null && !request.name().isBlank()) {
            bool.filter(f -> f.match(m -> m.field("name").query(FieldValue.of(request.name().trim()))));
            hasClause = true;
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            bool.filter(f -> f.term(t -> t.field("brand").value(FieldValue.of(request.brand().trim()))));
            hasClause = true;
        }

        if (request.brandId() != null) {
            bool.filter(f -> f.term(t -> t.field("brandDetail.id").value(FieldValue.of(request.brandId()))));
            hasClause = true;
        }

        if (request.category() != null && !request.category().isBlank()) {
            bool.filter(f -> f.term(t -> t.field("categories").value(FieldValue.of(request.category().trim()))));
            hasClause = true;
        }

        if (request.categoryId() != null) {
            bool.filter(f -> f.term(t -> t.field("categoryDetails.id").value(FieldValue.of(request.categoryId()))));
            hasClause = true;
        }

        if (request.sku() != null && !request.sku().isBlank()) {
            String sku = request.sku().trim();
            bool.filter(f -> f.bool(b -> b
                    .should(s -> s.term(t -> t.field("sku").value(FieldValue.of(sku))))
                    .should(s -> s.term(t -> t.field("variants.sku").value(FieldValue.of(sku))))
            ));
            hasClause = true;
        }

        if (request.attribute() != null && !request.attribute().isBlank()) {
            bool.filter(f -> f.match(m -> m.field("attributes.value").query(FieldValue.of(request.attribute().trim()))));
            hasClause = true;
        }

        if (request.description() != null && !request.description().isBlank()) {
            bool.filter(f -> f.match(m -> m.field("description").query(FieldValue.of(request.description().trim()))));
            hasClause = true;
        }

        if (request.minPrice() != null || request.maxPrice() != null) {
            bool.filter(f -> f.range(r -> {
                r.field("price");
                if (request.minPrice() != null) {
                    r.gte(JsonData.of(request.minPrice().doubleValue()));
                }
                if (request.maxPrice() != null) {
                    r.lte(JsonData.of(request.maxPrice().doubleValue()));
                }
                return r;
            }));
            hasClause = true;
        }

        if (request.available() != null) {
            bool.filter(f -> f.term(t -> t.field("available").value(FieldValue.of(request.available()))));
            hasClause = true;
        }

        if (!hasClause) {
            return Query.of(q -> q.matchAll(m -> m));
        }

        return Query.of(q -> q.bool(bool.build()));
    }

    private List<SortOptions> buildSortOptions(ProductSearchRequest request, Pageable pageable) {
        List<SortOptions> options = new ArrayList<>();

        if (pageable.getSort().isSorted()) {
            pageable.getSort().forEach(order -> {
                String property = order.getProperty();
                SortOrder dir = order.isAscending() ? SortOrder.Asc : SortOrder.Desc;

                switch (property) {
                    case "score", "_score", "relevance" ->
                            options.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
                    case "price" ->
                            options.add(SortOptions.of(s -> s.field(f -> f.field("price").order(dir))));
                    case "createdAt" ->
                            options.add(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(dir))));
                    case "name" ->
                            options.add(SortOptions.of(s -> s.field(f -> f.field("name.keyword").order(dir))));
                    default ->
                            options.add(SortOptions.of(s -> s.field(f -> f.field(property).order(dir))));
                }
            });
        }

        if (options.isEmpty()) {
            if (request.keyword() != null && !request.keyword().isBlank()) {
                options.add(SortOptions.of(s -> s.score(sc -> sc.order(SortOrder.Desc))));
            } else {
                options.add(SortOptions.of(s -> s.field(f -> f.field("createdAt").order(SortOrder.Desc))));
            }
        }

        return options;
    }

    private void addSuggestion(List<SuggestionItem> items, Set<String> seen, String key, String value, String label) {
        String dedupeKey = key + ":" + value.trim().toLowerCase();
        if (seen.add(dedupeKey)) {
            items.add(new SuggestionItem(key, value.trim(), label));
        }
    }

    private String extractSnippet(String text, String keyword) {
        if (text == null) return null;
        Pattern pattern = Pattern.compile("(?i)\\b([a-zA-Z0-9\\s]{0,25}" + Pattern.quote(keyword) + "[a-zA-Z0-9\\s]{0,25})\\b");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", " ").trim();
        }
        return null;
    }
}
