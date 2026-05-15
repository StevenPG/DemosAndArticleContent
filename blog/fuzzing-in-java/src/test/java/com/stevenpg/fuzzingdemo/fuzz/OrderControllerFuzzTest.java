package com.stevenpg.fuzzingdemo.fuzz;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fuzz tests for OrderController.
 *
 * INTEGER OVERFLOW — WHY FUZZING FINDS IT
 * ─────────────────────────────────────────
 * The planted bug is in {@code createOrder}: two {@code int} fields are multiplied
 * together using 32-bit arithmetic. When both values are individually valid (each
 * ≤ Integer.MAX_VALUE) but their product exceeds 2^31 - 1, Java silently wraps
 * the result. This demo makes the overflow detectable by throwing an explicit
 * ArithmeticException, which becomes a 500 that the fuzz test catches.
 *
 * Manual testing rarely finds this because engineers test with "reasonable" values.
 * Fuzzing automatically explores the extreme edges of the valid input space — and a
 * coverage-guided fuzzer will grow the digit count of the quantity / unitPrice fields
 * until the product crosses the 32-bit boundary.
 */
@SpringBootTest
class OrderControllerFuzzTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // -------------------------------------------------------------------------
    // Fuzz: POST /api/orders   — planted integer overflow    [byte[] style]
    // -------------------------------------------------------------------------

    /**
     * Sends the raw fuzz bytes directly as the JSON request body for order creation.
     *
     * ⚠️ This test FAILS in regression mode against the pre-committed seed corpus file
     * {@code OrderControllerFuzzTestInputs/fuzzCreateOrder/crash-int-overflow.json},
     * which sets quantity=100000 and unitPrice=100000. Their product (10,000,000,000)
     * overflows 32-bit int, the planted bug throws ArithmeticException, and
     * GlobalExceptionHandler turns it into a 500 — failing the assertion below.
     *
     * Using {@code byte[]} (the HTTP body IS the fuzz target) keeps the seed corpus a
     * literal, human-readable JSON file. A coverage-guided run still finds the overflow
     * by mutating the digits of the numeric fields.
     */
    @FuzzTest
    void fuzzCreateOrder(byte[] data) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data))
                // ASSERTION: ArithmeticException from overflow produces 500 via GlobalExceptionHandler,
                // which is exactly what this assertion catches.
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: GET /api/orders/{orderId}   — path variable + expand params
    // -------------------------------------------------------------------------

    /**
     * Exercises the optional {@code expand} query parameter. The allow-list
     * constraint on each value should prevent unexpected input, but the fuzzer
     * may find parameter combinations that slip through.
     */
    @FuzzTest
    void fuzzGetOrder(FuzzedDataProvider data) throws Exception {
        String orderId = data.consumeString(60);
        String expand  = data.consumeString(50);

        mockMvc.perform(get("/api/orders/{orderId}", orderId)
                        .param("expand", expand))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: GET /api/orders   — list with customer + status filter
    // -------------------------------------------------------------------------

    @FuzzTest
    void fuzzListOrders(FuzzedDataProvider data) throws Exception {
        String customerId = data.consumeString(50);
        String status     = data.consumeString(20);
        int    page       = data.consumeInt();
        int    size       = data.consumeInt();

        mockMvc.perform(get("/api/orders")
                        .param("customerId", customerId)
                        .param("status",     status)
                        .param("page",       String.valueOf(page))
                        .param("size",       String.valueOf(size)))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: DELETE /api/orders/{orderId}
    // -------------------------------------------------------------------------

    @FuzzTest
    void fuzzCancelOrder(FuzzedDataProvider data) throws Exception {
        String orderId = data.consumeString(60);

        mockMvc.perform(delete("/api/orders/{orderId}", orderId))
                .andExpect(status().is(lessThan(500)));
    }
}
