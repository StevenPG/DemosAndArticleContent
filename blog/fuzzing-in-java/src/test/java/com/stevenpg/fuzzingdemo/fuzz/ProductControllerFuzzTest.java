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
 * Fuzz tests for ProductController.
 *
 * WHAT MAKES POST /api/products INTERESTING FOR FUZZING
 * ──────────────────────────────────────────────────────
 * The request body contains a nested {@code Map<String, String> metadata} field.
 * Jakarta Bean Validation has no built-in constraint that validates the *values*
 * inside a map, only the map reference itself (@NotNull) or its size (@Size).
 *
 * This means a map entry like {@code "category": null} passes all declared
 * constraints but then crashes inside {@code ProductController#resolveCategory}
 * when the null value has {@code .toUpperCase()} called on it.
 *
 * WHY FuzzedDataProvider INSTEAD OF byte[]
 * ─────────────────────────────────────────
 * Sending raw bytes as a JSON body causes the fuzzer to stall: nearly every
 * mutation produces either invalid JSON or a body missing required fields, both
 * of which return 400 before reaching any application logic. Coverage stays flat
 * and the fuzzer has no signal to guide it toward the buggy code path.
 *
 * By constructing the request field-by-field with FuzzedDataProvider, the fuzzer
 * always generates inputs that pass Bean Validation, giving it coverage signal
 * inside the controller. It then quickly discovers that setting the "category"
 * metadata value to null triggers a NullPointerException and a 500 response.
 */
@SpringBootTest
class ProductControllerFuzzTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // -------------------------------------------------------------------------
    // Fuzz: POST /api/products   — planted NullPointerException
    // -------------------------------------------------------------------------

    /**
     * Constructs a structurally valid POST /api/products body from fuzz data so that
     * Bean Validation always passes and the fuzzer's mutations reach controller logic.
     *
     * With raw byte[] input every mutation produces invalid JSON or missing required
     * fields, all of which return 400 — the fuzzer sees flat coverage and never reaches
     * {@code resolveCategory}. By building the request field-by-field here, the fuzzer
     * always gets past validation and can explore the code paths that follow, including
     * the {@code metadata.get("category").toUpperCase()} NPE when the map value is null.
     */
    @FuzzTest
    void fuzzCreateProduct(FuzzedDataProvider data) throws Exception {
        // Required fields — always present so @NotBlank / @NotNull pass.
        String name = sanitizeForJson(data.consumeString(200));
        if (name.isBlank()) name = "x";

        // consumeInt keeps price within @DecimalMin(0.01) / @DecimalMax(999999.99).
        String price = String.format("%.2f", data.consumeInt(1, 9_999_999) / 100.0);

        // metadata is where the bug lives: {"category": null} triggers the NPE.
        String metadata = "";
        if (data.consumeBoolean()) {
            String value = data.consumeBoolean()
                    ? "null"
                    : "\"" + sanitizeForJson(data.consumeString(50)) + "\"";
            metadata = ",\"metadata\":{\"category\":" + value + "}";
        }

        String body = "{\"name\":\"" + name + "\",\"price\":" + price + metadata + "}";

        try {
            mockMvc.perform(post("/api/products")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().is(lessThan(500)));
        } catch (AssertionError e) {
            throw new AssertionError("Crashing input: " + body, e);
        }
    }

    /** Strip characters that would break the hand-rolled JSON string literals above. */
    private static String sanitizeForJson(String s) {
        return s.replace("\\", "").replace("\"", "").replace("\n", "").replace("\r", "");
    }

    // -------------------------------------------------------------------------
    // Fuzz: GET /api/products   — query parameter combinations
    // -------------------------------------------------------------------------

    /**
     * Explores all combinations of the list-products query parameters.
     * The constraint layer rejects most fuzzed values, but valid combinations
     * should always produce a 200, never a 500.
     */
    @FuzzTest
    void fuzzListProducts(FuzzedDataProvider data) throws Exception {
        String category = data.consumeString(50);
        String minPrice = data.consumeString(20);
        String maxPrice = data.consumeString(20);
        String sortBy   = data.consumeString(20);
        String sortDir  = data.consumeString(10);
        int    page     = data.consumeInt();
        int    size     = data.consumeInt();

        mockMvc.perform(get("/api/products")
                        .param("category", category)
                        .param("minPrice",  minPrice)
                        .param("maxPrice",  maxPrice)
                        .param("sortBy",    sortBy)
                        .param("sortDir",   sortDir)
                        .param("page",      String.valueOf(page))
                        .param("size",      String.valueOf(size)))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: PUT /api/products/{id}   — partial update
    // -------------------------------------------------------------------------

    /**
     * Sends arbitrary JSON bodies to the update endpoint. Valid partial-update
     * bodies should produce 200; invalid ones 400; never 500.
     */
    @FuzzTest
    void fuzzUpdateProduct(FuzzedDataProvider data) throws Exception {
        String productId = data.consumeString(60);
        byte[] body      = data.consumeRemainingAsBytes();

        mockMvc.perform(put("/api/products/{id}", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: DELETE /api/products/{id}
    // -------------------------------------------------------------------------

    @FuzzTest
    void fuzzDeleteProduct(FuzzedDataProvider data) throws Exception {
        String productId = data.consumeString(60);

        mockMvc.perform(delete("/api/products/{id}", productId))
                .andExpect(status().is(lessThan(500)));
    }
}
