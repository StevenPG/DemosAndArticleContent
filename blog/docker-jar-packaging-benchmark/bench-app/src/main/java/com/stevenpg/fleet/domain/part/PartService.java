package com.stevenpg.fleet.domain.part;

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
public class PartService {

    private final PartRepository repository;

    public PartService(PartRepository repository) {
        this.repository = repository;
    }

    public PageResponse<PartDto> list(int page, int size) {
        Page<PartDto> result = repository.findAll(PageRequest.of(page, size)).map(PartMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<PartDto> byStatus(PartStatus status, int page, int size) {
        Page<PartDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(PartMapper::toDto);
        return PageResponse.from(result);
    }

    public PartDto byId(Long id) {
        return repository.findById(id)
                .map(PartMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Part", id));
    }

    public PartDto byCode(String code) {
        return repository.findByCode(code)
                .map(PartMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Part", code));
    }

    public List<PartSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(PartStatus.ACTIVE),
                "grounded", repository.countByStatus(PartStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public PartDto create(PartDto dto) {
        Part saved = repository.save(PartMapper.toEntity(dto));
        PartEvent.from(saved);
        return PartMapper.toDto(saved);
    }

    @Transactional
    public PartDto update(Long id, PartDto dto) {
        Part existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Part", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return PartMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
