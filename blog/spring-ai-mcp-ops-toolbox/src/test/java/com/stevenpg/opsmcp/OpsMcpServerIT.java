package com.stevenpg.opsmcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Drives the server the way a real MCP client does: the official Java SDK
 * client over Streamable HTTP, against a running server on a random port.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "ops.mcp.api-key=test-key",
        "demo.failure-rate=1.0",
        "demo.slow-rate=0.0"
})
class OpsMcpServerIT {

    @LocalServerPort
    int port;

    McpSyncClient client;

    @BeforeEach
    void connect() {
        client = client("test-key");
        client.initialize();
    }

    @AfterEach
    void close() {
        client.closeGracefully();
    }

    private McpSyncClient client(String key) {
        var transport = HttpClientStreamableHttpTransport.builder("http://localhost:" + port)
                .endpoint("/mcp")
                .httpRequestCustomizer((builder, method, uri, body, context) ->
                        builder.header("Authorization", "Bearer " + key))
                .build();
        return McpClient.sync(transport).requestTimeout(Duration.ofSeconds(10)).build();
    }

    private String call(String tool, Map<String, Object> args) {
        CallToolResult result = client.callTool(CallToolRequest.builder(tool).arguments(args).build());
        assertThat(result.isError()).as("tool %s returned an error: %s", tool, result.content()).isNotEqualTo(Boolean.TRUE);
        return ((TextContent) result.content().getFirst()).text();
    }

    @Test
    void advertisesEveryToolWithAccurateHints() {
        var tools = client.listTools().tools();
        assertThat(tools).extracting(McpSchema.Tool::name).containsExactlyInAnyOrder(
                "get_health", "list_metrics", "get_metric", "http_traffic_summary",
                "recent_logs", "get_log_level", "set_log_level_temporarily",
                "jvm_summary", "thread_summary");

        var readOnly = tools.stream().filter(t -> Boolean.TRUE.equals(t.annotations().readOnlyHint()))
                .map(McpSchema.Tool::name).toList();
        assertThat(readOnly).hasSize(8).doesNotContain("set_log_level_temporarily");
    }

    @Test
    void healthReportsUpWithComponents() {
        String health = call("get_health", Map.of());
        assertThat(health).contains("\"status\":\"UP\"").contains("diskSpace");
    }

    @Test
    void failingEndpointShowsUpInTrafficAndLogs() throws Exception {
        var http = HttpClient.newHttpClient();
        for (int i = 1; i <= 3; i++) {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/orders/" + i)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(502);
        }

        String traffic = call("http_traffic_summary", Map.of());
        assertThat(traffic).contains("/api/orders/{id}").contains("\"serverErrors\":3");

        String logs = call("recent_logs", Map.of("minLevel", "ERROR", "contains", "inventory-service"));
        assertThat(logs).contains("SocketTimeoutException").contains("OrderController");
    }

    @Test
    void logLevelChangesAreAllowListedAndReportTheirRevertTime() {
        String change = call("set_log_level_temporarily",
                Map.of("logger", "com.stevenpg.opsmcp.demo", "level", "DEBUG", "ttlMinutes", 1));
        assertThat(change).contains("\"newLevel\":\"DEBUG\"").contains("revertsAt");

        String level = call("get_log_level", Map.of("logger", "com.stevenpg.opsmcp.demo"));
        assertThat(level).contains("\"effectiveLevel\":\"DEBUG\"").contains("pendingRevertAt");

        CallToolResult refused = client.callTool(CallToolRequest.builder("set_log_level_temporarily")
                .arguments(Map.of("logger", "ROOT", "level", "TRACE")).build());
        assertThat(refused.isError()).isTrue();
        assertThat(((TextContent) refused.content().getFirst()).text()).contains("not under an allow-listed prefix");
    }

    @Test
    void jvmAndThreadSummariesAreCompact() {
        assertThat(call("jvm_summary", Map.of())).contains("heapUsedMiB").contains("garbageCollectors");
        assertThat(call("thread_summary", Map.of())).contains("byState").contains("largestPools");
    }

    @Test
    void appInfoResourceAndTriagePrompt() {
        var resource = client.readResource(McpSchema.ReadResourceRequest.builder("ops://app/info").build());
        assertThat(((McpSchema.TextResourceContents) resource.contents().getFirst()).text())
                .contains("ops-toolbox-demo");

        var prompt = client.getPrompt(McpSchema.GetPromptRequest.builder("triage")
                .arguments(Map.of("symptom", "orders API returning 502s")).build());
        assertThat(((TextContent) prompt.messages().getFirst().content()).text())
                .contains("orders API returning 502s").contains("get_health");
    }

    @Test
    void rejectsClientsWithoutTheKey() {
        McpSyncClient intruder = client("wrong-key");
        assertThatThrownBy(intruder::initialize).isNotNull();
        intruder.close();
    }
}
