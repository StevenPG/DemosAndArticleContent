package streaming

import (
	v2 "encoding/json/v2"
	"strings"
	"testing"
)

const telemetry = `{
  "vehicle": "N724PG",
  "operator": {"name": "Ada Lovelace", "email": "ada@example.com", "badge": 1815},
  "samples": [
    {"t": 0.000, "alt_m": 1524.0, "pilot_note": "wheels up"},
    {"t": 0.250, "alt_m": 1531.5, "pilot_note": "nominal"}
  ],
  "raw_precision": 1.7976931348623157e308,
  "big_int": 123456789012345678901234567890
}`

func TestRedactPreservesStructure(t *testing.T) {
	var out strings.Builder
	if err := Redact(strings.NewReader(telemetry), &out, "email", "pilot_note"); err != nil {
		t.Fatalf("Redact: %v", err)
	}
	got := out.String()

	if strings.Contains(got, "ada@example.com") {
		t.Fatal("email survived redaction")
	}
	if strings.Contains(got, "wheels up") || strings.Contains(got, "nominal") {
		t.Fatal("pilot_note survived redaction")
	}
	if strings.Count(got, "[REDACTED]") != 3 {
		t.Fatalf("want 3 redactions, got %d in:\n%s", strings.Count(got, "[REDACTED]"), got)
	}

	// Everything else is untouched.
	for _, keep := range []string{"N724PG", "Ada Lovelace", "1815", "1524.0", "1531.5"} {
		if !strings.Contains(got, keep) {
			t.Fatalf("lost %q from output:\n%s", keep, got)
		}
	}

	// The output is still valid JSON.
	var check map[string]any
	if err := v2.Unmarshal([]byte(got), &check); err != nil {
		t.Fatalf("redacted output is not valid JSON: %v\n%s", err, got)
	}
	t.Logf("redacted:\n%s", got)
}

// The property that makes the token layer worth using: numbers are copied as
// raw text, so no precision is lost. Unmarshal-then-remarshal through
// map[string]any would round every number through float64 and destroy both of
// these values.
func TestNumericPrecisionPreserved(t *testing.T) {
	var out strings.Builder
	if err := Redact(strings.NewReader(telemetry), &out, "email"); err != nil {
		t.Fatal(err)
	}
	got := out.String()

	if !strings.Contains(got, "1.7976931348623157e308") {
		t.Fatalf("float precision lost:\n%s", got)
	}
	if !strings.Contains(got, "123456789012345678901234567890") {
		t.Fatalf("big integer lost:\n%s", got)
	}

	// Contrast: the round-trip through Go values that people usually write.
	var viaValues map[string]any
	if err := v2.Unmarshal([]byte(telemetry), &viaValues); err != nil {
		t.Fatal(err)
	}
	reencoded, err := v2.Marshal(viaValues)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(reencoded), "123456789012345678901234567890") {
		t.Log("note: big integer survived the value round-trip on this build")
	} else {
		t.Logf("value round-trip mangled the big integer, as expected:\n%s", reencoded)
	}
}

// Redacting a whole subtree works the same as a scalar, because SkipValue
// walks the entire value.
func TestRedactWholeSubtree(t *testing.T) {
	var out strings.Builder
	if err := Redact(strings.NewReader(telemetry), &out, "operator", "samples"); err != nil {
		t.Fatal(err)
	}
	got := out.String()

	if strings.Contains(got, "Ada Lovelace") || strings.Contains(got, "alt_m") {
		t.Fatalf("subtree survived:\n%s", got)
	}
	if !strings.Contains(got, "N724PG") {
		t.Fatalf("sibling data lost:\n%s", got)
	}
	t.Logf("subtree-redacted:\n%s", got)
}

func TestMaxDepth(t *testing.T) {
	cases := map[string]int{
		`1`:                 0,
		`{"a":1}`:           1,
		`{"a":{"b":1}}`:     2,
		`{"a":[{"b":[1]}]}`: 4,
	}
	for in, want := range cases {
		got, err := MaxDepth(strings.NewReader(in))
		if err != nil {
			t.Fatalf("%s: %v", in, err)
		}
		if got != want {
			t.Errorf("MaxDepth(%s) = %d, want %d", in, got, want)
		}
	}
}

// v2 errors carry a JSON Pointer to the exact location of the problem, which
// v1 errors did not.
func TestErrorsCarryJSONPointer(t *testing.T) {
	type Doc struct {
		Items []struct {
			Name string `json:"name"`
		} `json:"items"`
	}

	// A duplicate name buried inside the second array element.
	in := []byte(`{"items":[{"name":"a"},{"name":"b","name":"c"}]}`)

	var d Doc
	err := v2.Unmarshal(in, &d)
	if err == nil {
		t.Fatal("expected duplicate-name error")
	}

	desc := DescribeError(err)
	if !strings.Contains(desc, "/items/1") {
		t.Fatalf("error did not locate the fault; got: %s", desc)
	}
	t.Logf("located: %s", desc)
	t.Logf("raw:     %v", err)
}
