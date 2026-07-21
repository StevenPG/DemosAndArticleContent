--liquibase formatted sql

--changeset steve:008
ALTER TABLE customers ALTER COLUMN first_name SET NOT NULL;
ALTER TABLE customers DROP COLUMN name;
--rollback ALTER TABLE customers ADD COLUMN name TEXT; UPDATE customers SET name = first_name || ' ' || COALESCE(last_name, ''); ALTER TABLE customers ALTER COLUMN first_name DROP NOT NULL;
