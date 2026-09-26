package com.stevenpg.opsmcp.tools;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.util.List;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * Resources are the "attach this to the conversation" half of MCP: static-ish
 * context the client can read without the model deciding to call a tool.
 */
@Component
public class OpsResources {

    private final Environment environment;
    private final JsonMapper json;

    public OpsResources(Environment environment, JsonMapper json) {
        this.environment = environment;
        this.json = json;
    }

    public record AppInfo(String application, List<String> activeProfiles, String javaVersion, Instant startedAt,
                          String pid) {
    }

    @McpResource(uri = "ops://app/info", name = "app-info", mimeType = "application/json",
            description = "Which application and instance this MCP server is attached to: name, profiles, JVM, start time, PID.")
    public String appInfo() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        return json.writeValueAsString(new AppInfo(
                environment.getProperty("spring.application.name"),
                List.of(environment.getActiveProfiles()),
                Runtime.version().toString(),
                Instant.ofEpochMilli(runtime.getStartTime()),
                String.valueOf(ProcessHandle.current().pid())));
    }
}
