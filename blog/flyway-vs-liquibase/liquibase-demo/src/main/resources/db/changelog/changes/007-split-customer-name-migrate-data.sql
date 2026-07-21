--liquibase formatted sql

--changeset steve:007
UPDATE customers
SET first_name = split_part(name, ' ', 1),
    last_name  = NULLIF(substring(name FROM position(' ' IN name) + 1), name);
--rollback UPDATE customers SET first_name = NULL, last_name = NULL;
