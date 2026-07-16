package orders

import (
	"context"
	"errors"
	"log/slog"

	"github.com/google/uuid"
)

// The service depends on small interfaces declared *here*, on the consumer
// side (Go convention, inverted from Spring where the implementation usually
// defines the contract). Anything satisfying them can be injected — including
// test fakes, with no mocking framework.
type PaymentCharger interface {
	Charge(ctx context.Context, orderID uuid.UUID, amountCents int64) (paymentID string, err error)
}

type InventoryReserver interface {
	Reserve(ctx context.Context, orderID uuid.UUID, item string, quantity int) error
}

type EventPublisher interface {
	PublishOrderCreated(ctx context.Context, orderID uuid.UUID) error
}

type Service struct {
	repo      *Repository
	payment   PaymentCharger
	inventory InventoryReserver
	events    EventPublisher
	log       *slog.Logger
}

func NewService(repo *Repository, payment PaymentCharger, inventory InventoryReserver,
	events EventPublisher, log *slog.Logger) *Service {
	return &Service{repo: repo, payment: payment, inventory: inventory, events: events, log: log}
}

func (s *Service) Create(ctx context.Context, req CreateOrderRequest) (Order, error) {
	order := NewOrder(req.CustomerEmail, req.Item, req.Quantity, req.TotalCents)
	if err := s.repo.Insert(ctx, order); err != nil {
		return Order{}, err
	}
	if err := s.events.PublishOrderCreated(ctx, order.ID); err != nil {
		// Same trade-off as the Spring app: the order row exists even if the
		// event fails. Log it; a real system would use an outbox.
		s.log.Error("failed to publish order-created event", "orderId", order.ID, "err", err)
	}
	s.log.Info("created order", "orderId", order.ID, "customer", req.CustomerEmail)
	return order, nil
}

func (s *Service) Get(ctx context.Context, id uuid.UUID) (Order, error) {
	return s.repo.GetByID(ctx, id)
}

func (s *Service) List(ctx context.Context, status *Status) ([]Order, error) {
	return s.repo.List(ctx, status)
}

// Process is invoked by the Kafka consumer. Events for orders created by the
// Spring app share the topic and are skipped here, since they don't exist in
// this service's database.
func (s *Service) Process(ctx context.Context, orderID uuid.UUID) error {
	claimed, err := s.repo.ClaimForProcessing(ctx, orderID)
	if err != nil {
		return err
	}
	if !claimed {
		s.log.Debug("skipping event for unknown or already-processed order", "orderId", orderID)
		return nil
	}

	order, err := s.repo.GetByID(ctx, orderID)
	if err != nil {
		if errors.Is(err, ErrNotFound) {
			return nil
		}
		return err
	}

	paymentID, err := s.payment.Charge(ctx, order.ID, order.TotalCents)
	if err != nil {
		s.log.Warn("order failed at payment", "orderId", order.ID, "err", err)
		return s.repo.MarkFailed(ctx, order.ID, err.Error())
	}
	if err := s.inventory.Reserve(ctx, order.ID, order.Item, order.Quantity); err != nil {
		s.log.Warn("order failed at inventory", "orderId", order.ID, "err", err)
		return s.repo.MarkFailed(ctx, order.ID, err.Error())
	}

	s.log.Info("order completed", "orderId", order.ID, "paymentId", paymentID)
	return s.repo.MarkCompleted(ctx, order.ID, paymentID)
}
