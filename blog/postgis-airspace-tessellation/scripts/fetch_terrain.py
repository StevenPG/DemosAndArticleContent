"""
Download real elevation data for the Stone Mountain area and load it into
PostGIS as point samples.

Source: AWS Open Data "Terrain Tiles" (the former Mapzen tileset) in the
terrarium PNG encoding — public, no API key required:

    https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png
    elevation_m = (R * 256 + G + B / 256) - 32768        (orthometric / MSL)

Heights are converted to WGS84 *ellipsoidal* meters on load (the convention
CesiumJS renders in) by applying the local EGM96 geoid undulation. The value
must match geoid_offset_m() in sql/01_schema.sql.
"""
import io
import math
import sys

import requests
from PIL import Image

from db import connect

# Area of interest: Stone Mountain, GA, with margin around every footprint.
LON_MIN, LON_MAX = -84.170, -84.118
LAT_MIN, LAT_MAX = 33.788, 33.822

ZOOM = 14                # ~8 m ground resolution at this latitude
GEOID_OFFSET_M = -30.7   # EGM96 undulation near 33.8N, -84.15W
TILE_URL = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/{z}/{x}/{y}.png"


def tile_xy(lon: float, lat: float, z: int) -> tuple[int, int]:
    n = 2 ** z
    x = int((lon + 180.0) / 360.0 * n)
    lat_r = math.radians(lat)
    y = int((1.0 - math.asinh(math.tan(lat_r)) / math.pi) / 2.0 * n)
    return x, y


def pixel_lonlat(x: int, y: int, px: int, py: int, z: int) -> tuple[float, float]:
    """Lon/lat of a pixel center within tile (x, y)."""
    n = 2 ** z
    lon = (x + (px + 0.5) / 256.0) / n * 360.0 - 180.0
    merc_y = (y + (py + 0.5) / 256.0) / n
    lat = math.degrees(math.atan(math.sinh(math.pi * (1.0 - 2.0 * merc_y))))
    return lon, lat


def fetch_tile(x: int, y: int, z: int) -> Image.Image:
    resp = requests.get(TILE_URL.format(z=z, x=x, y=y), timeout=30)
    resp.raise_for_status()
    return Image.open(io.BytesIO(resp.content)).convert("RGB")


def main() -> None:
    x_min, y_min = tile_xy(LON_MIN, LAT_MAX, ZOOM)   # note: y grows southward
    x_max, y_max = tile_xy(LON_MAX, LAT_MIN, ZOOM)
    tiles = [(x, y) for x in range(x_min, x_max + 1) for y in range(y_min, y_max + 1)]
    print(f"Fetching {len(tiles)} terrain tiles at z{ZOOM} ...")

    samples: list[tuple[float, float, float]] = []
    for i, (x, y) in enumerate(tiles, 1):
        img = fetch_tile(x, y, ZOOM)
        px_data = img.load()
        for py in range(256):
            for px in range(256):
                lon, lat = pixel_lonlat(x, y, px, py, ZOOM)
                if not (LON_MIN <= lon <= LON_MAX and LAT_MIN <= lat <= LAT_MAX):
                    continue
                r, g, b = px_data[px, py]
                elev_msl = (r * 256 + g + b / 256.0) - 32768.0
                samples.append((lon, lat, elev_msl + GEOID_OFFSET_M))
        print(f"  tile {i}/{len(tiles)} ({x},{y}) -> {len(samples)} samples so far")

    if not samples:
        sys.exit("No samples decoded — check the AOI/zoom settings.")

    elevs = [s[2] for s in samples]
    print(f"Loading {len(samples)} samples into PostGIS "
          f"(ellipsoidal range {min(elevs):.0f}..{max(elevs):.0f} m)")

    with connect() as conn, conn.cursor() as cur:
        cur.execute("TRUNCATE terrain_points")
        with cur.copy("COPY terrain_points (lon, lat, elev_ellip) FROM STDIN") as copy:
            for row in samples:
                copy.write_row(row)
        cur.execute("""
            UPDATE terrain_points
               SET geom_utm = ST_Transform(ST_SetSRID(ST_MakePoint(lon, lat), 4326), 32616)
        """)
        cur.execute("ANALYZE terrain_points")
    print("Done.")


if __name__ == "__main__":
    main()
