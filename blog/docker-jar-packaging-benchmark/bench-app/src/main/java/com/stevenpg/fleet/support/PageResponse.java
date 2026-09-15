package com.stevenpg.fleet.support;

import java.util.List;
import org.springframework.data.domain.Page;

/** Stable page envelope - Boot 4 warns about serializing PageImpl directly. */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
