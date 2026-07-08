package com.stevenpg.grpc.inventory.grpc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.google.protobuf.Timestamp;
import com.stevenpg.grpc.inventory.domain.InventoryRepository;
import com.stevenpg.grpc.inventory.domain.ProductRecord;
import com.stevenpg.grpc.inventory.proto.GetProductRequest;
import com.stevenpg.grpc.inventory.proto.InventoryServiceGrpc;
import com.stevenpg.grpc.inventory.proto.ListProductsRequest;
import com.stevenpg.grpc.inventory.proto.ListProductsResponse;
import com.stevenpg.grpc.inventory.proto.OrderRequest;
import com.stevenpg.grpc.inventory.proto.OrderStatus;
import com.stevenpg.grpc.inventory.proto.Product;
import com.stevenpg.grpc.inventory.proto.Shipment;
import com.stevenpg.grpc.inventory.proto.ShipmentSummary;
import com.stevenpg.grpc.inventory.proto.StockUpdate;
import com.stevenpg.grpc.inventory.proto.WatchStockRequest;

import io.grpc.Context;
import io.grpc.stub.StreamObserver;

/**
 * The gRPC service implementation - the heart of the server.
 *
 * <p>How this becomes a network endpoint: protoc generated
 * {@link InventoryServiceGrpc.InventoryServiceImplBase} from inventory.proto;
 * we extend it and override one method per RPC. Because {@code ImplBase}
 * implements {@link io.grpc.BindableService} and this class is a Spring
 * {@code @Service} bean, Spring gRPC discovers it automatically and binds it
 * to the Netty gRPC server. No registration code, no XML, no port juggling.
 *
 * <p>Every method receives a {@link StreamObserver} for the RESPONSE side:
 * <ul>
 *   <li>{@code onNext(msg)}      - send one message to the client</li>
 *   <li>{@code onCompleted()}    - close the stream successfully</li>
 *   <li>{@code onError(t)}       - close the stream with a gRPC status</li>
 * </ul>
 * Unary methods call {@code onNext} exactly once; streaming methods call it
 * as many times as they like. Client-streaming and bidirectional methods
 * additionally RETURN a StreamObserver through which the client's incoming
 * messages arrive - inverted control compared to unary handlers.
 */
@Service
public class InventoryGrpcService extends InventoryServiceGrpc.InventoryServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(InventoryGrpcService.class);

    private static final int DEFAULT_WATCH_UPDATES = 5;

    private final InventoryRepository repository;

    public InventoryGrpcService(InventoryRepository repository) {
        // Plain constructor injection - a gRPC service is a normal bean and
        // can depend on repositories, clients, metrics, anything.
        this.repository = repository;
    }

    // ------------------------------------------------------------------
    // 1. UNARY: one request in, one response out
    // ------------------------------------------------------------------
    @Override
    public void getProduct(GetProductRequest request, StreamObserver<Product> responseObserver) {
        ProductRecord record = repository.findBySku(request.getSku())
                // Thrown exceptions do NOT need to be handled here: the
                // GrpcExceptionHandler bean in GrpcExceptionConfig maps this
                // to status NOT_FOUND, keeping business code free of
                // transport concerns (just like @RestControllerAdvice).
                .orElseThrow(() -> new ProductNotFoundException(request.getSku()));

        responseObserver.onNext(toProto(record));
        responseObserver.onCompleted();
    }

    @Override
    public void listProducts(ListProductsRequest request,
            StreamObserver<ListProductsResponse> responseObserver) {
        ListProductsResponse.Builder response = ListProductsResponse.newBuilder();
        repository.findAll(request.getCategory())
                .forEach(record -> response.addProducts(toProto(record)));

        responseObserver.onNext(response.build());
        responseObserver.onCompleted();
    }

    // ------------------------------------------------------------------
    // 2. SERVER STREAMING: one request in, many responses out
    // ------------------------------------------------------------------
    @Override
    public void watchStock(WatchStockRequest request, StreamObserver<StockUpdate> responseObserver) {
        ProductRecord product = repository.findBySku(request.getSku())
                .orElseThrow(() -> new ProductNotFoundException(request.getSku()));

        int updates = request.getMaxUpdates() > 0 ? request.getMaxUpdates() : DEFAULT_WATCH_UPDATES;
        int quantity = product.quantityAvailable();

        for (int i = 0; i < updates; i++) {
            // CRITICAL streaming discipline: check for client cancellation.
            // If the client hangs up (or its deadline expires) and we keep
            // writing, we waste server resources; long-lived streams that
            // never check are the classic gRPC resource leak.
            if (Context.current().isCancelled()) {
                log.info("watchStock({}) cancelled by client after {} updates", request.getSku(), i);
                return; // no onCompleted - the call is already dead
            }

            // Simulate market activity: alternate sales and restocks.
            StockUpdate.Builder update = StockUpdate.newBuilder()
                    .setSku(request.getSku())
                    .setOccurredAt(toTimestamp(Instant.now()));

            boolean sale = ThreadLocalRandom.current().nextBoolean() && quantity > 0;
            if (sale) {
                int sold = 1 + ThreadLocalRandom.current().nextInt(Math.min(3, quantity));
                quantity -= sold;
                update.setSale(StockUpdate.Sale.newBuilder().setUnitsSold(sold));
            }
            else {
                int added = 1 + ThreadLocalRandom.current().nextInt(10);
                quantity += added;
                update.setRestock(StockUpdate.Restock.newBuilder()
                        .setUnitsAdded(added)
                        .setSupplier("Acme Wholesale"));
            }
            update.setQuantityAvailable(quantity);

            // Each onNext is one message pushed to the client immediately -
            // this is a live feed, not a buffered list.
            responseObserver.onNext(update.build());

            // Pace the demo so streaming is visible to the naked eye.
            sleep(400);
        }

        // Completing tells the client "no more updates, clean shutdown".
        responseObserver.onCompleted();
    }

    // ------------------------------------------------------------------
    // 3. CLIENT STREAMING: many requests in, one response out
    // ------------------------------------------------------------------
    @Override
    public StreamObserver<Shipment> recordShipments(StreamObserver<ShipmentSummary> responseObserver) {
        // Note the inversion: WE return an observer, and gRPC feeds the
        // client's messages into it as they arrive. State accumulated across
        // messages lives in this anonymous class - one instance per call, so
        // no cross-request leakage.
        return new StreamObserver<>() {

            private final AtomicInteger shipmentCount = new AtomicInteger();
            private final AtomicInteger totalUnits = new AtomicInteger();
            private final Map<String, Integer> updatedQuantities = new LinkedHashMap<>();

            @Override
            public void onNext(Shipment shipment) {
                // Called once per message the client streams up.
                repository.restock(shipment.getSku(), shipment.getQuantity())
                        .ifPresentOrElse(
                                updated -> {
                                    shipmentCount.incrementAndGet();
                                    totalUnits.addAndGet(shipment.getQuantity());
                                    updatedQuantities.put(updated.sku(), updated.quantityAvailable());
                                },
                                () -> log.warn("Ignoring shipment for unknown SKU {}", shipment.getSku()));
            }

            @Override
            public void onError(Throwable t) {
                // The CLIENT aborted mid-stream. Roll back / release
                // resources here. There is nothing to send - the call is over.
                log.warn("recordShipments stream aborted by client", t);
            }

            @Override
            public void onCompleted() {
                // The client finished streaming - NOW we send our single
                // aggregated response.
                responseObserver.onNext(ShipmentSummary.newBuilder()
                        .setShipmentsReceived(shipmentCount.get())
                        .setTotalUnitsAdded(totalUnits.get())
                        .putAllUpdatedQuantities(updatedQuantities)
                        .build());
                responseObserver.onCompleted();
            }
        };
    }

    // ------------------------------------------------------------------
    // 4. BIDIRECTIONAL STREAMING: both sides stream independently
    // ------------------------------------------------------------------
    @Override
    public StreamObserver<OrderRequest> processOrders(StreamObserver<OrderStatus> responseObserver) {
        // This implementation answers each order as it arrives (pipelined
        // request/response). Nothing REQUIRES that shape - the server could
        // batch responses, reorder them, or push unsolicited messages; the
        // two directions are fully independent streams.
        return new StreamObserver<>() {

            @Override
            public void onNext(OrderRequest order) {
                OrderStatus.Builder status = OrderStatus.newBuilder()
                        .setOrderId(order.getOrderId()); // correlate!

                repository.findBySku(order.getSku()).ifPresentOrElse(
                        product -> repository.trySell(order.getSku(), order.getQuantity())
                                .ifPresentOrElse(
                                        sold -> status
                                                .setResult(OrderStatus.Result.RESULT_CONFIRMED)
                                                .setTotalPriceCents(product.priceCents() * order.getQuantity())
                                                .setMessage("Confirmed - " + sold.quantityAvailable()
                                                        + " units remaining"),
                                        () -> status
                                                .setResult(OrderStatus.Result.RESULT_REJECTED_OUT_OF_STOCK)
                                                .setMessage("Insufficient stock for " + order.getSku())),
                        () -> status
                                .setResult(OrderStatus.Result.RESULT_REJECTED_UNKNOWN_SKU)
                                .setMessage("Unknown SKU " + order.getSku()));

                // Respond immediately, while the client may still be sending
                // more orders - both streams are live at the same time.
                responseObserver.onNext(status.build());
            }

            @Override
            public void onError(Throwable t) {
                log.warn("processOrders stream aborted by client", t);
            }

            @Override
            public void onCompleted() {
                // Client is done ordering; close our side too.
                responseObserver.onCompleted();
            }
        };
    }

    // ------------------------------------------------------------------
    // Mapping helpers: domain model <-> protobuf wire model
    // ------------------------------------------------------------------

    /** Protobuf messages are immutable; they are always built via builders. */
    private static Product toProto(ProductRecord record) {
        return Product.newBuilder()
                .setSku(record.sku())
                .setName(record.name())
                .setDescription(record.description())
                .setPriceCents(record.priceCents())
                .setCurrency(record.currency())
                .setQuantityAvailable(record.quantityAvailable())
                .setCategory(record.category())
                .setUpdatedAt(toTimestamp(record.updatedAt()))
                .build();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.newBuilder()
                .setSeconds(instant.getEpochSecond())
                .setNanos(instant.getNano())
                .build();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
