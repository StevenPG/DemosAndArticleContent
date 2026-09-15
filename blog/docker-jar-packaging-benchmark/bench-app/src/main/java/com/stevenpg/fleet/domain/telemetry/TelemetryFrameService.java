package com.stevenpg.fleet.domain.telemetry;

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
public class TelemetryFrameService {

    private final TelemetryFrameRepository repository;

    public TelemetryFrameService(TelemetryFrameRepository repository) {
        this.repository = repository;
    }

    public PageResponse<TelemetryFrameDto> list(int page, int size) {
        Page<TelemetryFrameDto> result = repository.findAll(PageRequest.of(page, size)).map(TelemetryFrameMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<TelemetryFrameDto> byStatus(TelemetryFrameStatus status, int page, int size) {
        Page<TelemetryFrameDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(TelemetryFrameMapper::toDto);
        return PageResponse.from(result);
    }

    public TelemetryFrameDto byId(Long id) {
        return repository.findById(id)
                .map(TelemetryFrameMapper::toDto)
                .orElseThrow(() -> new NotFoundException("TelemetryFrame", id));
    }

    public TelemetryFrameDto byCode(String code) {
        return repository.findByCode(code)
                .map(TelemetryFrameMapper::toDto)
                .orElseThrow(() -> new NotFoundException("TelemetryFrame", code));
    }

    public List<TelemetryFrameSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(TelemetryFrameStatus.ACTIVE),
                "grounded", repository.countByStatus(TelemetryFrameStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public TelemetryFrameDto create(TelemetryFrameDto dto) {
        TelemetryFrame saved = repository.save(TelemetryFrameMapper.toEntity(dto));
        TelemetryFrameEvent.from(saved);
        return TelemetryFrameMapper.toDto(saved);
    }

    @Transactional
    public TelemetryFrameDto update(Long id, TelemetryFrameDto dto) {
        TelemetryFrame existing = repository.findById(id).orElseThrow(() -> new NotFoundException("TelemetryFrame", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return TelemetryFrameMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
