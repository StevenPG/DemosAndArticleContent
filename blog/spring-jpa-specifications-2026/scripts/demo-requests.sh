#!/usr/bin/env bash
#
# Walks the search endpoint through progressively narrower filters, so you can watch a single
# Specification-backed query absorb one more condition at a time.
#
# Start the app first:
#   docker compose up -d
#   ./gradlew bootRun
#
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"

show() {
  local label="$1"
  local url="$2"
  echo
  echo "=============================================================="
  echo "$label"
  echo "GET $url"
  echo "--------------------------------------------------------------"
  curl -sS "$url" | jq '{matched: .page.totalElements, first: (.content[0] // null)}'
}

show "No filters at all — 120 flights" \
  "$BASE/api/flights/search?size=1"

show "One filter: departing Atlanta" \
  "$BASE/api/flights/search?origin=ATL&size=1"

show "Two: Atlanta, under \$300" \
  "$BASE/api/flights/search?origin=ATL&maxPrice=300&size=1"

show "Three: ... on a SkyTeam or Star Alliance carrier" \
  "$BASE/api/flights/search?origin=ATL&maxPrice=300&airlineCodes=DL&airlineCodes=UA&size=1"

show "Four: ... that actually has wifi AND seat power" \
  "$BASE/api/flights/search?origin=ATL&maxPrice=300&airlineCodes=DL&airlineCodes=UA&requiredAmenities=WIFI&requiredAmenities=POWER&size=1"

# POWER rather than WIFI on purpose. SampleData adds WIFI when i % 3 != 0 and the BUSINESS
# cabin when i % 3 == 0, so no flight in the dataset has both and this panel would always
# report zero matches. POWER lands on every BUSINESS flight, so the narrowing stays visible.
show "Five: ... passenger flights with a business cabin and seats left" \
  "$BASE/api/flights/search?origin=ATL&maxPrice=300&requiredAmenities=POWER&cabinClass=BUSINESS&minSeatsAvailable=10&size=1"

# No origin filter here. SampleData sets wifiVendor from i % 4 and origin from i % 8, so the
# vendor is constant within any one airport - "origin=ATL&wifiVendor=Starlink" matches all 15
# ATL flights and "origin=ORD&wifiVendor=Starlink" matches none, which makes the jsonb
# predicate look like it does nothing. Across the whole dataset it selects 30 of 120.
show "Reaching into the jsonb column: Starlink wifi only" \
  "$BASE/api/flights/search?wifiVendor=Starlink&size=1"

show "Morning departures, via PostgreSQL's date_part()" \
  "$BASE/api/flights/search?earliestDepartureHourUtc=6&latestDepartureHourUtc=11&size=1"

show "Flights with at least one Platinum booking (EXISTS subquery)" \
  "$BASE/api/flights/search?bookedByTier=PLATINUM&size=1"

echo
echo "=============================================================="
echo "DTO projection — narrowed SELECT list, no entities hydrated"
echo "GET $BASE/api/flights/summaries?origin=ATL&size=3"
echo "--------------------------------------------------------------"
curl -sS "$BASE/api/flights/summaries?origin=ATL&size=3" | jq '.content'

echo
echo "=============================================================="
echo "Keyset scrolling — page one, then the cursor for page two"
echo "--------------------------------------------------------------"
FIRST=$(curl -sS "$BASE/api/flights/scroll?origin=ATL&size=4")
echo "$FIRST" | jq '.content'

LAST_DEPARTURE=$(echo "$FIRST" | jq -r '.content[-1].departureTime')
LAST_ID=$(echo "$FIRST" | jq -r '.content[-1].id')

echo
echo "GET $BASE/api/flights/scroll?origin=ATL&size=4&afterDeparture=$LAST_DEPARTURE&afterId=$LAST_ID"
echo "--------------------------------------------------------------"
curl -sS "$BASE/api/flights/scroll?origin=ATL&size=4&afterDeparture=$LAST_DEPARTURE&afterId=$LAST_ID" | jq '.content'

echo
echo "Done. Watch the application log for the SQL behind each of these."
