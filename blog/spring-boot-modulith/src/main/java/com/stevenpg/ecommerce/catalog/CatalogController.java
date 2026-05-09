package com.stevenpg.ecommerce.catalog;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/catalog/products")
@Tag(name = "Catalog", description = "Browse the product catalog")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @Operation(summary = "List all products")
    List<Product> listProducts() {
        return catalog.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by ID")
    ResponseEntity<Product> getProduct(@PathVariable UUID id) {
        return catalog.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
