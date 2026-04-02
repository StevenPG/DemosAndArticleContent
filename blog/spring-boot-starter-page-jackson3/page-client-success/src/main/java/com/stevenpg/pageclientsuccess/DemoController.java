package com.stevenpg.pageclientsuccess;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demonstrates successful deserialization of a Spring Data {@link Page} response
 * from a remote REST API.
 *
 * <p>This works because {@code spring-boot-starter-page-jackson3} is on the classpath.
 * Its auto-configuration registers a Jackson mixin that maps {@code Page} to
 * {@code RestPage}, which has a proper {@code @JsonCreator} constructor.</p>
 */
@RestController
public class DemoController {

    private final RestClient restClient;

    public DemoController(RestClient restClient) {
        this.restClient = restClient;
    }

    @GetMapping("/call-server")
    public Map<String, Object> callServer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "3") int size) {

        Page<User> result = restClient.get()
                .uri("/api/users?page={page}&size={size}", page, size)
                .retrieve()
                .body(new ParameterizedTypeReference<Page<User>>() {});

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "SUCCESS");
        response.put("message", "Page<User> deserialized with spring-boot-starter-page-jackson3");
        response.put("page", result.getNumber());
        response.put("size", result.getSize());
        response.put("totalElements", result.getTotalElements());
        response.put("totalPages", result.getTotalPages());
        response.put("first", result.isFirst());
        response.put("last", result.isLast());
        response.put("content", result.getContent());
        return response;
    }
}
