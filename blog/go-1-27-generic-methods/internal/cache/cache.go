// Package cache demonstrates the decision that generic methods introduce:
// should the type parameter live on the TYPE or on the METHOD?
//
// The rule this package argues for:
//
//	Parameterize the TYPE when T is part of the value's identity -- when the
//	receiver's state is meaningfully "a container of T" and two instances with
//	different T should not share anything.
//
//	Parameterize the METHOD when T varies per call against receiver state that
//	is deliberately shared across all T.
//
// A cache is the clearest case of the second, and it is a case Go could not
// express before 1.27.
package cache

import (
	"fmt"
	"sync"
	"time"
)

type entry struct {
	val       any
	size      int
	expiresAt time.Time
}

// Cache is a single cache with ONE shared memory budget, ONE eviction policy
// and ONE mutex, holding values of many different types.
//
// This is why the type parameter belongs on the method. A Cache[T] would force
// one instance per value type: a Cache[User], a Cache[Session], a Cache[Order].
// Each would carry its own budget and its own eviction clock, so a burst of
// User traffic could not reclaim memory from cold Session entries -- which is
// the entire reason a shared cache exists. Splitting by type would not be a
// typing detail; it would break the eviction semantics.
type Cache struct {
	mu      sync.Mutex
	entries map[string]entry
	used    int
	budget  int
	ttl     time.Duration
	now     func() time.Time
	Hits    int
	Misses  int
	Evicted int
}

func New(budget int, ttl time.Duration) *Cache {
	return &Cache{
		entries: make(map[string]entry),
		budget:  budget,
		ttl:     ttl,
		now:     time.Now,
	}
}

// Put stores v under key, charging size bytes against the shared budget.
//
// A generic method, so callers store any type; the budget accounting is shared
// across all of them.
func (c *Cache) Put[T any](key string, v T, size int) {
	c.mu.Lock()
	defer c.mu.Unlock()

	if old, ok := c.entries[key]; ok {
		c.used -= old.size
	}
	c.entries[key] = entry{val: v, size: size, expiresAt: c.now().Add(c.ttl)}
	c.used += size
	c.evictLocked()
}

// Get returns the value stored at key as a T.
//
// The receiver is shared; T is per call. Exactly the shape that required a
// package-level func GetAs[T any](c *Cache, key string) before Go 1.27.
func (c *Cache) Get[T any](key string) (T, bool) {
	var zero T
	c.mu.Lock()
	defer c.mu.Unlock()

	e, ok := c.entries[key]
	if !ok || c.now().After(e.expiresAt) {
		if ok {
			delete(c.entries, key)
			c.used -= e.size
		}
		c.Misses++
		return zero, false
	}
	t, ok := e.val.(T)
	if !ok {
		// Stored under a different type than requested. Worth surfacing as a
		// miss rather than a panic: cache keys outlive deploys, and a key can
		// legitimately change type across versions.
		c.Misses++
		return zero, false
	}
	c.Hits++
	return t, true
}

// GetOrLoad returns the cached T, or calls load and caches its result.
//
// This is the method that makes the shared-receiver argument concrete: the
// singleflight-style state, the budget and the metrics all live on c, while T
// belongs to the caller.
func (c *Cache) GetOrLoad[T any](key string, size int, load func() (T, error)) (T, error) {
	if v, ok := c.Get[T](key); ok {
		return v, nil
	}
	v, err := load()
	if err != nil {
		var zero T
		return zero, fmt.Errorf("cache: load %q: %w", key, err)
	}
	c.Put(key, v, size)
	return v, nil
}

// evictLocked drops entries until the shared budget is satisfied.
func (c *Cache) evictLocked() {
	for c.used > c.budget {
		var oldestKey string
		var oldest time.Time
		for k, e := range c.entries {
			if oldestKey == "" || e.expiresAt.Before(oldest) {
				oldestKey, oldest = k, e.expiresAt
			}
		}
		if oldestKey == "" {
			return
		}
		c.used -= c.entries[oldestKey].size
		delete(c.entries, oldestKey)
		c.Evicted++
	}
}

func (c *Cache) Used() int {
	c.mu.Lock()
	defer c.mu.Unlock()
	return c.used
}
