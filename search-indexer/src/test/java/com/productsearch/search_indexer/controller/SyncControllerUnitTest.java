package com.productsearch.search_indexer.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.productsearch.search_indexer.service.ProductIndexingService;

class SyncControllerUnitTest {

    private ProductIndexingService productIndexingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        productIndexingService = mock(ProductIndexingService.class);
        SyncController controller = new SyncController(productIndexingService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void triggersFullSyncSuccessfully() throws Exception {
        when(productIndexingService.rebuildAllProducts()).thenReturn(100);

        mockMvc.perform(post("/api/v1/sync/full"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.syncedCount").value(100))
                .andExpect(jsonPath("$.message").value("Full sync completed successfully"));
    }

    @Test
    void returnsInternalServerErrorWhenSyncFails() throws Exception {
        when(productIndexingService.rebuildAllProducts())
                .thenThrow(new IOException("OpenSearch connection refused"));

        mockMvc.perform(post("/api/v1/sync/full"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("OpenSearch connection refused"));
    }
}
