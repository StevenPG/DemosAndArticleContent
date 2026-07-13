// downstream-stub plays the two external APIs that both order services call
// with OAuth2 client-credentials tokens:
//
//	:9095  payment service   — POST /payments               (scope payments:charge)
//	:9096  inventory service — POST /inventory/reservations (scope inventory:reserve)
//
// It validates each Bearer token for real: signature against Keycloak's JWKS,
// issuer, expiry, and the per-service scope. That makes the end-to-end
// client-credentials flow observable — send a bad or missing token and you
// get a 401/403, not a silent success.
package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"
)

func getenv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func main() {
	log := slog.New(slog.NewTextHandler(os.Stdout, nil))

	// Inside docker-compose the JWKS is fetched from the keycloak container,
	// but tokens are minted by clients talking to localhost:8090 — so the
	// expected issuer is configured separately from the JWKS URL.
	jwksURL := getenv("JWKS_URL", "http://keycloak:8080/realms/demo/protocol/openid-connect/certs")
	issuer := getenv("EXPECTED_ISSUER", "http://localhost:8090/realms/demo")

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// Keycloak may still be booting when this container starts; retry until
	// the JWKS endpoint responds.
	var keys keyfunc.Keyfunc
	var err error
	for {
		keys, err = keyfunc.NewDefaultCtx(ctx, []string{jwksURL})
		if err == nil {
			break
		}
		log.Info("waiting for keycloak JWKS", "url", jwksURL, "err", err)
		select {
		case <-ctx.Done():
			return
		case <-time.After(3 * time.Second):
		}
	}
	log.Info("JWKS loaded", "url", jwksURL)

	paymentSrv := serve(":9095", "payment", authenticated(keys, issuer, "payments:charge", log,
		"POST /payments", func(req map[string]any) map[string]any {
			return map[string]any{
				"paymentId": "pay-" + uuid.NewString()[:8],
				"status":    "CHARGED",
			}
		}), log)
	inventorySrv := serve(":9096", "inventory", authenticated(keys, issuer, "inventory:reserve", log,
		"POST /inventory/reservations", func(req map[string]any) map[string]any {
			return map[string]any{
				"reservationId": "res-" + uuid.NewString()[:8],
				"status":        "RESERVED",
			}
		}), log)

	<-ctx.Done()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = paymentSrv.Shutdown(shutdownCtx)
	_ = inventorySrv.Shutdown(shutdownCtx)
}

func serve(addr, name string, handler http.Handler, log *slog.Logger) *http.Server {
	srv := &http.Server{Addr: addr, Handler: handler, ReadHeaderTimeout: 5 * time.Second}
	go func() {
		log.Info("stub listening", "service", name, "addr", addr)
		if err := srv.ListenAndServe(); err != http.ErrServerClosed {
			log.Error("stub server failed", "service", name, "err", err)
			os.Exit(1)
		}
	}()
	return srv
}

func authenticated(keys keyfunc.Keyfunc, issuer, requiredScope string, log *slog.Logger,
	pattern string, respond func(map[string]any) map[string]any) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	})
	mux.HandleFunc(pattern, func(w http.ResponseWriter, r *http.Request) {
		tokenString, found := strings.CutPrefix(r.Header.Get("Authorization"), "Bearer ")
		if !found || tokenString == "" {
			http.Error(w, `{"error":"missing bearer token"}`, http.StatusUnauthorized)
			return
		}

		token, err := jwt.Parse(tokenString, keys.Keyfunc,
			jwt.WithIssuer(issuer),
			jwt.WithExpirationRequired(),
			jwt.WithValidMethods([]string{"RS256"}))
		if err != nil {
			log.Warn("rejected token", "err", err)
			http.Error(w, fmt.Sprintf(`{"error":%q}`, err.Error()), http.StatusUnauthorized)
			return
		}

		claims, _ := token.Claims.(jwt.MapClaims)
		scopes, _ := claims["scope"].(string)
		if !slices.Contains(strings.Fields(scopes), requiredScope) {
			http.Error(w, fmt.Sprintf(`{"error":"missing scope %s"}`, requiredScope), http.StatusForbidden)
			return
		}

		var body map[string]any
		if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
			http.Error(w, `{"error":"malformed JSON"}`, http.StatusBadRequest)
			return
		}

		clientID, _ := claims["azp"].(string)
		log.Info("authorized call", "path", r.URL.Path, "client", clientID, "body", body)

		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(respond(body))
	})
	return mux
}
