package com.example.demo;

import jakarta.validation.constraints.*;

public class FeedbackDTO {
    @NotBlank
    @Size(max = 500)
    private String message;

    @Min(1)
    @Max(5)
    private int rating;

    @Email
    private String userEmail;

    // Getters and setters omitted for brevity
}
