--liquibase formatted sql

--changeset steve:004
ALTER TABLE orders ADD COLUMN status TEXT NOT NULL DEFAULT 'NEW';
--rollback ALTER TABLE orders DROP COLUMN status;
