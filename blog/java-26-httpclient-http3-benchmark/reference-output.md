# HTTP/2 vs HTTP/3 Benchmark — Reference Output

> **These timings are not the blog post's numbers.** They were captured on a shared 4-core
> x86_64 Linux container over loopback, not the M3 the post is written around. What they
> establish is that the benchmark compiles, that both protocols really are exercised, and what
> the output looks like. Re-measure on real hardware before publishing.

Captured on 2026-08-01 with **Temurin JDK 26.0.2+10** and **Caddy 2.10.0**, over loopback with
no impairment.

## 1. Setup that actually worked

```bash
./scripts/setup-payloads.sh          # www/small.bin (1.0K), www/large.bin (1.0M)
caddy run --config Caddyfile &       # localhost:4443, protocols ["h1","h2","h3"]

cp "$JAVA_HOME/lib/security/cacerts" /tmp/bench-truststore.jks
keytool -importcert -noprompt -alias caddy-local \
    -file ~/.local/share/caddy/pki/authorities/local/root.crt \
    -keystore /tmp/bench-truststore.jks -storepass changeit
export JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=/tmp/bench-truststore.jks \
    -Djavax.net.ssl.trustStorePassword=changeit"

./scripts/run-bench.sh
```

`caddy run` announces `enabling HTTP/3 listener` and installs its root into the system store
and into whichever JDK it finds — which was the distro JDK 21 here, not the JDK 26 tarball the
benchmark uses, hence the explicit truststore. `JAVA_TOOL_OPTIONS` rather than `-D` flags so
`run-bench.sh` needs no editing.

## 2. The API compiles as written

`java.net.http.HttpOption.H3_DISCOVERY` and `HttpOption.Http3DiscoveryMode.HTTP_3_URI_ONLY`
exist in JDK 26 exactly as the source assumes — confirmed with `javap`:

```
public interface java.net.http.HttpOption<T> {
  public static final java.net.http.HttpOption<HttpOption$Http3DiscoveryMode> H3_DISCOVERY;
}
public final class java.net.http.HttpOption$Http3DiscoveryMode extends java.lang.Enum<...> {
  public static final ... ANY;
  public static final ... ALT_SVC;
  public static final ... HTTP_3_URI_ONLY;
}
```

`H2vsH3Bench.java` runs as a single-file source program with no edits.

## 3. Clean-loopback matrix

`./scripts/run-bench.sh`, 50 warmup + 500 measured per row:

| Protocol | Payload | Mode | p50 | p95 | p99 | wall |
| --- | --- | --- | ---: | ---: | ---: | ---: |
| HTTP/2 | 1 KB | sequential | 1.77ms | 3.23ms | 5.39ms | 999ms |
| HTTP/3 | 1 KB | sequential | 2.71ms | 5.93ms | 8.54ms | 1553ms |
| HTTP/2 | 1 MB | sequential | 12.48ms | 21.25ms | 38.56ms | 7189ms |
| HTTP/3 | 1 MB | sequential | 12.37ms | 26.32ms | 31.93ms | 7153ms |
| HTTP/2 | 1 MB | concurrent | 587.13ms | 813.98ms | 832.92ms | 5941ms |
| HTTP/3 | 1 MB | concurrent | 921.61ms | 1557.04ms | 1572.04ms | 9964ms |

On a loss-free loopback HTTP/3 has nothing to win and a userspace QUIC stack to pay for, so it
trails on the small sequential row and on the concurrent row, and draws on the 1 MB sequential
row. That is the expected shape — the whole point of the post's loss-injection runs is that
this ordering is supposed to invert once packets start dropping.

Every row completed, which means the in-code fallback guard never fired: each response really
did arrive over the protocol requested, `HTTP_3_URI_ONLY` really did prevent a silent TCP
downgrade, and QUIC really was carrying the HTTP/3 rows.

## 4. Not verified here

- **Loss injection.** `tc qdisc add dev lo root netem loss 2% delay 20ms` fails in this
  container with *"Specified qdisc kind is unknown"* — no `sch_netem`, and no module loading.
  Both impaired matrices (Linux `netem` and the macOS `dnctl`/`pfctl` recipe) are still
  unverified, and they are the runs the post's argument rests on.
- `tcpdump` confirmation of QUIC on the wire (the completed HTTP/3 rows are the evidence
  instead).

## 5. The defect this run found

HTTP/3 could not have worked as the project was committed. Against `https://localhost:4443`
every HTTP/3 run died in the handshake:

```
javax.net.ssl.SSLHandshakeException: QUIC connection establishment failed
Caused by: java.io.IOException: Connection closed by server peer: CRYPTO_ERROR|internal_error
```

Caddy's debug log has the cause. The JDK 26 HttpClient sends **no SNI extension on the QUIC
path** for the single-label authority `localhost`, while it does send one over TCP:

```
# HTTP/2, TCP
tls.handshake  choosing certificate  identifier=localhost
tls.handshake  matched certificate in cache  subjects=["localhost"]

# HTTP/3, QUIC
tls.handshake  no certificate matching TLS ClientHello  server_name=""  identifier=127.0.0.1
tls.handshake  no matching certificates and no custom selection logic  identifier=127.0.0.1
```

With no SNI, Caddy falls back to the connection's local IP as the certificate identifier, has
no certificate for `127.0.0.1`, and closes the connection. HTTP/2 keeps working throughout,
which makes it look like HTTP/3 is broken rather than like certificate selection having
nothing to go on.

Two things fix it, both confirmed:

1. `default_sni localhost` as a global option **plus** `tls internal` in the site block. The
   `tls` line matters: without it Caddy emits no TLS connection policy and the global option
   is silently dropped from the adapted config (`caddy adapt` shows
   `tls_connection_policies: null`). With it, the policy carries
   `match.sni: ["", "localhost"]` and `default_sni: localhost`, and HTTP/3 connects.
2. A dotted hostname. `bench.test` in `/etc/hosts` with `tls internal` works untouched,
   because the JDK does send SNI for those — Caddy logs `identifier=bench.test` on the QUIC
   handshake.

The committed `Caddyfile` now takes option 1, so `localhost:4443` works out of the box.
