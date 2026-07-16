// Package db owns the connection pool and schema migrations — the Go
// equivalent of Spring's auto-configured DataSource plus Flyway.
package db

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"strings"

	"github.com/golang-migrate/migrate/v4"
	_ "github.com/golang-migrate/migrate/v4/database/pgx/v5" // registers the pgx5:// driver
	"github.com/golang-migrate/migrate/v4/source/iofs"
	"github.com/jackc/pgx/v5/pgxpool"
)

// Connect opens a pgx connection pool and verifies it with a ping.
func Connect(ctx context.Context, databaseURL string) (*pgxpool.Pool, error) {
	pool, err := pgxpool.New(ctx, databaseURL)
	if err != nil {
		return nil, fmt.Errorf("create pool: %w", err)
	}
	if err := pool.Ping(ctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping database: %w", err)
	}
	return pool, nil
}

// Migrate applies versioned SQL migrations embedded in the binary at compile
// time (go:embed) — golang-migrate is the community's Flyway. It records
// applied versions in the schema_migrations table.
func Migrate(databaseURL string, migrations embed.FS) error {
	source, err := iofs.New(migrations, "migrations")
	if err != nil {
		return fmt.Errorf("load embedded migrations: %w", err)
	}

	// golang-migrate selects its database driver by URL scheme.
	url := strings.Replace(databaseURL, "postgres://", "pgx5://", 1)
	m, err := migrate.NewWithSourceInstance("iofs", source, url)
	if err != nil {
		return fmt.Errorf("init migrate: %w", err)
	}
	defer m.Close()

	if err := m.Up(); err != nil && !errors.Is(err, migrate.ErrNoChange) {
		return fmt.Errorf("apply migrations: %w", err)
	}
	return nil
}
