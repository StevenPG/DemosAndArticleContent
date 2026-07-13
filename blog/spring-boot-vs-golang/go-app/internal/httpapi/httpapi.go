// Package httpapi is the REST layer: stdlib net/http with the Go 1.22+
// method-and-path ServeMux patterns — no framework. Handlers, validation
// responses, and error mapping are explicit code.
package httpapi

import (
	"encoding/json"
	"errors"
	"log/slog"
	"net/http"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/prometheus/client_golang/prometheus/promhttp"

	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/auth"
	"github.com/StevenPG/DemosAndArticleContent/blog/spring-boot-vs-golang/go-app/internal/orders"
)

type Handler struct {
	service *orders.Service
	log     *slog.Logger
}

// NewMux wires every route, its auth requirement, and the operational
// endpoints. This table is the Go equivalent of @RequestMapping annotations
// plus SecurityConfig's authorizeHttpRequests block, in one place.
func NewMux(service *orders.Service, verifier *auth.Verifier, pool *pgxpool.Pool, log *slog.Logger) *http.ServeMux {
	h := &Handler{service: service, log: log}
	mux := http.NewServeMux()

	mux.Handle("POST /api/orders", verifier.RequireScope("orders:write", http.HandlerFunc(h.create)))
	mux.Handle("GET /api/orders/{id}", verifier.RequireScope("orders:read", http.HandlerFunc(h.get)))
	mux.Handle("GET /api/orders", verifier.RequireScope("orders:read", http.HandlerFunc(h.list)))

	// The actuator equivalents: liveness, readiness (with a real DB ping),
	// and Prometheus metrics.
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, http.StatusOK, map[string]string{"status": "UP"})
	})
	mux.HandleFunc("GET /readyz", func(w http.ResponseWriter, r *http.Request) {
		if err := pool.Ping(r.Context()); err != nil {
			writeJSON(w, http.StatusServiceUnavailable, map[string]string{"status": "DOWN", "db": err.Error()})
			return
		}
		writeJSON(w, http.StatusOK, map[string]string{"status": "UP", "db": "UP"})
	})
	mux.Handle("GET /metrics", promhttp.Handler())

	return mux
}

func (h *Handler) create(w http.ResponseWriter, r *http.Request) {
	var req orders.CreateOrderRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		writeProblem(w, http.StatusBadRequest, "malformed JSON body", nil)
		return
	}
	if errs := req.Validate(); len(errs) > 0 {
		writeProblem(w, http.StatusBadRequest, "Request validation failed", errs)
		return
	}

	order, err := h.service.Create(r.Context(), req)
	if err != nil {
		h.serverError(w, err)
		return
	}
	writeJSON(w, http.StatusCreated, order)
}

func (h *Handler) get(w http.ResponseWriter, r *http.Request) {
	id, err := uuid.Parse(r.PathValue("id"))
	if err != nil {
		writeProblem(w, http.StatusBadRequest, "invalid order id", nil)
		return
	}

	order, err := h.service.Get(r.Context(), id)
	if errors.Is(err, orders.ErrNotFound) {
		writeProblem(w, http.StatusNotFound, "Order "+id.String()+" not found", nil)
		return
	}
	if err != nil {
		h.serverError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, order)
}

func (h *Handler) list(w http.ResponseWriter, r *http.Request) {
	var status *orders.Status
	if s := r.URL.Query().Get("status"); s != "" {
		st := orders.Status(s)
		status = &st
	}

	result, err := h.service.List(r.Context(), status)
	if err != nil {
		h.serverError(w, err)
		return
	}
	writeJSON(w, http.StatusOK, result)
}

func (h *Handler) serverError(w http.ResponseWriter, err error) {
	h.log.Error("request failed", "err", err)
	writeProblem(w, http.StatusInternalServerError, "internal error", nil)
}

func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}

// writeProblem emits an RFC 9457 problem document, matching the ProblemDetail
// responses the Spring app produces.
func writeProblem(w http.ResponseWriter, status int, detail string, fieldErrors map[string]string) {
	body := map[string]any{
		"status": status,
		"title":  http.StatusText(status),
		"detail": detail,
	}
	if len(fieldErrors) > 0 {
		body["errors"] = fieldErrors
	}
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(body)
}
