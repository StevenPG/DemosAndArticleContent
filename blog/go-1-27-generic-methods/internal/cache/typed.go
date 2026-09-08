package cache

import "sync"

// TypedRing is the other side of the rule: here the type parameter belongs on
// the TYPE, not the method.
//
// A ring buffer IS a buffer of T. Its backing array, its length and its element
// type are one idea; you cannot store a User and a Session in the same ring and
// have Len() mean anything useful. T is part of the value's identity, so it
// goes on the type -- and the methods stay ordinary, non-generic methods that
// use the receiver's parameter.
//
// Generic methods do not replace generic types. Reaching for a generic method
// where a generic type belongs gives you an API that compiles and lies: it
// accepts types the container cannot coherently hold.
type TypedRing[T any] struct {
	mu   sync.Mutex
	buf  []T
	next int
	full bool
}

func NewRing[T any](n int) *TypedRing[T] {
	return &TypedRing[T]{buf: make([]T, n)}
}

// Push is NOT generic. It uses T from the receiver type.
func (r *TypedRing[T]) Push(v T) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.buf[r.next] = v
	r.next = (r.next + 1) % len(r.buf)
	if r.next == 0 {
		r.full = true
	}
}

func (r *TypedRing[T]) Snapshot() []T {
	r.mu.Lock()
	defer r.mu.Unlock()
	n := r.next
	if r.full {
		n = len(r.buf)
	}
	out := make([]T, 0, n)
	if r.full {
		out = append(out, r.buf[r.next:]...)
	}
	out = append(out, r.buf[:r.next]...)
	return out
}

// MapTo shows the two composing: a generic METHOD on a generic TYPE. R is the
// per-call type; T is the container's type. Both are legal in Go 1.27, and the
// method may reference either.
func (r *TypedRing[T]) MapTo[R any](f func(T) R) []R {
	src := r.Snapshot()
	out := make([]R, len(src))
	for i, v := range src {
		out[i] = f(v)
	}
	return out
}
