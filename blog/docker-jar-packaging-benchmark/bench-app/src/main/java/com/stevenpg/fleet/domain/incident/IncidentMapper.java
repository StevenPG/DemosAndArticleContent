package com.stevenpg.fleet.domain.incident;

public final class IncidentMapper {

    private IncidentMapper() {}

    public static IncidentDto toDto(Incident entity) {
        return new IncidentDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getReportNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Incident toEntity(IncidentDto dto) {
        Incident entity = new Incident();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? IncidentStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setReportNumber(dto.reportNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
