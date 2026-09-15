package com.stevenpg.fleet.domain.engine;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record EngineDto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        EngineStatus status,
        String region,
        String serialNumber,
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {}
