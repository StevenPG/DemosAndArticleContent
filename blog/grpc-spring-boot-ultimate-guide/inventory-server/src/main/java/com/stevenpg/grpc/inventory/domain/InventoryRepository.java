package com.stevenpg.grpc.inventory.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

import com.stevenpg.grpc.inventory.proto.ProductCategory;

/**
 * In-memory stand-in for a real database so the demo has zero external
 * dependencies. Thread-safety matters here: gRPC dispatches each call on a
 * worker thread, and streaming calls interleave, so this map is hit
 * concurrently exactly like a real datastore would be.
 */
@Repository
public class InventoryRepository {

    private final Map<String, ProductRecord> products = new ConcurrentHashMap<>();

    public InventoryRepository() {
        seed(new ProductRecord("SKU-0001", "Mechanical Keyboard",
                "Tenkeyless mechanical keyboard with hot-swappable switches",
                12999, "USD", 42, ProductCategory.PRODUCT_CATEGORY_ELECTRONICS, Instant.now()));
        seed(new ProductRecord("SKU-0002", "USB-C Dock",
                "11-in-1 USB-C docking station, dual 4K output",
                8950, "USD", 17, ProductCategory.PRODUCT_CATEGORY_ELECTRONICS, Instant.now()));
        seed(new ProductRecord("SKU-0003", "Distributed Systems Field Guide",
                "600 pages of consensus, clocks, and cautionary tales",
                4599, "USD", 5, ProductCategory.PRODUCT_CATEGORY_BOOKS, Instant.now()));
        seed(new ProductRecord("SKU-0004", "Conference Hoodie",
                "Soft-touch hoodie, unisex fit",
                3800, "USD", 120, ProductCategory.PRODUCT_CATEGORY_APPAREL, Instant.now()));
        seed(new ProductRecord("SKU-0005", "Cold Brew Concentrate",
                "1L cold brew concentrate, makes 8 cups",
                1499, "USD", 0, ProductCategory.PRODUCT_CATEGORY_GROCERY, Instant.now()));
    }

    private void seed(ProductRecord record) {
        products.put(record.sku(), record);
    }

    public Optional<ProductRecord> findBySku(String sku) {
        return Optional.ofNullable(products.get(sku));
    }

    public List<ProductRecord> findAll(ProductCategory categoryFilter) {
        return products.values().stream()
                .filter(p -> categoryFilter == ProductCategory.PRODUCT_CATEGORY_UNSPECIFIED
                        || p.category() == categoryFilter)
                .sorted(Comparator.comparing(ProductRecord::sku))
                .toList();
    }

    /**
     * Atomically adds restocked units and returns the updated record, or
     * empty when the SKU is unknown - the gRPC layer translates that into a
     * meaningful status code.
     */
    public Optional<ProductRecord> restock(String sku, int units) {
        return Optional.ofNullable(products.computeIfPresent(sku,
                (key, current) -> current.withQuantity(current.quantityAvailable() + units)));
    }

    /**
     * Attempts to decrement stock for a sale; returns the updated record, or
     * empty when the SKU is unknown or stock is insufficient.
     */
    public Optional<ProductRecord> trySell(String sku, int quantity) {
        final boolean[] sold = {false};
        ProductRecord result = products.computeIfPresent(sku, (key, current) -> {
            if (current.quantityAvailable() < quantity) {
                return current;
            }
            sold[0] = true;
            return current.withQuantity(current.quantityAvailable() - quantity);
        });
        return (result != null && sold[0]) ? Optional.of(result) : Optional.empty();
    }
}
