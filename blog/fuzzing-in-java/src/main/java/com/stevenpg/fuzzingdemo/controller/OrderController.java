package com.stevenpg.fuzzingdemo.controller;

import com.stevenpg.fuzzingdemo.dto.CreateOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates:
 *   - POST with arithmetic-heavy business logic (bug territory for fuzzers)
 *   - GET with path variable and multiple query options
 *   - DELETE
 *
 * PLANTED BUG (for fuzzer to discover):
 *   {@link #createOrder} computes the order total as {@code int total = quantity * unitPrice}.
 *   Both values are 32-bit integers, so when their product exceeds {@link Integer#MAX_VALUE}
 *   the result silently wraps around — a classic "integer overflow" defect.
 *
 *   Bean Validation allows both values to reach {@link Integer#MAX_VALUE} individually, so
 *   the overflow is not caught before the method body runs.
 *
 *   Reproduced by the Jazzer seed corpus file:
 *     src/test/resources/com/stevenpg/fuzzingdemo/fuzz/OrderControllerFuzzTestInputs/fuzzCreateOrder/crash-int-overflow.json
 *   with a human-readable explanation at:
 *     src/main/resources/fuzzing-findings/crash-order-int-overflow.json
 *   Run {@code ./gradlew test} to see the assertion fail, then apply the fix and watch it pass.
 *
 *   FIX: promote to long before multiplying:
 *        {@code long total = (long) quantity * unitPrice;}
 *        and change the response field type accordingly.
 */
@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {

    // -------------------------------------------------------------------------
    // POST /api/orders   — create a new order
    // -------------------------------------------------------------------------

    /**
     * Places a new order.
     *
     * ⚠️ PLANTED BUG: The total is computed using {@code int} arithmetic.
     * When {@code quantity * unitPrice} exceeds {@link Integer#MAX_VALUE}, the
     * product silently wraps around. There is a naive {@code if (total < 0)}
     * guard below, but it is itself buggy — it only catches overflows large
     * enough to flip the sign bit, and silently misses overflows that wrap to a
     * small positive number. Both the int arithmetic AND the partial guard are
     * defects a fuzzer will expose.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody @Valid CreateOrderRequest request) {

        int quantity  = request.quantity();
        int unitPrice = request.unitPrice();

        // BUG: int * int overflows when the product exceeds Integer.MAX_VALUE.
        // FIX: long total = (long) quantity * unitPrice;  (and return it as a long)
        int total = quantity * unitPrice;

        // NAIVE OVERFLOW GUARD — also buggy. A negative total can only happen
        // after overflow, so this catches *some* overflows. But an overflow that
        // wraps to a small positive number slips through completely. Proper
        // detection would compare against (long) quantity * unitPrice.
        if (total < 0) {
            throw new ArithmeticException(
                    "Order total overflowed: quantity=%d, unitPrice=%d, total=%d"
                    .formatted(quantity, unitPrice, total));
        }

        return ResponseEntity.status(201).body(Map.of(
                "orderId",         UUID.randomUUID().toString(),
                "productId",       request.productId(),
                "quantity",        quantity,
                "unitPrice",       unitPrice,
                "total",           total,
                "shippingAddress", request.shippingAddress(),
                "status",          "PENDING"
        ));
    }

    // -------------------------------------------------------------------------
    // GET /api/orders/{orderId}   — fetch order details with optional expansion
    // -------------------------------------------------------------------------

    /**
     * Retrieves an order by ID. The optional {@code expand} query parameter
     * controls whether related entities (items, shipment) are included inline.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$")
            String orderId,

            // Callers can pass multiple expand values: ?expand=items&expand=shipment
            @RequestParam(required = false)
            List<@Pattern(regexp = "^(items|shipment|customer)$",
                          message = "expand must be one of: items, shipment, customer") String> expand) {

        Map<String, Object> order = new java.util.HashMap<>(Map.of(
                "orderId", orderId,
                "status",  "SHIPPED",
                "total",   199_99
        ));

        if (expand != null) {
            if (expand.contains("items")) {
                order.put("items", List.of(
                        Map.of("productId", "p-001", "quantity", 2, "unitPrice", 9999)
                ));
            }
            if (expand.contains("shipment")) {
                order.put("shipment", Map.of("carrier", "UPS", "trackingNumber", "1Z999AA10123456784"));
            }
            if (expand.contains("customer")) {
                order.put("customer", Map.of("id", "u-042", "name", "Dana Kim"));
            }
        }

        return ResponseEntity.ok(order);
    }

    // -------------------------------------------------------------------------
    // GET /api/orders   — list orders with filters
    // -------------------------------------------------------------------------

    /**
     * Lists orders for a given customer, with optional status filter and pagination.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestParam
            @NotBlank
            String customerId,

            @RequestParam(required = false)
            @Pattern(regexp = "^(PENDING|SHIPPED|DELIVERED|CANCELLED)$",
                     message = "status must be one of: PENDING, SHIPPED, DELIVERED, CANCELLED")
            String status,

            @RequestParam(defaultValue = "0")  @Min(0)           Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {

        return ResponseEntity.ok(Map.of(
                "content",       List.of(),
                "page",          page,
                "size",          size,
                "totalElements", 0
        ));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/orders/{orderId}   — cancel an order
    // -------------------------------------------------------------------------

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$")
            String orderId) {

        return ResponseEntity.noContent().build();
    }
}
