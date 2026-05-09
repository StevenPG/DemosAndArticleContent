package com.stevenpg.ecommerce.catalog.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.ApplicationModuleInitializer;
import org.springframework.stereotype.Component;

/**
 * Demonstrates {@link ApplicationModuleInitializer} — a Spring Modulith lifecycle hook
 * that runs initialization logic for each module in dependency order when the application
 * starts (on {@code ApplicationReadyEvent}).
 *
 * Unlike {@code @PostConstruct} (which fires during bean creation in arbitrary order)
 * or {@code @EventListener(ApplicationReadyEvent.class)} (no guaranteed ordering),
 * {@code ApplicationModuleInitializer} implementations are invoked with all upstream
 * dependencies already initialized.  In this project, {@code catalog} has no deps, so
 * it always runs first.  If {@code orders} had its own initializer it would run after
 * catalog, inventory after orders, and payments last.
 *
 * Typical uses: validate seeded reference data, warm caches, register runtime
 * configuration derived from module state.
 */
@Component
class CatalogInitializer implements ApplicationModuleInitializer {

    private static final Logger log = LoggerFactory.getLogger(CatalogInitializer.class);

    private final ProductRepository products;

    CatalogInitializer(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void initialize() {
        long count = products.count();
        if (count == 0) {
            log.warn("Catalog module initialized with no products — is the seed data loaded?");
        } else {
            log.info("Catalog module initialized with {} product(s) in the catalog", count);
        }
    }
}
