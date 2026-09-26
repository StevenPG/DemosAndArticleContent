package com.stevenpg.defaults;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.LinkedHashMap;

import java.util.Map;

import com.sun.management.HotSpotDiagnosticMXBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the harness only. Not something to ship. */
@RestController
@RequestMapping("/bench")
public class BenchController {

    private final TelemetryStore store;

    public BenchController(TelemetryStore store) {
        this.store = store;
    }

    /** What this JVM decided on its own: collector, header layout, heap ceiling. */
    @GetMapping("/info")
    public Map<String, Object> info() {
        var hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        info.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
        info.put("collectors", ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).toList());
        info.put("compactObjectHeaders", flag(hotspot, "UseCompactObjectHeaders"));
        info.put("aircraft", store.aircraftCount());
        info.put("positions", store.positionCount());
        info.put("seedMillis", store.seedMillis());
        return info;
    }

    /** Full GC, then report what survived: the live set. */
    @PostMapping("/live-set")
    public Map<String, Object> liveSet() {
        var memory = ManagementFactory.getMemoryMXBean();
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        return Map.of("usedHeapBytes", memory.getHeapMemoryUsage().getUsed(),
                "positions", store.positionCount());
    }

    /** Cumulative collection count and time across every collector. */
    @GetMapping("/gc")
    public Map<String, Object> gc() {
        long count = 0;
        long millis = 0;
        for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
            // G1 Concurrent GC reports concurrent-cycle time, not pauses; leave it out.
            if (bean.getName().contains("Concurrent")) {
                continue;
            }
            count += Math.max(0, bean.getCollectionCount());
            millis += Math.max(0, bean.getCollectionTime());
        }
        return Map.of("collections", count, "collectionMillis", millis);
    }

    private static String flag(HotSpotDiagnosticMXBean hotspot, String name) {
        try {
            return hotspot.getVMOption(name).getValue();
        } catch (IllegalArgumentException notOnThisJdk) {
            return "n/a";
        }
    }

}
