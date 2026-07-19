"""Shared connection settings for the demo scripts."""
import os

import psycopg


def connect() -> psycopg.Connection:
    return psycopg.connect(
        host=os.environ.get("PGHOST", "localhost"),
        port=int(os.environ.get("PGPORT", "5432")),
        dbname=os.environ.get("PGDATABASE", "airspace"),
        user=os.environ.get("PGUSER", "airspace"),
        password=os.environ.get("PGPASSWORD", "airspace"),
    )
