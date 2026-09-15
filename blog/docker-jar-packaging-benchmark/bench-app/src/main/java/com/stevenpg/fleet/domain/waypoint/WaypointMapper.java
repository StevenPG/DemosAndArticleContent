package com.stevenpg.fleet.domain.waypoint;

public final class WaypointMapper {

    private WaypointMapper() {}

    public static WaypointDto toDto(Waypoint entity) {
        return new WaypointDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getFixName(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Waypoint toEntity(WaypointDto dto) {
        Waypoint entity = new Waypoint();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? WaypointStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setFixName(dto.fixName());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
