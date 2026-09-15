package com.stevenpg.fleet.domain.airport;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record AirportDto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        AirportStatus status,
        String region,
        String icaoCode,
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {}
