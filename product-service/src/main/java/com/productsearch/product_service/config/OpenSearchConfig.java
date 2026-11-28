package com.productsearch.product_service.config;

import java.net.URI;

import org.apache.hc.core5.http.HttpHost;
import org.opensearch.client.json.jackson.JacksonJsonpMapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.OpenSearchTransport;
import org.opensearch.client.transport.httpclient5.ApacheHttpClient5TransportBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@Configuration
public class OpenSearchConfig {

    @Value("${opensearch.host:http://localhost:9200}")
    private String opensearchHost;

    @Bean
    public OpenSearchClient openSearchClient(ObjectMapper mapper) {
        ObjectMapper configuredMapper = mapper.copy()
                .registerModule(new JavaTimeModule())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        URI uri = URI.create(opensearchHost);
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        HttpHost host = new HttpHost(uri.getScheme(), uri.getHost(), port);

        OpenSearchTransport transport = ApacheHttpClient5TransportBuilder.builder(host)
                .setMapper(new JacksonJsonpMapper(configuredMapper))
                .build();
        return new OpenSearchClient(transport);
    }
}
