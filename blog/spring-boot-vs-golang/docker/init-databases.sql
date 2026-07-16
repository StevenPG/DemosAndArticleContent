-- The two services keep separate databases in one Postgres instance so their
-- schemas can't collide. This runs once on first container start.
CREATE DATABASE orders_spring;
CREATE DATABASE orders_go;
