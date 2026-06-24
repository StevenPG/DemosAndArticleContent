package com.example.actuator.web;

import com.example.actuator.domain.Widget;
import com.example.actuator.service.WidgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A small REST API. Every request through these endpoints generates real traffic
 * that shows up across Actuator:
 * <ul>
 *     <li>{@code http.server.requests} timer metrics (per URI, status, method).</li>
 *     <li>Entries in the {@code httpexchanges} endpoint.</li>
 *     <li>Route entries in the {@code mappings} endpoint.</li>
 *     <li>Distributed-trace spans (via auto-instrumentation + {@code @Observed}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/widgets")
public class WidgetController {

    private final WidgetService widgetService;

    public WidgetController(WidgetService widgetService) {
        this.widgetService = widgetService;
    }

    @GetMapping
    public List<Widget> list() {
        return widgetService.findAll();
    }

    @GetMapping("/{id}")
    public Widget get(@PathVariable Long id) {
        return widgetService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Widget create(@Valid @RequestBody CreateWidgetRequest request) {
        return widgetService.create(request.name(), request.color());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        widgetService.delete(id);
    }

    /** Request payload; {@code @Valid} failures become 4xx entries in the request metrics. */
    public record CreateWidgetRequest(
            @NotBlank String name,
            @NotBlank String color) {
    }
}
