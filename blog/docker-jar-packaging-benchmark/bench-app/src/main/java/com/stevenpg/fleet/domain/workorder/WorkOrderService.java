package com.stevenpg.fleet.domain.workorder;

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
public class WorkOrderService {

    private final WorkOrderRepository repository;

    public WorkOrderService(WorkOrderRepository repository) {
        this.repository = repository;
    }

    public PageResponse<WorkOrderDto> list(int page, int size) {
        Page<WorkOrderDto> result = repository.findAll(PageRequest.of(page, size)).map(WorkOrderMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<WorkOrderDto> byStatus(WorkOrderStatus status, int page, int size) {
        Page<WorkOrderDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(WorkOrderMapper::toDto);
        return PageResponse.from(result);
    }

    public WorkOrderDto byId(Long id) {
        return repository.findById(id)
                .map(WorkOrderMapper::toDto)
                .orElseThrow(() -> new NotFoundException("WorkOrder", id));
    }

    public WorkOrderDto byCode(String code) {
        return repository.findByCode(code)
                .map(WorkOrderMapper::toDto)
                .orElseThrow(() -> new NotFoundException("WorkOrder", code));
    }

    public List<WorkOrderSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(WorkOrderStatus.ACTIVE),
                "grounded", repository.countByStatus(WorkOrderStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public WorkOrderDto create(WorkOrderDto dto) {
        WorkOrder saved = repository.save(WorkOrderMapper.toEntity(dto));
        WorkOrderEvent.from(saved);
        return WorkOrderMapper.toDto(saved);
    }

    @Transactional
    public WorkOrderDto update(Long id, WorkOrderDto dto) {
        WorkOrder existing = repository.findById(id).orElseThrow(() -> new NotFoundException("WorkOrder", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return WorkOrderMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
