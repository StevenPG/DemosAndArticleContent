package com.stevenpg.grpc.inventory.grpc;

/**
 * Plain business exception - note that it knows nothing about gRPC.
 * The translation to gRPC status NOT_FOUND happens centrally in
 * {@link com.stevenpg.grpc.inventory.config.GrpcExceptionConfig}.
 */
public class ProductNotFoundException extends RuntimeException {

    private final String sku;

    public ProductNotFoundException(String sku) {
        super("No product with SKU '" + sku + "'");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
