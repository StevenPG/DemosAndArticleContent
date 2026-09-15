package com.stevenpg.fleet.domain.supplier;

public final class SupplierMapper {

    private SupplierMapper() {}

    public static SupplierDto toDto(Supplier entity) {
        return new SupplierDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getCageCode(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Supplier toEntity(SupplierDto dto) {
        Supplier entity = new Supplier();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? SupplierStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setCageCode(dto.cageCode());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
