--liquibase formatted sql

--changeset steve:006
ALTER TABLE customers ADD COLUMN first_name TEXT;
ALTER TABLE customers ADD COLUMN last_name TEXT;
--rollback ALTER TABLE customers DROP COLUMN first_name; ALTER TABLE customers DROP COLUMN last_name;
