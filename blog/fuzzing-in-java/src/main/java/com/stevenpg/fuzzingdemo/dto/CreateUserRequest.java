package com.stevenpg.fuzzingdemo.dto;

import jakarta.validation.constraints.*;

/**
 * Request body for POST /api/users.
 *
 * Constraint annotations here are processed by Jakarta Bean Validation before the
 * controller method body runs. A fuzzer exercising this endpoint will encounter these
 * constraints and must find inputs that slip through them to reach the business logic.
 */
public record CreateUserRequest(

        @NotBlank(message = "name is required")
        @Size(min = 2, max = 100, message = "name must be between 2 and 100 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid address")
        @Size(max = 255)
        String email,

        // Age is optional but must be in a sensible range when provided.
        @Min(value = 0, message = "age cannot be negative")
        @Max(value = 150, message = "age exceeds realistic maximum")
        Integer age,

        // A free-form biography field. No content restrictions — intentionally broad
        // to give the fuzzer room to explore downstream handling.
        @Size(max = 1000, message = "bio must not exceed 1000 characters")
        String bio
) {}
