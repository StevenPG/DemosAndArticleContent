-- golang-migrate migration 0001: same schema as the Spring app's Flyway V1,
-- applied to this service's own database (orders_go).
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
