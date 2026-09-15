package com.stevenpg.fleet.domain.engine;

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
public class EngineService {

    private final EngineRepository repository;

    public EngineService(EngineRepository repository) {
        this.repository = repository;
    }

    public PageResponse<EngineDto> list(int page, int size) {
        Page<EngineDto> result = repository.findAll(PageRequest.of(page, size)).map(EngineMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<EngineDto> byStatus(EngineStatus status, int page, int size) {
        Page<EngineDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(EngineMapper::toDto);
        return PageResponse.from(result);
    }

    public EngineDto byId(Long id) {
        return repository.findById(id)
                .map(EngineMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Engine", id));
    }

    public EngineDto byCode(String code) {
        return repository.findByCode(code)
                .map(EngineMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Engine", code));
    }

    public List<EngineSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(EngineStatus.ACTIVE),
                "grounded", repository.countByStatus(EngineStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public EngineDto create(EngineDto dto) {
        Engine saved = repository.save(EngineMapper.toEntity(dto));
        EngineEvent.from(saved);
        return EngineMapper.toDto(saved);
    }

    @Transactional
    public EngineDto update(Long id, EngineDto dto) {
        Engine existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Engine", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return EngineMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
