package com.stevenpg.fleet.domain.flightplan;

public final class FlightPlanMapper {

    private FlightPlanMapper() {}

    public static FlightPlanDto toDto(FlightPlan entity) {
        return new FlightPlanDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getCallsign(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static FlightPlan toEntity(FlightPlanDto dto) {
        FlightPlan entity = new FlightPlan();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? FlightPlanStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setCallsign(dto.callsign());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
