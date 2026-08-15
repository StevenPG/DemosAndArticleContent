import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpOption;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * HTTP/2 vs HTTP/3 latency benchmark for the JDK 26 HttpClient (JEP 517).
 *
 * Usage:
 *   java src/H2vsH3Bench.java <HTTP_2|HTTP_3> <url> <sequential|concurrent>
 *
 * Sequential: 50 warmup requests, then 500 measured requests one at a time.
 *   Measures pure request latency. Reports p50/p95/p99.
 *
 * Concurrent: 50 virtual threads, each making 10 requests.
 *   Measures multiplexing behavior - under packet loss this is where HTTP/2's
 *   TCP head-of-line blocking (one lost segment stalls ALL streams on the
 *   connection) separates from QUIC's independent per-stream recovery.
 *
 * Design notes, mirrored from the blog post:
 *  - One shared HttpClient. Clients are expensive (connection + Alt-Svc cache);
 *    per-request clients would benchmark connection setup instead of requests.
 *  - HTTP_3_URI_ONLY discovery: we control the server and know it speaks QUIC
 *    on this authority. The default (ALT_SVC) would send the first request(s)
 *    over TCP and only upgrade later - useless for a controlled comparison.
 *  - The fallback guard throws if any response used a different protocol than
 *    requested. Without it, a silent downgrade means comparing HTTP/2 with
 *    itself and publishing garbage numbers.
 */
public class H2vsH3Bench {

    static final int WARMUP = 50;
    static final int SEQUENTIAL_RUNS = 500;
    static final int CONCURRENT_THREADS = 50;
    static final int REQUESTS_PER_THREAD = 10;

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            System.err.println("usage: H2vsH3Bench <HTTP_2|HTTP_3> <url> <sequential|concurrent>");
            System.exit(2);
        }
        var version = HttpClient.Version.valueOf(args[0]);
        var url = URI.create(args[1]);
        var mode = args[2];

        var client = HttpClient.newBuilder()
                .version(version)
                .build();

        var requestBuilder = HttpRequest.newBuilder().uri(url);
        if (version == HttpClient.Version.HTTP_3) {
            requestBuilder.setOption(HttpOption.H3_DISCOVERY,
                    HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY);
        }
        var request = requestBuilder.build();

        // Warmup: JIT, connection establishment, TLS session, QUIC handshake.
        for (int i = 0; i < WARMUP; i++) {
            checkedSend(client, request, version);
        }

        long wallStart = System.nanoTime();
        long[] samples = switch (mode) {
            case "sequential" -> sequential(client, request, version);
            case "concurrent" -> concurrent(client, request, version);
            default -> throw new IllegalArgumentException("unknown mode: " + mode);
        };
        long wallNanos = System.nanoTime() - wallStart;

        report(version, mode, samples, wallNanos);
    }

    static long[] sequential(HttpClient client, HttpRequest request,
                             HttpClient.Version version) throws Exception {
        long[] samples = new long[SEQUENTIAL_RUNS];
        for (int i = 0; i < SEQUENTIAL_RUNS; i++) {
            long t0 = System.nanoTime();
            checkedSend(client, request, version);
            samples[i] = System.nanoTime() - t0;
        }
        return samples;
    }

    static long[] concurrent(HttpClient client, HttpRequest request,
                             HttpClient.Version version) throws Exception {
        // One virtual thread per "user": all requests share the one client, so
        // HTTP/2 multiplexes them over a single TCP connection and HTTP/3 over
        // a single QUIC connection - exactly the comparison we want.
        // (Plain virtual-thread executor rather than StructuredTaskScope, so the
        // benchmark runs without --enable-preview; JEP 525 is still preview in 26.)
        List<Long> samples = new CopyOnWriteArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Void>> futures = new ArrayList<>();
            for (int t = 0; t < CONCURRENT_THREADS; t++) {
                futures.add(executor.submit((Callable<Void>) () -> {
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        long t0 = System.nanoTime();
                        checkedSend(client, request, version);
                        samples.add(System.nanoTime() - t0);
                    }
                    return null;
                }));
            }
            for (Future<Void> f : futures) f.get(); // propagate any failure
        }
        return samples.stream().mapToLong(Long::longValue).toArray();
    }

    static void checkedSend(HttpClient client, HttpRequest request,
                            HttpClient.Version expected) throws Exception {
        HttpResponse<Void> response = client.send(request, BodyHandlers.discarding());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        // The fallback guard. A benchmark that silently downgrades is worse
        // than one that crashes.
        if (response.version() != expected) {
            throw new IllegalStateException(
                    "Protocol fell back to " + response.version()
                    + " (expected " + expected + ") - check discovery mode,"
                    + " UDP reachability, and that the server speaks HTTP/3");
        }
    }

    static void report(HttpClient.Version version, String mode,
                       long[] samples, long wallNanos) {
        Arrays.sort(samples);
        int n = samples.length;
        System.out.printf(
                "%s %s n=%d p50=%.2fms p95=%.2fms p99=%.2fms wall=%.0fms%n",
                version, mode, n,
                samples[n / 2] / 1e6,
                samples[(int) (n * 0.95)] / 1e6,
                samples[(int) (n * 0.99)] / 1e6,
                wallNanos / 1e6);
    }
}
