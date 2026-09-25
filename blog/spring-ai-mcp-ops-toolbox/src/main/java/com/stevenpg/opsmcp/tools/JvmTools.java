package com.stevenpg.opsmcp.tools;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpTool.McpAnnotations;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Straight from the platform MXBeans: no actuator endpoint required. */
@Component
public class JvmTools {

    public record JvmSummary(String javaVersion, String uptime, int availableProcessors, double processCpuLoad,
                             long heapUsedMiB, long heapCommittedMiB, long heapMaxMiB, long nonHeapUsedMiB,
                             int liveThreads, int peakThreads, Map<String, String> garbageCollectors) {
    }

    public record BlockedThread(String name, String state, String waitingOn, String lockOwner, List<String> topFrames) {
    }

    public record ThreadSummary(int total, Map<Thread.State, Integer> byState, Map<String, Integer> largestPools,
                                List<String> deadlocked, List<BlockedThread> blocked) {
    }

    @McpTool(name = "jvm_summary", title = "JVM vitals",
            description = "JVM vitals: heap and non-heap usage, CPU load, thread counts, GC counts and cumulative GC time.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public JvmSummary jvmSummary() {
        var memory = ManagementFactory.getMemoryMXBean();
        var threads = ManagementFactory.getThreadMXBean();
        var runtime = ManagementFactory.getRuntimeMXBean();
        var os = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        Map<String, String> gcs = new LinkedHashMap<>();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcs.put(gc.getName(), gc.getCollectionCount() + " collections, " + gc.getCollectionTime() + " ms");
        }
        var heap = memory.getHeapMemoryUsage();
        return new JvmSummary(
                Runtime.version().toString(),
                Duration.ofMillis(runtime.getUptime()).withNanos(0).toString(),
                Runtime.getRuntime().availableProcessors(),
                Math.round(os.getProcessCpuLoad() * 1000) / 10.0,
                heap.getUsed() >> 20, heap.getCommitted() >> 20, heap.getMax() >> 20,
                memory.getNonHeapMemoryUsage().getUsed() >> 20,
                threads.getThreadCount(), threads.getPeakThreadCount(), gcs);
    }

    @McpTool(name = "thread_summary", title = "Thread states and blockers",
            description = "Thread counts by state, the largest thread pools by name, any deadlocks, and the "
                    + "top frames of BLOCKED threads with the lock they wait on. Use for hangs and pool exhaustion.",
            annotations = @McpAnnotations(readOnlyHint = true, idempotentHint = true, openWorldHint = false))
    public ThreadSummary threadSummary(
            @McpToolParam(description = "Max BLOCKED threads to detail (default 10)", required = false) Integer limit) {
        var mx = ManagementFactory.getThreadMXBean();
        ThreadInfo[] infos = mx.dumpAllThreads(true, true, 8);

        Map<Thread.State, Integer> byState = new EnumMap<>(Thread.State.class);
        Map<String, Integer> pools = new LinkedHashMap<>();
        for (ThreadInfo info : infos) {
            byState.merge(info.getThreadState(), 1, Integer::sum);
            // "http-nio-8080-exec-17" -> "http-nio-8080-exec-": group pool members together
            pools.merge(info.getThreadName().replaceAll("\\d+$", ""), 1, Integer::sum);
        }
        Map<String, Integer> largestPools = pools.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(8)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));

        long[] deadlockedIds = mx.findDeadlockedThreads();
        List<String> deadlocked = deadlockedIds == null ? List.of()
                : Arrays.stream(mx.getThreadInfo(deadlockedIds)).map(ThreadInfo::getThreadName).toList();

        List<BlockedThread> blocked = new ArrayList<>();
        int max = limit == null ? 10 : Math.clamp(limit, 1, 50);
        for (ThreadInfo info : infos) {
            if (info.getThreadState() == Thread.State.BLOCKED && blocked.size() < max) {
                blocked.add(new BlockedThread(info.getThreadName(), info.getThreadState().name(),
                        info.getLockName(), info.getLockOwnerName(),
                        Arrays.stream(info.getStackTrace()).limit(5).map(StackTraceElement::toString).toList()));
            }
        }
        return new ThreadSummary(infos.length, byState, largestPools, deadlocked, blocked);
    }
}
