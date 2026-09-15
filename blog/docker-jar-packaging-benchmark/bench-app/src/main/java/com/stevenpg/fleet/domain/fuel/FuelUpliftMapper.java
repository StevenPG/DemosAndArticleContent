package com.stevenpg.fleet.domain.fuel;

public final class FuelUpliftMapper {

    private FuelUpliftMapper() {}

    public static FuelUpliftDto toDto(FuelUplift entity) {
        return new FuelUpliftDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getTicketNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static FuelUplift toEntity(FuelUpliftDto dto) {
        FuelUplift entity = new FuelUplift();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? FuelUpliftStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setTicketNumber(dto.ticketNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
