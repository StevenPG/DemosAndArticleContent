package com.stevenpg.fleet.domain.avionics;

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
public class AvionicsUnitService {

    private final AvionicsUnitRepository repository;

    public AvionicsUnitService(AvionicsUnitRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AvionicsUnitDto> list(int page, int size) {
        Page<AvionicsUnitDto> result = repository.findAll(PageRequest.of(page, size)).map(AvionicsUnitMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<AvionicsUnitDto> byStatus(AvionicsUnitStatus status, int page, int size) {
        Page<AvionicsUnitDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(AvionicsUnitMapper::toDto);
        return PageResponse.from(result);
    }

    public AvionicsUnitDto byId(Long id) {
        return repository.findById(id)
                .map(AvionicsUnitMapper::toDto)
                .orElseThrow(() -> new NotFoundException("AvionicsUnit", id));
    }

    public AvionicsUnitDto byCode(String code) {
        return repository.findByCode(code)
                .map(AvionicsUnitMapper::toDto)
                .orElseThrow(() -> new NotFoundException("AvionicsUnit", code));
    }

    public List<AvionicsUnitSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(AvionicsUnitStatus.ACTIVE),
                "grounded", repository.countByStatus(AvionicsUnitStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public AvionicsUnitDto create(AvionicsUnitDto dto) {
        AvionicsUnit saved = repository.save(AvionicsUnitMapper.toEntity(dto));
        AvionicsUnitEvent.from(saved);
        return AvionicsUnitMapper.toDto(saved);
    }

    @Transactional
    public AvionicsUnitDto update(Long id, AvionicsUnitDto dto) {
        AvionicsUnit existing = repository.findById(id).orElseThrow(() -> new NotFoundException("AvionicsUnit", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return AvionicsUnitMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
