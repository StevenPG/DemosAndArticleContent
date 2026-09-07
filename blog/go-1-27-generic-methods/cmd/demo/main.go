// Command demo runs the generic-method examples end to end and prints what
// each one demonstrates. Everything here is also covered by tests; this binary
// exists so you can see the behaviour without reading test output.
package main

import (
	"fmt"
	"math/rand/v2"
	"time"

	"github.com/StevenPG/demos/go-1-27-generic-methods/internal/cache"
	"github.com/StevenPG/demos/go-1-27-generic-methods/internal/config"
	"github.com/StevenPG/demos/go-1-27-generic-methods/internal/stdlib"
)

type RetryPolicy struct {
	Attempts int
	Backoff  time.Duration
}

type User struct {
	ID   int
	Name string
}

type Session struct{ Token string }

func main() {
	section("1. The typed-accessor family, collapsed")

	src := config.New(map[string]any{
		"service.name": "telemetry-ingest",
		"http.port":    8080,
		"http.timeout": 5 * time.Second,
		"retry":        RetryPolicy{Attempts: 3, Backoff: 250 * time.Millisecond},
	})

	name, _ := src.Get[string]("service.name")
	port, _ := src.Get[int]("http.port")
	timeout, _ := src.Get[time.Duration]("http.timeout")
	fmt.Printf("  name=%s port=%d timeout=%s\n", name, port, timeout)

	// The capability the accessor family cannot have: a type the config
	// package has never heard of.
	policy, _ := src.Get[RetryPolicy]("retry")
	fmt.Printf("  caller-defined type: %+v\n", policy)

	// Inference, no explicit instantiation.
	fmt.Printf("  GetOr inferred: %d\n", src.GetOr("http.missing", 3000))

	if _, err := src.Get[string]("http.port"); err != nil {
		fmt.Printf("  type mismatch: %v\n", err)
	}

	section("2. One cache, one budget, many types")

	c := cache.New(100, time.Minute)
	c.Put("user:1", User{ID: 1, Name: "ada"}, 40)
	c.Put("session:abc", Session{Token: "abc"}, 40)
	fmt.Printf("  used=%d/100\n", c.Used())

	u, _ := c.Get[User]("user:1")
	s, _ := c.Get[Session]("session:abc")
	fmt.Printf("  Get[User]=%+v  Get[Session]=%+v\n", u, s)

	c.Put("user:2", User{ID: 2, Name: "grace"}, 40)
	fmt.Printf("  after third insert: used=%d evicted=%d (reclaimed across types)\n", c.Used(), c.Evicted)

	section("3. The stdlib's own adoption")

	r := rand.New(rand.NewPCG(42, 1024))
	fmt.Printf("  r.N(100)        = %d   (generic method, new in 1.27)\n", r.N(100))
	fmt.Printf("  r.N[uint32](50) = %d   (explicit instantiation)\n", r.N[uint32](50))

	flags := stdlib.TypedFlagMethods()
	fmt.Printf("  flag.FlagSet carries %d methods that one generic method would replace:\n", len(flags))
	fmt.Printf("    %v\n", flags)
	fmt.Printf("  viper exposes %d typed Get* accessors for the same reason\n", len(stdlib.ViperTypedAccessors()))

	section("4. What still does not compile")
	fmt.Println("  Run: go test ./internal/limits/ -v")
	fmt.Println("  It compiles three illegal programs and asserts on the real compiler output.")
}

func section(title string) {
	fmt.Printf("\n=== %s\n", title)
}
