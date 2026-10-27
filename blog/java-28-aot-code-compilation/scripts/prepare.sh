#!/usr/bin/env bash
# One-time setup after `./gradlew build`: extract the Spring Boot jar, generate CLI input,
# build the Go load generator.
source "$(dirname "$0")/lib.sh"

# The AOT cache only covers classes loaded by the JDK's built-in class loaders from the
# class path. Spring Boot's nested "fat jar" loader is a custom class loader, so we use the
# extracted layout Spring recommends for CDS/AOT: a thin jar + lib/ on a plain class path.
rm -rf "$WORK/spring-rest"
"$JDK_HOME_28/bin/java" -Djarmode=tools -jar "$ROOT/apps/spring-rest/build/libs/spring-rest.jar" \
  extract --destination "$WORK/spring-rest"

"$JDK_HOME_28/bin/java" -jar "$CLI_JAR" generate --out "$CLI_INPUT" --rows 20000

(cd "$ROOT/loadgen" && go build -o loadgen .)
echo "prepared: $SPRING_JAR, $CLI_INPUT, $LOADGEN"
