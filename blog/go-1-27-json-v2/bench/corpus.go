// Package bench compares encoding/json (v1) with encoding/json/v2 across five
// payload shapes chosen to stress different parts of a JSON codec.
//
// The corpus is generated deterministically in code rather than checked in, so
// a run is reproducible on any machine without large fixture files.
package bench

import (
	"fmt"
	"math/rand/v2"
	"strings"
)

// --- Shape 1: small flat struct. The single most common real payload: an API
// response of a dozen scalar fields. Dominated by per-call overhead. ---

type Small struct {
	ID      int     `json:"id"`
	Name    string  `json:"name"`
	Email   string  `json:"email"`
	Active  bool    `json:"active"`
	Score   float64 `json:"score"`
	Country string  `json:"country"`
	Version int     `json:"version"`
}

// --- Shape 2: deeply nested. Stresses the codec's recursion and state
// machine rather than its scalar handling. ---

type Nested struct {
	Level int     `json:"level"`
	Label string  `json:"label"`
	Child *Nested `json:"child,omitempty"`
}

// --- Shape 3: large homogeneous array. The batch/telemetry case, where
// throughput per element is what matters. ---

type Sample struct {
	T     float64 `json:"t"`
	Alt   float64 `json:"alt_m"`
	Lat   float64 `json:"lat"`
	Lon   float64 `json:"lon"`
	Valid bool    `json:"valid"`
}

type Batch struct {
	Vehicle string   `json:"vehicle"`
	Samples []Sample `json:"samples"`
}

// --- Shape 4: string-heavy with escapes. Escaping and unescaping is a
// meaningful share of JSON cost and the two versions handle UTF-8 differently,
// so this is where a difference would show. ---

type Document struct {
	Title string   `json:"title"`
	Body  string   `json:"body"`
	Tags  []string `json:"tags"`
}

// --- Shape 5: dynamic map[string]any. The reflection-heavy worst case, and
// what most logging and passthrough code actually does. ---

type Dynamic map[string]any

// Fixtures holds one generated value of each shape.
type Fixtures struct {
	Small    Small
	Nested   *Nested
	Batch    Batch
	Document Document
	Dynamic  Dynamic
}

// NewFixtures builds the corpus from a fixed seed, so every run and every
// machine measures identical bytes.
func NewFixtures() Fixtures {
	r := rand.New(rand.NewPCG(0x5eed, 0x1227))

	small := Small{
		ID: 918273, Name: "Ada Lovelace", Email: "ada@example.com",
		Active: true, Score: 99.5, Country: "GB", Version: 3,
	}

	// 32 levels deep.
	var nested *Nested
	for i := 32; i >= 1; i-- {
		nested = &Nested{Level: i, Label: fmt.Sprintf("level-%02d", i), Child: nested}
	}

	// 2,000 samples.
	samples := make([]Sample, 2000)
	for i := range samples {
		samples[i] = Sample{
			T:     float64(i) * 0.25,
			Alt:   1500 + r.Float64()*100,
			Lat:   38.9 + r.Float64(),
			Lon:   -77.0 - r.Float64(),
			Valid: i%17 != 0,
		}
	}
	batch := Batch{Vehicle: "N724PG", Samples: samples}

	// Escape-heavy body: quotes, backslashes, newlines, tabs, non-ASCII.
	var body strings.Builder
	for i := 0; i < 400; i++ {
		body.WriteString("line \"")
		body.WriteString(fmt.Sprintf("%03d", i))
		body.WriteString("\" \\ path\\to\\file\tnaïve café → résumé\n")
	}
	doc := Document{
		Title: "Post-flight \"analysis\" report — ré-entry",
		Body:  body.String(),
		Tags:  []string{"telemetry", "naïve", "flight/test", "a\"b"},
	}

	dyn := Dynamic{}
	for i := 0; i < 200; i++ {
		switch i % 5 {
		case 0:
			dyn[fmt.Sprintf("k%03d", i)] = r.Float64() * 1000
		case 1:
			dyn[fmt.Sprintf("k%03d", i)] = fmt.Sprintf("value-%03d", i)
		case 2:
			dyn[fmt.Sprintf("k%03d", i)] = i%2 == 0
		case 3:
			dyn[fmt.Sprintf("k%03d", i)] = []any{1.0, "two", true, nil}
		case 4:
			dyn[fmt.Sprintf("k%03d", i)] = map[string]any{
				"inner": fmt.Sprintf("n%03d", i),
				"count": float64(i),
			}
		}
	}

	return Fixtures{Small: small, Nested: nested, Batch: batch, Document: doc, Dynamic: dyn}
}
