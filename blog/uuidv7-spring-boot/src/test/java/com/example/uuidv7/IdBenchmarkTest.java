package com.example.uuidv7;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Reproducible benchmark comparing primary key strategies on PostgreSQL 18:
 * bigint identity, UUIDv4 (random), and UUIDv7 (time-ordered).
 *
 * Measures batched insert throughput, primary key index size, and B-tree leaf
 * fragmentation. Plain JDBC is used deliberately so no ORM behavior muddies
 * the numbers.
 *
 * Run with: ./gradlew test --tests IdBenchmarkTest
 */
@Testcontainers
class IdBenchmarkTest {

    static final int ROWS = 1_000_000;
    static final int BATCH_SIZE = 1_000;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    static final TimeBasedEpochGenerator UUID7 = Generators.timeBasedEpochGenerator();

    @BeforeAll
    static void createTables() throws SQLException {
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE users_bigint (
                    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    email VARCHAR(255) NOT NULL
                )""");
            st.execute("""
                CREATE TABLE users_uuid4 (
                    id UUID PRIMARY KEY,
                    email VARCHAR(255) NOT NULL
                )""");
            st.execute("""
                CREATE TABLE users_uuid7 (
                    id UUID PRIMARY KEY,
                    email VARCHAR(255) NOT NULL
                )""");
            st.execute("CREATE EXTENSION IF NOT EXISTS pgstattuple");
        }
    }

    @Test
    void benchmark() throws SQLException {
        try (Connection conn = connect()) {
            conn.setAutoCommit(false);

            long bigintMs = timedBigintInsert(conn);
            long uuid4Ms = timedUuidInsert(conn, "users_uuid4", UUID::randomUUID);
            long uuid7Ms = timedUuidInsert(conn, "users_uuid7", UUID7::generate);

            System.out.printf("%n=== Insert throughput (%,d rows, batch %,d) ===%n", ROWS, BATCH_SIZE);
            System.out.printf("bigint : %,d ms%n", bigintMs);
            System.out.printf("uuidv4 : %,d ms%n", uuid4Ms);
            System.out.printf("uuidv7 : %,d ms%n", uuid7Ms);

            System.out.printf("%n=== Primary key index stats ===%n");
            for (String table : new String[]{"users_bigint", "users_uuid4", "users_uuid7"}) {
                printIndexStats(conn, table);
            }
        }
    }

    private long timedBigintInsert(Connection conn) throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO users_bigint (email) VALUES (?)")) {
            for (int i = 1; i <= ROWS; i++) {
                ps.setString(1, "user" + i + "@example.com");
                ps.addBatch();
                if (i % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private long timedUuidInsert(Connection conn, String table, Supplier<UUID> ids)
            throws SQLException {
        long start = System.nanoTime();
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO " + table + " (id, email) VALUES (?, ?)")) {
            for (int i = 1; i <= ROWS; i++) {
                ps.setObject(1, ids.get());
                ps.setString(2, "user" + i + "@example.com");
                ps.addBatch();
                if (i % BATCH_SIZE == 0) {
                    ps.executeBatch();
                    conn.commit();
                }
            }
            ps.executeBatch();
            conn.commit();
        }
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void printIndexStats(Connection conn, String table) throws SQLException {
        String pkey = table + "_pkey";
        try (PreparedStatement ps = conn.prepareStatement("""
                SELECT pg_size_pretty(pg_relation_size(?::regclass)) AS index_size,
                       (SELECT leaf_fragmentation FROM pgstatindex(?)) AS leaf_frag
                """)) {
            ps.setString(1, pkey);
            ps.setString(2, pkey);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                System.out.printf("%-18s index=%-10s leaf_fragmentation=%.2f%%%n",
                        pkey, rs.getString("index_size"), rs.getDouble("leaf_frag"));
            }
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }
}
