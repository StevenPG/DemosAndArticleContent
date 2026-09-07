package stdlib

import (
	"math/rand/v2"
	"testing"
)

// The stdlib's own adoption: math/rand/v2 gained a generic METHOD in Go 1.27,
// alongside the package-level generic function it already had.
//
//	func         N[Int intType](n Int) Int      // since 1.22
//	func (r *Rand) N[Int intType](n Int) Int    // new in 1.27
func TestRandGenericMethod(t *testing.T) {
	r := rand.New(rand.NewPCG(1, 2))

	// The generic method. T is inferred from the argument.
	d := r.N(100)
	if d < 0 || d >= 100 {
		t.Fatalf("N(100) = %d, out of range", d)
	}

	// Explicit instantiation with a different integer type.
	u := r.N[uint32](50)
	if u >= 50 {
		t.Fatalf("N[uint32](50) = %d, out of range", u)
	}

	// A named integer type works too -- the constraint is any integer type.
	type Port int32
	p := r.N[Port](1024)
	if p < 0 || p >= 1024 {
		t.Fatalf("N[Port](1024) = %d, out of range", p)
	}

	t.Logf("r.N(100)=%d r.N[uint32](50)=%d r.N[Port](1024)=%d", d, u, p)
}

// Before 1.27 the only option on a *Rand receiver was the package-level
// function against the global source, or one of the fixed-width methods.
func TestPackageLevelStillWorks(t *testing.T) {
	if v := rand.N(10); v < 0 || v >= 10 {
		t.Fatalf("rand.N(10) = %d", v)
	}
}

func TestTypedFlagMethodCount(t *testing.T) {
	got := TypedFlagMethods()
	if len(got) != 16 {
		t.Fatalf("found %d typed flag methods, want 16: %v", len(got), got)
	}
	t.Logf("flag.FlagSet carries %d methods that a single generic method would replace:\n%v", len(got), got)
}

func TestViperAccessorCount(t *testing.T) {
	got := ViperTypedAccessors()
	if len(got) < 19 {
		t.Fatalf("expected at least 19 viper accessors, got %d", len(got))
	}
	t.Logf("viper exposes %d typed Get* accessors", len(got))
}
