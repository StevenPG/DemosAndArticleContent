package com.stevenpg.fleet.domain.part;

public final class PartMapper {

    private PartMapper() {}

    public static PartDto toDto(Part entity) {
        return new PartDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getPartNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Part toEntity(PartDto dto) {
        Part entity = new Part();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? PartStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setPartNumber(dto.partNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
