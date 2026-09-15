package com.stevenpg.fleet.domain.crew;

import java.time.Instant;

/** Emitted on every write - kept on the classpath to grow the class count the
 * way a real event-carrying service would. */
public record CrewMemberEvent(Long id, String code, CrewMemberStatus status, Instant occurredAt) {

    public static CrewMemberEvent from(CrewMember entity) {
        return new CrewMemberEvent(entity.getId(), entity.getCode(), entity.getStatus(), Instant.now());
    }
}
