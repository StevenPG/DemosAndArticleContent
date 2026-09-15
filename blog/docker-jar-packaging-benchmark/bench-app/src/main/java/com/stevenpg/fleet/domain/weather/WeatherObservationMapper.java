package com.stevenpg.fleet.domain.weather;

public final class WeatherObservationMapper {

    private WeatherObservationMapper() {}

    public static WeatherObservationDto toDto(WeatherObservation entity) {
        return new WeatherObservationDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getMetar(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static WeatherObservation toEntity(WeatherObservationDto dto) {
        WeatherObservation entity = new WeatherObservation();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? WeatherObservationStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setMetar(dto.metar());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
