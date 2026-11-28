package com.productsearch.search_indexer.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.productsearch.search_indexer.document.ProductDocument;
import com.productsearch.search_indexer.service.ProductIndexingService;
import com.productsearch.search_indexer.service.ProductProjectionReader;

class ProductChangeConsumerTest {

    private final ProductProjectionReader productProjectionReader = mock(ProductProjectionReader.class);
    private final ProductIndexingService productIndexingService = mock(ProductIndexingService.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private ProductChangeConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new ProductChangeConsumer(
                new ObjectMapper(),
                productProjectionReader,
                productIndexingService);
    }

    @Test
    void acknowledgesTheCdcRecordOnlyAfterTheOpenSearchWrite() throws Exception {
        ProductDocument document = product(1L);
        when(productProjectionReader.findAffectedProductIds(eq("products"), any(JsonNode.class)))
                .thenReturn(Set.of(1L));
        when(productProjectionReader.findProduct(1L)).thenReturn(Optional.of(document));

        consumer.consume(record(), acknowledgment);

        var order = inOrder(productIndexingService, acknowledgment);
        order.verify(productIndexingService).index(document);
        order.verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotAcknowledgeARecordWhenOpenSearchWriteFails() throws Exception {
        ProductDocument document = product(1L);
        when(productProjectionReader.findAffectedProductIds(eq("products"), any(JsonNode.class)))
                .thenReturn(Set.of(1L));
        when(productProjectionReader.findProduct(1L)).thenReturn(Optional.of(document));
        org.mockito.Mockito.doThrow(new IOException("OpenSearch unavailable"))
                .when(productIndexingService).index(document);

        assertThatThrownBy(() -> consumer.consume(record(), acknowledgment))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IOException.class);
        verify(acknowledgment, never()).acknowledge();
    }

    private ConsumerRecord<String, String> record() {
        String value = """
                {
                  "payload": {
                    "before": {"id": 1},
                    "after": {"id": 1},
                    "source": {"table": "products"},
                    "op": "u"
                  }
                }
                """;
        return new ConsumerRecord<>("product-search.product_search.products", 0, 0L, "1", value);
    }

    private ProductDocument product(long id) {
        return new ProductDocument(
                id,
                "Test product",
                "test-product",
                "Test description",
                "TEST-1",
                "Test brand",
                new ProductDocument.BrandDetail(1L, "Test brand", "test-brand"),
                List.of(),
                List.of(),
                BigDecimal.ONE,
                "ACTIVE",
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
    }
}
