package com.stevenpg.grpc.inventory.domain;

import java.time.Instant;

import com.stevenpg.grpc.inventory.proto.ProductCategory;

/**
 * Internal domain model for a product.
 *
 * <p>Deliberately a separate type from the generated protobuf
 * {@link com.stevenpg.grpc.inventory.proto.Product} message. Keeping the
 * wire model and the domain model apart is the same discipline as not
 * exposing JPA entities from a REST controller: the proto contract can
 * evolve for API reasons while the domain evolves for business reasons.
 * The mapping between the two happens at the gRPC service boundary.
 */
public record ProductRecord(
        String sku,
        String name,
        String description,
        long priceCents,
        String currency,
        int quantityAvailable,
        ProductCategory category,
        Instant updatedAt) {

    public ProductRecord withQuantity(int newQuantity) {
        return new ProductRecord(sku, name, description, priceCents, currency,
                newQuantity, category, Instant.now());
    }
}
