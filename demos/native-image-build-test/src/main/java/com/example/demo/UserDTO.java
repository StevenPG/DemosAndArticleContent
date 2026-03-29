package com.example.demo;

import jakarta.validation.constraints.*;

public class UserDTO {
    @NotBlank
    @Size(min = 3, max = 50)
    private String username;

    @Email
    private String email;

    @Pattern(regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$")
    private String password;

    @Min(18)
    private int age;

    // Getters and setters omitted for brevity
}
