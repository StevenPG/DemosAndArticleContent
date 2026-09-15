package com.stevenpg.fleet.domain.certification;

public final class CertificationMapper {

    private CertificationMapper() {}

    public static CertificationDto toDto(Certification entity) {
        return new CertificationDto(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getStatus(),
                entity.getRegion(),
                entity.getAuthority(),
                entity.getMassKg(),
                entity.getCycles(),
                entity.isActive(),
                entity.getUpdatedAt());
    }

    public static Certification toEntity(CertificationDto dto) {
        Certification entity = new Certification();
        entity.setId(dto.id());
        entity.setCode(dto.code());
        entity.setName(dto.name());
        entity.setStatus(dto.status() == null ? CertificationStatus.ACTIVE : dto.status());
        entity.setRegion(dto.region());
        entity.setAuthority(dto.authority());
        entity.setMassKg(dto.massKg());
        entity.setCycles(dto.cycles());
        entity.setActive(dto.active());
        return entity;
    }
}
