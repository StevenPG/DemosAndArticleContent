#!/usr/bin/env bash
# Downloads the three Jackson 3 jars the comparison needs into lib/.
set -euo pipefail
cd "$(dirname "$0")"
REPO=${MAVEN_REPO:-https://repo1.maven.org/maven2}
JACKSON=3.2.4
ANNOTATIONS=2.22
mkdir -p lib
fetch() { [[ -f "lib/$3" ]] || curl -fsSL -o "lib/$3" "$REPO/$1/$2/$3"; }
fetch tools/jackson/core           "jackson-core/$JACKSON"            "jackson-core-$JACKSON.jar"
fetch tools/jackson/core           "jackson-databind/$JACKSON"        "jackson-databind-$JACKSON.jar"
fetch com/fasterxml/jackson/core   "jackson-annotations/$ANNOTATIONS" "jackson-annotations-$ANNOTATIONS.jar"
ls lib
