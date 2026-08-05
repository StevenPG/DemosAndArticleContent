plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "zero-downtime-partition-swap"

// Shared partition-naming contract + event type used by both services.
include("common")
// Kafka batch consumer -> COPY into detached per-minute staging tables (the hot write path).
include("ingest-service")
// Index build + CHECK constraint + ATTACH PARTITION orchestration (the swap), plus the JPA read API.
include("maintenance-service")
// Naive/tuned Spring Data JPA saveAll() baseline, for the COPY comparison.
include("jpa-baseline-service")
