# Java 27 defaults benchmark - results

Docker arch `x86_64`, 4 CPUs visible to Docker, 2 runs per row (medians shown), 20 s load at 16 client threads.

## `small` - `--cpus 1 --memory 1g`

| Row | Collector | Compact headers | Max heap MiB | Ready s | Live set MiB | Container idle MiB | Container loaded MiB | req/s | p50 ms | p99 ms | GCs | GC ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `jdk25` | Serial | false | 248 | 8.23 | 118.5 | 232 | 326 | 393 | 7.0 | 319.1 | 4 | 60 |
| `jdk25+coh` | Serial | true | 248 | 8.30 | 101.4 | 205 | 307 | 399 | 6.4 | 304.4 | 4 | 70 |
| `jdk26` | Serial | false | 256 | 8.11 | 118.8 | 249 | 337 | 391 | 6.0 | 310.7 | 4 | 55 |
| `jdk27` | G1 | true | 256 | 8.17 | 101.4 | 230 | 269 | 299 | 6.7 | 452.4 | 8 | 46 |
| `jdk27-coh` | G1 | false | 256 | 8.30 | 118.5 | 264 | 284 | 267 | 7.8 | 500.2 | 10 | 116 |
| `jdk27+serial` | Serial | true | 256 | 8.29 | 101.3 | 217 | 316 | 370 | 6.4 | 392.1 | 4 | 24 |

## `medium` - `--cpus 2 --memory 2g`

| Row | Collector | Compact headers | Max heap MiB | Ready s | Live set MiB | Container idle MiB | Container loaded MiB | req/s | p50 ms | p99 ms | GCs | GC ms |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `jdk25` | G1 | false | 512 | 4.07 | 118.2 | 267 | 389 | 870 | 5.2 | 184.2 | 8 | 146 |
| `jdk25+coh` | G1 | true | 512 | 4.19 | 101.1 | 246 | 339 | 956 | 4.0 | 159.2 | 9 | 70 |
| `jdk26` | G1 | false | 512 | 3.99 | 118.3 | 322 | 373 | 924 | 3.7 | 173.4 | 8 | 70 |
| `jdk27` | G1 | true | 512 | 4.19 | 101.5 | 287 | 392 | 979 | 4.1 | 148.2 | 5 | 52 |
| `jdk27-coh` | G1 | false | 512 | 4.11 | 118.6 | 326 | 385 | 923 | 3.9 | 169.9 | 8 | 63 |
| `jdk27+serial` | Serial | true | 512 | 4.17 | 101.5 | 234 | 365 | 1,223 | 4.1 | 108.1 | 10 | 209 |

## Rows

- `jdk25` - JDK 25 LTS, all defaults
- `jdk25+coh` - JDK 25 with the flag you could already set (`-XX:+UseCompactObjectHeaders`)
- `jdk26` - JDK 26, all defaults
- `jdk27` - JDK 27, all defaults (G1 + compact headers)
- `jdk27-coh` - JDK 27, legacy 12-byte headers (`-XX:-UseCompactObjectHeaders`)
- `jdk27+serial` - JDK 27, Serial pinned back (`-XX:+UseSerialGC`)
