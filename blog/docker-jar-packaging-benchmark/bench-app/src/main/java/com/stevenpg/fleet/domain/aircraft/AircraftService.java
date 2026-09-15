package com.stevenpg.fleet.domain.aircraft;

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
public class AircraftService {

    private final AircraftRepository repository;

    public AircraftService(AircraftRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AircraftDto> list(int page, int size) {
        Page<AircraftDto> result = repository.findAll(PageRequest.of(page, size)).map(AircraftMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<AircraftDto> byStatus(AircraftStatus status, int page, int size) {
        Page<AircraftDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(AircraftMapper::toDto);
        return PageResponse.from(result);
    }

    public AircraftDto byId(Long id) {
        return repository.findById(id)
                .map(AircraftMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Aircraft", id));
    }

    public AircraftDto byCode(String code) {
        return repository.findByCode(code)
                .map(AircraftMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Aircraft", code));
    }

    public List<AircraftSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(AircraftStatus.ACTIVE),
                "grounded", repository.countByStatus(AircraftStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public AircraftDto create(AircraftDto dto) {
        Aircraft saved = repository.save(AircraftMapper.toEntity(dto));
        AircraftEvent.from(saved);
        return AircraftMapper.toDto(saved);
    }

    @Transactional
    public AircraftDto update(Long id, AircraftDto dto) {
        Aircraft existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Aircraft", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return AircraftMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
