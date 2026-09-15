package com.stevenpg.fleet.domain.incident;

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
public class IncidentService {

    private final IncidentRepository repository;

    public IncidentService(IncidentRepository repository) {
        this.repository = repository;
    }

    public PageResponse<IncidentDto> list(int page, int size) {
        Page<IncidentDto> result = repository.findAll(PageRequest.of(page, size)).map(IncidentMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<IncidentDto> byStatus(IncidentStatus status, int page, int size) {
        Page<IncidentDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(IncidentMapper::toDto);
        return PageResponse.from(result);
    }

    public IncidentDto byId(Long id) {
        return repository.findById(id)
                .map(IncidentMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Incident", id));
    }

    public IncidentDto byCode(String code) {
        return repository.findByCode(code)
                .map(IncidentMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Incident", code));
    }

    public List<IncidentSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(IncidentStatus.ACTIVE),
                "grounded", repository.countByStatus(IncidentStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public IncidentDto create(IncidentDto dto) {
        Incident saved = repository.save(IncidentMapper.toEntity(dto));
        IncidentEvent.from(saved);
        return IncidentMapper.toDto(saved);
    }

    @Transactional
    public IncidentDto update(Long id, IncidentDto dto) {
        Incident existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Incident", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return IncidentMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
