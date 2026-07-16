-- ============================================================================
-- 04_overlap.sql — 3D conflict detection between tessellated airspaces
--
-- Three detection levels, deliberately separated because they answer
-- different questions at wildly different costs:
--
--   find_conflicts_intersects()          ~fast, but over-reports
--       ST_3DIntersects on the solids after an indexed &&& 3D-bbox prefilter.
--       "Intersects" includes mere touching: ALPHA's ceiling and BRAVO's
--       floor share exact faces at 100 m AGL, so every stacked cell pair is
--       reported even though no airspace volume is shared.
--
--   find_conflicts_csg(max_pairs)        correct, brutally expensive
--       Confirms candidates by computing ST_Volume(ST_3DIntersection(...)),
--       a full CSG boolean between two solids in SFCGAL's exact arithmetic.
--       Face contact has zero volume, so touching is correctly ignored — at
--       roughly 80 ms *per cell pair*. This is the method you'd need for
--       arbitrary solids; max_pairs bounds the damage so it can be sampled
--       and benchmarked without running for 20 minutes.
--
--   find_conflicts_prism()               correct AND fast, the punchline
--       Exploits what tessellation gave us: every cell is a vertical prism,
--       so two cells share volume if and only if their 2D cells overlap with
--       positive area AND their z-intervals overlap with positive length.
--       The shared volume is exactly overlap_area * z_overlap. 2D GEOS math
--       plus arithmetic — no CSG anywhere.
-- ============================================================================

-- Recreate from scratch so signature changes don't strand old overloads.
DROP VIEW IF EXISTS conflict_summary;
DROP FUNCTION IF EXISTS find_conflicts_intersects();
DROP FUNCTION IF EXISTS find_conflicts_csg(integer);
DROP FUNCTION IF EXISTS find_conflicts_prism();
DROP FUNCTION IF EXISTS find_conflicts_prism(text, text);

CREATE FUNCTION find_conflicts_intersects()
RETURNS TABLE (
    airspace_a text, airspace_b text,
    cell_a bigint,  cell_b bigint
)
LANGUAGE sql STABLE
AS $$
    SELECT ca.airspace_id, cb.airspace_id, ca.cell_id, cb.cell_id
    FROM airspace_cells ca
    JOIN airspace_cells cb
      ON ca.airspace_id < cb.airspace_id
     AND ca.solid &&& cb.solid
     AND ST_3DIntersects(ca.solid, cb.solid)
$$;

CREATE OR REPLACE FUNCTION find_conflicts_csg(max_pairs integer DEFAULT 200)
RETURNS TABLE (
    airspace_a text, airspace_b text,
    cell_a bigint,  cell_b bigint,
    volume_m3 double precision
)
LANGUAGE sql STABLE
AS $$
    SELECT * FROM (
        SELECT ca.airspace_id, cb.airspace_id, ca.cell_id, cb.cell_id,
               ST_Volume(ST_3DIntersection(ca.solid, cb.solid)) AS volume_m3
        FROM airspace_cells ca
        JOIN airspace_cells cb
          ON ca.airspace_id < cb.airspace_id
         AND ca.solid &&& cb.solid
         AND ST_3DIntersects(ca.solid, cb.solid)
        LIMIT max_pairs
    ) candidates
    WHERE volume_m3 > 0.001
$$;

-- By default every pairwise combination of airspaces is checked. Pass two
-- airspace ids to restrict detection to that single pair, e.g.
--   SELECT * FROM find_conflicts_prism('BRAVO', 'CHARLIE');
CREATE OR REPLACE FUNCTION find_conflicts_prism(
    only_a text DEFAULT NULL,
    only_b text DEFAULT NULL
)
RETURNS TABLE (
    airspace_a text, airspace_b text,
    cell_a bigint,  cell_b bigint,
    volume_m3 double precision
)
LANGUAGE sql STABLE
AS $$
    SELECT * FROM (
        SELECT ca.airspace_id, cb.airspace_id, ca.cell_id, cb.cell_id,
               ST_Area(ST_Intersection(ca.cell_utm, cb.cell_utm))
                 * (least(ca.top_z, cb.top_z) - greatest(ca.bottom_z, cb.bottom_z))
                 AS volume_m3
        FROM airspace_cells ca
        JOIN airspace_cells cb
          ON ca.airspace_id < cb.airspace_id
         AND (only_a IS NULL OR ca.airspace_id = least(only_a, only_b))
         AND (only_b IS NULL OR cb.airspace_id = greatest(only_a, only_b))
         AND ca.solid &&& cb.solid                                    -- indexed 3D bbox
         AND least(ca.top_z, cb.top_z) > greatest(ca.bottom_z, cb.bottom_z)
         AND ST_Relate(ca.cell_utm, cb.cell_utm, '2********')         -- interiors share area
    ) shared
    WHERE volume_m3 > 0.001
$$;

-- Headline result: which airspace pairs actually conflict, and by how much.
CREATE OR REPLACE VIEW conflict_summary AS
SELECT airspace_a, airspace_b,
       count(*)                          AS conflicting_cell_pairs,
       round(sum(volume_m3)::numeric, 1) AS shared_volume_m3
FROM find_conflicts_prism()
GROUP BY airspace_a, airspace_b
ORDER BY airspace_a, airspace_b;
