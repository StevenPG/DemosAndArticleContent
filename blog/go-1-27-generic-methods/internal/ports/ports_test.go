package ports

import (
	"strings"
	"testing"
)

// Strategy 1: fully type-safe, no assertions, one binding per entity.
func TestParameterizedInterface(t *testing.T) {
	users := NewMemRepo[User]()
	orders := NewMemRepo[Order]()

	// Both satisfy the port -- at different instantiations.
	var _ Repository[User] = users
	var _ Repository[Order] = orders

	if err := users.Put("u1", User{ID: "u1", Name: "ada"}); err != nil {
		t.Fatal(err)
	}
	svc := NewUserService(users)
	if err := svc.Rename("u1", "grace"); err != nil {
		t.Fatal(err)
	}
	got, err := users.Get("u1")
	if err != nil || got.Name != "grace" {
		t.Fatalf("Get = %+v, %v", got, err)
	}

	// The cost, made concrete: you cannot pass the order repo where a user
	// repo is wanted. That is the safety AND the wiring burden.
	// var _ Repository[User] = orders  // does not compile
}

// Strategy 2: heterogeneous port, generic method on the concrete adapter.
func TestGenericMethodOnAdapter(t *testing.T) {
	kv := NewMemKV()
	kv.Store("user:1", User{ID: "u1", Name: "ada"})
	kv.Store("order:1", Order{ID: "o1", Total: 4200})

	u, err := kv.LoadAs[User]("user:1")
	if err != nil || u.Name != "ada" {
		t.Fatalf("LoadAs[User] = %+v, %v", u, err)
	}
	o, err := kv.LoadAs[Order]("order:1")
	if err != nil || o.Total != 4200 {
		t.Fatalf("LoadAs[Order] = %+v, %v", o, err)
	}

	// One store, one instance, two entity types -- the arrangement strategy 1
	// cannot model without two stores.
	if got := len(kv.Keys()); got != 2 {
		t.Fatalf("keys = %d, want 2", got)
	}

	if _, err := kv.LoadAs[Order]("user:1"); err == nil {
		t.Fatal("want type mismatch error")
	} else if !strings.Contains(err.Error(), "want ports.Order") {
		t.Fatalf("unexpected error text: %v", err)
	}
}

// Strategy 3: generic function over the interface value. Still necessary,
// still unimproved by Go 1.27 -- this is the case generic methods do not reach.
func TestGenericFunctionOverInterface(t *testing.T) {
	kv := NewMemKV()
	kv.Store("user:1", User{ID: "u1", Name: "ada"})

	// Callers hold the INTERFACE here, not the concrete type, so LoadAs the
	// method is unreachable and LoadAs the function is the only option.
	var port KV = kv

	u, err := LoadAs[User](port, "user:1")
	if err != nil || u.Name != "ada" {
		t.Fatalf("LoadAs = %+v, %v", u, err)
	}
}
