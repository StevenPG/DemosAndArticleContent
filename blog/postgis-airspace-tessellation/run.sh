#!/usr/bin/env bash
# End-to-end pipeline: schema -> airspaces -> terrain -> tessellate/export.
# Assumes PostGIS is reachable (docker compose up -d, or any local instance)
# with the connection defaults below (override via PG* env vars).
set -euo pipefail
cd "$(dirname "$0")"

export PGHOST="${PGHOST:-localhost}" PGPORT="${PGPORT:-5432}"
export PGDATABASE="${PGDATABASE:-airspace}" PGUSER="${PGUSER:-airspace}"
export PGPASSWORD="${PGPASSWORD:-airspace}"

echo "==> Applying SQL"
for f in sql/01_schema.sql sql/02_airspaces.sql sql/03_tessellate.sql sql/04_overlap.sql; do
  psql -v ON_ERROR_STOP=1 -q -f "$f"
done

echo "==> Fetching terrain (AWS Terrain Tiles)"
python3 scripts/fetch_terrain.py

echo "==> Tessellating + exporting viewer data (50 m cells)"
python3 scripts/export_cells.py 50

echo "==> Done. Serve the viewer with:"
echo "    cd viewer && python3 -m http.server 8000"
echo "    then open http://localhost:8000"
echo "Optional: python3 scripts/benchmark.py"
