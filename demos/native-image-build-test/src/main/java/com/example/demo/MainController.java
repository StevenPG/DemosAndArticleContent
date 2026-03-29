package com.example.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@Validated
public class MainController {

    @GetMapping("/users")
    public ResponseEntity<List<String>> getAllUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(List.of("user1", "user2"));
    }

    @PostMapping("/users")
    public ResponseEntity<String> createUser(
            @Valid @RequestBody UserDTO user) {
        return ResponseEntity.ok("User created");
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<String> updateUser(
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
            String id,
            @Valid @RequestBody UserDTO user) {
        return ResponseEntity.ok("User updated");
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(
            @PathVariable @NotBlank String id) {
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/orders")
    public ResponseEntity<String> createOrder(
            @Valid @RequestBody OrderDTO order) {
        return ResponseEntity.ok("Order created");
    }

    @GetMapping("/products")
    public ResponseEntity<List<String>> searchProducts(
            @RequestParam @Size(min = 3, max = 50) String query,
            @RequestParam(required = false) @Pattern(regexp = "^(asc|desc)$") String sort) {
        return ResponseEntity.ok(List.of("product1", "product2"));
    }

    @PostMapping("/feedback")
    public ResponseEntity<String> submitFeedback(
            @Valid @RequestBody FeedbackDTO feedback) {
        return ResponseEntity.ok("Feedback received");
    }

    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Integer>> getStatistics(
            @RequestParam @Past java.time.LocalDate startDate,
            @RequestParam @PastOrPresent java.time.LocalDate endDate) {
        return ResponseEntity.ok(Map.of("visits", 100, "purchases", 50));
    }
}
