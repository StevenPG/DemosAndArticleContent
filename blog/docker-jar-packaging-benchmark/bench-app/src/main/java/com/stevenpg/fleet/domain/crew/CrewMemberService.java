package com.stevenpg.fleet.domain.crew;

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
public class CrewMemberService {

    private final CrewMemberRepository repository;

    public CrewMemberService(CrewMemberRepository repository) {
        this.repository = repository;
    }

    public PageResponse<CrewMemberDto> list(int page, int size) {
        Page<CrewMemberDto> result = repository.findAll(PageRequest.of(page, size)).map(CrewMemberMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<CrewMemberDto> byStatus(CrewMemberStatus status, int page, int size) {
        Page<CrewMemberDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(CrewMemberMapper::toDto);
        return PageResponse.from(result);
    }

    public CrewMemberDto byId(Long id) {
        return repository.findById(id)
                .map(CrewMemberMapper::toDto)
                .orElseThrow(() -> new NotFoundException("CrewMember", id));
    }

    public CrewMemberDto byCode(String code) {
        return repository.findByCode(code)
                .map(CrewMemberMapper::toDto)
                .orElseThrow(() -> new NotFoundException("CrewMember", code));
    }

    public List<CrewMemberSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(CrewMemberStatus.ACTIVE),
                "grounded", repository.countByStatus(CrewMemberStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public CrewMemberDto create(CrewMemberDto dto) {
        CrewMember saved = repository.save(CrewMemberMapper.toEntity(dto));
        CrewMemberEvent.from(saved);
        return CrewMemberMapper.toDto(saved);
    }

    @Transactional
    public CrewMemberDto update(Long id, CrewMemberDto dto) {
        CrewMember existing = repository.findById(id).orElseThrow(() -> new NotFoundException("CrewMember", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return CrewMemberMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
