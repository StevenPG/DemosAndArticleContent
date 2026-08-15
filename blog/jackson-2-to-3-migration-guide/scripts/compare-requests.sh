#!/usr/bin/env bash
# ===========================================================================
# compare-requests.sh - fires the SAME request at both apps, side by side,
# so the article can show "identical JSON, different code required to get
# there." Run ./scripts/run-both.sh first.
# ===========================================================================
set -euo pipefail

J2="http://localhost:8082/api"
J3="http://localhost:8083/api"

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

banner "1. Same payload from both versions: GET /api/posts/sample"
echo "--- jackson2-example (Spring Boot 3.5 / Jackson 2.21) ---"
curl -sf "$J2/posts/sample" | "${PRETTY[@]}"
echo "--- jackson3-example (Spring Boot 4.1 / Jackson 3.1)  ---"
curl -sf "$J3/posts/sample" | "${PRETTY[@]}"
echo "(Optional<String>, Instant, and the custom Money type all render identically -"
echo " jackson3-example just needed two fewer modules registered to get there.)"

banner "2. Record DTO round trip: POST /api/comments/echo"
BODY='{"author":"reader42","body":"Great migration guide!"}'
echo "--- jackson2-example ---"
curl -sf -X POST "$J2/comments/echo" -H 'Content-Type: application/json' -d "$BODY" | "${PRETTY[@]}"
echo "--- jackson3-example ---"
curl -sf -X POST "$J3/comments/echo" -H 'Content-Type: application/json' -d "$BODY" | "${PRETTY[@]}"

banner "3. Streaming API output: GET /api/health/streamed"
echo "--- jackson2-example ---"
curl -sf "$J2/health/streamed" | "${PRETTY[@]}"
echo "--- jackson3-example ---"
curl -sf "$J3/health/streamed" | "${PRETTY[@]}"

banner "4. Tree model enrichment: GET /api/posts/sample/enriched"
echo "--- jackson2-example ---"
curl -sf "$J2/posts/sample/enriched" | "${PRETTY[@]}"
echo "--- jackson3-example ---"
curl -sf "$J3/posts/sample/enriched" | "${PRETTY[@]}"

banner "5. The same failure, mapped by checked vs. unchecked exceptions: GET /api/posts/broken/raw-json"
echo "--- jackson2-example (JsonProcessingException is CHECKED) ---"
curl -s "$J2/posts/broken/raw-json" | "${PRETTY[@]}"
echo "HTTP status: $(curl -s -o /dev/null -w '%{http_code}' "$J2/posts/broken/raw-json")"
echo "--- jackson3-example (JacksonException is UNCHECKED) ---"
curl -s "$J3/posts/broken/raw-json" | "${PRETTY[@]}"
echo "HTTP status: $(curl -s -o /dev/null -w '%{http_code}' "$J3/posts/broken/raw-json")"
echo "(Same InvalidDefinitionException, same HTTP 500 - the only difference is"
echo " whether the compiler forced BlogPostController to say so.)"
