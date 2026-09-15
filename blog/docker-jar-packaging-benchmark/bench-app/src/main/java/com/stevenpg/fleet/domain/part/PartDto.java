package com.stevenpg.fleet.domain.part;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record PartDto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        PartStatus status,
        String region,
        String partNumber,
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {}
