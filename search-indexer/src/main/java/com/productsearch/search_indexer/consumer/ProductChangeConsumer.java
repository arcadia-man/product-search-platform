package com.productsearch.search_indexer.consumer;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productsearch.search_indexer.document.ProductDocument;
import com.productsearch.search_indexer.service.ProductIndexingService;
import com.productsearch.search_indexer.service.ProductProjectionReader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductChangeConsumer {

    private final ObjectMapper objectMapper;
    private final ProductProjectionReader productProjectionReader;
    private final ProductIndexingService productIndexingService;

    @KafkaListener(
            topics = "${search-indexer.cdc.topic:product-changes}",
            groupId = "${spring.kafka.consumer.group-id:product-search-indexer}",
            concurrency = "${search-indexer.cdc.concurrency:2}",
            autoStartup = "false")
    public void consume(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            if (record.value() != null) {
                processChange(record.value());
            }
            acknowledgment.acknowledge();
        } catch (IOException | RuntimeException e) {
            log.error("Failed to index CDC record from {}-{} at offset {}",
                    record.topic(), record.partition(), record.offset(), e);
            throw new IllegalStateException("Product CDC indexing failed", e);
        }
    }

    private void processChange(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode event = root.has("payload") ? root.get("payload") : root;
        String table = event.path("source").path("table").asText(null);
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("CDC event is missing source.table");
        }

        JsonNode before = event.path("before");
        JsonNode after = event.path("after");
        Set<Long> affectedProductIds = new LinkedHashSet<>();
        affectedProductIds.addAll(productProjectionReader.findAffectedProductIds(table, before));
        affectedProductIds.addAll(productProjectionReader.findAffectedProductIds(table, after));

        for (Long productId : affectedProductIds) {
            Optional<ProductDocument> currentProduct = productProjectionReader.findProduct(productId);
            if (currentProduct.isPresent()) {
                productIndexingService.index(currentProduct.get());
            } else {
                productIndexingService.delete(productId);
            }
        }
    }
}
