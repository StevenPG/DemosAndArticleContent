package com.stevenpg.fleet.domain.supplier;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SupplierDto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        SupplierStatus status,
        String region,
        String cageCode,
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {}
