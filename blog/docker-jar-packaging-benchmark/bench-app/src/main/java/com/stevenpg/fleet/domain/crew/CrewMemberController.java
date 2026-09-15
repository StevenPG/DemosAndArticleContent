package com.stevenpg.fleet.domain.crew;

import com.stevenpg.fleet.support.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crew")
public class CrewMemberController {

    private final CrewMemberService service;

    public CrewMemberController(CrewMemberService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CrewMemberDto> list(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size);
    }

    @GetMapping("/status/{status}")
    public PageResponse<CrewMemberDto> byStatus(
            @PathVariable CrewMemberStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.byStatus(status, page, size);
    }

    @GetMapping("/summaries")
    public List<CrewMemberSummary> summaries(@RequestParam(defaultValue = "20") int size) {
        return service.summaries(size);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    public CrewMemberDto byId(@PathVariable Long id) {
        return service.byId(id);
    }

    @GetMapping("/code/{code}")
    public CrewMemberDto byCode(@PathVariable String code) {
        return service.byCode(code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CrewMemberDto create(@Valid @RequestBody CrewMemberDto dto) {
        return service.create(dto);
    }

    @PutMapping("/{id}")
    public CrewMemberDto update(@PathVariable Long id, @Valid @RequestBody CrewMemberDto dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
