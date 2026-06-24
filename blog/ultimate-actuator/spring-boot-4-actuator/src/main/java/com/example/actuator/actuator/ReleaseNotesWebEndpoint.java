package com.example.actuator.actuator;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * A {@link WebEndpoint} exposed only over HTTP (not JMX) at
 * {@code /actuator/releasenotes}.
 *
 * <p>Use {@code @WebEndpoint} (instead of the technology-agnostic {@code @Endpoint})
 * when the endpoint is inherently web-oriented and you don't want it on the JMX
 * surface. There are matching {@code @JmxEndpoint} and {@code @ServletEndpoint}
 * variants for the inverse cases.
 */
@Component
@WebEndpoint(id = "releasenotes")
public class ReleaseNotesWebEndpoint {

    @ReadOperation
    public Map<String, Object> releaseNotes() {
        return Map.of(
                "current", "1.4.0",
                "highlights", List.of(
                        "Added the /actuator/featureflags custom endpoint",
                        "Wired Prometheus + OpenTelemetry tracing",
                        "Liveness/readiness probes for Kubernetes"));
    }
}
