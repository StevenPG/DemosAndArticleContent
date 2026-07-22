# HTTP/2 vs HTTP/3 Latency Benchmark — Java 26 HttpClient (JEP 517)

Companion project for the blog post
[HTTP/3 in Java 26's HttpClient: Working Code and a Real Benchmark](https://stevenpg.com/posts/java-26-httpclient-http3/).

Java 26's built-in `java.net.http.HttpClient` speaks HTTP/3 (QUIC over UDP)
with zero third-party dependencies. This project measures HTTP/2 vs HTTP/3
request latency against the same server, on a clean network and under
injected packet loss — because loss is where QUIC's independent stream
recovery earns its keep.

```
                       ┌───────────────────────────────┐
   H2vsH3Bench.java ──►│  Caddy  :4443                 │
   (JDK 26 HttpClient) │  HTTP/1.1 + HTTP/2 (TCP)      │
                       │  HTTP/3 (QUIC / UDP)          │
                       └───────────────────────────────┘
          same port, same handler, protocol chosen by the client
```

## Requirements

- **JDK 26+** (HTTP/3 support is standard API in 26 — no preview flags)
- **Caddy 2.x** (`brew install caddy` / `apt install caddy`) — chosen because it
  serves HTTP/1.1, HTTP/2, and HTTP/3 simultaneously on one port with zero config
- Linux `tc`/netem or macOS `dnctl`/`pfctl` for the packet-loss runs (optional)

## Quick start

```bash
./scripts/setup-payloads.sh      # generates www/small.bin (1KB) and www/large.bin (1MB)
caddy run                        # terminal 1 — serves https://localhost:4443
./scripts/run-bench.sh           # terminal 2 — full matrix, both protocols
```

Or run a single configuration by hand:

```bash
java src/H2vsH3Bench.java HTTP_2 https://localhost:4443/small.bin sequential
java src/H2vsH3Bench.java HTTP_3 https://localhost:4443/small.bin sequential
java src/H2vsH3Bench.java HTTP_3 https://localhost:4443/large.bin concurrent
```

Arguments: `<HTTP_2|HTTP_3> <url> <sequential|concurrent>`.
Sequential mode: 50 warmup + 500 measured requests, one at a time (pure latency).
Concurrent mode: 50 virtual threads × 10 requests each (multiplexing under load).
Output is p50/p95/p99 in milliseconds plus total wall time.

### TLS trust for localhost

Caddy mints a certificate from its own local CA. Two options:

1. **Recommended:** trust Caddy's root once — `caddy trust` (installs the local CA
   into the system/Java trust store on most platforms), or import
   `~/.local/share/caddy/pki/authorities/local/root.crt` into a throwaway truststore
   and pass `-Djavax.net.ssl.trustStore=...`.
2. Quick and dirty (benchmarking only, never production):
   `java -Djdk.internal.httpclient.disableHostnameVerification=true ...`
   — note this does NOT disable chain validation, so option 1 is usually still needed.

## Injecting latency + loss

The interesting comparison. HTTP/2 multiplexes streams over one TCP connection,
so one lost segment stalls *every* stream (transport head-of-line blocking).
QUIC streams recover independently.

**Linux (loopback):**

```bash
sudo tc qdisc add dev lo root netem loss 2% delay 20ms
./scripts/run-bench.sh
sudo tc qdisc del dev lo root      # ALWAYS clean up
```

**macOS:**

```bash
# Create a dummynet pipe with 20ms delay and 2% packet loss
sudo dnctl pipe 1 config delay 20 plr 0.02

# Send loopback traffic on port 4443 through the pipe
echo "dummynet in proto {tcp,udp} from any to any port 4443 pipe 1" | sudo pfctl -f -
sudo pfctl -e

./scripts/run-bench.sh

# Tear down
sudo pfctl -d
sudo dnctl -q flush
```

## Verifying which protocol was actually used

The benchmark **throws** if a response arrives over a different protocol than
requested (`HTTP_3_URI_ONLY` discovery mode disables silent TCP fallback), so a
completed run *is* the verification. To see it on the wire anyway:

```bash
sudo tcpdump -i lo0 'udp port 4443'    # QUIC = UDP; HTTP/2 shows up as TCP
```

Or turn on the JDK client's own logging:

```bash
java -Djdk.httpclient.HttpClient.log=requests,quic src/H2vsH3Bench.java ...
```

## Gotchas reproduced here on purpose

- `HTTP_3_URI_ONLY` is set explicitly because the default discovery mode
  (`ALT_SVC`) never uses HTTP/3 for the *first* connection — a benchmark (or a
  one-shot CLI) would silently measure HTTP/2 against itself.
- The `HttpClient` is reused across all requests. Building a client per request
  measures connection setup, not request latency, and defeats Alt-Svc caching.
- Docker users: `-p 4443:4443` publishes **TCP only**. QUIC needs
  `-p 4443:4443/udp` as well.
