package com.example.partitionswap.ingest.copy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The classification policy decides whether a failed batch is retried forever
 * or dead-lettered, so getting it wrong means either a permanently wedged
 * partition or silently discarded rows. Both directions are asserted here,
 * including the deliberate "unknown means transient" default.
 */
class CopyFailureClassificationTest {

    private static SQLException sqlException(String sqlState) {
        return new SQLException("boom", sqlState);
    }

    @ParameterizedTest(name = "SQLState {0} is poison")
    @ValueSource(strings = {
            "22001",  // string data right truncation
            "22007",  // invalid datetime format
            "22P02",  // invalid text representation
            "23502",  // not null violation
            "23505",  // unique violation
            "23514",  // check violation
            "42703",  // undefined column — encoder and table disagree after a bad deploy
            "42P01",  // undefined table
    })
    void dataAndSchemaErrorsAreDeadLettered(String sqlState) {
        assertThat(CopyBatchWriter.classify(sqlException(sqlState), "staging"))
                .isInstanceOf(PoisonBatchException.class)
                .hasMessageContaining("staging")
                .hasMessageContaining(sqlState)
                .cause().isInstanceOf(SQLException.class);
    }

    @ParameterizedTest(name = "SQLState {0} is transient")
    @ValueSource(strings = {
            "08000",  // connection exception
            "08006",  // connection failure
            "08003",  // connection does not exist
            "40001",  // serialization failure
            "40P01",  // deadlock detected
            "53100",  // disk full
            "53200",  // out of memory
            "53300",  // too many connections
            "57P01",  // admin shutdown
            "57P03",  // cannot connect now — Postgres is starting up
            "58030",  // io error
    })
    void infrastructureErrorsAreRetriedForever(String sqlState) {
        assertThat(CopyBatchWriter.classify(sqlException(sqlState), "staging"))
                .isInstanceOf(TransientIngestException.class);
    }

    @Test
    void unknownSqlStatesDefaultToTransient() {
        // The safe direction: a stalled partition is visible and self-healing,
        // whereas dead-lettering good rows on an unrecognised error is silent
        // data loss.
        assertThat(CopyBatchWriter.classify(sqlException("99999"), "staging"))
                .isInstanceOf(TransientIngestException.class);
    }

    @Test
    void nullAndMalformedSqlStatesDefaultToTransient() {
        assertThat(CopyBatchWriter.classify(new SQLException("no state"), "staging"))
                .isInstanceOf(TransientIngestException.class);
        assertThat(CopyBatchWriter.classify(sqlException("7"), "staging"))
                .isInstanceOf(TransientIngestException.class);
    }
}
