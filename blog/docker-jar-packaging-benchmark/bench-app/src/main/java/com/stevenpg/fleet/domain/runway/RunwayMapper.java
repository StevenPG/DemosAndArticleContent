package com.stevenpg.fleet.domain.runway;

public final class RunwayMapper {

    private RunwayMapper() {}

    public static RunwayDto toDto(Runway entity) {
        return new RunwayDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getDesignator(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Runway toEntity(RunwayDto dto) {
        Runway entity = new Runway();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? RunwayStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setDesignator(dto.designator());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
