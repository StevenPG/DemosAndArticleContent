# How Terrain-Following Airspace Volumes Work in PostGIS

A complete technical explanation of this demo's approach: what's wrong with
the common centroid-sampled flat-volume model, how tessellation replaces it
entirely inside PostGIS, why the conflict math is exact and fast, and how to
migrate an existing system onto it. Every number in this document comes from
running this repo's pipeline over real Stone Mountain terrain.

---

## 1. The system this replaces

A very common way to handle AGL airspaces in a 2D-GIS-shaped system:

1. Store the airspace as a 2D polygon plus `lower_agl` / `upper_agl`.
2. When an altitude check is needed, sample the terrain **once at the
   polygon's center point**, and convert the whole airspace to a flat slab:
   `[center_ground + lower_agl, center_ground + upper_agl]`.
3. Check conflicts as: `footprints intersect in 2D` AND `flat altitude
   intervals overlap`.

This is cheap and it *feels* right, and over flat terrain it mostly is. Over
terrain relief, it is quietly wrong in every direction at once. Here is what
one terrain sample at the centroid does to this demo's airspaces (ground
elevations in ellipsoidal meters; the footprint of ALPHA/BRAVO covers the
Stone Mountain dome, CHARLIE's overlaps its eastern flank):

| airspace | defined as | centroid ground | true ground range | flat slab becomes |
|---|---|---:|---:|---:|
| ALPHA | 0–100 m AGL | 473.6 | 226.6 – 481.2 | [473.6, 573.6] |
| BRAVO | 100–200 m AGL | 473.6 | 226.6 – 481.2 | [573.6, 673.6] |
| CHARLIE | 50–150 m AGL | 235.5 | 223.9 – 476.2 | [285.5, 385.5] |

ALPHA/BRAVO's centroid happens to land near the summit; CHARLIE's happens to
land on the flats. Three distinct failure classes follow:

**False negatives on real conflicts.** Flat-BRAVO [573.6, 673.6] vs
flat-CHARLIE [285.5, 385.5]: the intervals don't come close — *no conflict
reported*. The truth from the terrain-following model: BRAVO and CHARLIE
share **25.7 million m³** of airspace across 241 cell pairs, because out on
the flats BRAVO's band is at ~[326, 426] — squarely inside CHARLIE's actual
band there. The centroid model silently approves an intruder flying through
a transit layer.

**Volumes that don't contain their own traffic.** ALPHA is *defined* as
"surface to 100 m AGL," but its flat slab floats at 473.6 m — which is
**247 m above the actual ground** at the footprint edges. A drone at 50 m AGL
over the flats — dead center of ALPHA's intended volume — is reported
*outside* it. The airspace fails at its only job.

**Volumes that extend underground.** Flat-CHARLIE's ceiling (385.5 m) is
~90 m *below the ground surface* on the dome's flank (476.2 m). Part of the
stored volume is inside the mountain; any altitude check in that region is
meaningless.

The failure size scales directly with terrain relief inside the footprint
(here ~250 m). It's also unstable: reshape a footprint slightly and its
centroid can move from a ridge to a valley, flipping conflict verdicts with
no change to the airspace's real-world meaning.

---

## 2. The replacement: tessellated terrain-following volumes

The idea, in one sentence: **split the footprint into small cells, resolve
AGL against the terrain per cell instead of once, and store each cell as a
true 3D solid**. The union of solids approximates the real, curved airspace
volume, and the approximation error is bounded by how much the terrain can
vary inside one cell.

Everything happens in PostGIS. The pipeline (this repo's `sql/` files):

### 2.1 Ingredients

- **PostGIS + SFCGAL** (`postgis_sfcgal` extension): SFCGAL supplies real 3D
  geometry — polyhedral surfaces, SOLIDs, `ST_3DIntersects`, `ST_Extrude`,
  `ST_MakeSolid`, `ST_Volume`.
- **A terrain model** queryable per point. Here: ~289k point samples decoded
  from free AWS Terrain Tiles into a `terrain_points` table with a spatial
  index. A raster (`postgis_raster`) or any DEM source works identically.
- **A projected CRS for the math** (here EPSG:32616, UTM 16N) so that
  distances, areas, and volumes are in meters, not degrees.
- **One height datum for everything.** This repo stores all z values in
  WGS84 *ellipsoidal* meters because CesiumJS renders in them. DEMs and MSL
  airspace definitions are orthometric, so they're converted on the way in
  with the local geoid undulation (−30.7 m near Atlanta). Pick either datum —
  just never mix them; a mix-up is a silent ~30 m vertical error, which is
  exactly the class of bug the flat-slab model has all the time.

### 2.2 Tessellation (`build_airspace_cells(cell_size_m)`)

```sql
-- 1. grid the footprint (origin-anchored, so identical footprints
--    produce byte-identical cells)
ST_SquareGrid(cell_size_m, ST_Transform(footprint, 32616))

-- 2. clip each square to the footprint (edge cells become partial polygons)
ST_Intersection(grid_cell, footprint_utm)

-- 3. per-cell ground elevation: average of DEM samples inside the cell
SELECT avg(t.elev_ellip) FROM terrain_points t
WHERE ST_Intersects(t.geom_utm, cell)

-- 4. vertical bounds
--    AGL:  bottom = ground + lower_agl,           top = ground + upper_agl
--    MSL:  bottom = greatest(lower_msl_converted, ground)   (never underground)
--          top    = upper_msl_converted

-- 5. sweep the flat cell into a closed 3D solid
ST_MakeSolid(ST_Extrude(ST_Force3D(cell, bottom_z), 0, 0, top_z - bottom_z))
```

Each row of the resulting `airspace_cells` table is a vertical prism: a flat
polygon at `bottom_z` extruded straight up to `top_z`. `ST_MakeSolid` tags
the closed polyhedral surface as a SOLID so SFCGAL's 3D predicates treat it
as filled volume, not an empty shell.

Two details that matter more than they look:

- **Grid anchoring.** `ST_SquareGrid` anchors cells to the SRS origin, not
  to the footprint. Two airspaces with the same footprint therefore get
  *identical* cell geometries, and stacked airspaces (ALPHA's ceiling =
  BRAVO's floor, everywhere) share exact faces. No floating-point slivers,
  no phantom hairline overlaps.
- **Same ground sample for stacked volumes.** Because the cells are
  identical, both layers get the same per-cell ground elevation, so
  `ALPHA.top_z = BRAVO.bottom_z` holds *exactly*, cell by cell, over any
  terrain.

The tessellation is a one-time, write-side cost: ~3.5 s for ~3,100 cells at
50 m resolution, ~7.5 s for ~11,900 cells at 25 m (all timings from
`benchmark_results.md`, local PostGIS 16 / SFCGAL 1.5.1). You re-run it only
when an airspace or the DEM changes — and only for that airspace.

### 2.3 The index

```sql
CREATE INDEX ON airspace_cells USING gist (solid gist_geometry_ops_nd);
```

The `_nd` operator class indexes **3D bounding boxes** (X, Y *and* Z), and
the `&&&` operator queries it. This is the difference between "compare every
cell against every cell" and "compare each cell against the handful whose 3D
boxes actually touch it." Every conflict query below starts with an indexed
`a.solid &&& b.solid`.

---

## 3. Conflict detection — and the efficient way

Given two tessellated airspaces, "do they conflict?" becomes "does any cell
of A share volume with any cell of B?" There are three ways to answer, with
wildly different cost/correctness trade-offs. All three are in
`sql/04_overlap.sql`.

### 3.1 `ST_3DIntersects` — fast but over-reports

```sql
SELECT ...
FROM airspace_cells ca
JOIN airspace_cells cb
  ON ca.airspace_id < cb.airspace_id
 AND ca.solid &&& cb.solid
 AND ST_3DIntersects(ca.solid, cb.solid);
```

Sub-second at every resolution tested. But "intersects" is true for mere
*touching* — and a properly designed airspace system is full of deliberate
touching: stacked layers share ceiling/floor faces, adjacent sectors share
walls. At 25 m resolution this query returns 48,620 "intersecting" pairs, of
which only 3,599 share any actual volume. Useful as a candidate filter;
wrong as a verdict.

### 3.2 CSG confirmation — correct but ~40 ms *per pair*

```sql
... AND ST_Volume(ST_3DIntersection(ca.solid, cb.solid)) > 0.001
```

`ST_3DIntersection` computes the true boolean intersection of two solids in
SFCGAL's exact arithmetic; its volume is zero for touching and positive for
real overlap. Correct for *arbitrary* solids — and measured at 37–48 ms per
candidate pair here, which extrapolates to **~39 minutes** for one sweep at
25 m resolution. That is the price of ignoring the structure of your own
data.

### 3.3 The prism shortcut — exact *and* sub-second (this is the takeaway)

Every tessellated cell is a **vertical prism**: constant 2D cross-section,
flat horizontal top and bottom. For two vertical prisms, 3D intersection
*decomposes*:

> prisms share volume  ⇔  their 2D footprints overlap with positive area
>                          AND their z-intervals overlap with positive length
>
> shared volume  =  overlap_area × z_overlap        (exactly)

```sql
SELECT ca.airspace_id, cb.airspace_id, ca.cell_id, cb.cell_id,
       ST_Area(ST_Intersection(ca.cell_utm, cb.cell_utm))
         * (least(ca.top_z, cb.top_z) - greatest(ca.bottom_z, cb.bottom_z))
         AS volume_m3
FROM airspace_cells ca
JOIN airspace_cells cb
  ON ca.airspace_id < cb.airspace_id
 AND ca.solid &&& cb.solid                                   -- indexed 3D prefilter
 AND least(ca.top_z, cb.top_z) > greatest(ca.bottom_z, cb.bottom_z)  -- strict z overlap
 AND ST_Relate(ca.cell_utm, cb.cell_utm, '2********')        -- interiors share 2D area
```

Note what each line buys:

- `&&&` — indexed 3D bounding-box scan; the join never goes quadratic.
- the `least/greatest` comparison — **strict** inequality, so touching
  (ceiling meets floor) is not a conflict. This one comparison is the entire
  solution to the stacked-airspace semantics problem.
- `ST_Relate(..., '2********')` — the DE-9IM pattern requiring the two 2D
  *interiors* to share area, so cells that only share an edge or corner in
  plan view don't count. Plain 2D `ST_Intersects` would over-report exactly
  the way 3D `ST_3DIntersects` does.
- the volume expression — plain arithmetic on values already in the table.
  No CSG anywhere on the hot path.

Cross-checked against the CSG method pair-by-pair, the volumes agree to the
third decimal (mm³ at 50 m cells) — it is not an approximation of 3.2, it is
the same answer computed the smart way. Cost at 25 m / 11,859 cells:
**0.87 s** for the full sweep, versus ~2,330 s estimated for CSG —
**~2,600× faster**, from one WHERE clause exploiting prism structure.

| cell size | cells | ST_3DIntersects pairs | prism sweep | true conflicts | CSG estimate |
|---:|---:|---:|---:|---:|---:|
| 100 m | 839 | 3,238 | 0.05 s | 278 | ~120 s |
| 50 m | 3,106 | 12,485 | 0.20 s | 974 | ~523 s |
| 25 m | 11,859 | 48,620 | 0.87 s | 3,599 | ~2,330 s |

Two more properties worth calling out:

- **The answer converges.** Total conflicting volume across resolutions:
  89.74M → 89.52M → 89.45M m³ (100 → 50 → 25 m). A 0.3% spread, and the
  yes/no verdicts per airspace pair were identical at all three resolutions.
  Resolution buys boundary precision, not different decisions — so you can
  run coarse tessellation for interactive checks and refine offline.
- **It's scoped naturally.** The demo's `find_conflicts_prism(a, b)` takes
  an optional airspace pair; the same shape works for "this one new/changed
  airspace against everything" — which is the query a live system actually
  runs, and it's an indexed fraction of the full sweep.

### 3.4 Why keep the solids at all, if the prism math wins?

Because the solids are the general-purpose representation: they render
(exported to CesiumJS), they answer point-in-airspace (`ST_3DIntersects`
against a point), they measure (`ST_Volume`), and they still work when
something *isn't* a prism — a climb corridor, a sloped approach surface, a
noise-abatement cone. The architecture is: keep solids as truth, use prism
math as the fast path for the (overwhelmingly common) prism-vs-prism case,
and fall back to CSG only for exotic pairs.

---

## 4. Migrating a centroid-based system

Concrete path from the model in §1 to the model in §2:

1. **Keep your definitions table.** `footprint + lower + upper +
   altitude_ref` is already the right source of truth — the centroid *slab*
   was the derived artifact, and it's the only thing being replaced.
2. **Stand up a terrain source** (`terrain_points` or a raster) covering
   your operating area, in one declared vertical datum. Record the geoid
   offset(s) used; make the conversion explicit in one function like this
   repo's `geoid_offset_m()`.
3. **Add `airspace_cells`** and the `build_airspace_cells` procedure;
   backfill by tessellating every existing airspace. Choose cell size from
   your terrain, not from habit: cells small enough that ground variation
   within one cell is below your vertical safety margin. (Flat plains
   tolerate 200 m cells; this demo's dome wants ≤50 m. The benchmark table
   is the tool for pricing that choice.)
4. **Rewrite the conflict check** as the §3.3 query — indexed `&&&`
   prefilter, strict interval overlap, `ST_Relate` interior test. Wire
   "airspace changed" events to re-tessellate that airspace and run the
   scoped variant against the rest.
5. **Regression-test against the old system** and expect disagreements —
   they are the point. Every disagreement is one of the §1 failure classes:
   a conflict the centroid model missed (flat-BRAVO × flat-CHARLIE here), a
   phantom conflict it invented, or a volume that didn't contain its own
   traffic. This repo's dataset is small enough to audit by hand and by eye
   (the Cesium viewer renders exactly what the database stores).
6. **Altitude checks become trivial and finally correct**: a position at
   (lon, lat, alt) is in an airspace iff it's in some cell's 2D footprint
   with `bottom_z ≤ alt ≤ top_z` — one indexed lookup, resolved against the
   terrain *under that position*, not under a centroid a kilometer away.

Ongoing costs, from the benchmark: re-tessellating one airspace is
sub-second to a few seconds; the scoped conflict check against thousands of
existing cells is milliseconds. Both fit comfortably inside a
write-transaction or an async validation step.

---

## 5. Limitations to keep in mind

- **Stair-stepping.** Per-cell flat floors approximate a smooth surface with
  steps of (terrain slope × cell size). That error is *bounded and
  chosen* — unlike the centroid model's error, which is unbounded and
  accidental. Halving cell size halves it, at ~4× cell count.
- **Geoid handling.** A constant offset is fine for ~10 km areas; beyond
  that, evaluate a geoid grid (PROJ + EGM2008) per point on terrain load.
- **DEM agreement.** Conflict math only needs *internal* consistency, but
  visual registration against a renderer's terrain (Cesium World Terrain,
  ESRI World Elevation) is only as good as the DEMs' agreement — expect
  a few meters of visual offset, and sample the renderer's own terrain if
  that matters.
- **Non-prism volumes** (sloped surfaces, cones) fall back to the CSG path;
  keep them rare or pre-tessellate them into prisms too.
