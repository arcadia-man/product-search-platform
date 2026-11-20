package com.productsearch.product_service.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.productsearch.product_service.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Override
    @EntityGraph(attributePaths = { "brand", "categories" })
    List<Product> findAll();

    @Override
    @EntityGraph(attributePaths = { "brand", "categories" })
    Page<Product> findAll(Pageable pageable);

    @Query("""
                SELECT DISTINCT p FROM Product p
                LEFT JOIN p.brand b
                LEFT JOIN p.categories c
                WHERE (:keyword IS NULL OR
                       LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR
                       LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
                  AND (:brandId IS NULL OR b.id = :brandId)
                  AND (:categoryId IS NULL OR c.id = :categoryId)
                  AND (:minPrice IS NULL OR p.price >= :minPrice)
                  AND (:maxPrice IS NULL OR p.price <= :maxPrice)
            """)
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("brandId") Long brandId,
            @Param("categoryId") Long categoryId,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

}