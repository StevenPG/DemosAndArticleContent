import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

/**
 * Measures the retained size per instance of a few object shapes, so the
 * "4 bytes per object" headline can be checked against 8-byte alignment.
 *
 *   java -Xmx2g probes/HeaderFootprint.java                              # JDK 27: compact headers
 *   java -Xmx2g -XX:-UseCompactObjectHeaders probes/HeaderFootprint.java # JDK 27: legacy 12-byte headers
 *
 * Method: allocate N instances into a pre-sized Object[], force a full GC,
 * and divide the heap delta by N. The Object[] itself is allocated before the
 * baseline so it is not counted. Numbers are bytes per instance *including*
 * anything the instance owns (a String's backing byte[], a boxed key, ...).
 */
public class HeaderFootprint {

    record Point(int x, int y) {}

    record Sample(long epochMillis, int aircraftId, float lat, float lon, float altitude, short speed) {}

    static final class Node {
        Node next;
        int value;
    }

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 2_000_000;

        Map<String, IntFunction<Object>> shapes = new LinkedHashMap<>();
        shapes.put("new Object()", i -> new Object());
        shapes.put("Integer (outside cache)", i -> Integer.valueOf(1_000 + i));
        shapes.put("Long", i -> Long.valueOf(i));
        shapes.put("record Point(int, int)", i -> new Point(i, i));
        shapes.put("Node { ref, int }", i -> new Node());
        shapes.put("record Sample(long, int, 3x float, short)", i -> new Sample(i, i, 1f, 2f, 3f, (short) 4));
        shapes.put("byte[16]", i -> new byte[16]);
        shapes.put("String, 8 latin1 chars", i -> String.format("AC%06d", i % 1_000_000));
        shapes.put("ArrayList, 4 Integers", i -> {
            var list = new ArrayList<Integer>(4);
            for (int j = 0; j < 4; j++) list.add(1_000 + i + j);
            return list;
        });
        shapes.put("HashMap entry (Long -> Point)", i -> null); // measured separately below

        System.out.printf("%-42s %s%n", "java.version", System.getProperty("java.version"));
        System.out.printf("%-42s %s%n", "UseCompactObjectHeaders", compactHeaders());
        System.out.printf("%-42s %,d%n%n", "instances per shape", n);
        System.out.printf("%-42s %10s%n", "shape", "bytes/obj");

        // Throwaway pass: the first measurement otherwise absorbs startup garbage
        // (class loading, the Object[] of the first shape) and reads low.
        measure(n, i -> new Object());

        for (var shape : shapes.entrySet()) {
            double perInstance = shape.getKey().startsWith("HashMap")
                    ? measureMap(n)
                    : measure(n, shape.getValue());
            System.out.printf("%-42s %10.1f%n", shape.getKey(), perInstance);
        }
    }

    private static double measure(int n, IntFunction<Object> factory) {
        Object[] hold = new Object[n];
        long before = usedAfterGc();
        for (int i = 0; i < n; i++) {
            hold[i] = factory.apply(i);
        }
        long after = usedAfterGc();
        keep(hold);
        return (after - before) / (double) n;
    }

    /** One HashMap entry: the Node, its boxed Long key and a Point value, plus a share of the table. */
    private static double measureMap(int n) {
        long before = usedAfterGc();
        Map<Long, Point> map = new HashMap<>(n * 2);
        for (int i = 0; i < n; i++) {
            map.put((long) i + 1_000, new Point(i, i));
        }
        long after = usedAfterGc();
        keep(map);
        return (after - before) / (double) n;
    }

    private static volatile Object sink;

    private static void keep(Object o) {
        sink = o;
        sink = null;
    }

    private static long usedAfterGc() {
        var memory = ManagementFactory.getMemoryMXBean();
        for (int i = 0; i < 4; i++) {
            System.gc();
        }
        return memory.getHeapMemoryUsage().getUsed();
    }

    private static String compactHeaders() {
        try {
            var hotspot = ManagementFactory.getPlatformMXBean(com.sun.management.HotSpotDiagnosticMXBean.class);
            return hotspot.getVMOption("UseCompactObjectHeaders").getValue();
        } catch (IllegalArgumentException notOnThisJdk) {
            return "n/a";
        }
    }
}
