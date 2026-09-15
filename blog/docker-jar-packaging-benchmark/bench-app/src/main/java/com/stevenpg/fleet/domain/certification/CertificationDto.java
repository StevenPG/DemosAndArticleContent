package com.stevenpg.fleet.domain.certification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CertificationDto(
        Long id,
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 160) String name,
        CertificationStatus status,
        String region,
        String authority,
        @PositiveOrZero double massKg,
        @PositiveOrZero int cycles,
        boolean active,
        Instant updatedAt) {}
