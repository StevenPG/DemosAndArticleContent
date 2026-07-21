--liquibase formatted sql

--changeset steve:003
CREATE INDEX idx_orders_customer ON orders (customer_id);
--rollback DROP INDEX idx_orders_customer;
