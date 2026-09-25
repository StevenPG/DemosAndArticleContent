package com.stevenpg.opsmcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.stereotype.Component;

/**
 * Delegates to the actuator's own HealthEndpoint bean rather than reimplementing
 * it, so the agent sees exactly what your readiness probe sees, including
 * every HealthIndicator you've already written.
 */
@Component
public class HealthTools {

    private final HealthEndpoint healthEndpoint;

    public HealthTools(HealthEndpoint healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    @McpTool(name = "get_health", title = "Application health",
            description = "Overall application health and the status of every health component "
                    + "(disk space, database, downstream services...). Start here when something is wrong.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public HealthDescriptor health() {
        return healthEndpoint.health();
    }
}
