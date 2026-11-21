package com.productsearch.product_service.controller;

import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.productsearch.product_service.dto.CatalogUpdateRequest;
import com.productsearch.product_service.dto.CatalogUpdateResponse;
import com.productsearch.product_service.service.CatalogUpdateService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogUpdateController {

    private final CatalogUpdateService updateService;

    @PatchMapping("/{entity}")
    public CatalogUpdateResponse updateByEntity(
            @PathVariable("entity") String entity,
            @Valid @RequestBody CatalogUpdateRequest request) {
        return updateService.update(entity, null, request);
    }

    @PatchMapping("/{entity}/{id}")
    public CatalogUpdateResponse updateByEntityAndId(
            @PathVariable("entity") String entity,
            @PathVariable("id") Long id,
            @Valid @RequestBody CatalogUpdateRequest request) {
        return updateService.update(entity, id, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "success", false,
                "error", ex.getMessage()
        ));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "success", false,
                "error", ex.getMessage()
        ));
    }
}
