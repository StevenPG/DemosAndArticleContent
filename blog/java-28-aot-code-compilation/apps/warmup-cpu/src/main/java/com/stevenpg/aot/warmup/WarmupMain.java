package com.stevenpg.aot.warmup;

/**
 * Batch warmup benchmark, modelled on the javac benchmark in JEP 544: run the same unit of
 * work N times in one JVM and print how long each iteration took. Iteration 1 is "cold",
 * the tail is steady state; the shape of the curve in between is what JEP 544 changes.
 *
 * Usage: java -jar warmup-cpu.jar <adsb|javac> [iterations]
 * Output: CSV "iteration,millis" on stdout, then CHECKSUM=<n>. Wall time is measured outside.
 */
public final class WarmupMain {

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "adsb";
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 20;

        Workload workload = switch (mode) {
            case "adsb" -> new AdsbWorkload();
            case "javac" -> new JavacWorkload();
            default -> throw new IllegalArgumentException("unknown mode: " + mode);
        };

        System.out.println("iteration,millis");
        long checksum = 0;
        for (int i = 1; i <= iterations; i++) {
            long t0 = System.nanoTime();
            checksum += workload.runOnce();
            long t1 = System.nanoTime();
            System.out.printf("%d,%.2f%n", i, (t1 - t0) / 1e6);
        }
        // Printed so the JIT can't treat the work as dead code - and as a cheap correctness
        // check: every JDK/cache configuration must produce the same checksum.
        System.out.println("CHECKSUM=" + checksum);
    }

    interface Workload {
        long runOnce() throws Exception;
    }
}
