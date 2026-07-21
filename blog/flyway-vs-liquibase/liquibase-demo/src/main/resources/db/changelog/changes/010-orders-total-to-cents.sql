--liquibase formatted sql

--changeset steve:010
DROP VIEW customer_order_summary;
ALTER TABLE orders ADD COLUMN total_cents BIGINT;
UPDATE orders SET total_cents = ROUND(total * 100);
ALTER TABLE orders ALTER COLUMN total_cents SET NOT NULL;
ALTER TABLE orders DROP COLUMN total;
CREATE VIEW customer_order_summary AS
SELECT c.id AS customer_id,
       c.first_name,
       c.last_name,
       COUNT(o.id)                     AS order_count,
       COALESCE(SUM(o.total_cents), 0) AS lifetime_total_cents
FROM customers c
         LEFT JOIN orders o ON o.customer_id = c.id
GROUP BY c.id, c.first_name, c.last_name;
--rollback DROP VIEW customer_order_summary; ALTER TABLE orders ADD COLUMN total NUMERIC(10,2); UPDATE orders SET total = total_cents / 100.0; ALTER TABLE orders DROP COLUMN total_cents; CREATE VIEW customer_order_summary AS SELECT c.id AS customer_id, c.first_name, c.last_name, COUNT(o.id) AS order_count, COALESCE(SUM(o.total), 0) AS lifetime_total FROM customers c LEFT JOIN orders o ON o.customer_id = c.id GROUP BY c.id, c.first_name, c.last_name;
