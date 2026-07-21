--liquibase formatted sql

--changeset steve:005
--comment Data migration: everything older than 30 days is considered archived
UPDATE orders SET status = 'ARCHIVED' WHERE created_at < NOW() - INTERVAL '30 days';
--rollback UPDATE orders SET status = 'NEW' WHERE status = 'ARCHIVED';
