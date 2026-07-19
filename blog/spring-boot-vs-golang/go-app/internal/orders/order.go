// Package orders holds the domain model, persistence, and business logic —
// roughly what lives in the Spring app's domain package, minus the framework.
package orders

import (
	"net/mail"
	"time"

	"github.com/google/uuid"
)

type Status string

const (
	StatusPending    Status = "PENDING"
	StatusProcessing Status = "PROCESSING"
	StatusCompleted  Status = "COMPLETED"
	StatusFailed     Status = "FAILED"
)

// Order is a plain struct. There is no ORM in this app: the repository maps
// it to and from SQL by hand, which is the mainstream Go approach.
type Order struct {
	ID            uuid.UUID `json:"id"`
	CustomerEmail string    `json:"customerEmail"`
	Item          string    `json:"item"`
	Quantity      int       `json:"quantity"`
	TotalCents    int64     `json:"totalCents"`
	Status        Status    `json:"status"`
	PaymentID     *string   `json:"paymentId"`
	FailureReason *string   `json:"failureReason"`
	CreatedAt     time.Time `json:"createdAt"`
	UpdatedAt     time.Time `json:"updatedAt"`
}

func NewOrder(customerEmail, item string, quantity int, totalCents int64) Order {
	now := time.Now().UTC()
	return Order{
		ID:            uuid.New(),
		CustomerEmail: customerEmail,
		Item:          item,
		Quantity:      quantity,
		TotalCents:    totalCents,
		Status:        StatusPending,
		CreatedAt:     now,
		UpdatedAt:     now,
	}
}

// CreateOrderRequest mirrors the Spring DTO. Where Spring declares
// constraints as annotations, Go validates imperatively: an explicit method
// returning field -> message, called by the handler.
type CreateOrderRequest struct {
	CustomerEmail string `json:"customerEmail"`
	Item          string `json:"item"`
	Quantity      int    `json:"quantity"`
	TotalCents    int64  `json:"totalCents"`
}

func (r CreateOrderRequest) Validate() map[string]string {
	errs := map[string]string{}
	if r.CustomerEmail == "" {
		errs["customerEmail"] = "must not be blank"
	} else if _, err := mail.ParseAddress(r.CustomerEmail); err != nil {
		errs["customerEmail"] = "must be a well-formed email address"
	}
	if r.Item == "" {
		errs["item"] = "must not be blank"
	}
	if r.Quantity < 1 || r.Quantity > 1000 {
		errs["quantity"] = "must be between 1 and 1000"
	}
	if r.TotalCents <= 0 {
		errs["totalCents"] = "must be greater than 0"
	}
	return errs
}
