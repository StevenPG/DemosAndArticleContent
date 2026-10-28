package com.stevenpg.aot.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;
import java.util.stream.Collectors;

/**
 * A short-lived CLI: start, read a flight log, print a report, exit. This is the workload
 * where JVM startup + warmup is the WHOLE runtime - think CI steps, K8s Jobs, Lambdas,
 * git hooks. Picocli adds realistic annotation/reflection-driven startup work.
 */
@Command(name = "flightlog", mixinStandardHelpOptions = true, version = "flightlog 1.0",
        subcommands = {FlightLog.Generate.class, FlightLog.Summarize.class})
public class FlightLog {

    public static void main(String[] args) {
        System.exit(new CommandLine(new FlightLog()).execute(args));
    }

    record Leg(String tail, String operator, String from, String to, int blockMinutes, double fuelKg, int pax) {
        static Leg parse(String line) {
            String[] f = line.split(",");
            return new Leg(f[0], f[1], f[2], f[3], Integer.parseInt(f[4]), Double.parseDouble(f[5]), Integer.parseInt(f[6]));
        }
    }

    @Command(name = "generate", description = "Write a deterministic synthetic flight log CSV.")
    static class Generate implements Callable<Integer> {
        @Option(names = "--out", required = true) Path out;
        @Option(names = "--rows", defaultValue = "20000") int rows;

        @Override
        public Integer call() throws IOException {
            RandomGenerator rnd = RandomGeneratorFactory.of("L64X128MixRandom").create(7);
            String[] airports = {"KJFK", "KLAX", "KORD", "KATL", "KDFW", "KDEN", "KSEA", "KBOS", "KPHL", "KIAD", "KMCO", "KSFO"};
            String[] ops = {"AAL", "DAL", "UAL", "SWA", "JBU", "ASA"};
            try (BufferedWriter w = Files.newBufferedWriter(out)) {
                for (int i = 0; i < rows; i++) {
                    String from = airports[rnd.nextInt(airports.length)], to = airports[rnd.nextInt(airports.length)];
                    w.write(String.format(Locale.ROOT, "N%03dX%c,%s,%s,%s,%d,%.1f,%d%n", rnd.nextInt(1000),
                            (char) ('A' + rnd.nextInt(26)), ops[rnd.nextInt(ops.length)], from, to,
                            45 + rnd.nextInt(360), 1500 + rnd.nextDouble() * 20000, 50 + rnd.nextInt(250)));
                }
            }
            return 0;
        }
    }

    @Command(name = "summarize", description = "Summarize fuel burn and utilization per operator and route.")
    static class Summarize implements Callable<Integer> {
        @Option(names = "--input", required = true) Path input;
        @Option(names = "--top", defaultValue = "10") int top;
        @Option(names = "--format", defaultValue = "table") Format format;

        enum Format { table, json }

        record RouteStats(String route, long legs, double avgFuelPerPaxKg, double totalBlockHours) {
        }

        @Override
        public Integer call() throws IOException {
            List<Leg> legs;
            try (var lines = Files.lines(input)) {
                legs = lines.map(Leg::parse).toList();
            }
            Map<String, List<Leg>> byOperator = legs.stream()
                    .collect(Collectors.groupingBy(Leg::operator, TreeMap::new, Collectors.toList()));

            List<String> out = new ArrayList<>();
            byOperator.forEach((op, opLegs) -> {
                List<RouteStats> routes = opLegs.stream()
                        .collect(Collectors.groupingBy(l -> l.from() + "-" + l.to()))
                        .entrySet().stream()
                        .map(e -> new RouteStats(e.getKey(), e.getValue().size(),
                                e.getValue().stream().mapToDouble(l -> l.fuelKg() / l.pax()).average().orElse(0),
                                e.getValue().stream().mapToInt(Leg::blockMinutes).sum() / 60.0))
                        .sorted(Comparator.comparingDouble(RouteStats::totalBlockHours).reversed())
                        .limit(top)
                        .toList();
                out.add(switch (format) {
                    case table -> routes.stream()
                            .map(r -> String.format(Locale.ROOT, "%-4s %-9s %5d legs %8.2f kg/pax %9.1f h",
                                    op, r.route(), r.legs(), r.avgFuelPerPaxKg(), r.totalBlockHours()))
                            .collect(Collectors.joining("\n"));
                    case json -> routes.stream()
                            .map(r -> String.format(Locale.ROOT,
                                    "{\"operator\":\"%s\",\"route\":\"%s\",\"legs\":%d,\"kgPerPax\":%.2f,\"blockHours\":%.1f}",
                                    op, r.route(), r.legs(), r.avgFuelPerPaxKg(), r.totalBlockHours()))
                            .collect(Collectors.joining(",\n", "[", "]"));
                });
            });
            System.out.println(String.join("\n", out));
            return 0;
        }
    }
}
