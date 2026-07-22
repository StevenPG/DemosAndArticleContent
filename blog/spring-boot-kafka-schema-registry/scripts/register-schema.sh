#!/usr/bin/env bash
#
# Register a schema version under a subject. The registry runs the compatibility
# check first and rejects the request (HTTP 409) if the change is not allowed.
#
# Usage:
#   ./scripts/register-schema.sh <subject> <path-to-.avsc>
#
# Example:
#   ./scripts/register-schema.sh orders-value evolution/order-event-v2-backward-compatible.avsc
#
set -euo pipefail

REGISTRY="${SCHEMA_REGISTRY_URL:-http://localhost:8081}"
SUBJECT="${1:?subject required, e.g. orders-value}"
SCHEMA_FILE="${2:?path to .avsc required}"

payload="$(jq -n --rawfile schema "$SCHEMA_FILE" '{schema: $schema, schemaType: "AVRO"}')"

echo "Registering '$SCHEMA_FILE' under subject '$SUBJECT' on $REGISTRY"
curl -s -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "$payload" \
  "$REGISTRY/subjects/$SUBJECT/versions" | jq .
