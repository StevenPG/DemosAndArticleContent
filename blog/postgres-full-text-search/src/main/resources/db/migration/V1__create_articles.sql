CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE articles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title TEXT NOT NULL,
    body TEXT NOT NULL,
    -- Generated tsvector column: title weighted A, body weighted B, kept in
    -- sync by Postgres itself. No triggers, no application code.
    search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('english', title), 'A') ||
        setweight(to_tsvector('english', body), 'B')
    ) STORED
);

-- Full-text search index
CREATE INDEX idx_articles_search ON articles USING GIN (search_vector);

-- Trigram index for fuzzy/substring matching
CREATE INDEX idx_articles_title_trgm ON articles USING GIN (title gin_trgm_ops);
