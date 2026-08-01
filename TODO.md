# TODO — Java 26 Benchmark Projects (PR #13)

Everything that needs to happen before/after merging
[PR #13](https://github.com/StevenPG/DemosAndArticleContent/pull/13) and before the
two companion blog posts on stevenpg.com flip from `draft: true` to published.

**Status:** both projects have now been built and run on **Temurin JDK 26.0.2+10**, on a
shared 4-core x86_64 Linux container. Everything compiles and runs; four real defects were
found and fixed. Captured output lives in each project's `reference-output.md`. What is still
open is the impaired-network runs and the numbers themselves, which have to come off the M3.

## `blog/java-26-httpclient-http3-benchmark`

### Done

- [x] **Smoke-run on JDK 26.** `setup-payloads.sh`, `caddy run`, and both
      `java src/H2vsH3Bench.java HTTP_2|HTTP_3 ...` invocations work. No API drift:
      `HttpOption.H3_DISCOVERY` and `Http3DiscoveryMode.HTTP_3_URI_ONLY` exist in JDK 26
      exactly as written (verified with `javap`).
- [x] **Localhost TLS trust sorted out.** `caddy run` installs its root into the system store
      and the JDK it can find, which is not necessarily the JDK 26 you benchmark with. The
      README now carries the `keytool` + `JAVA_TOOL_OPTIONS` recipe that worked.
- [x] **Fixed: HTTP/3 could not connect at all.** The JDK 26 HttpClient sends no SNI on the
      QUIC path for `localhost`, so Caddy could not select a certificate and killed the
      handshake with `CRYPTO_ERROR|internal_error` while HTTP/2 kept working. The `Caddyfile`
      now sets `default_sni localhost` + `tls internal`. Full write-up in
      `reference-output.md`.
- [x] **Confirmed the HTTP/3 path is real.** The fallback guard throws on any protocol
      mismatch, and all HTTP/3 rows completed, so the responses did arrive over QUIC.
- [x] **Clean-network matrix run** (`./scripts/run-bench.sh`) — table in `reference-output.md`.

### Still open

- [ ] **Run the matrix on the M3**, clean network then with loss injection. The container
      numbers are shape-only.
- [ ] **Loss injection is still unverified on both platforms.** `tc qdisc ... netem` is not
      available in the container (no `sch_netem`), so neither the Linux commands nor the macOS
      `dnctl`/`pfctl` recipe in the README has been executed. These are the runs the post's
      argument depends on — on a loss-free loopback HTTP/3 loses every row, as expected.
- [ ] **Transfer the numbers** into the three `*TBD*` tables in
      `coding-steve` → `src/content/blog/2026-07-20-java-26-httpclient-http3.md`,
      remove that post's `[DRAFT NOTE — numbers pending]` callout, and reconcile the
      "what to expect" paragraph with what was actually measured.

### Nice to have

- [ ] Concurrent-mode note: consider a `StructuredTaskScope` variant behind
      `--enable-preview` once JEP 525 finalizes (the blog post's prose uses it; the
      benchmark deliberately uses a plain virtual-thread executor to avoid the flag).

## `blog/java-26-aot-cache-zgc-benchmark`

### Done

- [x] **Build check.** `cd bench-app && ./gradlew bootJar` succeeds. Fixed: the toolchain was
      pinned to Java 25 while the README asks for JDK 26, so the build failed on a machine
      with exactly the required JDK. Now 26, with a README note that `gradlew` prefers
      `JAVA_HOME` over `PATH`.
- [x] **Training profile verified** — and fixed. It ran as a `CommandLineRunner`, which fires
      before readiness flips to `ACCEPTING_TRAFFIC` and raced `DataLoader`'s runner (both at
      `@Order(Integer.MAX_VALUE)`), so it printed `readiness -> 503` and `/products/1 -> 500`
      and trained the failure paths. Now driven off the availability event; all three requests
      answer 200. Port 8080 had no clash.
- [x] **`-XX:AOTCacheOutput` one-step flow works on JDK 26** — no two-step record/create
      needed. Cache is ~118 MiB.
- [x] **Cache-engagement check fixed and verified both ways.** The old grep for
      `"Unable to use AOT cache"` / `"AOT cache disabled"` does not fire for a stale cache —
      JDK 26 prints neither phrase, it prints `The AOT cache has been truncated` +
      `[error][aot] Unable to map shared spaces` and then starts uncached. The check is now
      positive (require `Mapped static region`, reject `[error][aot]`); a healthy cache passes
      and a truncated one exits 1.
- [x] **Readiness endpoint confirmed.** `/actuator/health/readiness` returns 200 on Boot 4.1
      with only `management.endpoint.health.probes.enabled=true`; no exposure override needed.
- [x] **Fixed: ZGC cannot use a G1-built cache.** JEP 516 made the object cache GC-agnostic,
      but the cache records its oop encoding and ZGC has no compressed oops, so `app.aot`
      is rejected with `Unable to use AOT cache` and the JVM silently continues uncached.
      `train.sh` now builds `app-zgc.aot` as well and `run-matrix.sh` routes it. **The
      README's "one cache artifact per app version, consumed under all three collectors"
      claim was wrong and the blog post must not repeat it** — the rule is one artifact per
      oop encoding.
- [x] **Matrix run** on the container: G1 −33.6%, ZGC −29.0%, Serial −30.7%. Table in
      `reference-output.md`.
- [x] **RSS sampling** works on Linux via `/proc`; the macOS `ps -o rss=` fallback is still
      untested.

### Still open

- [ ] **Run the matrix on the M3 with JDK 26 Temurin**, plus the JDK 25-vs-26 ZGC comparison
      rows (needs a JDK 25 install for the "before" row — not done here).
- [ ] **Transfer the numbers** into the `*TBD*` tables in
      `coding-steve` → `src/content/blog/2026-07-29-java-26-aot-cache-zgc-leyden-benchmarks.md`
      (three tables: GC×cache matrix, ZGC across JDK versions, cache size/RSS),
      remove the draft-note callout, and reconcile the "expected ~40% band" analysis — the
      container came in at 29-34%.
- [ ] **Rework the post's "train once, run anywhere" section** around the two-artifact
      reality above. This is the one finding that changes the post's argument, not just its
      numbers.
- [ ] Verify the macOS RSS path (`ps -o rss=`) prints sane MB values.
- [ ] Consider noting that `measure-startup.sh` forks a `curl` every 5ms, which competes with
      the JVM it is timing on core-constrained machines.

### Nice to have

- [ ] Pin an exact JDK 26 build (e.g. Temurin 26.0.x) in both READMEs once the
      benchmark numbers are recorded, for reproducibility.

## Repo/PR housekeeping

- [ ] Review + merge [PR #13](https://github.com/StevenPG/DemosAndArticleContent/pull/13)
      — the blog posts link to `tree/main/blog/...` paths that only resolve after merge.
- [ ] After merge, delete the `claude/java-26-benchmark-projects` branch.
- [ ] The blog-side checklist lives in `coding-steve/TODO.md` — keep the two in sync
      (its benchmark items point at this PR).
