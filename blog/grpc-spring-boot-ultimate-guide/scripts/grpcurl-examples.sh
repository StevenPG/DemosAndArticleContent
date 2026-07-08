#!/usr/bin/env bash
# ===========================================================================
# grpcurl-examples.sh - talk gRPC to inventory-server DIRECTLY (no REST).
#
# grpcurl is "curl for gRPC" (https://github.com/fullstorydev/grpcurl).
# These commands work with NO .proto files on your machine because the
# server exposes the standard REFLECTION service - it describes its own
# API over gRPC. This is exactly how Postman's gRPC mode works too.
# ===========================================================================
set -euo pipefail

if ! command -v grpcurl >/dev/null 2>&1; then
    echo "grpcurl is not installed."
    echo "  macOS:  brew install grpcurl"
    echo "  Linux:  https://github.com/fullstorydev/grpcurl/releases"
    exit 1
fi

HOST="localhost:9090"

echo "==> Which services does the server expose? (reflection)"
grpcurl -plaintext "$HOST" list

echo
echo "==> What methods does InventoryService have?"
grpcurl -plaintext "$HOST" list inventory.v1.InventoryService

echo
echo "==> Full schema of a method, straight from the server"
grpcurl -plaintext "$HOST" describe inventory.v1.InventoryService.GetProduct

echo
echo "==> UNARY call (JSON in, JSON out - grpcurl transcodes to protobuf)"
grpcurl -plaintext -d '{"sku": "SKU-0001"}' \
    "$HOST" inventory.v1.InventoryService/GetProduct

echo
echo "==> SERVER STREAMING - watch each message arrive live"
grpcurl -plaintext -d '{"sku": "SKU-0004", "max_updates": 3}' \
    "$HOST" inventory.v1.InventoryService/WatchStock

echo
echo "==> CLIENT STREAMING - multiple JSON objects on stdin become the stream"
grpcurl -plaintext -d @ "$HOST" inventory.v1.InventoryService/RecordShipments <<'EOF'
{"sku": "SKU-0002", "quantity": 7, "supplier": "grpcurl demo"}
{"sku": "SKU-0003", "quantity": 2, "supplier": "grpcurl demo"}
EOF

echo
echo "==> BIDIRECTIONAL STREAMING"
grpcurl -plaintext -d @ "$HOST" inventory.v1.InventoryService/ProcessOrders <<'EOF'
{"order_id": "g-1", "sku": "SKU-0001", "quantity": 1}
{"order_id": "g-2", "sku": "SKU-9999", "quantity": 1}
EOF

echo
echo "==> Standard health service (what k8s gRPC probes call)"
grpcurl -plaintext "$HOST" grpc.health.v1.Health/Check
