package com.example.partitionswap.repository;

import com.example.partitionswap.domain.ReadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Read-side repository over the indexed {@code events} table.
 *
 * <p>The native query filters on {@code occurred_at}, the partition key, so PostgreSQL
 * prunes every minute partition outside the window at plan time and answers the rest
 * from the {@code (event_type, occurred_at)} index built during the swap.
 */
public interface ReadEventRepository extends JpaRepository<ReadEvent, Long> {

    long countByOccurredAtGreaterThanEqual(Instant from);

    List<ReadEvent> findTop3ByOrderByOccurredAtDesc();

    @Query(value = """
            SELECT event_type AS "eventType", count(*) AS "count"
            FROM events
            WHERE occurred_at >= :from
            GROUP BY event_type
            ORDER BY count(*) DESC
            """, nativeQuery = true)
    List<EventTypeCount> countByTypeSince(@Param("from") Instant from);
}
