// Package auth is the hand-rolled slice of Spring Security this app needs:
// validate an inbound Bearer JWT against the IdP's JWKS and enforce a scope
// per route. It's ~100 lines instead of a starter dependency — nothing is
// hidden, and nothing you didn't write runs.
package auth

import (
	"context"
	"fmt"
	"net/http"
	"slices"
	"strings"

	"github.com/MicahParks/keyfunc/v3"
	"github.com/golang-jwt/jwt/v5"
)

// Verifier validates JWTs using keys fetched (and background-refreshed) from
// the issuer's JWKS endpoint — what Spring's issuer-uri property sets up.
type Verifier struct {
	keys   keyfunc.Keyfunc
	issuer string
}

func NewVerifier(ctx context.Context, jwksURL, issuer string) (*Verifier, error) {
	keys, err := keyfunc.NewDefaultCtx(ctx, []string{jwksURL})
	if err != nil {
		return nil, fmt.Errorf("fetch JWKS from %s: %w", jwksURL, err)
	}
	return &Verifier{keys: keys, issuer: issuer}, nil
}

type contextKey struct{}

// Claims extracts the validated token claims stored by the middleware.
func Claims(ctx context.Context) jwt.MapClaims {
	claims, _ := ctx.Value(contextKey{}).(jwt.MapClaims)
	return claims
}

// RequireScope wraps a handler with authentication + a scope check, the
// counterpart of .requestMatchers(...).hasAuthority("SCOPE_x") in Spring.
func (v *Verifier) RequireScope(scope string, next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		tokenString, ok := bearerToken(r)
		if !ok {
			unauthorized(w, "missing bearer token")
			return
		}

		token, err := jwt.Parse(tokenString, v.keys.Keyfunc,
			jwt.WithIssuer(v.issuer),
			jwt.WithExpirationRequired(),
			jwt.WithValidMethods([]string{"RS256"}))
		if err != nil {
			unauthorized(w, "invalid token: "+err.Error())
			return
		}

		claims, ok := token.Claims.(jwt.MapClaims)
		if !ok || !hasScope(claims, scope) {
			forbidden(w, "token lacks required scope "+scope)
			return
		}

		next.ServeHTTP(w, r.WithContext(context.WithValue(r.Context(), contextKey{}, claims)))
	})
}

func bearerToken(r *http.Request) (string, bool) {
	header := r.Header.Get("Authorization")
	token, found := strings.CutPrefix(header, "Bearer ")
	return token, found && token != ""
}

// hasScope checks the OAuth2 "scope" claim (space-delimited string, per RFC
// 8693 / Keycloak's format).
func hasScope(claims jwt.MapClaims, scope string) bool {
	raw, _ := claims["scope"].(string)
	return slices.Contains(strings.Fields(raw), scope)
}

func unauthorized(w http.ResponseWriter, detail string) {
	problem(w, http.StatusUnauthorized, detail)
}

func forbidden(w http.ResponseWriter, detail string) {
	problem(w, http.StatusForbidden, detail)
}

func problem(w http.ResponseWriter, status int, detail string) {
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(status)
	fmt.Fprintf(w, `{"status":%d,"detail":%q}`, status, detail)
}
