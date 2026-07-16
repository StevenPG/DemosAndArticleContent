-- ============================================================================
-- 01_schema.sql — extensions, tables, and constants
--
-- Everything vertical in this schema is stored in ELLIPSOIDAL meters (WGS84),
-- because that is the convention CesiumJS renders in. Terrain tiles arrive in
-- orthometric ("MSL") meters and are converted on load using a fixed geoid
-- offset for the Stone Mountain area (see geoid_offset_m below).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS postgis;
CREATE EXTENSION IF NOT EXISTS postgis_sfcgal;

-- EGM96 geoid undulation near Stone Mountain, GA (33.8N, -84.15W).
-- ellipsoidal_height = orthometric_height + geoid_offset_m()
-- Over an area this small (~5 km) the undulation is effectively constant.
CREATE OR REPLACE FUNCTION geoid_offset_m() RETURNS double precision
LANGUAGE sql IMMUTABLE AS $$ SELECT -30.7 $$;

-- ----------------------------------------------------------------------------
-- Airspace definitions: how a real system stores them.
-- A 2D footprint plus vertical bounds and an altitude reference.
--   AGL: lower/upper are meters above ground level
--   MSL: lower/upper are orthometric meters above mean sea level
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS airspaces (
    id           text PRIMARY KEY,
    name         text NOT NULL,
    altitude_ref text NOT NULL CHECK (altitude_ref IN ('AGL', 'MSL')),
    lower_m      double precision NOT NULL,
    upper_m      double precision NOT NULL,
    color        text NOT NULL,             -- viewer hint
    footprint    geometry(Polygon, 4326) NOT NULL,
    CHECK (upper_m > lower_m)
);

-- ----------------------------------------------------------------------------
-- Terrain samples decoded from AWS Terrain Tiles (terrarium encoding).
-- One row per DEM pixel inside the area of interest (~8 m spacing at z14).
-- geom_utm is the working geometry: all tessellation happens in EPSG:32616
-- (UTM zone 16N) so distances and volumes are in honest meters.
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS terrain_points (
    lon        double precision NOT NULL,
    lat        double precision NOT NULL,
    elev_ellip double precision NOT NULL,   -- ellipsoidal meters
    geom_utm   geometry(Point, 32616)
);

CREATE INDEX IF NOT EXISTS terrain_points_geom_utm_idx
    ON terrain_points USING gist (geom_utm);

-- ----------------------------------------------------------------------------
-- Tessellated airspace cells: one terrain-following 3D prism per grid cell.
-- Populated by build_airspace_cells() (sql/03_tessellate.sql).
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS airspace_cells (
    cell_id     bigserial PRIMARY KEY,
    airspace_id text NOT NULL REFERENCES airspaces (id),
    cell_utm    geometry(Polygon, 32616) NOT NULL,  -- 2D cell, working CRS
    cell_ll     geometry(Polygon, 4326)  NOT NULL,  -- 2D cell, for export
    ground_elev double precision NOT NULL,          -- ellipsoidal m
    bottom_z    double precision NOT NULL,          -- ellipsoidal m
    top_z       double precision NOT NULL,          -- ellipsoidal m
    solid       geometry NOT NULL                   -- SFCGAL SOLID, EPSG:32616
);

-- n-dimensional GiST index: this is what makes the &&& 3D bounding-box
-- prefilter in the overlap queries an index scan instead of an O(n^2) join
CREATE INDEX IF NOT EXISTS airspace_cells_solid_nd_idx
    ON airspace_cells USING gist (solid gist_geometry_ops_nd);
