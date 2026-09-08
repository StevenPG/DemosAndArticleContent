package cache

import (
	"errors"
	"testing"
	"time"
)

type User struct {
	ID   int
	Name string
}

type Session struct {
	Token string
}

// The load-bearing test: one cache, one budget, many types. This is the
// arrangement a Cache[T] cannot express.
func TestSharedBudgetAcrossTypes(t *testing.T) {
	c := New(100, time.Minute)

	c.Put("user:1", User{ID: 1, Name: "ada"}, 40)
	c.Put("session:abc", Session{Token: "abc"}, 40)

	if got := c.Used(); got != 80 {
		t.Fatalf("used = %d, want 80", got)
	}

	u, ok := c.Get[User]("user:1")
	if !ok || u.Name != "ada" {
		t.Fatalf("Get[User] = %+v, %v", u, ok)
	}
	s, ok := c.Get[Session]("session:abc")
	if !ok || s.Token != "abc" {
		t.Fatalf("Get[Session] = %+v, %v", s, ok)
	}

	// A third value pushes past the shared budget, and eviction reclaims from
	// whichever type held the coldest entry -- across type boundaries. With one
	// cache per type this reclamation is impossible.
	c.Put("user:2", User{ID: 2, Name: "grace"}, 40)
	if c.Evicted == 0 {
		t.Fatal("expected cross-type eviction, got none")
	}
	if c.Used() > 100 {
		t.Fatalf("budget exceeded: used = %d", c.Used())
	}
	t.Logf("after eviction: used=%d evicted=%d", c.Used(), c.Evicted)
}

func TestGetWrongTypeIsMissNotPanic(t *testing.T) {
	c := New(100, time.Minute)
	c.Put("k", User{ID: 1}, 10)

	if _, ok := c.Get[Session]("k"); ok {
		t.Fatal("expected miss on type mismatch")
	}
}

func TestGetOrLoadInfersFromClosure(t *testing.T) {
	c := New(100, time.Minute)
	calls := 0

	load := func() (User, error) {
		calls++
		return User{ID: 7, Name: "hopper"}, nil
	}

	// No explicit instantiation: T is inferred from load's return type.
	u1, err := c.GetOrLoad("user:7", 20, load)
	if err != nil || u1.Name != "hopper" {
		t.Fatalf("first load: %+v %v", u1, err)
	}
	u2, err := c.GetOrLoad("user:7", 20, load)
	if err != nil || u2.ID != 7 {
		t.Fatalf("second load: %+v %v", u2, err)
	}
	if calls != 1 {
		t.Fatalf("load called %d times, want 1 (second should hit cache)", calls)
	}
}

func TestGetOrLoadPropagatesError(t *testing.T) {
	c := New(100, time.Minute)
	sentinel := errors.New("upstream down")

	_, err := c.GetOrLoad("x", 10, func() (User, error) { return User{}, sentinel })
	if !errors.Is(err, sentinel) {
		t.Fatalf("error not wrapped: %v", err)
	}
}

func TestExpiry(t *testing.T) {
	c := New(100, 50*time.Millisecond)
	base := time.Now()
	c.now = func() time.Time { return base }

	c.Put("k", User{ID: 1}, 10)
	if _, ok := c.Get[User]("k"); !ok {
		t.Fatal("want hit before expiry")
	}

	c.now = func() time.Time { return base.Add(time.Second) }
	if _, ok := c.Get[User]("k"); ok {
		t.Fatal("want miss after expiry")
	}
}

// The other half of the rule: T on the type, methods stay ordinary.
func TestTypedRing(t *testing.T) {
	r := NewRing[int](3)
	r.Push(1)
	r.Push(2)
	r.Push(3)
	r.Push(4) // wraps, evicting 1

	got := r.Snapshot()
	want := []int{2, 3, 4}
	if len(got) != len(want) {
		t.Fatalf("snapshot = %v, want %v", got, want)
	}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("snapshot = %v, want %v", got, want)
		}
	}
}

// Generic method on a generic type: R per call, T from the receiver.
func TestGenericMethodOnGenericType(t *testing.T) {
	r := NewRing[User](2)
	r.Push(User{ID: 1, Name: "ada"})
	r.Push(User{ID: 2, Name: "grace"})

	names := r.MapTo(func(u User) string { return u.Name })
	if len(names) != 2 || names[0] != "ada" || names[1] != "grace" {
		t.Fatalf("MapTo = %v", names)
	}

	ids := r.MapTo(func(u User) int { return u.ID })
	if ids[0] != 1 || ids[1] != 2 {
		t.Fatalf("MapTo[int] = %v", ids)
	}
}
