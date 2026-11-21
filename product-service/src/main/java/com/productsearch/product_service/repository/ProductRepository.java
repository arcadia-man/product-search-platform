package com.productsearch.product_service.repository;

import java.math.BigDecimal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.productsearch.product_service.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
        SELECT p
        FROM Product p
        WHERE
            (
                :keyword IS NULL
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(COALESCE(p.description, ''))
                    LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(p.brand.name) LIKE LOWER(CONCAT('%', :keyword, '%'))

                OR EXISTS (
                    SELECT v.id
                    FROM ProductVariant v
                    WHERE v.product = p
                    AND LOWER(v.sku) LIKE LOWER(CONCAT('%', :keyword, '%'))
                )

                OR EXISTS (
                    SELECT a.id
                    FROM ProductAttribute a
                    WHERE a.product = p
                    AND (
                        LOWER(a.attributeName)
                            LIKE LOWER(CONCAT('%', :keyword, '%'))
                        OR LOWER(a.attributeValue)
                            LIKE LOWER(CONCAT('%', :keyword, '%'))
                    )
                )

                OR EXISTS (
                    SELECT c.id
                    FROM Category c
                    WHERE c MEMBER OF p.categories
                    AND LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
                )
            )

            AND (:name IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :name, '%')))

            AND (:brand IS NULL OR LOWER(p.brand.name) = LOWER(:brand))
            AND (:brandId IS NULL OR p.brand.id = :brandId)

            AND (
                :category IS NULL
                OR EXISTS (
                    SELECT c_filter.id
                    FROM Category c_filter
                    WHERE c_filter MEMBER OF p.categories
                    AND LOWER(c_filter.name) = LOWER(:category)
                )
            )

            AND (
                :categoryId IS NULL
                OR EXISTS (
                    SELECT c2.id
                    FROM Category c2
                    WHERE c2 MEMBER OF p.categories
                    AND c2.id = :categoryId
                )
            )

            AND (
                :sku IS NULL
                OR LOWER(p.sku) = LOWER(:sku)
                OR EXISTS (
                    SELECT v_sku.id
                    FROM ProductVariant v_sku
                    WHERE v_sku.product = p
                    AND LOWER(v_sku.sku) = LOWER(:sku)
                )
            )

            AND (
                :attribute IS NULL
                OR EXISTS (
                    SELECT a_filter.id
                    FROM ProductAttribute a_filter
                    WHERE a_filter.product = p
                    AND (
                        LOWER(a_filter.attributeName) = LOWER(:attribute)
                        OR LOWER(a_filter.attributeValue) = LOWER(:attribute)
                    )
                )
            )

            AND (
                :description IS NULL
                OR LOWER(COALESCE(p.description, '')) LIKE LOWER(CONCAT('%', :description, '%'))
            )

            AND (
                (:minPrice IS NULL AND :maxPrice IS NULL)
                OR EXISTS (
                    SELECT pp.id
                    FROM ProductPrice pp
                    WHERE pp.product = p
                    AND (pp.validFrom IS NULL OR pp.validFrom <= CURRENT_TIMESTAMP)
                    AND (pp.validTo IS NULL OR pp.validTo > CURRENT_TIMESTAMP)
                    AND (:minPrice IS NULL OR
                        COALESCE(pp.discountPrice, pp.price) >= :minPrice)
                    AND (:maxPrice IS NULL OR
                        COALESCE(pp.discountPrice, pp.price) <= :maxPrice)
                )
            )

            AND (
                :available IS NULL
                OR (
                    :available = TRUE
                    AND EXISTS (
                        SELECT i.id
                        FROM Inventory i
                        WHERE i.variant.product = p
                        AND i.availableQuantity > 0
                    )
                )
                OR (
                    :available = FALSE
                    AND NOT EXISTS (
                        SELECT i.id
                        FROM Inventory i
                        WHERE i.variant.product = p
                        AND i.availableQuantity > 0
                    )
                )
            )
        """)
    Page<Product> searchProducts(
            @Param("keyword") String keyword,
            @Param("name") String name,
            @Param("brand") String brand,
            @Param("brandId") Long brandId,
            @Param("category") String category,
            @Param("categoryId") Long categoryId,
            @Param("sku") String sku,
            @Param("attribute") String attribute,
            @Param("description") String description,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            @Param("available") Boolean available,
            Pageable pageable
    );
}
