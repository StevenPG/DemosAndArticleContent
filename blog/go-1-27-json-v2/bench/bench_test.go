package bench

import (
	v1 "encoding/json"
	v2 "encoding/json/v2"
	"testing"
)

// Benchmark naming is deliberate: benchstat compares benchmarks whose names
// differ only by the final /vN element, so `benchstat -col /impl` renders a v1
// vs v2 table directly. See results/README.md for how the numbers were taken.

var fx = NewFixtures()

// Pre-encoded inputs for the unmarshal benchmarks, produced with v1 so both
// implementations decode byte-identical input.
var (
	smallJSON    = mustMarshal(fx.Small)
	nestedJSON   = mustMarshal(fx.Nested)
	batchJSON    = mustMarshal(fx.Batch)
	documentJSON = mustMarshal(fx.Document)
	dynamicJSON  = mustMarshal(fx.Dynamic)
)

func mustMarshal(v any) []byte {
	b, err := v1.Marshal(v)
	if err != nil {
		panic(err)
	}
	return b
}

// ---------------------------------------------------------------------------
// Marshal
// ---------------------------------------------------------------------------

func BenchmarkMarshal(b *testing.B) {
	cases := []struct {
		name string
		val  any
		size int
	}{
		{"Small", fx.Small, len(smallJSON)},
		{"Nested", fx.Nested, len(nestedJSON)},
		{"Batch", fx.Batch, len(batchJSON)},
		{"Document", fx.Document, len(documentJSON)},
		{"Dynamic", fx.Dynamic, len(dynamicJSON)},
	}

	for _, c := range cases {
		b.Run(c.name+"/impl=v1", func(b *testing.B) {
			b.SetBytes(int64(c.size))
			b.ReportAllocs()
			for b.Loop() {
				if _, err := v1.Marshal(c.val); err != nil {
					b.Fatal(err)
				}
			}
		})
		b.Run(c.name+"/impl=v2", func(b *testing.B) {
			b.SetBytes(int64(c.size))
			b.ReportAllocs()
			for b.Loop() {
				if _, err := v2.Marshal(c.val); err != nil {
					b.Fatal(err)
				}
			}
		})
	}
}

// ---------------------------------------------------------------------------
// Unmarshal
// ---------------------------------------------------------------------------

func BenchmarkUnmarshal(b *testing.B) {
	b.Run("Small/impl=v1", func(b *testing.B) {
		b.SetBytes(int64(len(smallJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Small
			if err := v1.Unmarshal(smallJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("Small/impl=v2", func(b *testing.B) {
		b.SetBytes(int64(len(smallJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Small
			if err := v2.Unmarshal(smallJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})

	b.Run("Nested/impl=v1", func(b *testing.B) {
		b.SetBytes(int64(len(nestedJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Nested
			if err := v1.Unmarshal(nestedJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("Nested/impl=v2", func(b *testing.B) {
		b.SetBytes(int64(len(nestedJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Nested
			if err := v2.Unmarshal(nestedJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})

	b.Run("Batch/impl=v1", func(b *testing.B) {
		b.SetBytes(int64(len(batchJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Batch
			if err := v1.Unmarshal(batchJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("Batch/impl=v2", func(b *testing.B) {
		b.SetBytes(int64(len(batchJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Batch
			if err := v2.Unmarshal(batchJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})

	b.Run("Document/impl=v1", func(b *testing.B) {
		b.SetBytes(int64(len(documentJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Document
			if err := v1.Unmarshal(documentJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("Document/impl=v2", func(b *testing.B) {
		b.SetBytes(int64(len(documentJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Document
			if err := v2.Unmarshal(documentJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})

	b.Run("Dynamic/impl=v1", func(b *testing.B) {
		b.SetBytes(int64(len(dynamicJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Dynamic
			if err := v1.Unmarshal(dynamicJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
	b.Run("Dynamic/impl=v2", func(b *testing.B) {
		b.SetBytes(int64(len(dynamicJSON)))
		b.ReportAllocs()
		for b.Loop() {
			var out Dynamic
			if err := v2.Unmarshal(dynamicJSON, &out); err != nil {
				b.Fatal(err)
			}
		}
	})
}

// TestCorpusSizes records the payload sizes so the results table can state what
// was actually measured.
func TestCorpusSizes(t *testing.T) {
	t.Logf("Small=%d B  Nested=%d B  Batch=%d B  Document=%d B  Dynamic=%d B",
		len(smallJSON), len(nestedJSON), len(batchJSON), len(documentJSON), len(dynamicJSON))
}

// TestBothProduceEquivalentOutput guards the comparison: if v1 and v2 disagreed
// on these fixtures, the benchmark would be measuring two different jobs.
func TestBothProduceEquivalentOutput(t *testing.T) {
	cases := []struct {
		name string
		val  any
	}{
		{"Small", fx.Small},
		{"Nested", fx.Nested},
		{"Batch", fx.Batch},
		{"Document", fx.Document},
	}
	for _, c := range cases {
		a, err := v1.Marshal(c.val)
		if err != nil {
			t.Fatalf("%s v1: %v", c.name, err)
		}
		b, err := v2.Marshal(c.val)
		if err != nil {
			t.Fatalf("%s v2: %v", c.name, err)
		}
		if string(a) != string(b) {
			t.Errorf("%s: v1 and v2 produced different JSON\n v1: %.200s\n v2: %.200s", c.name, a, b)
		}
	}
	// Dynamic is excluded: map iteration order makes byte comparison
	// meaningless, and v2 sorts map keys by default while v1 does too --
	// but the nil-slice difference inside []any would still show.
}
