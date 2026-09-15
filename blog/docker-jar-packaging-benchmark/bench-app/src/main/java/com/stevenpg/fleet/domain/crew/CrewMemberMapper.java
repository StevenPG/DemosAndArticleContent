package com.stevenpg.fleet.domain.crew;

public final class CrewMemberMapper {

    private CrewMemberMapper() {}

    public static CrewMemberDto toDto(CrewMember entity) {
        return new CrewMemberDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getLicenseNumber(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static CrewMember toEntity(CrewMemberDto dto) {
        CrewMember entity = new CrewMember();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? CrewMemberStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setLicenseNumber(dto.licenseNumber());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
