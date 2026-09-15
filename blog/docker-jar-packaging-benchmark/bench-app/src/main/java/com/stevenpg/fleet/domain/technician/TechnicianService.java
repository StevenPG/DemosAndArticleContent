package com.stevenpg.fleet.domain.technician;

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
public class TechnicianService {

    private final TechnicianRepository repository;

    public TechnicianService(TechnicianRepository repository) {
        this.repository = repository;
    }

    public PageResponse<TechnicianDto> list(int page, int size) {
        Page<TechnicianDto> result = repository.findAll(PageRequest.of(page, size)).map(TechnicianMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<TechnicianDto> byStatus(TechnicianStatus status, int page, int size) {
        Page<TechnicianDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(TechnicianMapper::toDto);
        return PageResponse.from(result);
    }

    public TechnicianDto byId(Long id) {
        return repository.findById(id)
                .map(TechnicianMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Technician", id));
    }

    public TechnicianDto byCode(String code) {
        return repository.findByCode(code)
                .map(TechnicianMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Technician", code));
    }

    public List<TechnicianSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(TechnicianStatus.ACTIVE),
                "grounded", repository.countByStatus(TechnicianStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public TechnicianDto create(TechnicianDto dto) {
        Technician saved = repository.save(TechnicianMapper.toEntity(dto));
        TechnicianEvent.from(saved);
        return TechnicianMapper.toDto(saved);
    }

    @Transactional
    public TechnicianDto update(Long id, TechnicianDto dto) {
        Technician existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Technician", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return TechnicianMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
