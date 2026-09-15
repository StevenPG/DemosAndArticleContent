package com.stevenpg.fleet.startup;

import com.stevenpg.fleet.support.BuildInfo;
import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Prints a single machine-readable line once the context is ready. The measure
 * scripts grep for BENCH_READY_MS rather than parsing Boot's own banner, and it
 * doubles as a sanity check that the JVM-reported uptime and the wall clock the
 * harness measures agree.
 */
@Component
public class StartupTimeLogger {

    private static final Logger log = LoggerFactory.getLogger(StartupTimeLogger.class);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        log.info("BENCH_READY_MS={} revision={}", uptimeMs, BuildInfo.REVISION);
        log.info("BENCH_LOADED_CLASSES={}", ManagementFactory.getClassLoadingMXBean().getLoadedClassCount());
    }
}
