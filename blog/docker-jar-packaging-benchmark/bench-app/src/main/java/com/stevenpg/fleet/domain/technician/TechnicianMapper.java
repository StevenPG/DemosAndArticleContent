package com.stevenpg.fleet.domain.technician;

public final class TechnicianMapper {

    private TechnicianMapper() {}

    public static TechnicianDto toDto(Technician entity) {
        return new TechnicianDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getBadgeId(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Technician toEntity(TechnicianDto dto) {
        Technician entity = new Technician();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? TechnicianStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setBadgeId(dto.badgeId());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
