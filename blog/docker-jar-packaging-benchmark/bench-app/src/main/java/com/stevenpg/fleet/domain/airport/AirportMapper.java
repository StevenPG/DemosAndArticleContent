package com.stevenpg.fleet.domain.airport;

public final class AirportMapper {

    private AirportMapper() {}

    public static AirportDto toDto(Airport entity) {
        return new AirportDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getIcaoCode(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Airport toEntity(AirportDto dto) {
        Airport entity = new Airport();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? AirportStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setIcaoCode(dto.icaoCode());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
