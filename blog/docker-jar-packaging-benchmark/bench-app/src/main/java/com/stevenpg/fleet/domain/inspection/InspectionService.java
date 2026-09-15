package com.stevenpg.fleet.domain.inspection;

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
public class InspectionService {

    private final InspectionRepository repository;

    public InspectionService(InspectionRepository repository) {
        this.repository = repository;
    }

    public PageResponse<InspectionDto> list(int page, int size) {
        Page<InspectionDto> result = repository.findAll(PageRequest.of(page, size)).map(InspectionMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<InspectionDto> byStatus(InspectionStatus status, int page, int size) {
        Page<InspectionDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(InspectionMapper::toDto);
        return PageResponse.from(result);
    }

    public InspectionDto byId(Long id) {
        return repository.findById(id)
                .map(InspectionMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Inspection", id));
    }

    public InspectionDto byCode(String code) {
        return repository.findByCode(code)
                .map(InspectionMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Inspection", code));
    }

    public List<InspectionSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(InspectionStatus.ACTIVE),
                "grounded", repository.countByStatus(InspectionStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public InspectionDto create(InspectionDto dto) {
        Inspection saved = repository.save(InspectionMapper.toEntity(dto));
        InspectionEvent.from(saved);
        return InspectionMapper.toDto(saved);
    }

    @Transactional
    public InspectionDto update(Long id, InspectionDto dto) {
        Inspection existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Inspection", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return InspectionMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
