package com.productsearch.product_service.service;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.productsearch.product_service.dto.ProductResponse;
import com.productsearch.product_service.dto.ProductSearchRequest;
import com.productsearch.product_service.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "sku", "price", "status", "createdAt", "updatedAt");

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(
            ProductSearchRequest request,
            Pageable pageable) {
        String keyword = cleanString(request.keyword());
        String name = cleanString(request.name());
        String brand = cleanString(request.brand());
        String category = cleanString(request.category());
        String sku = cleanString(request.sku());
        String attribute = cleanString(request.attribute());
        String description = cleanString(request.description());

        Pageable safePageable = normalizePageable(pageable);

        return productRepository.searchProducts(
                keyword,
                name,
                brand,
                request.brandId(),
                category,
                request.categoryId(),
                sku,
                attribute,
                description,
                request.minPrice(),
                request.maxPrice(),
                request.available(),
                safePageable
        ).map(ProductResponse::from);
    }

    private String cleanString(String val) {
        if (val == null) {
            return null;
        }
        String trimmed = val.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Pageable normalizePageable(Pageable pageable) {
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize() > 0 ? Math.min(pageable.getPageSize(), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;

        var allowedOrders = pageable.getSort().stream()
                .filter(order -> ALLOWED_SORT_FIELDS.contains(order.getProperty()))
                .toList();

        Sort sort = allowedOrders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(allowedOrders);

        return PageRequest.of(page, size, sort);
    }
}
