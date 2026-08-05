package com.example.partitionswap.maintenance.swap;

import com.example.partitionswap.common.PartitionWindow;
import com.example.partitionswap.common.Partitions;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The Postgres catalog is the coordination channel between the two services:
 * the ingest service creates staging tables, and this service discovers them
 * by scanning {@code pg_class} for tables matching the naming contract that
 * have no row in {@code pg_inherits} (i.e. are not yet attached to the
 * parent). No queue, no shared state, no chatter between the services — the
 * database already knows exactly which tables exist and which are attached.
 */
@Component
public class PartitionCatalog {

    private final JdbcClient jdbcClient;

    public PartitionCatalog(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /** False until the ingest service's Flyway migration has run. */
    public boolean parentTableExists() {
        return jdbcClient.sql("SELECT to_regclass(?) IS NOT NULL")
                .param(Partitions.PARENT_TABLE)
                .query(Boolean.class)
                .single();
    }

    /** Staging tables that match the naming contract and are not attached to any parent. */
    public List<PartitionWindow> detachedStagingTables() {
        return jdbcClient.sql("""
                        SELECT c.relname
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = current_schema()
                          AND c.relkind = 'r'
                          AND c.relname LIKE ?
                          AND NOT EXISTS (SELECT 1 FROM pg_inherits i WHERE i.inhrelid = c.oid)
                        ORDER BY c.relname
                        """)
                .param(Partitions.TABLE_PREFIX + "%")
                .query(String.class)
                .list()
                .stream()
                .map(Partitions::parseTableName)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Partitions currently attached to the parent, oldest first. */
    public List<AttachedPartition> attachedPartitions() {
        return jdbcClient.sql("""
                        SELECT c.relname,
                               pg_get_expr(c.relpartbound, c.oid) AS bounds,
                               c.reltuples::bigint                AS approx_rows,
                               pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size
                        FROM pg_inherits i
                        JOIN pg_class c ON c.oid = i.inhrelid
                        WHERE i.inhparent = ?::regclass
                        ORDER BY c.relname
                        """)
                .param(Partitions.PARENT_TABLE)
                .query((rs, rowNum) -> new AttachedPartition(
                        rs.getString("relname"),
                        rs.getString("bounds"),
                        rs.getLong("approx_rows"),
                        rs.getString("total_size")))
                .list();
    }

    public record AttachedPartition(String tableName, String bounds, long approxRows, String totalSize) {
    }
}
