package com.stevenpg.fleet.domain.inspection;

public final class InspectionMapper {

    private InspectionMapper() {}

    public static InspectionDto toDto(Inspection entity) {
        return new InspectionDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getChecklistRef(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Inspection toEntity(InspectionDto dto) {
        Inspection entity = new Inspection();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? InspectionStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setChecklistRef(dto.checklistRef());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
