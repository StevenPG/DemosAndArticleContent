"""
Tessellate the airspaces at the chosen resolution, run the prism-exact
conflict detection, and write viewer/data/cells.json for the Cesium page.

Usage: python scripts/export_cells.py [cell_size_m]   (default 50)
"""
import json
import pathlib
import sys
from datetime import datetime, timezone

from db import connect

OUT = pathlib.Path(__file__).resolve().parent.parent / "viewer" / "data" / "cells.json"


def main() -> None:
    cell_size = float(sys.argv[1]) if len(sys.argv) > 1 else 50.0

    with connect() as conn, conn.cursor() as cur:
        print(f"Tessellating at {cell_size:g} m ...")
        cur.execute("CALL build_airspace_cells(%s)", (cell_size,))
        conn.commit()

        cur.execute("""
            SELECT id, name, altitude_ref, lower_m, upper_m, color
            FROM airspaces ORDER BY id
        """)
        airspaces = {
            row[0]: {
                "id": row[0], "name": row[1], "ref": row[2],
                "lower": row[3], "upper": row[4], "color": row[5],
                "cells": [],
            }
            for row in cur.fetchall()
        }

        print("Running prism-exact conflict detection ...")
        cur.execute("SELECT airspace_a, airspace_b, cell_a, cell_b, volume_m3 FROM find_conflicts_prism()")
        conflict_partners: dict[int, set[str]] = {}
        pair_stats: dict[tuple[str, str], dict] = {}
        for a_id, b_id, cell_a, cell_b, vol in cur.fetchall():
            conflict_partners.setdefault(cell_a, set()).add(b_id)
            conflict_partners.setdefault(cell_b, set()).add(a_id)
            stats = pair_stats.setdefault((a_id, b_id), {"cellPairs": 0, "volumeM3": 0.0})
            stats["cellPairs"] += 1
            stats["volumeM3"] += vol

        cur.execute("""
            SELECT cell_id, airspace_id, ST_AsGeoJSON(cell_ll, 7),
                   ground_elev, bottom_z, top_z
            FROM airspace_cells ORDER BY cell_id
        """)
        n_cells = 0
        for cell_id, airspace_id, gj, ground, bottom, top in cur.fetchall():
            ring = json.loads(gj)["coordinates"][0]
            airspaces[airspace_id]["cells"].append({
                "ring": [[round(lon, 7), round(lat, 7)] for lon, lat in ring],
                "ground": round(ground, 2),
                "bottom": round(bottom, 2),
                "top": round(top, 2),
                "conflicts": sorted(conflict_partners.get(cell_id, ())),
            })
            n_cells += 1

    payload = {
        "generated": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "cellSizeM": cell_size,
        "heightsAre": "WGS84 ellipsoidal meters",
        "airspaces": list(airspaces.values()),
        "conflicts": [
            {"pair": list(pair), "cellPairs": s["cellPairs"], "volumeM3": round(s["volumeM3"], 1)}
            for pair, s in sorted(pair_stats.items())
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, separators=(",", ":")))
    print(f"Wrote {n_cells} cells, {len(pair_stats)} conflicting pairs -> {OUT}")
    for c in payload["conflicts"]:
        print(f"  {c['pair'][0]} x {c['pair'][1]}: {c['cellPairs']} cell pairs, {c['volumeM3']:,} m³ shared")


if __name__ == "__main__":
    main()
