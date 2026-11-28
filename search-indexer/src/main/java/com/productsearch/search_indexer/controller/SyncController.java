package com.productsearch.search_indexer.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.productsearch.search_indexer.service.ProductIndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/v1/sync")
@RequiredArgsConstructor
@Slf4j
public class SyncController {

    private final ProductIndexingService productIndexingService;

    @PostMapping({"/full", ""})
    public ResponseEntity<Map<String, Object>> triggerFullSync() {
        log.info("Full sync triggered via REST API");
        try {
            int syncedCount = productIndexingService.rebuildAllProducts();
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "syncedCount", syncedCount,
                    "message", "Full sync completed successfully"
            ));
        } catch (IOException | RuntimeException e) {
            log.error("Full sync failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage() != null ? e.getMessage() : "Unknown error during sync"
            ));
        }
    }
}
