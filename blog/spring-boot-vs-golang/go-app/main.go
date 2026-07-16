// The Go order service: a functional twin of ../spring-app built with the
// standard library plus focused dependencies. main() does explicitly what
// Spring Boot's auto-configuration does reflectively — construct every
// component, wire the graph, start the servers and loops, and shut them all
// down cleanly. See ../GOLANG_REFERENCE.md for the piece-by-piece mapping.
package main

import (
	"context"
	"embed"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/auth"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/clients"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/config"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/db"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/httpapi"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/jobs"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/messaging"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/orders"
)

//go:embed migrations/*.sql
var migrations embed.FS

func main() {
	log := slog.New(slog.NewTextHandler(os.Stdout, nil))
	if err := run(log); err != nil {
		log.Error("fatal", "err", err)
		os.Exit(1)
	}
}

func run(log *slog.Logger) error {
	// Cancelled on SIGINT/SIGTERM; everything long-running hangs off this
	// context, so cancellation is the shutdown signal for the whole app.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	cfg, err := config.Load()
	if err != nil {
		return err
	}

	// --- Database: pool + migrations (Spring: DataSource + Flyway) ---
	pool, err := db.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()

	if err := db.Migrate(cfg.DatabaseURL, migrations); err != nil {
		return err
	}
	log.Info("database ready, migrations applied")

	// --- Inbound auth (Spring: oauth2ResourceServer + issuer-uri) ---
	verifier, err := auth.NewVerifier(ctx, cfg.JWKSURL(), cfg.OAuthIssuer)
	if err != nil {
		return err
	}

	// --- Outbound OAuth2 clients (Spring: two client registrations) ---
	payment := clients.NewPaymentClient(ctx, cfg.PaymentBaseURL,
		cfg.Payment.ClientID, cfg.Payment.ClientSecret, cfg.TokenURL(), cfg.Payment.Scopes)
	inventory := clients.NewInventoryClient(ctx, cfg.InventoryBaseURL,
		cfg.Inventory.ClientID, cfg.Inventory.ClientSecret, cfg.TokenURL(), cfg.Inventory.Scopes)

	// --- Kafka + domain wiring (Spring: component scan does this) ---
	producer := messaging.NewProducer(cfg.KafkaBrokers, cfg.OrdersTopic, log)
	defer producer.Close()

	repo := orders.NewRepository(pool)
	service := orders.NewService(repo, payment, inventory, producer, log)

	consumer := messaging.NewConsumer(cfg.KafkaBrokers, cfg.OrdersTopic, cfg.ConsumerGroup, service, log)
	defer consumer.Close()
	go consumer.Run(ctx)

	// --- Scheduled job (Spring: @Scheduled) ---
	reporter := jobs.NewPendingOrdersReporter(repo, cfg.ReportInterval, log)
	go reporter.Run(ctx)

	// --- HTTP server (Spring: embedded Tomcat) ---
	server := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           httpapi.NewMux(service, verifier, pool, log),
		ReadHeaderTimeout: 5 * time.Second,
	}

	errCh := make(chan error, 1)
	go func() {
		log.Info("http server listening", "addr", server.Addr)
		if err := server.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
	}()

	select {
	case err := <-errCh:
		return fmt.Errorf("http server: %w", err)
	case <-ctx.Done():
	}

	// Graceful shutdown: stop accepting connections, give in-flight requests
	// ten seconds to finish. Deferred Closes handle Kafka and the pool.
	log.Info("shutting down")
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	return server.Shutdown(shutdownCtx)
}
