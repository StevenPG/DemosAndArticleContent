package com.stevenpg.fleet.domain.sensor;

public final class SensorMapper {

    private SensorMapper() {}

    public static SensorDto toDto(Sensor entity) {
        return new SensorDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getChannel(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Sensor toEntity(SensorDto dto) {
        Sensor entity = new Sensor();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? SensorStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setChannel(dto.channel());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
