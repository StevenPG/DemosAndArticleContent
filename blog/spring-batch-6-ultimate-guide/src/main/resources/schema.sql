-- Reference DDL for the application tables.
-- In production this is applied via Flyway or Liquibase.
-- For local dev, spring.jpa.hibernate.ddl-auto=update will create the tables.

CREATE TABLE IF NOT EXISTS orders (
    id            VARCHAR(50) PRIMARY KEY,
    customer_id   VARCHAR(50),
    customer_name VARCHAR(100),
    product_code  VARCHAR(50),
    amount        NUMERIC(12, 2),
    order_date    VARCHAR(20),
    created_at    TIMESTAMP
);

CREATE TABLE IF NOT EXISTS skipped_orders (
    id         BIGSERIAL PRIMARY KEY,
    order_id   VARCHAR(50),
    reason     TEXT,
    skipped_at TIMESTAMP
);
