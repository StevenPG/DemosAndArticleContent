package com.stevenpg.fleet.domain.certification;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record CertificationEvent(Long id, String code, CertificationStatus status, Instant occurredAt) {

    public static CertificationEvent from(Certification entity) {
        return new CertificationEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
