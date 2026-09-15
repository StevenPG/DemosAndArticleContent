package com.stevenpg.fleet.domain.flightplan;

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
public class FlightPlanService {

    private final FlightPlanRepository repository;

    public FlightPlanService(FlightPlanRepository repository) {
        this.repository = repository;
    }

    public PageResponse<FlightPlanDto> list(int page, int size) {
        Page<FlightPlanDto> result = repository.findAll(PageRequest.of(page, size)).map(FlightPlanMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<FlightPlanDto> byStatus(FlightPlanStatus status, int page, int size) {
        Page<FlightPlanDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(FlightPlanMapper::toDto);
        return PageResponse.from(result);
    }

    public FlightPlanDto byId(Long id) {
        return repository.findById(id)
                .map(FlightPlanMapper::toDto)
                .orElseThrow(() -> new NotFoundException("FlightPlan", id));
    }

    public FlightPlanDto byCode(String code) {
        return repository.findByCode(code)
                .map(FlightPlanMapper::toDto)
                .orElseThrow(() -> new NotFoundException("FlightPlan", code));
    }

    public List<FlightPlanSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(FlightPlanStatus.ACTIVE),
                "grounded", repository.countByStatus(FlightPlanStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public FlightPlanDto create(FlightPlanDto dto) {
        FlightPlan saved = repository.save(FlightPlanMapper.toEntity(dto));
        FlightPlanEvent.from(saved);
        return FlightPlanMapper.toDto(saved);
    }

    @Transactional
    public FlightPlanDto update(Long id, FlightPlanDto dto) {
        FlightPlan existing = repository.findById(id).orElseThrow(() -> new NotFoundException("FlightPlan", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return FlightPlanMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
