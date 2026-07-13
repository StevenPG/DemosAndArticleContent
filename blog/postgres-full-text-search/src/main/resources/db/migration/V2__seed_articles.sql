-- Seed 50,000 synthetic articles so latency comparisons mean something.
-- Titles and bodies are built from a rotating vocabulary so full-text and
-- trigram queries both have realistic hit rates.
INSERT INTO articles (title, body)
SELECT
    'Article ' || i || ': ' ||
    (ARRAY['Postgres performance tuning',
           'Spring Boot configuration deep dive',
           'Kubernetes networking explained',
           'Kafka partitioning strategies',
           'Elasticsearch cluster sizing',
           'Full text search relevance ranking',
           'Database indexing fundamentals',
           'Distributed caching patterns'])[1 + (i % 8)],
    'This is the body of article ' || i || '. ' ||
    repeat(
        (ARRAY['PostgreSQL query planning and index selection matter at scale. ',
               'Spring Boot applications expose search endpoints over JDBC. ',
               'Text search relevance depends on ranking and term frequency. ',
               'Trigram similarity catches typos that exact matching misses. '])[1 + (i % 4)],
        20)
FROM generate_series(1, 50000) AS i;

ANALYZE articles;
