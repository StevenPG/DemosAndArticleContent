package com.stevenpg.fleet.domain.telemetry;

public final class TelemetryFrameMapper {

    private TelemetryFrameMapper() {}

    public static TelemetryFrameDto toDto(TelemetryFrame entity) {
        return new TelemetryFrameDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getFrameId(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static TelemetryFrame toEntity(TelemetryFrameDto dto) {
        TelemetryFrame entity = new TelemetryFrame();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? TelemetryFrameStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setFrameId(dto.frameId());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
