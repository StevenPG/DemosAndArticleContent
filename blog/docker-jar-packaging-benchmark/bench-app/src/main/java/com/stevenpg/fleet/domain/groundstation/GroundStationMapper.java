package com.stevenpg.fleet.domain.groundstation;

public final class GroundStationMapper {

    private GroundStationMapper() {}

    public static GroundStationDto toDto(GroundStation entity) {
        return new GroundStationDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getSiteCode(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static GroundStation toEntity(GroundStationDto dto) {
        GroundStation entity = new GroundStation();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? GroundStationStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setSiteCode(dto.siteCode());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
