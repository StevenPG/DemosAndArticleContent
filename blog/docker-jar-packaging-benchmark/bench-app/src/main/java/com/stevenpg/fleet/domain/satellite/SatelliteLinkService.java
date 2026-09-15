package com.stevenpg.fleet.domain.satellite;

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
public class SatelliteLinkService {

    private final SatelliteLinkRepository repository;

    public SatelliteLinkService(SatelliteLinkRepository repository) {
        this.repository = repository;
    }

    public PageResponse<SatelliteLinkDto> list(int page, int size) {
        Page<SatelliteLinkDto> result = repository.findAll(PageRequest.of(page, size)).map(SatelliteLinkMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<SatelliteLinkDto> byStatus(SatelliteLinkStatus status, int page, int size) {
        Page<SatelliteLinkDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(SatelliteLinkMapper::toDto);
        return PageResponse.from(result);
    }

    public SatelliteLinkDto byId(Long id) {
        return repository.findById(id)
                .map(SatelliteLinkMapper::toDto)
                .orElseThrow(() -> new NotFoundException("SatelliteLink", id));
    }

    public SatelliteLinkDto byCode(String code) {
        return repository.findByCode(code)
                .map(SatelliteLinkMapper::toDto)
                .orElseThrow(() -> new NotFoundException("SatelliteLink", code));
    }

    public List<SatelliteLinkSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(SatelliteLinkStatus.ACTIVE),
                "grounded", repository.countByStatus(SatelliteLinkStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public SatelliteLinkDto create(SatelliteLinkDto dto) {
        SatelliteLink saved = repository.save(SatelliteLinkMapper.toEntity(dto));
        SatelliteLinkEvent.from(saved);
        return SatelliteLinkMapper.toDto(saved);
    }

    @Transactional
    public SatelliteLinkDto update(Long id, SatelliteLinkDto dto) {
        SatelliteLink existing = repository.findById(id).orElseThrow(() -> new NotFoundException("SatelliteLink", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return SatelliteLinkMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
