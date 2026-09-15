package com.stevenpg.fleet.domain.inventory;

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
public class InventoryItemService {

    private final InventoryItemRepository repository;

    public InventoryItemService(InventoryItemRepository repository) {
        this.repository = repository;
    }

    public PageResponse<InventoryItemDto> list(int page, int size) {
        Page<InventoryItemDto> result = repository.findAll(PageRequest.of(page, size)).map(InventoryItemMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<InventoryItemDto> byStatus(InventoryItemStatus status, int page, int size) {
        Page<InventoryItemDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(InventoryItemMapper::toDto);
        return PageResponse.from(result);
    }

    public InventoryItemDto byId(Long id) {
        return repository.findById(id)
                .map(InventoryItemMapper::toDto)
                .orElseThrow(() -> new NotFoundException("InventoryItem", id));
    }

    public InventoryItemDto byCode(String code) {
        return repository.findByCode(code)
                .map(InventoryItemMapper::toDto)
                .orElseThrow(() -> new NotFoundException("InventoryItem", code));
    }

    public List<InventoryItemSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(InventoryItemStatus.ACTIVE),
                "grounded", repository.countByStatus(InventoryItemStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public InventoryItemDto create(InventoryItemDto dto) {
        InventoryItem saved = repository.save(InventoryItemMapper.toEntity(dto));
        InventoryItemEvent.from(saved);
        return InventoryItemMapper.toDto(saved);
    }

    @Transactional
    public InventoryItemDto update(Long id, InventoryItemDto dto) {
        InventoryItem existing = repository.findById(id).orElseThrow(() -> new NotFoundException("InventoryItem", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return InventoryItemMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
