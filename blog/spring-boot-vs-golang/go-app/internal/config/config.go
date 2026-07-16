// Package config is the Go answer to application.yaml + @ConfigurationProperties:
// a plain struct populated from environment variables with sensible defaults,
// loaded once in main() and passed to whatever needs it.
package config

import (
	"fmt"
	"os"
	"strings"
	"time"
)

type OAuthClient struct {
	ClientID     string
	ClientSecret string
	Scopes       []string
}

type Config struct {
	Port        string
	DatabaseURL string

	KafkaBrokers  []string
	OrdersTopic   string
	ConsumerGroup string

	// Issuer of inbound JWTs; JWKS and token endpoints are derived from it
	// using Keycloak's well-known layout.
	OAuthIssuer string

	PaymentBaseURL   string
	InventoryBaseURL string
	Payment          OAuthClient
	Inventory        OAuthClient

	ReportInterval time.Duration
}

func (c Config) JWKSURL() string {
	return c.OAuthIssuer + "/protocol/openid-connect/certs"
}

func (c Config) TokenURL() string {
	return c.OAuthIssuer + "/protocol/openid-connect/token"
}

// Load reads configuration from the environment. Unlike Spring's relaxed
// binding and profile machinery, this is deliberately boring: one function,
// explicit names, explicit defaults.
func Load() (Config, error) {
	interval, err := time.ParseDuration(getenv("REPORT_INTERVAL", "30s"))
	if err != nil {
		return Config{}, fmt.Errorf("parse REPORT_INTERVAL: %w", err)
	}

	return Config{
		Port:        getenv("PORT", "8081"),
		DatabaseURL: getenv("DATABASE_URL", "postgres://orders:orders@localhost:5432/orders_go"),

		KafkaBrokers:  strings.Split(getenv("KAFKA_BROKERS", "localhost:9092"), ","),
		OrdersTopic:   getenv("ORDERS_TOPIC", "order-events"),
		ConsumerGroup: getenv("KAFKA_GROUP_ID", "go-order-service"),

		OAuthIssuer: getenv("OAUTH_ISSUER", "http://localhost:8090/realms/demo"),

		PaymentBaseURL:   getenv("PAYMENT_BASE_URL", "http://localhost:9095"),
		InventoryBaseURL: getenv("INVENTORY_BASE_URL", "http://localhost:9096"),
		Payment: OAuthClient{
			ClientID:     getenv("PAYMENT_CLIENT_ID", "payment-client"),
			ClientSecret: getenv("PAYMENT_CLIENT_SECRET", "payment-client-secret"),
			Scopes:       []string{"payments:charge"},
		},
		Inventory: OAuthClient{
			ClientID:     getenv("INVENTORY_CLIENT_ID", "inventory-client"),
			ClientSecret: getenv("INVENTORY_CLIENT_SECRET", "inventory-client-secret"),
			Scopes:       []string{"inventory:reserve"},
		},

		ReportInterval: interval,
	}, nil
}

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
