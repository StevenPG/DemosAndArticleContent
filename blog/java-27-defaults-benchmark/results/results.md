# Java 27 defaults benchmark - results

Docker arch `aarch64`, 2 CPUs visible to Docker, 3 runs per row (medians shown), 30 s load at 16 client threads.

## `small` - `--cpus 1 --memory 1g`

| Row | Collector | Compact headers | Max heap MiB | Ready s | Live set MiB | Container idle MiB | Container loaded MiB | req/s | p50 ms | p99 ms | GCs | GC ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `jdk25` | Serial | false | 248 | 3.74 | 118.4 | 290 | 383 | 1,550 | 1.9 | 94.5 | 22 | 129 |
| `jdk25+coh` | Serial | true | 248 | 3.45 | 101.2 | 246 | 356 | 1,294 | 2.1 | 103.6 | 20 | 125 |
| `jdk26` | Serial | false | 256 | 3.39 | 118.6 | 296 | 388 | 1,571 | 1.8 | 94.6 | 19 | 49 |
| `jdk27` | G1 | true | 256 | 3.72 | 101.4 | 295 | 346 | 1,280 | 2.0 | 107.7 | 29 | 161 |
| `jdk27-coh` | G1 | false | 256 | 3.51 | 118.5 | 299 | 363 | 1,131 | 2.0 | 123.5 | 30 | 166 |
| `jdk27+serial` | Serial | true | 256 | 3.57 | 101.3 | 256 | 370 | 1,610 | 1.9 | 91.9 | 19 | 79 |

## `medium` - `--cpus 2 --memory 2g`

| Row | Collector | Compact headers | Max heap MiB | Ready s | Live set MiB | Container idle MiB | Container loaded MiB | req/s | p50 ms | p99 ms | GCs | GC ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `jdk25` | G1 | false | 512 | 1.78 | 118.3 | 286 | 447 | 2,203 | 1.4 | 69.1 | 20 | 98 |
| `jdk25+coh` | G1 | true | 512 | 2.55 | 101.0 | 288 | 376 | 1,769 | 2.2 | 90.7 | 25 | 96 |
| `jdk26` | G1 | false | 512 | 1.76 | 118.4 | 346 | 450 | 2,217 | 1.6 | 72.6 | 20 | 100 |
| `jdk27` | G1 | true | 512 | 2.62 | 101.4 | 322 | 393 | 2,152 | 2.0 | 73.2 | 19 | 100 |
| `jdk27-coh` | G1 | false | 512 | 2.01 | 118.6 | 338 | 438 | 2,435 | 1.5 | 62.7 | 20 | 97 |
| `jdk27+serial` | Serial | true | 512 | 1.83 | 101.3 | 289 | 417 | 2,939 | 1.3 | 51.2 | 36 | 124 |

## Rows

- `jdk25` - JDK 25 LTS, all defaults
- `jdk25+coh` - JDK 25 with the flag you could already set (`-XX:+UseCompactObjectHeaders`)
- `jdk26` - JDK 26, all defaults
- `jdk27` - JDK 27, all defaults (G1 + compact headers)
- `jdk27-coh` - JDK 27, legacy 12-byte headers (`-XX:-UseCompactObjectHeaders`)
- `jdk27+serial` - JDK 27, Serial pinned back (`-XX:+UseSerialGC`)
