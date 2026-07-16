package orders

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

// ErrNotFound is the repository's "Optional.empty()": callers decide whether
// it becomes a 404, a skipped Kafka event, or something else.
var ErrNotFound = errors.New("order not found")

// Repository is hand-written SQL over a pgx connection pool — the equivalent
// of the entire Spring Data JPA + Hibernate layer. Every query is visible,
// which is the trade the Go ecosystem generally prefers.
type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

const orderColumns = `id, customer_email, item, quantity, total_cents, status,
	payment_id, failure_reason, created_at, updated_at`

func scanOrder(row pgx.Row) (Order, error) {
	var o Order
	err := row.Scan(&o.ID, &o.CustomerEmail, &o.Item, &o.Quantity, &o.TotalCents,
		&o.Status, &o.PaymentID, &o.FailureReason, &o.CreatedAt, &o.UpdatedAt)
	return o, err
}

func (r *Repository) Insert(ctx context.Context, o Order) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO orders (`+orderColumns+`)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
		o.ID, o.CustomerEmail, o.Item, o.Quantity, o.TotalCents, o.Status,
		o.PaymentID, o.FailureReason, o.CreatedAt, o.UpdatedAt)
	if err != nil {
		return fmt.Errorf("insert order: %w", err)
	}
	return nil
}

func (r *Repository) GetByID(ctx context.Context, id uuid.UUID) (Order, error) {
	o, err := scanOrder(r.pool.QueryRow(ctx,
		`SELECT `+orderColumns+` FROM orders WHERE id = $1`, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return Order{}, ErrNotFound
	}
	if err != nil {
		return Order{}, fmt.Errorf("get order: %w", err)
	}
	return o, nil
}

// List returns all orders, optionally filtered by status (newest first when
// filtered, matching the Spring derived query findByStatusOrderByCreatedAtDesc).
func (r *Repository) List(ctx context.Context, status *Status) ([]Order, error) {
	query := `SELECT ` + orderColumns + ` FROM orders`
	args := []any{}
	if status != nil {
		query += ` WHERE status = $1 ORDER BY created_at DESC`
		args = append(args, *status)
	}

	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("list orders: %w", err)
	}
	defer rows.Close()

	result := []Order{}
	for rows.Next() {
		o, err := scanOrder(rows)
		if err != nil {
			return nil, fmt.Errorf("scan order: %w", err)
		}
		result = append(result, o)
	}
	return result, rows.Err()
}

// ClaimForProcessing atomically flips PENDING -> PROCESSING. The conditional
// UPDATE doubles as an idempotency guard for duplicate Kafka deliveries; it
// returns false when the order doesn't exist or was already claimed.
func (r *Repository) ClaimForProcessing(ctx context.Context, id uuid.UUID) (bool, error) {
	tag, err := r.pool.Exec(ctx, `
		UPDATE orders SET status = $1, updated_at = $2
		WHERE id = $3 AND status = $4`,
		StatusProcessing, time.Now().UTC(), id, StatusPending)
	if err != nil {
		return false, fmt.Errorf("claim order: %w", err)
	}
	return tag.RowsAffected() == 1, nil
}

func (r *Repository) MarkCompleted(ctx context.Context, id uuid.UUID, paymentID string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE orders SET status = $1, payment_id = $2, updated_at = $3 WHERE id = $4`,
		StatusCompleted, paymentID, time.Now().UTC(), id)
	if err != nil {
		return fmt.Errorf("mark completed: %w", err)
	}
	return nil
}

func (r *Repository) MarkFailed(ctx context.Context, id uuid.UUID, reason string) error {
	_, err := r.pool.Exec(ctx, `
		UPDATE orders SET status = $1, failure_reason = $2, updated_at = $3 WHERE id = $4`,
		StatusFailed, reason, time.Now().UTC(), id)
	if err != nil {
		return fmt.Errorf("mark failed: %w", err)
	}
	return nil
}

// CountUnfinished backs the scheduled reporter and the orders_pending gauge.
func (r *Repository) CountUnfinished(ctx context.Context) (int64, error) {
	var count int64
	err := r.pool.QueryRow(ctx,
		`SELECT COUNT(*) FROM orders WHERE status IN ($1, $2)`,
		StatusPending, StatusProcessing).Scan(&count)
	if err != nil {
		return 0, fmt.Errorf("count unfinished: %w", err)
	}
	return count, nil
}
