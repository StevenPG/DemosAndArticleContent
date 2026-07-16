-- ============================================================================
-- 03_tessellate.sql — turn 2D footprints + AGL/MSL bounds into 3D solids
--
-- build_airspace_cells(cell_size_m) rebuilds the airspace_cells table:
--
--   1. ST_SquareGrid lays a global-origin-aligned grid (in UTM meters) over
--      each footprint. Because the grid is anchored to the SRS origin, two
--      airspaces with the same footprint get byte-identical cells — which is
--      what lets stacked volumes share exact faces.
--   2. Each cell is clipped to the footprint and assigned a ground elevation:
--      the average of all terrain samples inside it (fallback: nearest sample
--      to its centroid, for edge slivers thinner than the DEM spacing).
--   3. Vertical bounds become ellipsoidal z values. AGL bounds ride on the
--      per-cell ground elevation; MSL bounds are fixed, converted through the
--      geoid offset, clamped so volumes never extend underground.
--   4. ST_Extrude sweeps the flat cell up into a closed polyhedral surface,
--      and ST_MakeSolid marks it as a true SOLID so SFCGAL's 3D predicates
--      treat it as a filled volume rather than an empty shell.
-- ============================================================================

CREATE OR REPLACE PROCEDURE build_airspace_cells(cell_size_m double precision)
LANGUAGE plpgsql
AS $$
BEGIN
    TRUNCATE airspace_cells;

    INSERT INTO airspace_cells
        (airspace_id, cell_utm, cell_ll, ground_elev, bottom_z, top_z, solid)
    WITH utm AS (
        SELECT id, altitude_ref, lower_m, upper_m,
               ST_Transform(footprint, 32616) AS fp
        FROM airspaces
    ),
    grid AS (
        SELECT u.id AS airspace_id, u.altitude_ref, u.lower_m, u.upper_m,
               ST_Intersection(g.geom, u.fp) AS cell
        FROM utm u
        CROSS JOIN LATERAL ST_SquareGrid(cell_size_m, u.fp) AS g
        WHERE ST_Intersects(g.geom, u.fp)
    ),
    cells AS (
        SELECT airspace_id, altitude_ref, lower_m, upper_m,
               d.geom AS cell
        FROM grid,
             LATERAL ST_Dump(ST_CollectionExtract(cell, 3)) AS d
        WHERE ST_Area(d.geom) > 0.01
    ),
    with_ground AS (
        SELECT c.*,
               COALESCE(
                   (SELECT avg(t.elev_ellip)
                      FROM terrain_points t
                     WHERE ST_Intersects(t.geom_utm, c.cell)),
                   (SELECT t.elev_ellip
                      FROM terrain_points t
                     ORDER BY t.geom_utm <-> ST_Centroid(c.cell)
                     LIMIT 1)
               ) AS ground_elev
        FROM cells c
    ),
    with_bounds AS (
        SELECT *,
               CASE altitude_ref
                   WHEN 'AGL' THEN ground_elev + lower_m
                   -- MSL floors are fixed, but never below the ground itself
                   ELSE greatest(lower_m + geoid_offset_m(), ground_elev)
               END AS bottom_z,
               CASE altitude_ref
                   WHEN 'AGL' THEN ground_elev + upper_m
                   ELSE upper_m + geoid_offset_m()
               END AS top_z
        FROM with_ground
    )
    SELECT airspace_id,
           cell                       AS cell_utm,
           ST_Transform(cell, 4326)   AS cell_ll,
           ground_elev, bottom_z, top_z,
           ST_MakeSolid(
               ST_Extrude(ST_Force3D(cell, bottom_z), 0, 0, top_z - bottom_z)
           ) AS solid
    FROM with_bounds
    WHERE top_z > bottom_z;   -- drop MSL cells entirely below ground

    ANALYZE airspace_cells;
END;
$$;
