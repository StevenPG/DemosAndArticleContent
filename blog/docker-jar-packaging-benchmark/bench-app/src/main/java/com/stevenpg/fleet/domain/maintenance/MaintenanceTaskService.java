package com.stevenpg.fleet.domain.maintenance;

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
public class MaintenanceTaskService {

    private final MaintenanceTaskRepository repository;

    public MaintenanceTaskService(MaintenanceTaskRepository repository) {
        this.repository = repository;
    }

    public PageResponse<MaintenanceTaskDto> list(int page, int size) {
        Page<MaintenanceTaskDto> result = repository.findAll(PageRequest.of(page, size)).map(MaintenanceTaskMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<MaintenanceTaskDto> byStatus(MaintenanceTaskStatus status, int page, int size) {
        Page<MaintenanceTaskDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(MaintenanceTaskMapper::toDto);
        return PageResponse.from(result);
    }

    public MaintenanceTaskDto byId(Long id) {
        return repository.findById(id)
                .map(MaintenanceTaskMapper::toDto)
                .orElseThrow(() -> new NotFoundException("MaintenanceTask", id));
    }

    public MaintenanceTaskDto byCode(String code) {
        return repository.findByCode(code)
                .map(MaintenanceTaskMapper::toDto)
                .orElseThrow(() -> new NotFoundException("MaintenanceTask", code));
    }

    public List<MaintenanceTaskSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(MaintenanceTaskStatus.ACTIVE),
                "grounded", repository.countByStatus(MaintenanceTaskStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public MaintenanceTaskDto create(MaintenanceTaskDto dto) {
        MaintenanceTask saved = repository.save(MaintenanceTaskMapper.toEntity(dto));
        MaintenanceTaskEvent.from(saved);
        return MaintenanceTaskMapper.toDto(saved);
    }

    @Transactional
    public MaintenanceTaskDto update(Long id, MaintenanceTaskDto dto) {
        MaintenanceTask existing = repository.findById(id).orElseThrow(() -> new NotFoundException("MaintenanceTask", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return MaintenanceTaskMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
