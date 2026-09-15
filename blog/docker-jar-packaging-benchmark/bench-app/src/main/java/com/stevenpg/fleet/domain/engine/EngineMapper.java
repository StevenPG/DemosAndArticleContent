package com.stevenpg.fleet.domain.engine;

public final class EngineMapper {

    private EngineMapper() {}

    public static EngineDto toDto(Engine entity) {
        return new EngineDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getSerialNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Engine toEntity(EngineDto dto) {
        Engine entity = new Engine();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? EngineStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setSerialNumber(dto.serialNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
