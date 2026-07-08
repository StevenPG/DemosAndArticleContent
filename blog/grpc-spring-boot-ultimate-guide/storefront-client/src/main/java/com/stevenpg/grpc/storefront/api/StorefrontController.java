package com.stevenpg.grpc.storefront.api;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.stevenpg.grpc.inventory.proto.GetProductRequest;
import com.stevenpg.grpc.inventory.proto.InventoryServiceGrpc;
import com.stevenpg.grpc.inventory.proto.ListProductsRequest;
import com.stevenpg.grpc.inventory.proto.OrderRequest;
import com.stevenpg.grpc.inventory.proto.OrderStatus;
import com.stevenpg.grpc.inventory.proto.Shipment;
import com.stevenpg.grpc.inventory.proto.ShipmentSummary;
import com.stevenpg.grpc.inventory.proto.StockUpdate;
import com.stevenpg.grpc.inventory.proto.WatchStockRequest;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.OrderBatchResultDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.OrderDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.OrderStatusDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.ProductDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.ShipmentDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.ShipmentSummaryDto;
import com.stevenpg.grpc.storefront.api.StorefrontDtos.StockUpdateDto;

import io.grpc.stub.StreamObserver;

/**
 * REST facade over the gRPC InventoryService - one endpoint per RPC shape.
 *
 * <p>This is where the two worlds meet, and each endpoint demonstrates a
 * different client-side gRPC technique:
 *
 * <pre>
 * GET  /api/products            -> unary            (blocking stub)
 * GET  /api/products/{sku}      -> unary + DEADLINE  (blocking stub)
 * GET  /api/products/{sku}/stock/stream
 *                               -> server streaming  (async stub -> SSE)
 * POST /api/shipments           -> client streaming  (async stub)
 * POST /api/orders              -> bidi streaming    (async stub)
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class StorefrontController {

    private static final Logger log = LoggerFactory.getLogger(StorefrontController.class);

    private final InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub;
    private final InventoryServiceGrpc.InventoryServiceStub asyncStub;

    public StorefrontController(
            InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub,
            InventoryServiceGrpc.InventoryServiceStub asyncStub) {
        this.blockingStub = blockingStub;
        this.asyncStub = asyncStub;
    }

    // ------------------------------------------------------------------
    // UNARY via blocking stub - looks exactly like a local method call
    // ------------------------------------------------------------------

    @GetMapping("/products")
    public List<ProductDto> listProducts() {
        return blockingStub
                .listProducts(ListProductsRequest.getDefaultInstance())
                .getProductsList().stream()
                .map(ProductDto::from)
                .toList();
    }

    @GetMapping("/products/{sku}")
    public ProductDto getProduct(@PathVariable String sku) {
        // DEADLINES: the single most important gRPC production habit.
        // A deadline is an absolute point in time after which the CALL is
        // abandoned - it propagates over the wire, so the server (and any
        // service the server calls in turn!) can stop wasted work. Without
        // one, a hung downstream holds your threads forever.
        //
        // withDeadlineAfter returns a NEW stub (stubs are immutable), so
        // this is a per-call setting - exactly what you want.
        return ProductDto.from(blockingStub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .getProduct(GetProductRequest.newBuilder().setSku(sku).build()));
        // If the server misses the deadline the client gets
        // DEADLINE_EXCEEDED, which GrpcStatusRestAdvice maps to HTTP 504.
    }

    // ------------------------------------------------------------------
    // SERVER STREAMING via async stub, fanned out as Server-Sent Events
    // ------------------------------------------------------------------

    /**
     * Bridges a gRPC server stream to an HTTP SSE stream: each StockUpdate
     * the inventory server pushes becomes one SSE event in the browser.
     * Try it: {@code curl -N localhost:8080/api/products/SKU-0001/stock/stream}
     */
    @GetMapping(value = "/products/{sku}/stock/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStock(@PathVariable String sku,
            @RequestParam(defaultValue = "5") int updates) {

        // 0L = no servlet timeout; the gRPC deadline below bounds the call.
        SseEmitter emitter = new SseEmitter(0L);

        asyncStub
                .withDeadlineAfter(30, TimeUnit.SECONDS)
                .watchStock(
                        WatchStockRequest.newBuilder().setSku(sku).setMaxUpdates(updates).build(),
                        new StreamObserver<>() {
                            // These callbacks run on gRPC threads, not the
                            // request thread - the HTTP request returned the
                            // emitter long ago. This is push, not polling.
                            @Override
                            public void onNext(StockUpdate update) {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("stock-update")
                                            .data(StockUpdateDto.from(update)));
                                }
                                catch (Exception ex) {
                                    // Browser tab closed - nothing to do; the
                                    // deadline will reap the gRPC call.
                                    log.debug("SSE client went away", ex);
                                }
                            }

                            @Override
                            public void onError(Throwable t) {
                                emitter.completeWithError(t);
                            }

                            @Override
                            public void onCompleted() {
                                emitter.complete();
                            }
                        });

        return emitter;
    }

    // ------------------------------------------------------------------
    // CLIENT STREAMING via async stub
    // ------------------------------------------------------------------

    /**
     * Takes a JSON array of shipments and streams them up the gRPC call one
     * message at a time. With a real source (a scanner gun, a file, a queue)
     * the items would be streamed as produced - the array here just keeps
     * the demo curl-able.
     */
    @PostMapping("/shipments")
    public ShipmentSummaryDto recordShipments(@RequestBody List<ShipmentDto> shipments)
            throws InterruptedException {

        CountDownLatch done = new CountDownLatch(1);
        List<ShipmentSummary> result = new CopyOnWriteArrayList<>();
        List<Throwable> failure = new CopyOnWriteArrayList<>();

        // For client streaming the stub gives US an observer to write into,
        // and we hand IT an observer for the single response. Fully async -
        // so this MVC endpoint bridges back to blocking with a latch.
        StreamObserver<Shipment> upload = asyncStub.recordShipments(new StreamObserver<>() {
            @Override public void onNext(ShipmentSummary summary) { result.add(summary); }
            @Override public void onError(Throwable t) { failure.add(t); done.countDown(); }
            @Override public void onCompleted() { done.countDown(); }
        });

        try {
            for (ShipmentDto dto : shipments) {
                upload.onNext(Shipment.newBuilder()
                        .setSku(dto.sku())
                        .setQuantity(dto.quantity())
                        .setSupplier(dto.supplier() == null ? "unknown" : dto.supplier())
                        .build());
            }
            // Half-close: tells the server "no more messages", which
            // triggers its aggregated ShipmentSummary response.
            upload.onCompleted();
        }
        catch (RuntimeException ex) {
            upload.onError(ex); // abort the stream cleanly on our side
            throw ex;
        }

        if (!done.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for shipment summary");
        }
        if (!failure.isEmpty()) {
            throw asRuntime(failure.getFirst());
        }
        return ShipmentSummaryDto.from(result.getFirst());
    }

    // ------------------------------------------------------------------
    // BIDIRECTIONAL STREAMING via async stub
    // ------------------------------------------------------------------

    /**
     * Streams a batch of orders while simultaneously receiving per-order
     * confirmations. Watch the storefront logs: responses begin arriving
     * BEFORE the last order has been sent - that interleaving is the
     * signature of bidirectional streaming.
     */
    @PostMapping("/orders")
    public OrderBatchResultDto processOrders(@RequestBody List<OrderDto> orders)
            throws InterruptedException {

        CountDownLatch done = new CountDownLatch(1);
        List<OrderStatusDto> statuses = new CopyOnWriteArrayList<>();
        List<Throwable> failure = new CopyOnWriteArrayList<>();

        StreamObserver<OrderRequest> orderStream = asyncStub.processOrders(new StreamObserver<>() {
            @Override
            public void onNext(OrderStatus status) {
                // Arrives while we may still be sending below.
                log.info("<- order {} : {}", status.getOrderId(), status.getResult());
                statuses.add(OrderStatusDto.from(status));
            }
            @Override public void onError(Throwable t) { failure.add(t); done.countDown(); }
            @Override public void onCompleted() { done.countDown(); }
        });

        for (OrderDto dto : orders) {
            log.info("-> order {} ({} x {})", dto.orderId(), dto.quantity(), dto.sku());
            orderStream.onNext(OrderRequest.newBuilder()
                    .setOrderId(dto.orderId())
                    .setSku(dto.sku())
                    .setQuantity(dto.quantity())
                    .build());
        }
        orderStream.onCompleted();

        if (!done.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for order confirmations");
        }
        if (!failure.isEmpty()) {
            throw asRuntime(failure.getFirst());
        }
        return new OrderBatchResultDto(statuses);
    }

    private static RuntimeException asRuntime(Throwable t) {
        return t instanceof RuntimeException re ? re : new RuntimeException(t);
    }
}
