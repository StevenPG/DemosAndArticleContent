#!/usr/bin/env bash
# bench.sh – Run pgbench against the minimal Postgres container.
#
# Usage:
#   ./scripts/bench.sh [scale] [clients] [duration_seconds]
#
# Defaults: scale=10 (~1.4 million rows), clients=5, duration=30s
#
# Requirements: pgbench must be installed on the host.
#   Ubuntu/Debian: sudo apt install postgresql-client
#   macOS:         brew install libpq && brew link --force libpq

set -euo pipefail

SCALE="${1:-10}"
CLIENTS="${2:-5}"
DURATION="${3:-30}"

DB_HOST="${PGHOST:-localhost}"
DB_PORT="${PGPORT:-5432}"
DB_USER="${POSTGRES_USER:-appuser}"
DB_PASS="${POSTGRES_PASSWORD:-changeme}"
DB_NAME="${POSTGRES_DB:-appdb}"

export PGPASSWORD="$DB_PASS"

echo "================================================================"
echo " PostgreSQL minimal-memory benchmark"
echo " Target: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo " Scale: ${SCALE}  |  Clients: ${CLIENTS}  |  Duration: ${DURATION}s"
echo "================================================================"
echo ""

# ── 1. Initialise pgbench schema ─────────────────────────────────────────────
echo "[1/3] Initialising pgbench tables (scale=${SCALE})…"
pgbench \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --initialize \
  --scale="$SCALE"
echo ""

# ── 2. Read-only benchmark (SELECT-heavy) ────────────────────────────────────
echo "[2/3] Read-only benchmark (${CLIENTS} clients, ${DURATION}s)…"
pgbench \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --select-only \
  --clients="$CLIENTS" \
  --jobs="$CLIENTS" \
  --time="$DURATION" \
  --report-per-command \
  --progress=5
echo ""

# ── 3. Read-write benchmark (TPC-B-like) ─────────────────────────────────────
echo "[3/3] Read-write benchmark (${CLIENTS} clients, ${DURATION}s)…"
pgbench \
  -h "$DB_HOST" \
  -p "$DB_PORT" \
  -U "$DB_USER" \
  -d "$DB_NAME" \
  --clients="$CLIENTS" \
  --jobs="$CLIENTS" \
  --time="$DURATION" \
  --report-per-command \
  --progress=5
echo ""

echo "Done."
