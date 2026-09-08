package config

import (
	"testing"
	"time"
)

// A caller-defined type. The point: the pre-1.27 accessor family cannot serve
// this at all without editing package config, because the family is a closed
// list. The generic method serves it for free.
type RetryPolicy struct {
	Attempts int
	Backoff  time.Duration
}

func fixture() *Source {
	return New(map[string]any{
		"service.name":    "telemetry-ingest",
		"http.port":       8080,
		"http.timeout":    5 * time.Second,
		"tracing.enabled": true,
		"upstreams":       []string{"alpha", "bravo"},
		"retry":           RetryPolicy{Attempts: 3, Backoff: 250 * time.Millisecond},
	})
}

func TestGenericMethodMatchesAccessorFamily(t *testing.T) {
	s := fixture()

	name, err := s.Get[string]("service.name")
	if err != nil || name != "telemetry-ingest" {
		t.Fatalf("Get[string] = %q, %v", name, err)
	}
	oldName, err := s.GetString("service.name")
	if err != nil || oldName != name {
		t.Fatalf("GetString disagrees with Get[string]: %q vs %q", oldName, name)
	}

	port, err := s.Get[int]("http.port")
	if err != nil || port != 8080 {
		t.Fatalf("Get[int] = %d, %v", port, err)
	}

	timeout, err := s.Get[time.Duration]("http.timeout")
	if err != nil || timeout != 5*time.Second {
		t.Fatalf("Get[time.Duration] = %v, %v", timeout, err)
	}
}

// The capability the accessor family simply does not have.
func TestGenericMethodServesCallerDefinedTypes(t *testing.T) {
	s := fixture()

	p, err := s.Get[RetryPolicy]("retry")
	if err != nil {
		t.Fatalf("Get[RetryPolicy]: %v", err)
	}
	if p.Attempts != 3 || p.Backoff != 250*time.Millisecond {
		t.Fatalf("got %+v", p)
	}
	// There is no s.GetRetryPolicy, and there never can be without config
	// taking a dependency on the caller's package.
}

// Type inference works on generic methods, so the common case needs no
// explicit instantiation at the call site.
func TestTypeInferenceFromArgument(t *testing.T) {
	s := fixture()

	// No [int] here -- inferred from the default value.
	if got := s.GetOr("http.port", 3000); got != 8080 {
		t.Fatalf("GetOr = %d, want 8080", got)
	}
	if got := s.GetOr("http.missing", 3000); got != 3000 {
		t.Fatalf("GetOr fallback = %d, want 3000", got)
	}
	// Inference picks up named types too.
	if got := s.GetOr("retry", RetryPolicy{Attempts: 1}); got.Attempts != 3 {
		t.Fatalf("GetOr[RetryPolicy] = %+v", got)
	}
}

// A generic method can be instantiated into an ordinary method value and
// passed around like any other func.
func TestInstantiatedMethodValue(t *testing.T) {
	s := fixture()

	var readPort func(string) (int, error) = s.Get[int]
	got, err := readPort("http.port")
	if err != nil || got != 8080 {
		t.Fatalf("method value = %d, %v", got, err)
	}
}

func TestWrongTypeAndMissingKeyErrors(t *testing.T) {
	s := fixture()

	if _, err := s.Get[string]("http.port"); err == nil {
		t.Fatal("want type error, got nil")
	} else {
		t.Logf("wrong type: %v", err)
	}

	if _, err := s.Get[int]("nope"); err == nil {
		t.Fatal("want missing-key error, got nil")
	} else {
		t.Logf("missing key: %v", err)
	}
}

// The pre-1.27 workaround still compiles and behaves identically. It is the
// ergonomics that changed, not the capability.
func TestWorkaroundStillWorks(t *testing.T) {
	s := fixture()

	viaFunc, err1 := Lookup[int](s, "http.port")
	viaMethod, err2 := s.Get[int]("http.port")
	if err1 != nil || err2 != nil || viaFunc != viaMethod {
		t.Fatalf("mismatch: %d/%v vs %d/%v", viaFunc, err1, viaMethod, err2)
	}
}
