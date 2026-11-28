package com.productsearch.search_indexer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.productsearch.search_indexer.document.ProductDocument;
import com.productsearch.search_indexer.service.ProductProjectionReader;

@SpringBootTest
class SearchIndexerApplicationTests {

	@Autowired
	private ProductProjectionReader productProjectionReader;

	@Test
	void contextLoads() {
	}

	@Test
	void readsTheCompleteProductSearchProjectionFromMySql() {
		ProductDocument product = productProjectionReader.findProduct(1L).orElseThrow();

		assertThat(product.brandDetail()).isNotNull();
		assertThat(product.categories()).isNotEmpty();
		assertThat(product.categoryDetails()).isNotEmpty();
		assertThat(product.variants()).isNotEmpty();
		assertThat(product.prices()).isNotEmpty();
		assertThat(product.attributes()).isNotEmpty();
		assertThat(product.components()).isNotEmpty();
		assertThat(product.variants()).allSatisfy(variant -> assertThat(variant.inventory()).isNotNull());
	}
}
