import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * What does a JVM inside a Kubernetes pod think its resources are?
 *
 * Prints one line of key=value pairs:
 *   - what the JVM decided: availableProcessors, max heap, the collector,
 *     and the memory size it believes it has (OperatingSystemMXBean is container-aware)
 *   - what the kernel says for *this container's* cgroup: memory.max and cpu.max (cgroup v2),
 *     or memory.limit_in_bytes and cpu.cfs_quota_us (cgroup v1)
 *
 * With mode "allocate", it then fills the heap in 16 MiB steps and reports how
 * far it got. A JVM that sized its heap from the pod limit throws
 * OutOfMemoryError; one that sized it from the *node* gets OOMKilled by the
 * kernel with no Java stack trace at all.
 *
 *   java ResourceProbe.java [allocate]
 */
public class ResourceProbe {

    public static void main(String[] args) throws Exception {
        var os = ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        String gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(b -> b.getName()).collect(Collectors.joining("+"));

        List<String> out = new ArrayList<>();
        out.add("container=" + System.getenv().getOrDefault("CONTAINER_NAME", "?"));
        out.add("java=" + Runtime.version());
        out.add("availableProcessors=" + Runtime.getRuntime().availableProcessors());
        out.add("maxHeapMiB=" + Runtime.getRuntime().maxMemory() / (1 << 20));
        out.add("jvmSeesMemoryMiB=" + os.getTotalMemorySize() / (1 << 20));
        out.add("gc=" + gc.replace(' ', '_'));
        out.add("cgroup=" + cgroupVersion());
        out.add("cgroupMemoryMax=" + read("/sys/fs/cgroup/memory.max", "/sys/fs/cgroup/memory/memory.limit_in_bytes"));
        out.add("cgroupCpuMax=" + read("/sys/fs/cgroup/cpu.max", "/sys/fs/cgroup/cpu/cpu.cfs_quota_us").replace(' ', '/'));
        out.add("procSelfCgroup=" + Files.readString(Path.of("/proc/self/cgroup")).strip().replace('\n', ';'));
        System.out.println(String.join(" ", out));

        if (args.length > 0 && args[0].equals("allocate")) {
            allocate();
        }
    }

    private static void allocate() {
        List<byte[]> hold = new ArrayList<>();
        int steps = 0;
        try {
            while (true) {
                hold.add(new byte[16 << 20]);
                steps++;
                if (steps % 8 == 0) {
                    System.out.println("allocatedMiB=" + steps * 16);
                }
            }
        } catch (OutOfMemoryError e) {
            hold.clear();
            System.out.println("outcome=java.lang.OutOfMemoryError allocatedMiB=" + steps * 16);
        }
    }

    private static String cgroupVersion() {
        return Files.exists(Path.of("/sys/fs/cgroup/cgroup.controllers")) ? "v2" : "v1";
    }

    private static String read(String v2, String v1) {
        for (String p : List.of(v2, v1)) {
            try {
                return Files.readString(Path.of(p)).strip();
            } catch (IOException ignored) {
                // try the next layout
            }
        }
        return "unreadable";
    }
}
