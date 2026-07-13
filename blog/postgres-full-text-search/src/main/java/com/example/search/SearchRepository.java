package com.example.search;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * The three search strategies from the article, side by side. JdbcClient keeps
 * the SQL visible — the SQL *is* the content here.
 */
@Repository
public class SearchRepository {

    private final JdbcClient jdbc;

    public SearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Baseline: ILIKE substring scan. No index can help the leading wildcard. */
    public List<SearchResult> ilike(String query) {
        return jdbc.sql("""
                        SELECT id, title, 0.0 AS score
                        FROM articles
                        WHERE title ILIKE '%' || :q || '%'
                           OR body ILIKE '%' || :q || '%'
                        LIMIT 20
                        """)
                .param("q", query)
                .query((rs, i) -> new SearchResult(
                        rs.getLong("id"), rs.getString("title"), rs.getDouble("score")))
                .list();
    }

    /** Full-text search: websearch syntax, GIN index, ts_rank ordering. */
    public List<SearchResult> fullText(String query) {
        return jdbc.sql("""
                        SELECT id, title,
                               ts_rank(search_vector, websearch_to_tsquery('english', :q)) AS score
                        FROM articles
                        WHERE search_vector @@ websearch_to_tsquery('english', :q)
                        ORDER BY score DESC
                        LIMIT 20
                        """)
                .param("q", query)
                .query((rs, i) -> new SearchResult(
                        rs.getLong("id"), rs.getString("title"), rs.getDouble("score")))
                .list();
    }

    /** Fuzzy: trigram similarity on the title, survives typos. */
    public List<SearchResult> fuzzy(String query) {
        return jdbc.sql("""
                        SELECT id, title, similarity(title, :q) AS score
                        FROM articles
                        WHERE title % :q
                        ORDER BY score DESC
                        LIMIT 20
                        """)
                .param("q", query)
                .query((rs, i) -> new SearchResult(
                        rs.getLong("id"), rs.getString("title"), rs.getDouble("score")))
                .list();
    }
}
