/**
 * Public API of the Catalog module.
 *
 * Exposes read-only access to products. All persistence and mutation logic
 * lives in the {@code internal} sub-package and must not be referenced by
 * other modules directly.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Catalog",
        allowedDependencies = {}
)
package com.stevenpg.ecommerce.catalog;
