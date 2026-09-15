package com.stevenpg.fleet.domain.alert;

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
public class AlertService {

    private final AlertRepository repository;

    public AlertService(AlertRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AlertDto> list(int page, int size) {
        Page<AlertDto> result = repository.findAll(PageRequest.of(page, size)).map(AlertMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<AlertDto> byStatus(AlertStatus status, int page, int size) {
        Page<AlertDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(AlertMapper::toDto);
        return PageResponse.from(result);
    }

    public AlertDto byId(Long id) {
        return repository.findById(id)
                .map(AlertMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Alert", id));
    }

    public AlertDto byCode(String code) {
        return repository.findByCode(code)
                .map(AlertMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Alert", code));
    }

    public List<AlertSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(AlertStatus.ACTIVE),
                "grounded", repository.countByStatus(AlertStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public AlertDto create(AlertDto dto) {
        Alert saved = repository.save(AlertMapper.toEntity(dto));
        AlertEvent.from(saved);
        return AlertMapper.toDto(saved);
    }

    @Transactional
    public AlertDto update(Long id, AlertDto dto) {
        Alert existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Alert", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return AlertMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
