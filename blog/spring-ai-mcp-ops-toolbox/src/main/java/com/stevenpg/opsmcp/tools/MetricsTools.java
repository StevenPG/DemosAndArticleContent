package com.stevenpg.opsmcp.tools;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.search.Search;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * Micrometer, queried in-process. The shapes returned here are deliberately
 * smaller than the actuator's /metrics JSON: an agent pays for every token.
 */
@Component
public class MetricsTools {

    private final MeterRegistry registry;

    public MetricsTools(MeterRegistry registry) {
        this.registry = registry;
    }

    public record MetricSeries(Map<String, String> tags, Map<String, Double> values) {
    }

    public record EndpointTraffic(String method, String uri, long requests, long serverErrors, long clientErrors,
                                  double meanMs, double maxMs) {
    }

    @McpTool(name = "list_metrics", title = "List metric names",
            description = "List available metric names, optionally filtered by prefix (e.g. 'jvm.', 'http.', 'hikaricp.').",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<String> listMetrics(
            @McpToolParam(description = "Only names starting with this prefix. Omit for all.", required = false) String prefix) {
        var names = new TreeSet<String>();
        for (Meter meter : registry.getMeters()) {
            String name = meter.getId().getName();
            if (prefix == null || prefix.isBlank() || name.startsWith(prefix)) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    @McpTool(name = "get_metric", title = "Read a metric",
            description = "Current values of one metric, one entry per tag combination. "
                    + "Use tag filters like 'status:500' or 'uri:/api/orders/{id}' to narrow it.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<MetricSeries> getMetric(
            @McpToolParam(description = "Exact metric name, e.g. jvm.memory.used") String name,
            @McpToolParam(description = "Tag filters as key:value strings", required = false) List<String> tags) {
        Search search = registry.find(name);
        if (tags != null) {
            for (String tag : tags) {
                int colon = tag.indexOf(':');
                if (colon > 0) {
                    search = search.tag(tag.substring(0, colon), tag.substring(colon + 1));
                }
            }
        }
        List<MetricSeries> out = new ArrayList<>();
        for (Meter meter : search.meters()) {
            Map<String, String> meterTags = new LinkedHashMap<>();
            for (Tag tag : meter.getId().getTags()) {
                meterTags.put(tag.getKey(), tag.getValue());
            }
            Map<String, Double> values = new LinkedHashMap<>();
            meter.measure().forEach(m -> values.put(m.getStatistic().name().toLowerCase(), m.getValue()));
            out.add(new MetricSeries(meterTags, values));
        }
        return out;
    }

    @McpTool(name = "http_traffic_summary", title = "HTTP traffic by endpoint",
            description = "Per-endpoint request counts, 4xx/5xx counts, mean and max latency since startup, "
                    + "busiest first. The fastest way to find which endpoint is failing or slow.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<EndpointTraffic> httpTrafficSummary(
            @McpToolParam(description = "Include /actuator and /mcp endpoints (default false)", required = false) Boolean includeInternal,
            @McpToolParam(description = "Max rows (default 20)", required = false) Integer limit) {
        record Key(String method, String uri) {
        }
        Map<Key, long[]> counts = new LinkedHashMap<>();   // requests, 5xx, 4xx
        Map<Key, double[]> timing = new LinkedHashMap<>(); // totalMs, maxMs
        for (Timer timer : registry.find("http.server.requests").timers()) {
            String uri = timer.getId().getTag("uri");
            if (!Boolean.TRUE.equals(includeInternal) && uri != null
                    && (uri.startsWith("/actuator") || uri.startsWith("/mcp"))) {
                continue;
            }
            var key = new Key(timer.getId().getTag("method"), uri);
            String status = timer.getId().getTag("status");
            long n = timer.count();
            long[] c = counts.computeIfAbsent(key, k -> new long[3]);
            c[0] += n;
            if (status != null && status.startsWith("5")) c[1] += n;
            if (status != null && status.startsWith("4")) c[2] += n;
            double[] t = timing.computeIfAbsent(key, k -> new double[2]);
            t[0] += timer.totalTime(TimeUnit.MILLISECONDS);
            t[1] = Math.max(t[1], timer.max(TimeUnit.MILLISECONDS));
        }
        return counts.entrySet().stream()
                .map(e -> {
                    long[] c = e.getValue();
                    double[] t = timing.get(e.getKey());
                    return new EndpointTraffic(e.getKey().method(), e.getKey().uri(), c[0], c[1], c[2],
                            c[0] == 0 ? 0 : round(t[0] / c[0]), round(t[1]));
                })
                .sorted(Comparator.comparingLong(EndpointTraffic::requests).reversed())
                .limit(limit == null ? 20 : limit)
                .toList();
    }

    private static double round(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
