package com.productsearch.product_service.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.productsearch.product_service.dto.ProductResponse;
import com.productsearch.product_service.dto.ProductSearchRequest;
import com.productsearch.product_service.dto.ProductSuggestionsResponse;
import com.productsearch.product_service.service.ProductOpenSearchService;
import com.productsearch.product_service.service.ProductSearchService;
import com.productsearch.product_service.service.ProductSuggestionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductSearchService productSearchService;
    private final ProductSuggestionService productSuggestionService;
    private final ProductOpenSearchService productOpenSearchService;

    @GetMapping("/search/db")
    public Page<ProductResponse> searchProducts(
            @Valid @ModelAttribute ProductSearchRequest request,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return productSearchService.search(request, pageable);
    }

    @GetMapping("/suggestions")
    public ProductSuggestionsResponse getSuggestions(
            @RequestParam("q") String keyword) {
        return productSuggestionService.getSuggestions(keyword);
    }

    @GetMapping({"/search/os", "/search"})
    public Page<ProductResponse> searchOpenSearch(
            @Valid @ModelAttribute ProductSearchRequest request,
            @PageableDefault(
                    size = 20,
                    sort = "createdAt",
                    direction = Sort.Direction.DESC
            ) Pageable pageable
    ) {
        return productOpenSearchService.search(request, pageable);
    }

    @GetMapping("/suggestions/os")
    public ProductSuggestionsResponse getOpenSearchSuggestions(
            @RequestParam("q") String keyword) {
        return productOpenSearchService.getSuggestions(keyword);
    }
}
