CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";
CREATE EXTENSION IF NOT EXISTS "pgrouting";

CREATE TABLE IF NOT EXISTS calculation_table (
  	id              SERIAL PRIMARY KEY,
    source        integer NULL,
    target        integer NULL,
    cost          integer NOT NULL,
    reverse_cost  integer NOT NULL,
	the_geom geometry(LineString,27700) NOT NULL,
    x1            float NULL,
    y1            float NULL,
    x2            float NULL,
    y2            float NULL,
    UNIQUE (x1, y1, x2, y2)
);

 WITH points as
 (
	 WITH sample as 
		(
			WITH area as
			(
				SELECT ST_Transform(
					ST_GeomFromGeoJSON('{"coordinates": [[[-75.62733363468617,39.978575910519226],[-75.62733363468617,39.947659199734915],[-75.58142614907263,39.947659199734915],[-75.58142614907263,39.978575910519226],[-75.62733363468617,39.978575910519226]]],"type": "Polygon"}'),
					4326
				) as geometry
			)
			SELECT (ST_SquareGrid(.001, geometry)).geom
			FROM
				area
		)
	-- 	SELECT ST_AsText(ST_ExteriorRing(geom))
		SELECT  ST_AsText(ST_PointN(ST_ExteriorRing(geom), 1)) as point_one,
				ST_AsText(ST_PointN(ST_ExteriorRing(geom), 2)) as point_two,
				ST_AsText(ST_PointN(ST_ExteriorRing(geom), 3)) as point_three,
				ST_AsText(ST_PointN(ST_ExteriorRing(geom), 4)) as point_four
		FROM sample
)
INSERT INTO calculation_table (
    cost, reverse_cost, the_geom
) SELECT 1, 1, ST_MakeLine(points.point_one, points.point_two) FROM points
	UNION ALL
	SELECT 1, 1, ST_MakeLine(points.point_two, points.point_three) FROM points
	UNION ALL
	SELECT 1, 1, ST_MakeLine(points.point_three, points.point_four) FROM points
	UNION ALL
	SELECT 1, 1, ST_MakeLine(points.point_four, points.point_one) FROM points
ON CONFLICT DO NOTHING;

SELECT pgr_createTopology('calculation_table', 0.001, 'the_geom', 'id');

select pgr_dijkstra('SELECT * FROM calculation_table', 1, 2);
SELECT seq, id1 AS node, id2 AS edge, cost
        FROM pgr_astar(
                'SELECT * FROM calculation_table',
                1, 10000, false, false
        );DROP TABLE calculation_table;
DROP TABLE calculation_table_vertices_pgrl