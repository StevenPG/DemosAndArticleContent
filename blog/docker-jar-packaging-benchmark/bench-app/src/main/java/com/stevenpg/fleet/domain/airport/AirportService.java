package com.stevenpg.fleet.domain.airport;

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
public class AirportService {

    private final AirportRepository repository;

    public AirportService(AirportRepository repository) {
        this.repository = repository;
    }

    public PageResponse<AirportDto> list(int page, int size) {
        Page<AirportDto> result = repository.findAll(PageRequest.of(page, size)).map(AirportMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<AirportDto> byStatus(AirportStatus status, int page, int size) {
        Page<AirportDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(AirportMapper::toDto);
        return PageResponse.from(result);
    }

    public AirportDto byId(Long id) {
        return repository.findById(id)
                .map(AirportMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Airport", id));
    }

    public AirportDto byCode(String code) {
        return repository.findByCode(code)
                .map(AirportMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Airport", code));
    }

    public List<AirportSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(AirportStatus.ACTIVE),
                "grounded", repository.countByStatus(AirportStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public AirportDto create(AirportDto dto) {
        Airport saved = repository.save(AirportMapper.toEntity(dto));
        AirportEvent.from(saved);
        return AirportMapper.toDto(saved);
    }

    @Transactional
    public AirportDto update(Long id, AirportDto dto) {
        Airport existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Airport", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return AirportMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
