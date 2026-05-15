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
 * A coverage-guided fuzzer discovers this path because:
 *   1. It generates a JSON body where "metadata" is present (increases coverage).
 *   2. It then places the key "category" inside the map (more coverage).
 *   3. It sets a null value for that key (reaches the unchecked call).
 *   4. The NullPointerException propagates to a 500, failing the invariant.
 *
 * The pre-committed corpus reproduces step 4 directly.
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
     * Sends the raw fuzz bytes directly as the JSON POST body for /api/products.
     *
     * ⚠️ This test FAILS in regression mode against the pre-committed seed corpus file
     * {@code ProductControllerFuzzTestInputs/fuzzCreateProduct/crash-metadata-npe.json},
     * which sends {@code "metadata": {"category": null}}. The planted bug in
     * {@code ProductController#resolveCategory} calls {@code .toUpperCase()} on that
     * null map value, throwing NullPointerException — surfaced as a 500.
     */
    @FuzzTest
    void fuzzCreateProduct(byte[] data) throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data))
                .andExpect(status().is(lessThan(500)));
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
