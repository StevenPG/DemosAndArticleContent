package com.stevenpg.opsmcp.tools;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.stereotype.Component;

import com.stevenpg.opsmcp.logs.LogLine;
import com.stevenpg.opsmcp.logs.LogRingBuffer;

import jakarta.annotation.PreDestroy;

/**
 * Read recent logs, and change a log level with guard rails:
 * only loggers under an allow-listed prefix, and every change reverts itself
 * after a TTL so an agent can't leave DEBUG on in production over the weekend.
 */
@Component
public class LogTools {

    private static final Logger log = LoggerFactory.getLogger(LogTools.class);
    private static final int MAX_TTL_MINUTES = 60;

    private final LogRingBuffer buffer;
    private final LoggingSystem loggingSystem;
    private final List<String> writablePrefixes;
    private final Map<String, PendingRevert> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService reverter = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("log-level-revert").factory());

    public LogTools(LogRingBuffer buffer, LoggingSystem loggingSystem,
                    @Value("${ops.mcp.writable-logger-prefixes}") List<String> writablePrefixes) {
        this.buffer = buffer;
        this.loggingSystem = loggingSystem;
        this.writablePrefixes = writablePrefixes;
    }

    public record LevelChange(String logger, String previousLevel, String newLevel, Instant revertsAt) {
    }

    public record LoggerLevel(String logger, String configuredLevel, String effectiveLevel, Instant pendingRevertAt) {
    }

    private record PendingRevert(LogLevel original, ScheduledFuture<?> task, Instant at) {
    }

    @McpTool(name = "recent_logs", title = "Recent log lines",
            description = "Most recent log lines from this instance, newest first. Includes a short stack trace "
                    + "summary for errors. Filter by minimum level, logger prefix, or text.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public List<LogLine> recentLogs(
            @McpToolParam(description = "Minimum level: TRACE, DEBUG, INFO, WARN, ERROR (default INFO)", required = false) String minLevel,
            @McpToolParam(description = "Only loggers starting with this, e.g. com.stevenpg.opsmcp.demo", required = false) String loggerPrefix,
            @McpToolParam(description = "Only lines whose message or error contains this text", required = false) String contains,
            @McpToolParam(description = "Max lines (default 50, max 500)", required = false) Integer limit) {
        int n = limit == null ? 50 : Math.clamp(limit, 1, 500);
        return buffer.recent(minLevel, loggerPrefix, contains, n);
    }

    @McpTool(name = "get_log_level", title = "Get a log level",
            description = "Configured and effective level of a logger, and whether a temporary change is pending revert.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public LoggerLevel getLogLevel(@McpToolParam(description = "Logger name, e.g. com.stevenpg.opsmcp.demo") String logger) {
        LoggerConfiguration config = loggingSystem.getLoggerConfiguration(logger);
        PendingRevert revert = pending.get(logger);
        return new LoggerLevel(logger,
                config == null || config.getConfiguredLevel() == null ? null : config.getConfiguredLevel().name(),
                config == null ? null : config.getEffectiveLevel().name(),
                revert == null ? null : revert.at());
    }

    @McpTool(name = "set_log_level_temporarily", title = "Temporarily change a log level",
            description = "Temporarily change a logger's level. It reverts automatically after ttlMinutes "
                    + "(default 15, max 60). Only loggers under the server's allow-listed prefixes can be changed.",
            annotations = @McpAnnotations(readOnlyHint = false, destructiveHint = false, idempotentHint = true, openWorldHint = false))
    public LevelChange setLogLevel(
            @McpToolParam(description = "Logger name, e.g. com.stevenpg.opsmcp.demo") String logger,
            @McpToolParam(description = "TRACE, DEBUG, INFO, WARN, ERROR or OFF") String level,
            @McpToolParam(description = "Minutes until the change reverts (default 15, max 60)", required = false) Integer ttlMinutes) {
        if (writablePrefixes.stream().noneMatch(logger::startsWith)) {
            throw new IllegalArgumentException("Logger '" + logger + "' is not under an allow-listed prefix " + writablePrefixes);
        }
        LogLevel target = LogLevel.valueOf(level.toUpperCase(Locale.ROOT));
        Duration ttl = Duration.ofMinutes(ttlMinutes == null ? 15 : Math.clamp(ttlMinutes, 1, MAX_TTL_MINUTES));

        LoggerConfiguration current = loggingSystem.getLoggerConfiguration(logger);
        LogLevel configured = current == null ? null : current.getConfiguredLevel();

        // If a revert is already pending, keep the *original* level as the revert target.
        PendingRevert previous = pending.remove(logger);
        LogLevel original = previous != null ? previous.original() : configured;
        if (previous != null) {
            previous.task().cancel(false);
        }

        loggingSystem.setLogLevel(logger, target);
        Instant at = Instant.now().plus(ttl);
        ScheduledFuture<?> task = reverter.schedule(() -> revert(logger), ttl.toMillis(), TimeUnit.MILLISECONDS);
        pending.put(logger, new PendingRevert(original, task, at));
        log.warn("MCP set logger '{}' to {} until {} (was {})", logger, target, at, describe(configured));
        return new LevelChange(logger, configured == null ? null : configured.name(), target.name(), at);
    }

    void revert(String logger) {
        PendingRevert revert = pending.remove(logger);
        if (revert != null) {
            loggingSystem.setLogLevel(logger, revert.original());
            log.warn("Reverted logger '{}' to {}", logger, describe(revert.original()));
        }
    }

    private static String describe(LogLevel level) {
        return level == null ? "inherited" : level.name();
    }

    @PreDestroy
    void shutdown() {
        pending.keySet().forEach(this::revert);
        reverter.shutdownNow();
    }
}
