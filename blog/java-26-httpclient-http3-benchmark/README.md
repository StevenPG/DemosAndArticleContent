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

Caddy mints a certificate from its own local CA. `caddy trust` (which `caddy run` also
attempts on startup) installs that CA into the system store and into the JDK it can find —
which is the JDK on `PATH`/`JAVA_HOME` at the time, not necessarily the JDK 26 you run the
benchmark with. What worked here, on a machine whose JDK 26 was a standalone tarball:

```bash
# Copy the JDK's own cacerts and add Caddy's root to the copy, so everything else
# still validates. Linux path shown; on macOS the root lives under
# ~/Library/Application Support/Caddy/pki/authorities/local/root.crt
cp "$JAVA_HOME/lib/security/cacerts" /tmp/bench-truststore.jks
keytool -importcert -noprompt -alias caddy-local \
    -file ~/.local/share/caddy/pki/authorities/local/root.crt \
    -keystore /tmp/bench-truststore.jks -storepass changeit

export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/tmp/bench-truststore.jks \
    -Djavax.net.ssl.trustStorePassword=changeit"
./scripts/run-bench.sh
```

`JAVA_TOOL_OPTIONS` is used rather than `-D` flags so `run-bench.sh` picks it up without
editing the `java src/H2vsH3Bench.java ...` lines.

Do not reach for `-Djdk.internal.httpclient.disableHostnameVerification=true` here: it does
not disable chain validation, so it fixes nothing on its own.

### Why the Caddyfile names a default SNI

The JDK 26 HttpClient sends no SNI extension on the QUIC path for the authority `localhost`,
though it does send one over TCP. Caddy's debug log shows `server_name: "localhost"` for the
HTTP/2 handshake and `server_name: ""` for the HTTP/3 one. Without SNI, Caddy falls back to
the connection's local IP as the certificate identifier, has no certificate for `127.0.0.1`,
and closes the QUIC handshake:

```
javax.net.ssl.SSLHandshakeException: QUIC connection establishment failed
Caused by: java.io.IOException: Connection closed by server peer: CRYPTO_ERROR|internal_error
```

HTTP/2 keeps working throughout, so this reads as "HTTP/3 is broken" rather than a
certificate-selection problem. The `default_sni localhost` global option plus `tls internal`
in the site block fixes it — the `tls` line is what makes Caddy emit a TLS connection policy
for the global option to attach to. A dotted hostname (`bench.test` in `/etc/hosts`, say)
also works, because the JDK does send SNI for those.

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
