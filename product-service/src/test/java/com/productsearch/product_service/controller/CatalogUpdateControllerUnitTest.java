package com.productsearch.product_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.productsearch.product_service.dto.CatalogUpdateResponse;
import com.productsearch.product_service.service.CatalogUpdateService;

class CatalogUpdateControllerUnitTest {

    private CatalogUpdateService catalogUpdateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        catalogUpdateService = mock(CatalogUpdateService.class);
        CatalogUpdateController controller = new CatalogUpdateController(catalogUpdateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void updatesCatalogEntitySuccessfully() throws Exception {
        CatalogUpdateResponse response = new CatalogUpdateResponse(
                true,
                "products",
                "products",
                1L,
                List.of("status"),
                "1 row(s) updated successfully in products"
        );

        when(catalogUpdateService.update(eq("products"), isNull(), any()))
                .thenReturn(response);

        String payload = """
                {
                    "id": 1,
                    "updates": {
                        "status": "ACTIVE"
                    }
                }
                """;

        mockMvc.perform(patch("/api/v1/catalog/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.table").value("products"))
                .andExpect(jsonPath("$.updatedFields[0]").value("status"));
    }

    @Test
    void handlesBadRequestWhenUpdateServiceThrows() throws Exception {
        when(catalogUpdateService.update(eq("invalid"), isNull(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported catalog entity: invalid"));

        mockMvc.perform(patch("/api/v1/catalog/invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": 1, \"updates\": {\"status\": \"ACTIVE\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("Unsupported catalog entity: invalid"));
    }
}
