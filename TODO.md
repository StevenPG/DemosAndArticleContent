# TODO — Java 26 Benchmark Projects (PR #13)

Everything that needs to happen before/after merging
[PR #13](https://github.com/StevenPG/DemosAndArticleContent/pull/13) and before the
two companion blog posts on stevenpg.com flip from `draft: true` to published.

**Important context:** this PR was authored in an environment with only JDK 21
available, so **nothing in it has been compiled or executed**. Shell scripts are
`bash -n` syntax-checked only. Everything below is about closing that gap.

## `blog/java-26-httpclient-http3-benchmark`

### Must do (blocks the HTTP/3 blog post)

- [ ] **Smoke-run on JDK 26**: `./scripts/setup-payloads.sh`, `caddy run`, then
      `java src/H2vsH3Bench.java HTTP_2 https://localhost:4443/small.bin sequential`
      and the same with `HTTP_3`. Fix any compile/API drift — the `HttpOption.H3_DISCOVERY`
      / `Http3DiscoveryMode` usage was written from JEP 517 docs, not compiled.
- [ ] **Sort out localhost TLS trust** and verify the README's two options actually work:
      `caddy trust` (does it reach the Java truststore on macOS?) vs importing
      `root.crt` into a throwaway truststore with `-Djavax.net.ssl.trustStore`.
      Update the README with whichever incantation actually worked.
- [ ] **Confirm the HTTP/3 path is real**: run `sudo tcpdump -i lo0 'udp port 4443'`
      once and see QUIC packets; the in-code fallback guard should make this redundant,
      but verify the guard itself fires by pointing an `HTTP_3` run at an HTTP/2-only server.
- [ ] **Run the full matrix** (`./scripts/run-bench.sh`) on the M3:
      clean network, then with loss injection.
- [ ] **Verify the macOS loss-injection commands** (`dnctl pipe` + `pfctl`) as written
      in the README — they were written from documentation, not tested. Fix as needed.
- [ ] **Transfer the numbers** into the three `*TBD*` tables in
      `coding-steve` → `src/content/blog/2026-07-20-java-26-httpclient-http3.md`,
      remove that post's `[DRAFT NOTE — numbers pending]` callout, and reconcile the
      "what to expect" paragraph with what was actually measured.

### Nice to have

- [ ] Add a reference-output log (like `grpc-spring-boot-ultimate-guide/reference-output.md`)
      showing a real run's output shape.
- [ ] Concurrent-mode note: consider a `StructuredTaskScope` variant behind
      `--enable-preview` once JEP 525 finalizes (the blog post's prose uses it; the
      benchmark deliberately uses a plain virtual-thread executor to avoid the flag).

## `blog/java-26-aot-cache-zgc-benchmark`

### Must do (blocks the AOT/ZGC blog post)

- [ ] **Build check**: `cd bench-app && ./gradlew bootJar`. Watch for:
      - Spring Boot `4.1.0` plugin availability (bump to latest `4.1.x` patch)
      - toolchain 25 download via Gradle
      - `spring-boot-starter-webmvc` + `data-jpa` + H2 wiring compiling as written
- [ ] **Verify the training profile**: `./scripts/train.sh` should show the three
      `[training] GET ... -> 200` lines and exit 0, leaving `app.aot`. The port is
      hardcoded to 8080 in `TrainingRunner` — confirm no clash, or parameterize.
- [ ] **Verify `-XX:AOTCacheOutput` one-step flow on JDK 26** — if the JDK still wants
      the two-step record/create flow for this app shape, update `train.sh` and the
      blog post's commands together.
- [ ] **Verify the cache-engagement check**: run `measure-startup.sh` once with a
      deliberately stale/corrupt `app.aot` and confirm the script FAILS (the grep
      patterns `"Unable to use AOT cache"` / `"AOT cache disabled"` were written from
      documentation; match them to the real `-Xlog:aot` output).
- [ ] **Confirm readiness endpoint path**: `/actuator/health/readiness` must return 200
      with only `management.endpoint.health.probes.enabled=true` (no
      `management.endpoints.web.exposure` override) on Boot 4.1 — adjust
      `application.yaml` if the health group isn't exposed by default.
- [ ] **Run the matrix** (`./scripts/train.sh && ./scripts/run-matrix.sh`) on the M3
      with JDK 26 Temurin, plus the JDK 25-vs-26 ZGC comparison rows (needs a JDK 25
      install for the "before" row).
- [ ] **Transfer the numbers** into the `*TBD*` tables in
      `coding-steve` → `src/content/blog/2026-07-29-java-26-aot-cache-zgc-leyden-benchmarks.md`
      (three tables: GC×cache matrix, ZGC across JDK versions, cache size/RSS),
      remove the draft-note callout, and reconcile the "expected ~40% band" analysis.
- [ ] **RSS sampling on macOS**: `measure-startup.sh` falls back to `ps -o rss=` when
      `/proc` is absent — confirm the value is KB on macOS (it is, but verify) and that
      the MB conversion prints sanely.

### Nice to have

- [ ] Add root README entry if the repo README indexes projects (check top-level `README.md`).
- [ ] Pin an exact JDK 26 build (e.g. Temurin 26.0.x) in both READMEs once the
      benchmark numbers are recorded, for reproducibility.

## Repo/PR housekeeping

- [ ] Review + merge [PR #13](https://github.com/StevenPG/DemosAndArticleContent/pull/13)
      — the blog posts link to `tree/main/blog/...` paths that only resolve after merge.
- [ ] After merge, delete the `claude/java-26-benchmark-projects` branch.
- [ ] The blog-side checklist lives in `coding-steve/TODO.md` — keep the two in sync
      (its benchmark items point at this PR).
