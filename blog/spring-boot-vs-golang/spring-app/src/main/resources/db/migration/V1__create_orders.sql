-- Flyway migration V1: the orders table. Flyway runs automatically at startup
-- (flyway-core + flyway-database-postgresql on the classpath) and records
-- applied versions in flyway_schema_history.
CREATE TABLE orders (
    id             UUID PRIMARY KEY,
    customer_email VARCHAR(320) NOT NULL,
    item           VARCHAR(255) NOT NULL,
    quantity       INTEGER      NOT NULL CHECK (quantity > 0),
    total_cents    BIGINT       NOT NULL CHECK (total_cents > 0),
    status         VARCHAR(20)  NOT NULL,
    payment_id     VARCHAR(64),
    failure_reason VARCHAR(255),
    created_at     TIMESTAMPTZ  NOT NULL,
    updated_at     TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_orders_status ON orders (status);
