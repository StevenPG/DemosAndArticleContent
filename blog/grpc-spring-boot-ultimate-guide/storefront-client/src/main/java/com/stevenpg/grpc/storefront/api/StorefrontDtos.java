package com.stevenpg.grpc.storefront.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.stevenpg.grpc.inventory.proto.OrderStatus;
import com.stevenpg.grpc.inventory.proto.Product;
import com.stevenpg.grpc.inventory.proto.ShipmentSummary;
import com.stevenpg.grpc.inventory.proto.StockUpdate;

/**
 * JSON DTOs for the REST edge, plus mappers from the protobuf messages.
 *
 * <p>Why not serialize the protobuf classes directly? Generated protobuf
 * Java classes are not JavaBean-friendly (Jackson trips over their internal
 * fields), and more importantly the REST contract should be free to differ
 * from the internal gRPC contract - here we render cents as a formatted
 * price string, and enums as trimmed names. If you ever DO want mechanical
 * proto<->JSON, protobuf ships {@code JsonFormat} in protobuf-java-util
 * which implements the official proto3 JSON mapping.
 */
public final class StorefrontDtos {

    private StorefrontDtos() {
    }

    public record ProductDto(
            String sku,
            String name,
            String description,
            String price,
            int quantityAvailable,
            String category,
            Instant updatedAt) {

        static ProductDto from(Product proto) {
            return new ProductDto(
                    proto.getSku(),
                    proto.getName(),
                    proto.getDescription(),
                    formatPrice(proto.getPriceCents(), proto.getCurrency()),
                    proto.getQuantityAvailable(),
                    // PRODUCT_CATEGORY_BOOKS -> BOOKS
                    proto.getCategory().name().replace("PRODUCT_CATEGORY_", ""),
                    Instant.ofEpochSecond(proto.getUpdatedAt().getSeconds(),
                            proto.getUpdatedAt().getNanos()));
        }
    }

    public record StockUpdateDto(
            String sku,
            int quantityAvailable,
            String reason,
            String detail,
            Instant occurredAt) {

        static StockUpdateDto from(StockUpdate proto) {
            // The oneof from the proto surfaces as a ReasonCase enum - the
            // generated code guarantees only one branch is populated.
            String reason;
            String detail;
            switch (proto.getReasonCase()) {
                case SALE -> {
                    reason = "SALE";
                    detail = proto.getSale().getUnitsSold() + " units sold";
                }
                case RESTOCK -> {
                    reason = "RESTOCK";
                    detail = proto.getRestock().getUnitsAdded() + " units from "
                            + proto.getRestock().getSupplier();
                }
                default -> {
                    reason = "UNKNOWN";
                    detail = "";
                }
            }
            return new StockUpdateDto(
                    proto.getSku(),
                    proto.getQuantityAvailable(),
                    reason,
                    detail,
                    Instant.ofEpochSecond(proto.getOccurredAt().getSeconds(),
                            proto.getOccurredAt().getNanos()));
        }
    }

    /** Inbound JSON for the client-streaming demo. */
    public record ShipmentDto(String sku, int quantity, String supplier) {
    }

    public record ShipmentSummaryDto(
            int shipmentsReceived,
            int totalUnitsAdded,
            Map<String, Integer> updatedQuantities) {

        static ShipmentSummaryDto from(ShipmentSummary proto) {
            return new ShipmentSummaryDto(
                    proto.getShipmentsReceived(),
                    proto.getTotalUnitsAdded(),
                    proto.getUpdatedQuantitiesMap());
        }
    }

    /** Inbound JSON for the bidirectional-streaming demo. */
    public record OrderDto(String orderId, String sku, int quantity) {
    }

    public record OrderStatusDto(
            String orderId,
            String result,
            String message,
            String totalPrice) {

        static OrderStatusDto from(OrderStatus proto) {
            return new OrderStatusDto(
                    proto.getOrderId(),
                    proto.getResult().name().replace("RESULT_", ""),
                    proto.getMessage(),
                    proto.getTotalPriceCents() > 0
                            ? formatPrice(proto.getTotalPriceCents(), "USD")
                            : null);
        }
    }

    public record OrderBatchResultDto(List<OrderStatusDto> statuses) {
    }

    private static String formatPrice(long cents, String currency) {
        return String.format("%d.%02d %s", cents / 100, cents % 100, currency);
    }
}
