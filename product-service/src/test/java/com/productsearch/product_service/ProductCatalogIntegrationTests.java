package com.productsearch.product_service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ProductCatalogIntegrationTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void testDbSuggestionsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions/db")
                        .param("q", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray())
                .andExpect(jsonPath("$.suggestions[0].key").exists())
                .andExpect(jsonPath("$.suggestions[0].value").exists())
                .andExpect(jsonPath("$.suggestions[0].displayLabel").exists());
    }

    @Test
    void backendSelectionMustUseExplicitRoutes() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions")
                        .param("q", "app")
                        .param("engine", "os"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/products/search")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testSearchProductsDbEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/products/search/db")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").exists())
                .andExpect(jsonPath("$.content[0].name").exists())
                .andExpect(jsonPath("$.content[0].brand").exists())
                .andExpect(jsonPath("$.content[0].variants").isArray())
                .andExpect(jsonPath("$.content[0].prices").isArray())
                .andExpect(jsonPath("$.content[0].attributes").isArray())
                .andExpect(jsonPath("$.content[0].components").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber())
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void testSearchWithSuggestionFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products/search/db")
                        .param("brand", "Apple")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].brand").value("Apple"));
    }

    @Test
    void fullSyncIsNoLongerExposedByProductService() throws Exception {
        mockMvc.perform(post("/api/v1/products/sync/opensearch"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testOpenSearchSearchAndSuggestionsEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/products/search/os")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").isNumber());

        mockMvc.perform(get("/api/v1/products/suggestions/os")
                        .param("q", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions").isArray());
    }

    @Test
    void openSearchSuggestionsMatchCaseInsensitivelyAndWithinWords() throws Exception {
        mockMvc.perform(get("/api/v1/products/suggestions/os")
                        .param("q", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[?(@.value == 'Apple')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/products/suggestions/os")
                        .param("q", "hone"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[?(@.value == 'iPhone 16')]").isNotEmpty());

        mockMvc.perform(get("/api/v1/products/suggestions/os")
                        .param("q", "iPhone 16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suggestions[?(@.value == 'iPhone 16')]").isNotEmpty());
    }

    @Test
    void testCatalogUpdateEndpoint() throws Exception {
        String updatePayload = """
                {
                    "id": 1,
                    "updates": {
                        "status": "ACTIVE"
                    }
                }
                """;

        mockMvc.perform(patch("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.table").value("products"))
                .andExpect(jsonPath("$.updatedFields[0]").value("status"));
    }

    @Test
    void testCatalogUpdateRejectsNonUpdatableFieldOnly() throws Exception {
        String invalidPayload = """
                {
                    "id": 1,
                    "updates": {
                        "created_at": "2026-01-01T00:00:00"
                    }
                }
                """;

        mockMvc.perform(patch("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
