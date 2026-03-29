# Auto Routing Research

TODO:

- [ ] Postgis docker container
- [ ] Set of scripts that connect into postgres/postgis
- [ ] Grafana instance to render visualization
- [ ] Script with fake results to change weights
- [ ] Add constraints into the mix

## Demonstration

### Polygon as GeoJson

    {
        "coordinates": [
          [
            [
              -75.68902456859064,
              40.0340143735292
            ],
            [
              -75.68902456859064,
              39.927747431953776
            ],
            [
              -75.53255688544492,
              39.927747431953776
            ],
            [
              -75.53255688544492,
              40.0340143735292
            ],
            [
              -75.68902456859064,
              40.0340143735292
            ]
          ]
        ],
        "type": "Polygon"
    }

### Convert to Geometry in Postgis (Remove ST_AsText to visualize)

    SELECT ST_AsText(ST_GeomFromGeoJSON('{"coordinates": [[[-75.68902456859064,40.0340143735292],[-75.68902456859064,39.927747431953776],[-75.53255688544492,39.927747431953776],[-75.53255688544492,40.0340143735292],[-75.68902456859064,40.0340143735292]]],"type": "Polygon"}')) As wkt;

### Generate a grid of arbitrary size

    WITH sample as 
    (
        WITH area as
        (
            SELECT ST_Transform(
                ST_GeomFromGeoJSON('{"coordinates": [[[-75.68902456859064,40.0340143735292],[-75.68902456859064,39.927747431953776],[-75.53255688544492,39.927747431953776],[-75.53255688544492,40.0340143735292],[-75.68902456859064,40.0340143735292]]],"type": "Polygon"}'),
                4326
            ) as geometry
        )
        SELECT (ST_SquareGrid(.001, geometry)).geom
        FROM
            area
    )
    SELECT ST_ExteriorRing(geom)
    FROM sample

### Print out the list of points making each polygon

     WITH points as
    (
      WITH sample as 
        (
          WITH area as
          (
            SELECT ST_Transform(
              ST_GeomFromGeoJSON('{"coordinates": [[[-75.68902456859064,40.0340143735292],[-75.68902456859064,39.927747431953776],[-75.53255688544492,39.927747431953776],[-75.53255688544492,40.0340143735292],[-75.68902456859064,40.0340143735292]]],"type": "Polygon"}'),
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
    SELECT *
    FROM
      points

### Format of pgRouting database

    edges_sql:	an SQL query, which should return a set of rows with the following columns:
    Column	Type	Default	Description
    id	ANY-INTEGER	 	Identifier of the edge.
    source	ANY-INTEGER	 	Identifier of the first end point vertex of the edge.
    target	ANY-INTEGER	 	Identifier of the second end point vertex of the edge.
    cost	ANY-NUMERICAL	 	
    Weight of the edge (source, target)

    When negative: edge (source, target) does not exist, therefore it’s not part of the graph.
    reverse_cost	ANY-NUMERICAL	-1	
    Weight of the edge (target, source),

    When negative: edge (target, source) does not exist, therefore it’s not part of the graph.
    x1	ANY-NUMERICAL	 	X coordinate of source vertex.
    y1	ANY-NUMERICAL	 	Y coordinate of source vertex.
    x2	ANY-NUMERICAL	 	X coordinate of target vertex.
    y2	ANY-NUMERICAL	 	Y coordinate of target vertex.

### Support UUID

    CREATE EXTENSION IF NOT EXISTS "uuid-ossp";


### Table Creation Script

  CREATE TABLE If NOt EXISTS calculation_table (
      id            UUID NOT NULL DEFAULT uuid_generate_v4() PRIMARY KEY,
      source        UUID NOT NULL DEFAULT uuid_generate_v4(),
      target        UUID NOT NULL DEFAULT uuid_generate_v4(),
      cost          int NOT NULL,
      reverse_cost  int NOT NULL,
      x1            int NOT NULL,
      y1            int NOT NULL,
      x2            int NOT NULL,
      y2            int NOT NULL,
      UNIQUE (x1, y1, x2, y2)
    );

### Run Script

    psql -h localhost -U postgres -d postgres -a -f script.sql