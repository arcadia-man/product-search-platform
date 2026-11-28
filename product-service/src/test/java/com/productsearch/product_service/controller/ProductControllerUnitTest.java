package com.productsearch.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.productsearch.product_service.dto.ProductResponse;
import com.productsearch.product_service.dto.ProductSuggestionsResponse;
import com.productsearch.product_service.dto.SuggestionItem;
import com.productsearch.product_service.service.ProductOpenSearchService;
import com.productsearch.product_service.service.ProductSearchService;
import com.productsearch.product_service.service.ProductSuggestionService;

class ProductControllerUnitTest {

    private ProductSearchService productSearchService;
    private ProductSuggestionService productSuggestionService;
    private ProductOpenSearchService productOpenSearchService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productSearchService = mock(ProductSearchService.class);
        productSuggestionService = mock(ProductSuggestionService.class);
        productOpenSearchService = mock(ProductOpenSearchService.class);

        ProductController controller = new ProductController(
                productSearchService,
                productSuggestionService,
                productOpenSearchService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void searchDbReturnsPaginatedResults() throws Exception {
        ProductResponse product = sampleProduct(1L, "Apple iPhone 15 Pro", "Apple");
        PageImpl<ProductResponse> page = new PageImpl<>(List.of(product), PageRequest.of(0, 10), 1);

        when(productSearchService.search(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search/db")
                        .param("keyword", "iphone")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Apple iPhone 15 Pro"))
                .andExpect(jsonPath("$.content[0].brand").value("Apple"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void suggestionsReturnsItemList() throws Exception {
        SuggestionItem item = new SuggestionItem("brand", "Apple", "Brand: Apple");
        when(productSuggestionService.getSuggestions("app"))
                .thenReturn(new ProductSuggestionsResponse(List.of(item)));

        mockMvc.perform(get("/api/v1/products/suggestions")
                        .param("q", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].key").value("brand"))
                .andExpect(jsonPath("$.suggestions[0].value").value("Apple"))
                .andExpect(jsonPath("$.suggestions[0].displayLabel").value("Brand: Apple"));
    }

    @Test
    void searchOsReturnsPaginatedResults() throws Exception {
        ProductResponse product = sampleProduct(2L, "Sony WH-1000XM5", "Sony");
        PageImpl<ProductResponse> page = new PageImpl<>(List.of(product), PageRequest.of(0, 10), 1);

        when(productOpenSearchService.search(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search/os")
                        .param("keyword", "headphones")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(2))
                .andExpect(jsonPath("$.content[0].name").value("Sony WH-1000XM5"))
                .andExpect(jsonPath("$.content[0].brand").value("Sony"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void suggestionsOsReturnsItemList() throws Exception {
        SuggestionItem item = new SuggestionItem("name", "Sony WH-1000XM5", "Product: Sony WH-1000XM5");
        when(productOpenSearchService.getSuggestions("sony"))
                .thenReturn(new ProductSuggestionsResponse(List.of(item)));

        mockMvc.perform(get("/api/v1/products/suggestions/os")
                        .param("q", "sony"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[0].key").value("name"))
                .andExpect(jsonPath("$.suggestions[0].value").value("Sony WH-1000XM5"));
    }

    private ProductResponse sampleProduct(Long id, String name, String brand) {
        return new ProductResponse(
                id,
                name,
                name.toLowerCase().replace(" ", "-"),
                "Description for " + name,
                "SKU-" + id,
                brand,
                new ProductResponse.BrandDto(1L, brand, brand.toLowerCase()),
                List.of("Electronics"),
                List.of(new ProductResponse.CategoryDto(1L, "Electronics", "electronics")),
                BigDecimal.valueOf(999.99),
                "ACTIVE",
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                LocalDateTime.now(),
                LocalDateTime.now()
        );
    }
}
