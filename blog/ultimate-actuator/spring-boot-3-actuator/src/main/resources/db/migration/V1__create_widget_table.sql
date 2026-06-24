-- Flyway migration. The applied migrations are reported by the
-- /actuator/flyway endpoint.
CREATE TABLE widget (
    id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name  VARCHAR(255) NOT NULL,
    color VARCHAR(255) NOT NULL
);
