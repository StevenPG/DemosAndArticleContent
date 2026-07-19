// Package jobs holds background work. Spring's @Scheduled is a goroutine and
// a time.Ticker here — scheduling, lifecycle, and shutdown are all visible.
package jobs

import (
	"context"
	"log/slog"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promauto"

	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/orders"
)

var pendingOrders = promauto.NewGauge(prometheus.GaugeOpts{
	Name: "orders_pending",
	Help: "Orders not yet completed or failed",
})

// PendingOrdersReporter periodically counts unfinished orders, logs the
// number, and exposes it as a Prometheus gauge on /metrics.
type PendingOrdersReporter struct {
	repo     *orders.Repository
	interval time.Duration
	log      *slog.Logger
}

func NewPendingOrdersReporter(repo *orders.Repository, interval time.Duration, log *slog.Logger) *PendingOrdersReporter {
	return &PendingOrdersReporter{repo: repo, interval: interval, log: log}
}

// Run blocks until the context is cancelled; start it with `go reporter.Run(ctx)`.
func (r *PendingOrdersReporter) Run(ctx context.Context) {
	ticker := time.NewTicker(r.interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			count, err := r.repo.CountUnfinished(ctx)
			if err != nil {
				r.log.Error("failed to count pending orders", "err", err)
				continue
			}
			pendingOrders.Set(float64(count))
			r.log.Info("pending/processing orders", "count", count)
		}
	}
}
