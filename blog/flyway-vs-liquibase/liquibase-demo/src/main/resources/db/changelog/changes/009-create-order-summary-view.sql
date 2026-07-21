--liquibase formatted sql

--changeset steve:009
CREATE VIEW customer_order_summary AS
SELECT c.id AS customer_id,
       c.first_name,
       c.last_name,
       COUNT(o.id)               AS order_count,
       COALESCE(SUM(o.total), 0) AS lifetime_total
FROM customers c
         LEFT JOIN orders o ON o.customer_id = c.id
GROUP BY c.id, c.first_name, c.last_name;
--rollback DROP VIEW customer_order_summary;
