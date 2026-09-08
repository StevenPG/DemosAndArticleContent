package config

import "fmt"

// Go 1.27: the type parameter moves onto the method. The whole family above
// collapses into two methods that cover every type, including types this
// package has never heard of.
//
// The stdlib made exactly this move: math/rand/v2 gained
//
//	func (r *Rand) N[Int intType](n Int) Int
//
// alongside the package-level N[Int intType](n Int) Int it already had.

// Get returns the value at key as a T.
func (s *Source) Get[T any](key string) (T, error) {
	var zero T
	v, err := s.lookup(key)
	if err != nil {
		return zero, err
	}
	t, ok := v.(T)
	if !ok {
		return zero, fmt.Errorf("config: key %q is %T, want %T", key, v, zero)
	}
	return t, nil
}

// GetOr returns the value at key as a T, or def if the key is missing or holds
// a different type.
//
// Note the type parameter is inferred from def -- callers write
// src.GetOr("port", 8080), with no explicit instantiation. Type inference works
// on generic methods exactly as it does on generic functions.
func (s *Source) GetOr[T any](key string, def T) T {
	v, err := s.Get[T](key)
	if err != nil {
		return def
	}
	return v
}

// MustGet returns the value at key as a T, panicking if it is absent or of the
// wrong type. Suitable for startup-time configuration where a missing value is
// a programming error, not a runtime condition.
func (s *Source) MustGet[T any](key string) T {
	v, err := s.Get[T](key)
	if err != nil {
		panic(err)
	}
	return v
}
