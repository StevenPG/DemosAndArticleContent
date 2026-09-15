package com.stevenpg.fleet.domain.supplier;

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
public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public PageResponse<SupplierDto> list(int page, int size) {
        Page<SupplierDto> result = repository.findAll(PageRequest.of(page, size)).map(SupplierMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<SupplierDto> byStatus(SupplierStatus status, int page, int size) {
        Page<SupplierDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(SupplierMapper::toDto);
        return PageResponse.from(result);
    }

    public SupplierDto byId(Long id) {
        return repository.findById(id)
                .map(SupplierMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    public SupplierDto byCode(String code) {
        return repository.findByCode(code)
                .map(SupplierMapper::toDto)
                .orElseThrow(() -> new NotFoundException("Supplier", code));
    }

    public List<SupplierSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(SupplierStatus.ACTIVE),
                "grounded", repository.countByStatus(SupplierStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public SupplierDto create(SupplierDto dto) {
        Supplier saved = repository.save(SupplierMapper.toEntity(dto));
        SupplierEvent.from(saved);
        return SupplierMapper.toDto(saved);
    }

    @Transactional
    public SupplierDto update(Long id, SupplierDto dto) {
        Supplier existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return SupplierMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
