package com.example.batchguide.listener;

import com.example.batchguide.domain.Order;
import com.example.batchguide.domain.OrderRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Spring Batch {@link SkipListener} that records skipped order items to the
 * {@code skipped_orders} audit table.
 *
 * <p>This listener is called by the framework whenever a skip event occurs during
 * read, process, or write phases of the {@code importStep}.  The three callback
 * methods correspond to the three phases.
 *
 * <p>For this pipeline the most important callback is {@link #onSkipInProcess} —
 * fired when the {@link com.example.batchguide.processor.OrderItemProcessor} throws
 * a {@link com.example.batchguide.exception.ValidationException}.  The original
 * {@link OrderRecord} is available so we can record which order was skipped and why.
 *
 * <p>The listener uses {@link JdbcTemplate} directly (rather than a JPA repository)
 * to avoid entangling the skip callback with JPA session lifecycle management.
 * Skip callbacks execute <em>outside</em> the chunk transaction, so using raw JDBC
 * ensures the skip record is committed independently of the chunk retry/rollback cycle.
 */
@Component
public class OrderSkipListener implements SkipListener<OrderRecord, Order> {

    private static final Logger log = LoggerFactory.getLogger(OrderSkipListener.class);

    private static final String INSERT_SQL =
            "INSERT INTO skipped_orders (order_id, reason, skipped_at) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Constructs the listener with the {@link JdbcTemplate} used to persist skip
     * records.
     *
     * @param jdbcTemplate autowired JDBC template bound to the primary datasource
     */
    public OrderSkipListener(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Called when a read error is skipped (e.g. a malformed CSV line that cannot be
     * parsed).  Logs a warning; no item is available to persist because the read
     * failed before an {@link OrderRecord} could be constructed.
     *
     * @param t the exception that caused the skip during the read phase
     */
    @Override
    public void onSkipInRead(Throwable t) {
        log.warn("[SKIP READ ] Could not parse item: {}", t.getMessage());
    }

    /**
     * Called when the processor throws a skippable exception (in this pipeline,
     * {@link com.example.batchguide.exception.ValidationException}).
     *
     * <p>Inserts a row into {@code skipped_orders} recording:
     * <ul>
     *   <li>The order id from the raw {@link OrderRecord}</li>
     *   <li>The exception message as the skip reason</li>
     *   <li>The current timestamp</li>
     * </ul>
     *
     * @param item the {@link OrderRecord} that failed validation; never {@code null}
     * @param t    the exception thrown by the processor; never {@code null}
     */
    @Override
    public void onSkipInProcess(OrderRecord item, Throwable t) {
        log.warn("[SKIP PROC ] Skipping order id={} reason='{}'",
                item.id(), t.getMessage());

        // Insert outside the chunk transaction — if the chunk rolls back, the skip
        // record is still preserved for audit purposes.
        jdbcTemplate.update(INSERT_SQL, item.id(), t.getMessage(), LocalDateTime.now());
    }

    /**
     * Called when a write error is skipped (e.g. a constraint violation that is
     * declared skippable).  Logs a warning with the failed item's id.
     *
     * @param item the {@link Order} entity that could not be written
     * @param t    the exception thrown during the write phase
     */
    @Override
    public void onSkipInWrite(Order item, Throwable t) {
        log.warn("[SKIP WRITE] Could not write order id={} reason='{}'",
                item.getId(), t.getMessage());
    }
}
