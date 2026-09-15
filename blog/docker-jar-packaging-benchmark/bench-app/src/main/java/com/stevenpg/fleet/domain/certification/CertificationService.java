package com.stevenpg.fleet.domain.certification;

import com.stevenpg.fleet.support.NotFoundException;
import com.stevenpg.fleet.support.PageResponse;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CertificationService {

    private final CertificationRepository repository;

    public CertificationService(CertificationRepository repository) {
        this.repository = repository;
    }

    public PageResponse<CertificationDto> list(int page, int size) {
        Page<CertificationDto> result = repository.findAll(PageRequest.of(page, size)).map(CertificationMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<CertificationDto> byStatus(CertificationStatus status, int page, int size) {
        Page<CertificationDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(CertificationMapper::toDto);
        return PageResponse.from(result);
    }

    public CertificationDto byId(Long id) {
        return repository.findById(id)
                .map(CertificationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Certification", id));
    }

    public CertificationDto byCode(String code) {
        return repository.findByCode(code)
                .map(CertificationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Certification", code));
    }

    public List<CertificationSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(CertificationStatus.ACTIVE),
                "grounded", repository.countByStatus(CertificationStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public CertificationDto create(CertificationDto dto) {
        Certification saved = repository.save(CertificationMapper.toEntity(dto));
        CertificationEvent.from(saved);
        return CertificationMapper.toDto(saved);
    }

    @Transactional
    public CertificationDto update(Long id, CertificationDto dto) {
        Certification existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Certification", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return CertificationMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
