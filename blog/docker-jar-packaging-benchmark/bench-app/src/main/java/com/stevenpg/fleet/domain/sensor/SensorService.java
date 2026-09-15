package com.stevenpg.fleet.domain.sensor;

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
public class SensorService {

    private final SensorRepository repository;

    public SensorService(SensorRepository repository) {
        this.repository = repository;
    }

    public PageResponse<SensorDto> list(int page, int size) {
        Page<SensorDto> result = repository.findAll(PageRequest.of(page, size)).map(SensorMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<SensorDto> byStatus(SensorStatus status, int page, int size) {
        Page<SensorDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(SensorMapper::toDto);
        return PageResponse.from(result);
    }

    public SensorDto byId(Long id) {
        return repository.findById(id)
                .map(SensorMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Sensor", id));
    }

    public SensorDto byCode(String code) {
        return repository.findByCode(code)
                .map(SensorMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Sensor", code));
    }

    public List<SensorSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(SensorStatus.ACTIVE),
                "grounded", repository.countByStatus(SensorStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public SensorDto create(SensorDto dto) {
        Sensor saved = repository.save(SensorMapper.toEntity(dto));
        SensorEvent.from(saved);
        return SensorMapper.toDto(saved);
    }

    @Transactional
    public SensorDto update(Long id, SensorDto dto) {
        Sensor existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Sensor", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return SensorMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
