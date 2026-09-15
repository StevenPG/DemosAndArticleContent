package com.stevenpg.fleet.domain.runway;

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
public class RunwayService {

    private final RunwayRepository repository;

    public RunwayService(RunwayRepository repository) {
        this.repository = repository;
    }

    public PageResponse<RunwayDto> list(int page, int size) {
        Page<RunwayDto> result = repository.findAll(PageRequest.of(page, size)).map(RunwayMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<RunwayDto> byStatus(RunwayStatus status, int page, int size) {
        Page<RunwayDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(RunwayMapper::toDto);
        return PageResponse.from(result);
    }

    public RunwayDto byId(Long id) {
        return repository.findById(id)
                .map(RunwayMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Runway", id));
    }

    public RunwayDto byCode(String code) {
        return repository.findByCode(code)
                .map(RunwayMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Runway", code));
    }

    public List<RunwaySummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(RunwayStatus.ACTIVE),
                "grounded", repository.countByStatus(RunwayStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public RunwayDto create(RunwayDto dto) {
        Runway saved = repository.save(RunwayMapper.toEntity(dto));
        RunwayEvent.from(saved);
        return RunwayMapper.toDto(saved);
    }

    @Transactional
    public RunwayDto update(Long id, RunwayDto dto) {
        Runway existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Runway", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return RunwayMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
