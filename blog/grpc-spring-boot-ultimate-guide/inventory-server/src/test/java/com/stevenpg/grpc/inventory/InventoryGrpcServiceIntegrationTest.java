package com.stevenpg.grpc.inventory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.grpc.test.autoconfigure.AutoConfigureTestGrpcTransport;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.grpc.client.GrpcChannelFactory;

import com.stevenpg.grpc.inventory.proto.GetProductRequest;
import com.stevenpg.grpc.inventory.proto.InventoryServiceGrpc;
import com.stevenpg.grpc.inventory.proto.ListProductsRequest;
import com.stevenpg.grpc.inventory.proto.OrderRequest;
import com.stevenpg.grpc.inventory.proto.OrderStatus;
import com.stevenpg.grpc.inventory.proto.Product;
import com.stevenpg.grpc.inventory.proto.Shipment;
import com.stevenpg.grpc.inventory.proto.ShipmentSummary;
import com.stevenpg.grpc.inventory.proto.StockUpdate;
import com.stevenpg.grpc.inventory.proto.WatchStockRequest;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Full-stack integration test WITHOUT opening a network port.
 *
 * <p>{@code @AutoConfigureTestGrpcTransport} (from Boot 4.1's
 * spring-boot-starter-grpc-server-test) swaps the Netty server for gRPC's
 * in-process transport. Requests still flow through the complete server
 * pipeline - interceptors, exception handlers, marshalling - so this tests
 * exactly what production runs, minus TCP. The {@link GrpcChannelFactory}
 * injected here hands back an in-process channel regardless of the address
 * we pass it.
 */
@SpringBootTest
@AutoConfigureTestGrpcTransport
class InventoryGrpcServiceIntegrationTest {

    @Autowired
    private GrpcChannelFactory channels;

    private InventoryServiceGrpc.InventoryServiceBlockingStub blockingStub() {
        return InventoryServiceGrpc.newBlockingStub(channels.createChannel("0.0.0.0:0"));
    }

    private InventoryServiceGrpc.InventoryServiceStub asyncStub() {
        return InventoryServiceGrpc.newStub(channels.createChannel("0.0.0.0:0"));
    }

    // ---- unary ----------------------------------------------------------

    @Test
    void getProductReturnsSeededProduct() {
        Product product = blockingStub().getProduct(
                GetProductRequest.newBuilder().setSku("SKU-0001").build());

        assertThat(product.getName()).isEqualTo("Mechanical Keyboard");
        assertThat(product.getPriceCents()).isEqualTo(12999);
    }

    @Test
    void getProductMapsUnknownSkuToNotFoundStatus() {
        // Verifies the GrpcExceptionHandler: the business exception must
        // surface to clients as NOT_FOUND, not UNKNOWN.
        StatusRuntimeException ex = catchThrowableOfType(StatusRuntimeException.class,
                () -> blockingStub().getProduct(
                        GetProductRequest.newBuilder().setSku("NOPE").build()));

        assertThat(ex.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
        assertThat(ex.getStatus().getDescription()).contains("NOPE");
    }

    @Test
    void listProductsReturnsWholeCatalog() {
        var response = blockingStub().listProducts(ListProductsRequest.getDefaultInstance());
        assertThat(response.getProductsCount()).isGreaterThanOrEqualTo(5);
    }

    // ---- server streaming ------------------------------------------------

    @Test
    void watchStockStreamsRequestedNumberOfUpdates() {
        // Blocking stubs expose server streams as an Iterator - each next()
        // blocks until the server pushes another message.
        var updates = blockingStub().watchStock(WatchStockRequest.newBuilder()
                .setSku("SKU-0004")
                .setMaxUpdates(3)
                .build());

        List<StockUpdate> received = new CopyOnWriteArrayList<>();
        updates.forEachRemaining(received::add);

        assertThat(received).hasSize(3);
        assertThat(received).allSatisfy(update ->
                assertThat(update.getReasonCase()).isNotEqualTo(StockUpdate.ReasonCase.REASON_NOT_SET));
    }

    // ---- client streaming --------------------------------------------------

    @Test
    void recordShipmentsAggregatesTheWholeStream() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<ShipmentSummary> summaries = new CopyOnWriteArrayList<>();

        // Client streaming requires the async stub: we get an observer to
        // write into, and pass an observer that receives the single summary.
        StreamObserver<Shipment> upload = asyncStub().recordShipments(new StreamObserver<>() {
            @Override public void onNext(ShipmentSummary summary) { summaries.add(summary); }
            @Override public void onError(Throwable t) { done.countDown(); }
            @Override public void onCompleted() { done.countDown(); }
        });

        upload.onNext(Shipment.newBuilder().setSku("SKU-0002").setQuantity(10).setSupplier("Acme").build());
        upload.onNext(Shipment.newBuilder().setSku("SKU-0003").setQuantity(5).setSupplier("Acme").build());
        upload.onCompleted();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().getShipmentsReceived()).isEqualTo(2);
        assertThat(summaries.getFirst().getTotalUnitsAdded()).isEqualTo(15);
    }

    // ---- bidirectional streaming -----------------------------------------

    @Test
    void processOrdersRespondsPerOrderWithCorrelationIds() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<OrderStatus> statuses = new CopyOnWriteArrayList<>();

        StreamObserver<OrderRequest> orders = asyncStub().processOrders(new StreamObserver<>() {
            @Override public void onNext(OrderStatus status) { statuses.add(status); }
            @Override public void onError(Throwable t) { done.countDown(); }
            @Override public void onCompleted() { done.countDown(); }
        });

        orders.onNext(OrderRequest.newBuilder()
                .setOrderId("order-1").setSku("SKU-0001").setQuantity(1).build());
        orders.onNext(OrderRequest.newBuilder()
                .setOrderId("order-2").setSku("DOES-NOT-EXIST").setQuantity(1).build());
        orders.onCompleted();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(statuses).hasSize(2);
        assertThat(statuses.get(0).getOrderId()).isEqualTo("order-1");
        assertThat(statuses.get(0).getResult()).isEqualTo(OrderStatus.Result.RESULT_CONFIRMED);
        assertThat(statuses.get(1).getOrderId()).isEqualTo("order-2");
        assertThat(statuses.get(1).getResult()).isEqualTo(OrderStatus.Result.RESULT_REJECTED_UNKNOWN_SKU);
    }
}
