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

import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.lessThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fuzz tests for UserController.
 *
 * HOW JAZZER WORKS WITH JUNIT 5
 * ─────────────────────────────
 * Methods annotated with {@link FuzzTest} behave differently depending on how the
 * test suite is run:
 *
 *   Regression mode (default — runs during {@code ./gradlew test}):
 *     Jazzer replays the empty input plus every file in the seed-corpus directory:
 *       src/test/resources/com/stevenpg/fuzzingdemo/fuzz/UserControllerFuzzTestInputs/<methodName>/
 *     Each corpus file becomes its own test invocation. This ensures previously
 *     discovered crashes are never silently re-introduced.
 *
 *   Fuzzing mode (set the env var {@code JAZZER_FUZZ=1}):
 *     Jazzer generates new inputs guided by code coverage. It mutates byte
 *     sequences, measures which new branches are reached, and retains inputs
 *     that increase coverage. When it triggers an unhandled exception it saves the
 *     offending bytes as a new corpus entry and reports the failure.
 *
 * TWO WAYS TO RECEIVE FUZZED INPUT
 * ─────────────────────────────────
 *   byte[] data              — the raw bytes ARE the input. Ideal when the thing you
 *                              fuzz is itself a byte stream (an HTTP body, a query
 *                              value). Corpus files are literal and human-readable.
 *   FuzzedDataProvider data  — a cursor that hands out typed values (consumeInt,
 *                              consumeString, ...). Ideal for building structured,
 *                              multi-field requests. Jazzer mutates the typed values
 *                              intelligently.
 *
 * This class uses both so you can compare them directly.
 */
@SpringBootTest
class UserControllerFuzzTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        // Build MockMvc from the full application context so that all Spring MVC
        // components (DispatcherServlet, exception handlers, validators, etc.) are
        // wired in — matching production behaviour as closely as possible.
        this.mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
    }

    // -------------------------------------------------------------------------
    // Fuzz: GET /api/users?name=<fuzzed>          [byte[] style]
    // -------------------------------------------------------------------------

    /**
     * Feeds arbitrary bytes as the {@code name} query parameter.
     *
     * The invariant under test: no name value — regardless of length or content —
     * should produce a 5xx response. Unexpected exceptions must be converted to a
     * 4xx by the application, never a 500.
     *
     * ⚠️ This test FAILS in regression mode against the pre-committed seed corpus file
     * {@code UserControllerFuzzTestInputs/fuzzSearchByName/crash-short-name} (contents:
     * the two characters "ab"). The planted bug in {@code UserController#buildSearchPrefix}
     * throws {@link StringIndexOutOfBoundsException} for 1- and 2-character names.
     *
     * Note the empty input passes: the controller guards the empty-string case — it
     * only forgot the 1-2 character case, which is precisely what the corpus file probes.
     */
    @FuzzTest
    void fuzzSearchByName(byte[] data) throws Exception {
        // The raw fuzz bytes ARE the query-parameter value.
        String name = new String(data, StandardCharsets.UTF_8);

        mockMvc.perform(get("/api/users")
                        .param("name", name))
                // ASSERTION: status must be < 500. 400 (constraint violation) is acceptable; 500 is a bug.
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: POST /api/users  — full request body    [byte[] style]
    // -------------------------------------------------------------------------

    /**
     * Sends the raw fuzz bytes directly as the JSON request body. Many inputs produce
     * 400 (unparseable JSON or validation failure); the fuzz engine still benefits by
     * learning which byte patterns reach deeper code paths. No planted bug here — a
     * well-behaved endpoint should never return 500 for a malformed body.
     */
    @FuzzTest
    void fuzzCreateUser(byte[] data) throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(data))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: GET /api/users/{id}   — path variable    [FuzzedDataProvider style]
    // -------------------------------------------------------------------------

    /**
     * Exercises the path variable constraint on {@code GET /api/users/{id}}.
     * Inputs that pass the {@code @Pattern} reach the controller body; everything
     * else is rejected with 400. Either way, no 500 should occur.
     */
    @FuzzTest
    void fuzzGetUserById(FuzzedDataProvider data) throws Exception {
        String userId = data.consumeString(60);

        mockMvc.perform(get("/api/users/{id}", userId))
                .andExpect(status().is(lessThan(500)));
    }

    // -------------------------------------------------------------------------
    // Fuzz: DELETE /api/users/{id}                   [FuzzedDataProvider style]
    // -------------------------------------------------------------------------

    @FuzzTest
    void fuzzDeleteUser(FuzzedDataProvider data) throws Exception {
        String userId = data.consumeString(60);

        mockMvc.perform(delete("/api/users/{id}", userId))
                .andExpect(status().is(lessThan(500)));
    }
}
