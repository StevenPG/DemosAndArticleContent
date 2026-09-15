package com.stevenpg.fleet.domain.aircraft;

public final class AircraftMapper {

    private AircraftMapper() {}

    public static AircraftDto toDto(Aircraft entity) {
        return new AircraftDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getTailNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Aircraft toEntity(AircraftDto dto) {
        Aircraft entity = new Aircraft();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? AircraftStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setTailNumber(dto.tailNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
