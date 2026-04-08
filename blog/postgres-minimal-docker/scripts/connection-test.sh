#!/usr/bin/env bash
# connection-test.sh – Verify that Postgres is reachable and log basic info.
#
# Usage:
#   ./scripts/connection-test.sh

set -euo pipefail

DB_HOST="${PGHOST:-localhost}"
DB_PORT="${PGPORT:-5432}"
DB_USER="${POSTGRES_USER:-appuser}"
DB_PASS="${POSTGRES_PASSWORD:-changeme}"
DB_NAME="${POSTGRES_DB:-appdb}"

export PGPASSWORD="$DB_PASS"

echo "Connecting to ${DB_HOST}:${DB_PORT} as ${DB_USER}…"
echo ""

psql \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  -c "
    SELECT version();

    SELECT
      name,
      setting,
      unit,
      short_desc
    FROM pg_settings
    WHERE name IN (
      'shared_buffers',
      'work_mem',
      'maintenance_work_mem',
      'max_connections',
      'wal_buffers',
      'effective_cache_size',
      'max_worker_processes'
    )
    ORDER BY name;
  "

echo ""
echo "Connection test passed."
