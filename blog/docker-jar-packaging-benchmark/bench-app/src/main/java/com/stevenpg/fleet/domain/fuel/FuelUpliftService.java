package com.stevenpg.fleet.domain.fuel;

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
public class FuelUpliftService {

    private final FuelUpliftRepository repository;

    public FuelUpliftService(FuelUpliftRepository repository) {
        this.repository = repository;
    }

    public PageResponse<FuelUpliftDto> list(int page, int size) {
        Page<FuelUpliftDto> result = repository.findAll(PageRequest.of(page, size)).map(FuelUpliftMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<FuelUpliftDto> byStatus(FuelUpliftStatus status, int page, int size) {
        Page<FuelUpliftDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(FuelUpliftMapper::toDto);
        return PageResponse.from(result);
    }

    public FuelUpliftDto byId(Long id) {
        return repository.findById(id)
                .map(FuelUpliftMapper::toDto)
                .orElseThrow(() -> new NotFoundException("FuelUplift", id));
    }

    public FuelUpliftDto byCode(String code) {
        return repository.findByCode(code)
                .map(FuelUpliftMapper::toDto)
                .orElseThrow(() -> new NotFoundException("FuelUplift", code));
    }

    public List<FuelUpliftSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(FuelUpliftStatus.ACTIVE),
                "grounded", repository.countByStatus(FuelUpliftStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public FuelUpliftDto create(FuelUpliftDto dto) {
        FuelUplift saved = repository.save(FuelUpliftMapper.toEntity(dto));
        FuelUpliftEvent.from(saved);
        return FuelUpliftMapper.toDto(saved);
    }

    @Transactional
    public FuelUpliftDto update(Long id, FuelUpliftDto dto) {
        FuelUplift existing = repository.findById(id).orElseThrow(() -> new NotFoundException("FuelUplift", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return FuelUpliftMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
