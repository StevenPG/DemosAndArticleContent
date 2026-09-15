package com.stevenpg.fleet.domain.satellite;

public final class SatelliteLinkMapper {

    private SatelliteLinkMapper() {}

    public static SatelliteLinkDto toDto(SatelliteLink entity) {
        return new SatelliteLinkDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getNoradId(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static SatelliteLink toEntity(SatelliteLinkDto dto) {
        SatelliteLink entity = new SatelliteLink();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? SatelliteLinkStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setNoradId(dto.noradId());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
