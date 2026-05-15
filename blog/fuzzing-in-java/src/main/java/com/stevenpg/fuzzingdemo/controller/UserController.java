package com.stevenpg.fuzzingdemo.controller;

import com.stevenpg.fuzzingdemo.dto.CreateUserRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates several common REST patterns:
 *   - Path variables with constraint validation
 *   - Optional query parameters with typed constraints
 *   - POST with a validated request body
 *   - DELETE
 *
 * PLANTED BUG (for fuzzer to discover):
 *   {@link #searchUsers} calls {@code name.substring(0, 3)} via {@code buildSearchPrefix}.
 *   The caller guards the null and empty cases, but forgot that 1- and 2-character
 *   names are also too short — so they reach that line and throw
 *   {@link StringIndexOutOfBoundsException}.
 *
 *   The pre-committed Jazzer seed corpus file at:
 *     src/test/resources/com/stevenpg/fuzzingdemo/fuzz/UserControllerFuzzTestInputs/fuzzSearchByName/crash-short-name
 *   contains the input "ab" that reproduces this crash. A human-readable explanation
 *   lives at src/main/resources/fuzzing-findings/crash-user-short-name.txt.
 *   Run {@code ./gradlew test} to see it fail, then fix the bug and watch it pass.
 *
 *   FIX: replace {@code name.substring(0, 3)} with
 *        {@code name.length() >= 3 ? name.substring(0, 3) : name}
 */
@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    // -------------------------------------------------------------------------
    // GET /api/users/{id}   — path variable + regex constraint
    // -------------------------------------------------------------------------

    /**
     * Retrieves a single user by ID.
     *
     * The {@code @Pattern} constraint ensures the ID contains only URL-safe
     * characters before any logic runs, preventing path traversal inputs from
     * reaching downstream code.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getUser(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$",
                     message = "id must be 1-50 alphanumeric or hyphen characters")
            String id) {

        return ResponseEntity.ok(Map.of(
                "id", id,
                "name", "Jane Smith",
                "email", "jane.smith@example.com",
                "active", true
        ));
    }

    // -------------------------------------------------------------------------
    // GET /api/users   — multiple optional query parameters
    // -------------------------------------------------------------------------

    /**
     * Lists users with optional filters.
     *
     * ⚠️ PLANTED BUG: When {@code name} is provided and is 1 or 2 characters long,
     * the {@code buildSearchPrefix} helper throws {@link StringIndexOutOfBoundsException}.
     * The fuzz test in {@code UserControllerFuzzTest#fuzzSearchByName} detects this
     * because the test asserts no 5xx responses are returned.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> searchUsers(
            @RequestParam(required = false) @Size(max = 100) String name,
            @RequestParam(required = false) @Min(0) @Max(150) Integer minAge,
            @RequestParam(required = false) @Min(0) @Max(150) Integer maxAge,
            @RequestParam(defaultValue = "true") Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) Integer page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) Integer size) {

        // BUG (partial guard): the developer guarded the null and empty cases, but
        // forgot that 1- and 2-character names are ALSO shorter than the substring(0, 3)
        // call inside buildSearchPrefix. This "almost correct" guard is a realistic bug
        // pattern — and exactly the kind of edge case a fuzzer finds automatically.
        String prefix = (name != null && !name.isEmpty()) ? buildSearchPrefix(name) : "";

        List<Map<String, Object>> results = List.of(
                Map.of("id", "u-001", "name", "Alice Nguyen",  "age", 29, "active", true),
                Map.of("id", "u-002", "name", "Bob Okafor",    "age", 35, "active", false),
                Map.of("id", "u-003", "name", "Carol Patel",   "age", 42, "active", true)
        );

        return ResponseEntity.ok(results);
    }

    /**
     * Extracts a 3-character search prefix for use in downstream lookups.
     *
     * ⚠️ BUG: No length guard — throws StringIndexOutOfBoundsException if
     *    {@code name.length() < 3}.
     */
    private String buildSearchPrefix(String name) {
        // BUG: should be: name.length() >= 3 ? name.substring(0, 3) : name
        return name.substring(0, 3).toLowerCase();
    }

    // -------------------------------------------------------------------------
    // POST /api/users   — validated request body
    // -------------------------------------------------------------------------

    /**
     * Creates a new user. The {@code @Valid} annotation triggers Jakarta Bean
     * Validation on the request body before the method runs. Invalid inputs
     * are rejected by the global exception handler with a 400 response.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createUser(
            @RequestBody @Valid CreateUserRequest request) {

        return ResponseEntity.status(201).body(Map.of(
                "id",    UUID.randomUUID().toString(),
                "name",  request.name(),
                "email", request.email()
        ));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/users/{id}   — soft delete by ID
    // -------------------------------------------------------------------------

    /**
     * Soft-deletes a user by ID. Returns 204 No Content on success.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable
            @NotBlank
            @Size(min = 1, max = 50)
            String id) {

        // In a real implementation: mark the record as deleted in the database.
        return ResponseEntity.noContent().build();
    }
}
