#!/usr/bin/env bash
# ===========================================================================
# demo-requests.sh - exercises all four gRPC method types through the
# storefront's REST edge. Run ./scripts/run-demo.sh first.
#
# Every request below travels: curl -> HTTP/JSON -> storefront-client
# -> gRPC/protobuf -> inventory-server, and back.
# ===========================================================================
set -euo pipefail

BASE="http://localhost:8080/api"

# Pretty-print JSON with jq when available, otherwise pass through.
if command -v jq >/dev/null 2>&1; then
    PRETTY=(jq .)
else
    PRETTY=(cat)
fi

banner() {
    echo
    echo "==================================================================="
    echo "  $1"
    echo "==================================================================="
}

banner "1. UNARY - GET /api/products (ListProducts RPC)"
curl -sf "$BASE/products" | "${PRETTY[@]}"

banner "2. UNARY with deadline - GET /api/products/SKU-0003 (GetProduct RPC)"
curl -sf "$BASE/products/SKU-0003" | "${PRETTY[@]}"

banner "3. ERROR MAPPING - unknown SKU -> gRPC NOT_FOUND -> HTTP 404"
curl -s "$BASE/products/SKU-9999" | "${PRETTY[@]}" || true
echo "HTTP status: $(curl -s -o /dev/null -w '%{http_code}' "$BASE/products/SKU-9999")"

banner "4. SERVER STREAMING - live stock feed as Server-Sent Events (WatchStock RPC)"
echo "(each event below was pushed by the server in real time, ~400ms apart)"
curl -sN "$BASE/products/SKU-0001/stock/stream?updates=4"

banner "5. CLIENT STREAMING - upload a batch of shipments (RecordShipments RPC)"
curl -sf -X POST "$BASE/shipments" \
    -H 'Content-Type: application/json' \
    -d '[
          {"sku": "SKU-0001", "quantity": 25, "supplier": "Acme Wholesale"},
          {"sku": "SKU-0005", "quantity": 60, "supplier": "Bean Supply Co"},
          {"sku": "SKU-0002", "quantity": 3,  "supplier": "Acme Wholesale"}
        ]' | "${PRETTY[@]}"

banner "6. BIDIRECTIONAL STREAMING - stream orders, stream confirmations (ProcessOrders RPC)"
curl -sf -X POST "$BASE/orders" \
    -H 'Content-Type: application/json' \
    -d '[
          {"orderId": "order-001", "sku": "SKU-0001", "quantity": 2},
          {"orderId": "order-002", "sku": "SKU-0004", "quantity": 1},
          {"orderId": "order-003", "sku": "SKU-0005", "quantity": 999},
          {"orderId": "order-004", "sku": "SKU-XXXX", "quantity": 1}
        ]' | "${PRETTY[@]}"

echo
echo "Done. Check .demo/*.log to see the interceptor logging on both sides."
