package com.productsearch.search_indexer.runner;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import com.productsearch.search_indexer.service.ProductIndexingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineStartupRunner implements ApplicationRunner {

    private final ProductIndexingService productIndexingService;
    private final KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Value("${search-indexer.full-sync-on-startup:false}")
    private boolean fullSyncOnStartup;

    @Value("${search-indexer.cdc.enabled:false}")
    private boolean cdcEnabled;

    @Value("${search-indexer.exit-on-complete:false}")
    private boolean exitOnComplete;

    @Value("${spring.main.web-application-type:servlet}")
    private String webApplicationType;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (fullSyncOnStartup) {
            int indexed = productIndexingService.rebuildAllProducts();
            log.info("Startup full sync completed with {} products", indexed);

            boolean shouldExit = exitOnComplete
                    || args.containsOption("search-indexer.exit-on-complete")
                    || args.containsOption("exit")
                    || (!cdcEnabled && "none".equalsIgnoreCase(webApplicationType));

            if (shouldExit) {
                log.info("Batch full sync finished and exit requested. Terminating runner process.");
                System.exit(0);
            }
        }

        if (cdcEnabled) {
            productIndexingService.requireExistingSearchIndex();
            kafkaListenerEndpointRegistry.getListenerContainers().forEach(container -> {
                if (!container.isRunning()) {
                    container.start();
                }
            });
            log.info("Started configured Kafka CDC consumers");
        }
    }
}
