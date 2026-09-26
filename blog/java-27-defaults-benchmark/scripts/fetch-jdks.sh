#!/usr/bin/env bash
# Download Temurin 25, 26 and 27 for the architecture Docker runs containers as
# (linux/aarch64 on Apple Silicon, linux/x64 on most CI and cloud hosts) into
# ../.jdks/jdk25, jdk26, jdk27. The benchmark mounts these into a plain Debian
# container so every row uses the same OS image and only the JVM changes.
#
# Temurin 27 was not on Docker Hub as an image when this was written, which is
# the other reason the JDKs are mounted rather than baked into per-JDK images.
set -euo pipefail

here="$(cd "$(dirname "$0")/.." && pwd)"
dest="$here/.jdks"
mkdir -p "$dest"

case "$(docker info --format '{{.Architecture}}')" in
  aarch64|arm64) arch=aarch64 ;;
  x86_64|amd64)  arch=x64 ;;
  *) echo "unsupported docker architecture" >&2; exit 1 ;;
esac

for v in 25 26 27; do
  if [[ -x "$dest/jdk$v/bin/java" ]]; then
    echo "jdk$v already present: $("$dest/jdk$v/bin/java" -version 2>&1 | head -1 || true)"
    continue
  fi
  url=$(curl -fsS "https://api.adoptium.net/v3/assets/latest/$v/hotspot?os=linux&architecture=$arch&image_type=jdk" \
        | python3 -c 'import json,sys; print(json.load(sys.stdin)[0]["binary"]["package"]["link"])')
  echo "jdk$v <- $url"
  mkdir -p "$dest/jdk$v"
  curl -fsSL "$url" | tar xz -C "$dest/jdk$v" --strip-components=1
done

echo
echo "Record these in results/results.md - the exact builds matter:"
for v in 25 26 27; do
  cat "$dest/jdk$v/release" | grep -E '^(JAVA_RUNTIME_VERSION|IMPLEMENTOR)=' | tr '\n' ' '
  echo
done
