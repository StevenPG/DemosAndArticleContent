// Package messaging is the Kafka layer: a producer the service publishes
// through, and a consumer loop that drives order processing. Compare with
// KafkaTemplate + @KafkaListener — here the poll loop is our code.
package messaging

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/google/uuid"
	"github.com/segmentio/kafka-go"
)

// OrderEvent matches the JSON shape produced by the Spring app, so both
// services can consume each other's events off the shared topic.
type OrderEvent struct {
	OrderID    uuid.UUID `json:"orderId"`
	Type       string    `json:"type"`
	Source     string    `json:"source"`
	OccurredAt time.Time `json:"occurredAt"`
}

const EventOrderCreated = "ORDER_CREATED"

const source = "go-order-service"

// Producer wraps a kafka-go Writer. The order id is the message key so all
// events for one order land on the same partition, preserving their order.
type Producer struct {
	writer *kafka.Writer
	log    *slog.Logger
}

func NewProducer(brokers []string, topic string, log *slog.Logger) *Producer {
	return &Producer{
		writer: &kafka.Writer{
			Addr:                   kafka.TCP(brokers...),
			Topic:                  topic,
			Balancer:               &kafka.Hash{},
			RequiredAcks:           kafka.RequireAll,
			AllowAutoTopicCreation: true,
		},
		log: log,
	}
}

func (p *Producer) PublishOrderCreated(ctx context.Context, orderID uuid.UUID) error {
	event := OrderEvent{
		OrderID:    orderID,
		Type:       EventOrderCreated,
		Source:     source,
		OccurredAt: time.Now().UTC(),
	}
	value, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal event: %w", err)
	}
	return p.writer.WriteMessages(ctx, kafka.Message{
		Key:   []byte(orderID.String()),
		Value: value,
	})
}

func (p *Producer) Close() error {
	return p.writer.Close()
}

// Processor is what the consumer calls for each event — satisfied by
// orders.Service.
type Processor interface {
	Process(ctx context.Context, orderID uuid.UUID) error
}

// Consumer owns the poll loop that @KafkaListener generates for you in
// Spring. Run blocks until the context is cancelled; offsets are committed
// automatically by the consumer-group Reader after each ReadMessage.
type Consumer struct {
	reader    *kafka.Reader
	processor Processor
	log       *slog.Logger
}

func NewConsumer(brokers []string, topic, groupID string, processor Processor, log *slog.Logger) *Consumer {
	return &Consumer{
		reader: kafka.NewReader(kafka.ReaderConfig{
			Brokers:     brokers,
			Topic:       topic,
			GroupID:     groupID,
			StartOffset: kafka.FirstOffset,
		}),
		processor: processor,
		log:       log,
	}
}

func (c *Consumer) Run(ctx context.Context) {
	c.log.Info("kafka consumer started")
	for {
		msg, err := c.reader.ReadMessage(ctx)
		if err != nil {
			if errors.Is(err, context.Canceled) || errors.Is(err, kafka.ErrGroupClosed) {
				c.log.Info("kafka consumer stopped")
				return
			}
			c.log.Error("kafka read failed", "err", err)
			continue
		}

		var event OrderEvent
		if err := json.Unmarshal(msg.Value, &event); err != nil {
			// A poison message: log and move on rather than retry forever.
			c.log.Error("skipping malformed event", "offset", msg.Offset, "err", err)
			continue
		}

		c.log.Info("received event", "type", event.Type, "orderId", event.OrderID, "source", event.Source)
		if event.Type == EventOrderCreated {
			if err := c.processor.Process(ctx, event.OrderID); err != nil {
				c.log.Error("failed to process order", "orderId", event.OrderID, "err", err)
			}
		}
	}
}

func (c *Consumer) Close() error {
	return c.reader.Close()
}
