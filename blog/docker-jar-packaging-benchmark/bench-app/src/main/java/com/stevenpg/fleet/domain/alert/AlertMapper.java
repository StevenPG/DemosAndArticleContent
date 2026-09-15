package com.stevenpg.fleet.domain.alert;

public final class AlertMapper {

    private AlertMapper() {}

    public static AlertDto toDto(Alert entity) {
        return new AlertDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getSeverityLabel(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Alert toEntity(AlertDto dto) {
        Alert entity = new Alert();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? AlertStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setSeverityLabel(dto.severityLabel());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
