// Package ports addresses the claim that generic methods simplify clean
// architecture. They do -- but not at the port boundary, which is where most
// people will try to use them first.
//
// Because an interface method may not declare type parameters, the port you
// actually want:
//
//	type Repository interface {
//	    Get[T any](id string) (T, error)   // does not compile
//	}
//
// is still illegal in Go 1.27. Three strategies remain. They are presented in
// the order you should reach for them.
package ports

import (
	"fmt"
	"maps"
	"slices"
)

type User struct {
	ID   string
	Name string
}

type Order struct {
	ID    string
	Total int
}

// ---------------------------------------------------------------------------
// Strategy 1: parameterize the INTERFACE.
//
// The type parameter moves to the interface itself, and the method stays
// ordinary. This is type-safe end to end and needs no assertions anywhere.
//
// The cost is real and worth stating plainly: Repository[User] and
// Repository[Order] are distinct types. Your dependency wiring gains one
// binding per entity, and a struct that depends on five repositories carries
// five separate fields. For most services that is fine -- the entity count is
// small and known at compile time.
// ---------------------------------------------------------------------------

type Repository[T any] interface {
	Get(id string) (T, error)
	Put(id string, v T) error
}

type MemRepo[T any] struct {
	items map[string]T
}

func NewMemRepo[T any]() *MemRepo[T] {
	return &MemRepo[T]{items: make(map[string]T)}
}

func (m *MemRepo[T]) Get(id string) (T, error) {
	var zero T
	v, ok := m.items[id]
	if !ok {
		return zero, fmt.Errorf("ports: no record %q", id)
	}
	return v, nil
}

func (m *MemRepo[T]) Put(id string, v T) error {
	m.items[id] = v
	return nil
}

// UserService depends on the port, not the implementation -- the property
// clean architecture actually cares about. Nothing here was blocked before
// Go 1.27; generic types have existed since 1.18.
type UserService struct {
	repo Repository[User]
}

func NewUserService(r Repository[User]) *UserService { return &UserService{repo: r} }

func (s *UserService) Rename(id, name string) error {
	u, err := s.repo.Get(id)
	if err != nil {
		return err
	}
	u.Name = name
	return s.repo.Put(id, u)
}

// ---------------------------------------------------------------------------
// Strategy 2: keep the port untyped, put the generic method on the ADAPTER.
//
// Use this when the port genuinely is heterogeneous -- a key/value store, a
// cache, a document database -- so that one instantiation per entity would be
// the wrong model. The port speaks any; the concrete adapter offers a generic
// method for callers that hold the concrete type.
// ---------------------------------------------------------------------------

type KV interface {
	Load(key string) (any, bool)
	Store(key string, v any)
	Keys() []string
}

type MemKV struct {
	items map[string]any
}

func NewMemKV() *MemKV { return &MemKV{items: make(map[string]any)} }

func (m *MemKV) Load(key string) (any, bool) { v, ok := m.items[key]; return v, ok }
func (m *MemKV) Store(key string, v any)     { m.items[key] = v }
func (m *MemKV) Keys() []string              { return slices.Sorted(maps.Keys(m.items)) }

// LoadAs is the generic method on the concrete adapter. Callers wired to the
// interface cannot reach it; callers holding a *MemKV get the typed call.
//
// Before Go 1.27 this had to be a package-level function.
func (m *MemKV) LoadAs[T any](key string) (T, error) {
	var zero T
	v, ok := m.items[key]
	if !ok {
		return zero, fmt.Errorf("ports: no key %q", key)
	}
	t, ok := v.(T)
	if !ok {
		return zero, fmt.Errorf("ports: key %q is %T, want %T", key, v, zero)
	}
	return t, nil
}

// ---------------------------------------------------------------------------
// Strategy 3: a generic FUNCTION over the interface.
//
// The port stays non-generic and single-instance; the type parameter lands at
// the call site instead. This is the one strategy generic methods did NOT
// improve -- a function is still the only thing that can be generic over an
// interface value -- so it is worth knowing it is still the right answer when
// you need one port instance serving many types.
// ---------------------------------------------------------------------------

func LoadAs[T any](kv KV, key string) (T, error) {
	var zero T
	v, ok := kv.Load(key)
	if !ok {
		return zero, fmt.Errorf("ports: no key %q", key)
	}
	t, ok := v.(T)
	if !ok {
		return zero, fmt.Errorf("ports: key %q is %T, want %T", key, v, zero)
	}
	return t, nil
}
