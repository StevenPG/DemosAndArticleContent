package com.stevenpg.fleet.domain.groundstation;

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
public class GroundStationService {

    private final GroundStationRepository repository;

    public GroundStationService(GroundStationRepository repository) {
        this.repository = repository;
    }

    public PageResponse<GroundStationDto> list(int page, int size) {
        Page<GroundStationDto> result = repository.findAll(PageRequest.of(page, size)).map(GroundStationMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<GroundStationDto> byStatus(GroundStationStatus status, int page, int size) {
        Page<GroundStationDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(GroundStationMapper::toDto);
        return PageResponse.from(result);
    }

    public GroundStationDto byId(Long id) {
        return repository.findById(id)
                .map(GroundStationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("GroundStation", id));
    }

    public GroundStationDto byCode(String code) {
        return repository.findByCode(code)
                .map(GroundStationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("GroundStation", code));
    }

    public List<GroundStationSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(GroundStationStatus.ACTIVE),
                "grounded", repository.countByStatus(GroundStationStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public GroundStationDto create(GroundStationDto dto) {
        GroundStation saved = repository.save(GroundStationMapper.toEntity(dto));
        GroundStationEvent.from(saved);
        return GroundStationMapper.toDto(saved);
    }

    @Transactional
    public GroundStationDto update(Long id, GroundStationDto dto) {
        GroundStation existing = repository.findById(id).orElseThrow(() -> new NotFoundException("GroundStation", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return GroundStationMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
