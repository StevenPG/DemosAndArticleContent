#!/usr/bin/env bash
#
# Dry-run a schema change against a subject WITHOUT registering it. This is the
# check you want to run in CI on every pull request that touches a schema.
#
# Usage:
#   ./scripts/check-compatibility.sh <subject> <path-to-.avsc>
#
# Example:
#   ./scripts/check-compatibility.sh orders-value evolution/order-event-v2-backward-compatible.avsc
#   ./scripts/check-compatibility.sh orders-value evolution/order-event-incompatible.avsc
#
set -euo pipefail

REGISTRY="${SCHEMA_REGISTRY_URL:-http://localhost:8081}"
SUBJECT="${1:?subject required, e.g. orders-value}"
SCHEMA_FILE="${2:?path to .avsc required}"

# --rawfile slurps the file as a JSON string, escaping it correctly for the API.
payload="$(jq -n --rawfile schema "$SCHEMA_FILE" '{schema: $schema, schemaType: "AVRO"}')"

echo "Checking '$SCHEMA_FILE' against subject '$SUBJECT' on $REGISTRY"
curl -s -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "$payload" \
  "$REGISTRY/compatibility/subjects/$SUBJECT/versions/latest" | jq .
