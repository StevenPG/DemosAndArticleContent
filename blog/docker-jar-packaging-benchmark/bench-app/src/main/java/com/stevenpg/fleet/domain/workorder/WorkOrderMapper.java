package com.stevenpg.fleet.domain.workorder;

public final class WorkOrderMapper {

    private WorkOrderMapper() {}

    public static WorkOrderDto toDto(WorkOrder entity) {
        return new WorkOrderDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getOrderNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static WorkOrder toEntity(WorkOrderDto dto) {
        WorkOrder entity = new WorkOrder();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? WorkOrderStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setOrderNumber(dto.orderNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
