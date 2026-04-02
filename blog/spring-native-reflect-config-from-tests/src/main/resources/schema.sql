CREATE TABLE IF NOT EXISTS plugin_registrations (
    id      SERIAL PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    class_name VARCHAR(255) NOT NULL
);
