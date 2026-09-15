package com.stevenpg.fleet.domain.maintenance;

public final class MaintenanceTaskMapper {

    private MaintenanceTaskMapper() {}

    public static MaintenanceTaskDto toDto(MaintenanceTask entity) {
        return new MaintenanceTaskDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getManualRef(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static MaintenanceTask toEntity(MaintenanceTaskDto dto) {
        MaintenanceTask entity = new MaintenanceTask();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? MaintenanceTaskStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setManualRef(dto.manualRef());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
