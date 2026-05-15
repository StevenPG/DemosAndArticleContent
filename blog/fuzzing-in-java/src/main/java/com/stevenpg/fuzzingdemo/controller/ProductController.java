package com.stevenpg.fuzzingdemo.controller;

import com.stevenpg.fuzzingdemo.dto.CreateProductRequest;
import com.stevenpg.fuzzingdemo.dto.UpdateProductRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates:
 *   - Rich query-parameter filtering (category, price range, pagination)
 *   - POST with a complex validated body including optional correlated fields
 *   - PUT for partial updates
 *   - Path variable constraints
 *
 * PLANTED BUG (for fuzzer to discover):
 *   {@link #createProduct} accesses {@code metadata.get("category").toUpperCase()}
 *   without null-checking the map value. When the caller sends a JSON body with
 *   {@code "metadata": {"category": null}}, the map key exists but its value is
 *   {@code null}, so {@code toUpperCase()} throws {@link NullPointerException}.
 *
 *   Reproduced by the Jazzer seed corpus file:
 *     src/test/resources/com/stevenpg/fuzzingdemo/fuzz/ProductControllerFuzzTestInputs/fuzzCreateProduct/crash-metadata-npe.json
 *   with a human-readable explanation at:
 *     src/main/resources/fuzzing-findings/crash-product-metadata-npe.json
 *
 *   FIX: replace the direct call with
 *        {@code Optional.ofNullable(metadata.get("category")).map(String::toUpperCase).orElse("UNCATEGORIZED")}
 */
@RestController
@RequestMapping("/api/products")
@Validated
public class ProductController {

    // -------------------------------------------------------------------------
    // GET /api/products   — filtered list with pagination
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of products, optionally filtered by category and
     * price range. All query parameters are optional.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> listProducts(
            @RequestParam(required = false)
            @Size(max = 100)
            String category,

            @RequestParam(required = false)
            @DecimalMin("0.00")
            BigDecimal minPrice,

            @RequestParam(required = false)
            @DecimalMin("0.00")
            BigDecimal maxPrice,

            @RequestParam(defaultValue = "0")
            @Min(0)
            Integer page,

            @RequestParam(defaultValue = "20")
            @Min(1) @Max(100)
            Integer size,

            // Allowed sort fields are validated via @Pattern to prevent injection.
            @RequestParam(defaultValue = "name")
            @Pattern(regexp = "^(name|price|createdAt)$",
                     message = "sortBy must be one of: name, price, createdAt")
            String sortBy,

            @RequestParam(defaultValue = "asc")
            @Pattern(regexp = "^(asc|desc)$", message = "sortDir must be asc or desc")
            String sortDir) {

        List<Map<String, Object>> products = List.of(
                Map.of("id", "p-001", "name", "Wireless Headphones", "price", 79.99,  "category", "ELECTRONICS"),
                Map.of("id", "p-002", "name", "Organic Coffee Beans", "price", 14.50, "category", "FOOD"),
                Map.of("id", "p-003", "name", "Running Shoes",        "price", 120.00,"category", "SPORTS")
        );

        return ResponseEntity.ok(Map.of(
                "content",       products,
                "page",          page,
                "size",          size,
                "totalElements", products.size()
        ));
    }

    // -------------------------------------------------------------------------
    // POST /api/products   — create with validated body
    // -------------------------------------------------------------------------

    /**
     * Creates a new product.
     *
     * ⚠️ PLANTED BUG: When {@code metadata} is present and contains a "category"
     * key with a null value, {@code resolveCategory} throws NullPointerException.
     * Bean Validation does not check the values inside a Map, so this input
     * passes constraint validation and reaches the buggy line.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createProduct(
            @RequestBody @Valid CreateProductRequest request) {

        String category = resolveCategory(request);

        BigDecimal finalPrice = applyDiscount(request);

        return ResponseEntity.status(201).body(Map.of(
                "id",       UUID.randomUUID().toString(),
                "name",     request.name(),
                "price",    finalPrice,
                "category", category
        ));
    }

    /**
     * Reads "category" from the metadata map and normalises it to upper-case.
     *
     * ⚠️ BUG: {@code map.get("category")} returns {@code null} when the key exists
     *    but was explicitly set to JSON {@code null}. The subsequent {@code .toUpperCase()}
     *    call then throws {@link NullPointerException}.
     */
    private String resolveCategory(CreateProductRequest request) {
        if (request.metadata() == null || !request.metadata().containsKey("category")) {
            return "UNCATEGORIZED";
        }
        // BUG: no null check on the map value.
        // FIX: return Optional.ofNullable(request.metadata().get("category"))
        //              .map(String::toUpperCase).orElse("UNCATEGORIZED");
        return request.metadata().get("category").toUpperCase();
    }

    private BigDecimal applyDiscount(CreateProductRequest request) {
        if (request.discountCode() == null || request.discountPercent() == null) {
            return request.price();
        }
        BigDecimal multiplier = BigDecimal.ONE.subtract(
                request.discountPercent().divide(BigDecimal.valueOf(100)));
        return request.price().multiply(multiplier);
    }

    // -------------------------------------------------------------------------
    // GET /api/products/{id}   — single product lookup
    // -------------------------------------------------------------------------

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getProduct(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$")
            String id) {

        return ResponseEntity.ok(Map.of(
                "id",    id,
                "name",  "Example Product",
                "price", 49.99
        ));
    }

    // -------------------------------------------------------------------------
    // PUT /api/products/{id}   — partial update
    // -------------------------------------------------------------------------

    /**
     * Applies a partial update to a product. Only non-null fields in the body
     * are applied; omitted fields retain their current values.
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateProduct(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$")
            String id,

            @RequestBody @Valid UpdateProductRequest request) {

        return ResponseEntity.ok(Map.of(
                "id",    id,
                "name",  request.name()  != null ? request.name()  : "Example Product",
                "price", request.price() != null ? request.price() : BigDecimal.valueOf(49.99)
        ));
    }

    // -------------------------------------------------------------------------
    // DELETE /api/products/{id}
    // -------------------------------------------------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable
            @NotBlank
            @Pattern(regexp = "^[a-zA-Z0-9\\-]{1,50}$")
            String id) {

        return ResponseEntity.noContent().build();
    }
}
