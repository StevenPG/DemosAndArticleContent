package com.stevenpg.gateway.inventory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * The inventory backend. Its whole reason to exist in this demo is <b>load
 * balancing</b>: we start two identical instances of this service (ports 8082 and
 * 8083) and let the gateway spread traffic across them with a {@code lb://} route.
 *
 * <p>Every response includes {@code instance}, which is just the port this JVM is
 * listening on. Call the same gateway URL repeatedly and watch {@code instance}
 * alternate between 8082 and 8083 — that is client-side load balancing at work.
 */
@RestController
public class InventoryController {

    private final String instance;

    public InventoryController(@Value("${server.port}") String port) {
        this.instance = "inventory:" + port;
    }

    private static final List<Item> ITEMS = List.of(
            new Item("SKU-COFFEE", "Single-origin beans", 120),
            new Item("SKU-MUG", "Ceramic mug", 40),
            new Item("SKU-FILTER", "Paper filters (100ct)", 300)
    );

    @GetMapping("/inventory")
    public Map<String, Object> list() {
        return Map.of("instance", instance, "items", ITEMS);
    }

    @GetMapping("/inventory/{sku}")
    public ResponseEntity<Item> bySku(@PathVariable String sku) {
        return ITEMS.stream()
                .filter(i -> i.sku().equalsIgnoreCase(sku))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** The clearest load-balancing probe: returns nothing but which instance answered. */
    @GetMapping("/inventory/whoami")
    public Map<String, String> whoami() {
        return Map.of("instance", instance);
    }

    /** A stock item. */
    public record Item(String sku, String name, int onHand) {}
}
