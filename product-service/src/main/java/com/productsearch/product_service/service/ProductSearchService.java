package com.productsearch.product_service.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.productsearch.product_service.dto.ProductResponse;
import com.productsearch.product_service.dto.ProductSearchRequest;
import com.productsearch.product_service.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<ProductResponse> search(
            ProductSearchRequest request,
            Pageable pageable
    ) {
        return productRepository.searchProducts(
                request.keyword(),
                request.brandId(),
                request.categoryId(),
                request.minPrice(),
                request.maxPrice(),
                pageable
        ).map(ProductResponse::from);
    }
}

