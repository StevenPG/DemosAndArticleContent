package com.stevenpg.fleet.domain.waypoint;

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
public class WaypointService {

    private final WaypointRepository repository;

    public WaypointService(WaypointRepository repository) {
        this.repository = repository;
    }

    public PageResponse<WaypointDto> list(int page, int size) {
        Page<WaypointDto> result = repository.findAll(PageRequest.of(page, size)).map(WaypointMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<WaypointDto> byStatus(WaypointStatus status, int page, int size) {
        Page<WaypointDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(WaypointMapper::toDto);
        return PageResponse.from(result);
    }

    public WaypointDto byId(Long id) {
        return repository.findById(id)
                .map(WaypointMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Waypoint", id));
    }

    public WaypointDto byCode(String code) {
        return repository.findByCode(code)
                .map(WaypointMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Waypoint", code));
    }

    public List<WaypointSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(WaypointStatus.ACTIVE),
                "grounded", repository.countByStatus(WaypointStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public WaypointDto create(WaypointDto dto) {
        Waypoint saved = repository.save(WaypointMapper.toEntity(dto));
        WaypointEvent.from(saved);
        return WaypointMapper.toDto(saved);
    }

    @Transactional
    public WaypointDto update(Long id, WaypointDto dto) {
        Waypoint existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Waypoint", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return WaypointMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
