package com.stevenpg.aot.rest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class RestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RestApplication.class, args);
    }

    /**
     * Startup benchmark hook. With -Dbench.exitOnReady=true the JVM halts the moment the
     * application is ready to serve traffic, so `hyperfine` measures exec-to-ready as plain
     * process wall time - no HTTP polling loop adding jitter. halt() (not exit()) skips the
     * shutdown hooks so context close time never leaks into the number.
     */
    @EventListener(ApplicationReadyEvent.class)
    void onReady() {
        if (Boolean.getBoolean("bench.exitOnReady")) {
            Runtime.getRuntime().halt(0);
        }
    }
}
