package com.stevenpg.ecommerce.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "View stock levels")
class InventoryController {

    private final InventoryService inventory;

    InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping
    @Operation(summary = "List all inventory items")
    List<InventoryItem> listAll() {
        return inventory.findAll();
    }

    @GetMapping("/product/{productId}")
    @Operation(summary = "Get stock for a product")
    ResponseEntity<InventoryItem> getByProduct(@PathVariable UUID productId) {
        return inventory.findByProductId(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
