package com.stevenpg.fleet.domain.avionics;

public final class AvionicsUnitMapper {

    private AvionicsUnitMapper() {}

    public static AvionicsUnitDto toDto(AvionicsUnit entity) {
        return new AvionicsUnitDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getFirmwareVersion(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static AvionicsUnit toEntity(AvionicsUnitDto dto) {
        AvionicsUnit entity = new AvionicsUnit();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? AvionicsUnitStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setFirmwareVersion(dto.firmwareVersion());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
