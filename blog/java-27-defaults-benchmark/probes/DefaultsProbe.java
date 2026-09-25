import com.sun.management.HotSpotDiagnosticMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.stream.Collectors;

import javax.net.ssl.SSLContext;

/**
 * Prints the defaults the JVM picked for *this* machine, without any flags.
 *
 * Run it with no options on each JDK, inside and outside a small container:
 *
 *   java probes/DefaultsProbe.java
 *   docker run --rm --cpus 1 --memory 1g -v $JDK:/jdk -v $PWD/probes:/p debian:trixie-slim \
 *       /jdk/bin/java /p/DefaultsProbe.java
 *
 * The three things JDK 27 changed silently:
 *   - which collector ergonomics selects on a small machine (JEP 523)
 *   - whether object headers are 12 or 8 bytes (JEP 534)
 *   - which TLS 1.3 key-exchange group is offered first (JEP 527)
 */
public class DefaultsProbe {

    public static void main(String[] args) throws Exception {
        var hotspot = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var runtime = Runtime.getRuntime();

        String collectors = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName)
                .collect(Collectors.joining(", "));

        System.out.printf("java.version            %s%n", System.getProperty("java.version"));
        System.out.printf("availableProcessors     %d%n", runtime.availableProcessors());
        System.out.printf("maxHeap                 %d MiB%n", runtime.maxMemory() / (1024 * 1024));
        System.out.printf("collector               %s%n", collectorName(hotspot));
        System.out.printf("collector MXBeans       %s%n", collectors);
        System.out.printf("UseCompactObjectHeaders %s%n", flag(hotspot, "UseCompactObjectHeaders"));
        System.out.printf("UseCompressedOops       %s%n", flag(hotspot, "UseCompressedOops"));

        SSLContext tls = SSLContext.getDefault();
        List<String> groups = List.of(tls.getDefaultSSLParameters().getNamedGroups());
        System.out.printf("TLS named groups        %s%n", String.join(", ", groups));
    }

    private static String collectorName(HotSpotDiagnosticMXBean hotspot) {
        for (String gc : List.of("UseSerialGC", "UseG1GC", "UseParallelGC", "UseZGC", "UseShenandoahGC", "UseEpsilonGC")) {
            if (flag(hotspot, gc).startsWith("true")) {
                return gc.substring(3, gc.length() - 2);
            }
        }
        return "unknown";
    }

    private static String flag(HotSpotDiagnosticMXBean hotspot, String name) {
        try {
            var option = hotspot.getVMOption(name);
            return option.getValue() + (option.getOrigin().name().equals("DEFAULT") ? "" : " (" + option.getOrigin() + ")");
        } catch (IllegalArgumentException notOnThisJdk) {
            return "n/a";
        }
    }
}
