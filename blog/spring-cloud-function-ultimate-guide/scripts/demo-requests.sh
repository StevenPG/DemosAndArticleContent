#!/usr/bin/env bash
# -----------------------------------------------------------------------------
# Exercises every feature of the guide against the running system, labeled.
# Run ./scripts/run-demo.sh first.
#
# The point to notice: the HTTP calls and the Kafka calls invoke the SAME
# functions-core beans. Nothing about the functions changed between surfaces.
# -----------------------------------------------------------------------------
set -uo pipefail
cd "$(dirname "$0")/.."

WEB=http://localhost:8080
hr() { printf '\n\033[1m== %s ==\033[0m\n' "$1"; }
post() { curl -s -X POST "$@"; echo; }

ORDER='{"orderId":"ord-1","customerId":"cust-alice","amount":199.99,"currency":"USD","itemCount":2}'
BIG_ORDER='{"orderId":"ord-2","customerId":"cust-bob","amount":12000.00,"currency":"USD","itemCount":9}'

# ---------------------------------------------------------------- HTTP surface
hr "Supplier — GET /generateOrders (source, no input)"
curl -s "$WEB/generateOrders"; echo

hr "Function — POST /enrichOrder (Order -> EnrichedOrder)"
post "$WEB/enrichOrder" -H 'Content-Type: application/json' -d "$ORDER"

hr "Composition — POST /enrichOrder,validateOrder (Order -> Decision)"
post "$WEB/enrichOrder,validateOrder" -H 'Content-Type: application/json' -d "$ORDER"

hr "Composition on a risky order -> REVIEW/REJECTED"
post "$WEB/enrichOrder,validateOrder" -H 'Content-Type: application/json' -d "$BIG_ORDER"

hr "Routing — POST /functionRouter with 'order-channel: express' -> fastApprove"
post "$WEB/functionRouter" -H 'Content-Type: application/json' -H 'order-channel: express' -d "$ORDER"

hr "Routing — POST /functionRouter with no channel -> full pipeline"
post "$WEB/functionRouter" -H 'Content-Type: application/json' -d "$ORDER"

hr "Message headers — POST /decideWithHeaders (watch the response headers)"
curl -s -i -X POST "$WEB/decideWithHeaders" -H 'Content-Type: application/json' -H 'channel: mobile-app' -d "$ORDER" \
  | grep -iE '^(HTTP/|channel:|decision-outcome:|processed-by:|customer-tier:)'; echo

hr "Custom content-type — POST /enrichOrder as text/csv"
post "$WEB/enrichOrder" -H 'Content-Type: text/csv' -d 'ord-9,cust-alice,199.99,USD,3'

hr "Batch — POST /validateBatch (List<Order> -> List<Decision>)"
post "$WEB/validateBatch" -H 'Content-Type: application/json' \
  -d "[$ORDER,$BIG_ORDER]"

hr "Dynamic function — POST /dynamicUppercase (registered at runtime)"
post "$WEB/dynamicUppercase" -H 'Content-Type: text/plain' -d 'hello from a runtime-registered function'

# --------------------------------------------------------------- Kafka surface
KCLI=(docker compose exec -T kafka /opt/kafka/bin)

hr "Kafka — publish a valid order to 'orders', read the Decision off 'decisions'"
echo "$ORDER" | "${KCLI[@]}"/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders >/dev/null 2>&1
echo "  produced -> orders"
echo "  decisions topic says:"
"${KCLI[@]}"/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic decisions \
  --from-beginning --max-messages 1 --timeout-ms 15000 2>/dev/null | sed 's/^/    /'

hr "Kafka DLQ — publish a poison order (currency EUR) -> lands on 'orders-dlq'"
echo '{"orderId":"ord-eur","customerId":"cust-bob","amount":50.00,"currency":"EUR","itemCount":1}' \
  | "${KCLI[@]}"/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic orders >/dev/null 2>&1
echo "  produced poison -> orders"
echo "  orders-dlq topic says:"
"${KCLI[@]}"/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic orders-dlq \
  --from-beginning --max-messages 1 --timeout-ms 15000 2>/dev/null | sed 's/^/    /'

# ------------------------------------------------------------- RSocket surface
hr "RSocket — the app-rsocket surface is on tcp://localhost:7000"
echo "  Routes: orders.enrich | orders.decide | orders.decideStream"
echo "  Drive it with the 'rsc' CLI, e.g.:"
echo "    rsc --route orders.decide --data '$ORDER' tcp://localhost:7000"
echo "  or just run:  ./gradlew :app-rsocket:test  (a real RSocket client test)"

echo
echo "Done. Tear down with ./scripts/stop-demo.sh"
