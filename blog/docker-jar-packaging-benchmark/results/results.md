# Results

Recorded 2026-09-15T15:58:40+00:00 by `scripts/benchmark.py`.

## Image size and build

| Variant | What it is | Pull size (compressed) | On disk | Layers | Cold build | Rebuild after code change | Re-pulled after a code change |
|---|---|---|---|---|---|---|---|
| `01-fatjar-jdk` | Fat jar on the full JDK image | 196 MB | 668 MB | 7 | 19.9s | 14.5s | 63 MB |
| `02-fatjar-jre` | Fat jar on the JRE image | 161 MB | 549 MB | 7 | 19.8s | 14.7s | 63 MB |
| `03-layered` | Spring Boot layered jar, JRE image | 161 MB | 551 MB | 10 | 21.6s | 13.7s | 1.5 MB |
| `04-extracted` | Extracted jar (thin jar + lib/), JRE image | 161 MB | 549 MB | 8 | 20.8s | 14.4s | 0.4 MB |
| `05-extracted-cds` | Extracted + AppCDS archive | 188 MB | 679 MB | 7 | 40.1s | 34.9s | 166 MB |
| `06-extracted-aot` | Extracted + JDK 25 AOT cache | 191 MB | 712 MB | 7 | 50.7s | 45.4s | 195 MB |
| `07-jlink-fatjar` | jlink runtime + fat jar, debian-slim | 128 MB | 347 MB | 4 | 34.5s | 29.6s | 63 MB |
| `08-jlink-extracted-aot` | jlink + extracted + AOT cache | 159 MB | 509 MB | 4 | 64.8s | 58.2s | 195 MB |
| `09-jlink-alpine` | jlink (musl) + extracted, Alpine | 101 MB | 239 MB | 5 | 42.1s | 28.4s | 0.4 MB |
| `10-jlink-distroless` | jlink + extracted, distroless | 108 MB | 267 MB | 21 | 34.1s | 25.7s | 0.4 MB |

## Startup, memory and throughput

| Variant | Startup (median) | Startup (best) | Classes loaded | RSS idle | RSS under load | Throughput | p50 | p99 |
|---|---|---|---|---|---|---|---|---|
| `01-fatjar-jdk` | 9152.8 ms | 8887.6 ms | 19246 | 298.4 MB | 370.6 MB | 775.3 rps | 12.9 ms | 87.97 ms |
| `02-fatjar-jre` | 8819.9 ms | 8723.9 ms | 19223 | 300.7 MB | 378.9 MB | 758.6 rps | 13.5 ms | 87.23 ms |
| `03-layered` | 7967.2 ms | 7705.1 ms | 19157 | 305.3 MB | 378.6 MB | 801.5 rps | 12.57 ms | 90.46 ms |
| `04-extracted` | 7809.3 ms | 7659.6 ms | 19117 | 296.6 MB | 388.0 MB | 758.5 rps | 13.65 ms | 88.76 ms |
| `05-extracted-cds` | 5578.1 ms | 5446.5 ms | 18923 | 276.6 MB | 363.5 MB | 736.8 rps | 13.49 ms | 88.76 ms |
| `06-extracted-aot` | 4461.8 ms | 4389.7 ms | 19664 | 270.7 MB | 358.1 MB | 962.5 rps | 12.22 ms | 75.73 ms |
| `07-jlink-fatjar` | 9235.1 ms | 9018.1 ms | 19202 | 282.5 MB | 369.7 MB | 807.3 rps | 12.79 ms | 84.5 ms |
| `08-jlink-extracted-aot` | 4376.2 ms | 4220.7 ms | 19636 | 272.2 MB | 367.4 MB | 1009.0 rps | 11.79 ms | 75.27 ms |
| `09-jlink-alpine` | 9028.0 ms | 8740.2 ms | 19099 | 286.5 MB | 348.0 MB | 693.1 rps | 13.97 ms | 90.19 ms |
| `10-jlink-distroless` | 7926.3 ms | 7692.5 ms | 19090 | 296.6 MB | 362.0 MB | 785.1 rps | 13.04 ms | 88.01 ms |
