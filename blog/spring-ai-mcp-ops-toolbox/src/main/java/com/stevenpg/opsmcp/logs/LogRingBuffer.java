package com.stevenpg.opsmcp.logs;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Keeps the last N log events in memory so the {@code recent_logs} tool can
 * answer "what just went wrong?" without shipping a log pipeline into the
 * agent. Attached to the root Logback logger at startup; no logback.xml needed.
 *
 * This is a debugging aid, not a log store: it holds minutes of history, is
 * per-instance, and is lost on restart. Your real logs still go to stdout.
 */
@Component
public class LogRingBuffer extends AppenderBase<ILoggingEvent> {

    private final int capacity;
    private final ArrayDeque<LogLine> lines;

    public LogRingBuffer(@Value("${ops.mcp.log-buffer-size:2000}") int capacity) {
        this.capacity = capacity;
        this.lines = new ArrayDeque<>(capacity);
        setName("mcp-ring-buffer");
    }

    @PostConstruct
    void attach() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(context);
        start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void detach() {
        var context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent event) {
        var line = new LogLine(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage(),
                summarize(event.getThrowableProxy()));
        synchronized (lines) {
            if (lines.size() == capacity) {
                lines.pollFirst();
            }
            lines.addLast(line);
        }
    }

    /**
     * Newest first. {@code minLevel} is inclusive (WARN returns WARN and ERROR).
     */
    public List<LogLine> recent(String minLevel, String loggerPrefix, String contains, int limit) {
        Level floor = Level.toLevel(minLevel == null ? "INFO" : minLevel.toUpperCase(Locale.ROOT), Level.INFO);
        Predicate<LogLine> matches = line -> Level.toLevel(line.level()).isGreaterOrEqual(floor)
                && (loggerPrefix == null || loggerPrefix.isBlank() || line.logger().startsWith(loggerPrefix))
                && (contains == null || contains.isBlank()
                    || line.message().contains(contains)
                    || (line.error() != null && line.error().contains(contains)));
        List<LogLine> out = new ArrayList<>();
        synchronized (lines) {
            Iterator<LogLine> newestFirst = lines.descendingIterator();
            while (newestFirst.hasNext() && out.size() < limit) {
                LogLine line = newestFirst.next();
                if (matches.test(line)) {
                    out.add(line);
                }
            }
        }
        return out;
    }

    /** Exception class, message, and the first four non-reflection frames of each cause: enough to localize, small enough for a context window. */
    private static String summarize(IThrowableProxy proxy) {
        if (proxy == null) {
            return null;
        }
        var sb = new StringBuilder();
        int depth = 0;
        for (IThrowableProxy t = proxy; t != null && depth < 3; t = t.getCause(), depth++) {
            if (depth > 0) {
                sb.append("\nCaused by: ");
            }
            sb.append(t.getClassName()).append(": ").append(t.getMessage());
            int shown = 0;
            for (var frame : t.getStackTraceElementProxyArray()) {
                StackTraceElement element = frame.getStackTraceElement();
                // Reflection plumbing costs tokens and tells the agent nothing.
                if (element.getClassName().startsWith("jdk.internal.reflect.")
                        || element.getClassName().startsWith("java.lang.reflect.")) {
                    continue;
                }
                sb.append("\n    at ").append(element);
                if (++shown == 4) {
                    break;
                }
            }
        }
        return sb.toString();
    }
}
