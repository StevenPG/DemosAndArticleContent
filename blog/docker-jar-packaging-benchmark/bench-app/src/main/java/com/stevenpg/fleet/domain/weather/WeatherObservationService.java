package com.stevenpg.fleet.domain.weather;

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
public class WeatherObservationService {

    private final WeatherObservationRepository repository;

    public WeatherObservationService(WeatherObservationRepository repository) {
        this.repository = repository;
    }

    public PageResponse<WeatherObservationDto> list(int page, int size) {
        Page<WeatherObservationDto> result = repository.findAll(PageRequest.of(page, size)).map(WeatherObservationMapper::toDto);
        return PageResponse.from(result);
    }

    public PageResponse<WeatherObservationDto> byStatus(WeatherObservationStatus status, int page, int size) {
        Page<WeatherObservationDto> result =
                repository.findByStatus(status, PageRequest.of(page, size)).map(WeatherObservationMapper::toDto);
        return PageResponse.from(result);
    }

    public WeatherObservationDto byId(Long id) {
        return repository.findById(id)
                .map(WeatherObservationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("WeatherObservation", id));
    }

    public WeatherObservationDto byCode(String code) {
        return repository.findByCode(code)
                .map(WeatherObservationMapper::toDto)
                .orElseThrow(() -> new NotFoundException("WeatherObservation", code));
    }

    public List<WeatherObservationSummary> summaries(int size) {
        return repository.findByActiveTrue(PageRequest.of(0, size));
    }

    public Map<String, Object> stats() {
        return Map.of(
                "total", repository.count(),
                "active", repository.countByStatus(WeatherObservationStatus.ACTIVE),
                "grounded", repository.countByStatus(WeatherObservationStatus.GROUNDED),
                "averageMassKg", repository.averageMassKg() == null ? 0d : repository.averageMassKg(),
                "totalCycles", repository.totalCycles());
    }

    @Transactional
    public WeatherObservationDto create(WeatherObservationDto dto) {
        WeatherObservation saved = repository.save(WeatherObservationMapper.toEntity(dto));
        WeatherObservationEvent.from(saved);
        return WeatherObservationMapper.toDto(saved);
    }

    @Transactional
    public WeatherObservationDto update(Long id, WeatherObservationDto dto) {
        WeatherObservation existing = repository.findById(id).orElseThrow(() -> new NotFoundException("WeatherObservation", id));
        existing.setName(dto.name());
        existing.setStatus(dto.status());
        existing.setRegion(dto.region());
        existing.setMassKg(dto.massKg());
        existing.setCycles(dto.cycles());
        existing.setActive(dto.active());
        return WeatherObservationMapper.toDto(repository.save(existing));
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }
}
