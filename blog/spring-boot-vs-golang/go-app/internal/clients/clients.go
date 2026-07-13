// Package clients holds the two outbound OAuth2 client-credentials targets.
// golang.org/x/oauth2/clientcredentials does what Spring's
// OAuth2AuthorizedClientManager does: fetch a token, cache it, refresh on
// expiry — exposed as a drop-in *http.Client that injects the Bearer header.
package clients

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/google/uuid"
	"golang.org/x/oauth2/clientcredentials"
)

func oauth2HTTPClient(ctx context.Context, clientID, clientSecret, tokenURL string, scopes []string) *http.Client {
	cfg := clientcredentials.Config{
		ClientID:     clientID,
		ClientSecret: clientSecret,
		TokenURL:     tokenURL,
		Scopes:       scopes,
	}
	client := cfg.Client(ctx)
	client.Timeout = 10 * time.Second
	return client
}

func postJSON(ctx context.Context, client *http.Client, url string, body any, out any) error {
	payload, err := json.Marshal(body)
	if err != nil {
		return fmt.Errorf("marshal request: %w", err)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, url, bytes.NewReader(payload))
	if err != nil {
		return fmt.Errorf("build request: %w", err)
	}
	req.Header.Set("Content-Type", "application/json")

	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("call %s: %w", url, err)
	}
	defer resp.Body.Close()

	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		snippet, _ := io.ReadAll(io.LimitReader(resp.Body, 256))
		return fmt.Errorf("%s returned %d: %s", url, resp.StatusCode, snippet)
	}
	return json.NewDecoder(resp.Body).Decode(out)
}

// PaymentClient is the first OAuth2 target (its own client id, secret, scope).
type PaymentClient struct {
	baseURL string
	http    *http.Client
}

func NewPaymentClient(ctx context.Context, baseURL, clientID, clientSecret, tokenURL string, scopes []string) *PaymentClient {
	return &PaymentClient{
		baseURL: baseURL,
		http:    oauth2HTTPClient(ctx, clientID, clientSecret, tokenURL, scopes),
	}
}

func (c *PaymentClient) Charge(ctx context.Context, orderID uuid.UUID, amountCents int64) (string, error) {
	var result struct {
		PaymentID string `json:"paymentId"`
		Status    string `json:"status"`
	}
	err := postJSON(ctx, c.http, c.baseURL+"/payments", map[string]any{
		"orderId":     orderID.String(),
		"amountCents": amountCents,
	}, &result)
	if err != nil {
		return "", err
	}
	return result.PaymentID, nil
}

// InventoryClient is the second OAuth2 target with a separate registration.
type InventoryClient struct {
	baseURL string
	http    *http.Client
}

func NewInventoryClient(ctx context.Context, baseURL, clientID, clientSecret, tokenURL string, scopes []string) *InventoryClient {
	return &InventoryClient{
		baseURL: baseURL,
		http:    oauth2HTTPClient(ctx, clientID, clientSecret, tokenURL, scopes),
	}
}

func (c *InventoryClient) Reserve(ctx context.Context, orderID uuid.UUID, item string, quantity int) error {
	var result struct {
		ReservationID string `json:"reservationId"`
		Status        string `json:"status"`
	}
	return postJSON(ctx, c.http, c.baseURL+"/inventory/reservations", map[string]any{
		"orderId":  orderID.String(),
		"item":     item,
		"quantity": quantity,
	}, &result)
}
