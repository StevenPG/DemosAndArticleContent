package com.stevenpg.pageclientfailure;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Demonstrates the deserialization failure that occurs when attempting to consume
 * a Spring Data {@link Page} response WITHOUT the {@code spring-boot-starter-page-jackson3}
 * library on the classpath.
 *
 * <p>Jackson 3 cannot deserialize JSON into {@code Page<T>} because:</p>
 * <ul>
 *   <li>{@code Page} is an interface — Jackson cannot instantiate it directly.</li>
 *   <li>{@code PageImpl} has no {@code @JsonCreator} constructor.</li>
 * </ul>
 *
 * <p>The exception caught here is the exact error you would see in production without the fix.</p>
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

        try {
            Page<User> result = restClient.get()
                    .uri("/api/users?page={page}&size={size}", page, size)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Page<User>>() {});

            // This line is unreachable without the library — Jackson will always throw.
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "SUCCESS");
            response.put("content", result.getContent());
            return response;

        } catch (Exception e) {
            Throwable root = rootCause(e);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("status", "FAILURE");
            response.put("error", e.getMessage());
            response.put("rootCause", root.getClass().getName() + ": " + root.getMessage());
            response.put("fix", "Add 'com.stevenpg:spring-boot-starter-page-jackson3:0.0.1' to your dependencies!");
            response.put("library", "https://github.com/StevenPG/spring-boot-starter-page-jackson3");
            return response;
        }
    }

    private Throwable rootCause(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }
}
