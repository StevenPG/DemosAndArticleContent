package com.stevenpg.ecommerce.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.test.ApplicationModuleTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@ApplicationModuleTest
class CatalogModuleTests {

    @Autowired
    CatalogService catalog;

    @Test
    void findAllProducts() {
        assertThat(catalog.findAll()).isNotNull();
    }

    @Test
    void findBySkuReturnsEmptyForUnknownSku() {
        assertThat(catalog.findBySku("DOES-NOT-EXIST")).isEmpty();
    }
}
