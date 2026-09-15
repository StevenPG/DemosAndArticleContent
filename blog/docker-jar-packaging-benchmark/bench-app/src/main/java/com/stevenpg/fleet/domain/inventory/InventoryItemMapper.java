package com.stevenpg.fleet.domain.inventory;

public final class InventoryItemMapper {

    private InventoryItemMapper() {}

    public static InventoryItemDto toDto(InventoryItem entity) {
        return new InventoryItemDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getBinLocation(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static InventoryItem toEntity(InventoryItemDto dto) {
        InventoryItem entity = new InventoryItem();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? InventoryItemStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setBinLocation(dto.binLocation());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
