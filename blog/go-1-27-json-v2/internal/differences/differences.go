// Package differences catalogues, and verifies at test time, every behavioural
// difference between encoding/json (v1) and encoding/json/v2 that this project
// found by running both against the same input on Go 1.27.1.
//
// Nothing here is quoted from release notes. Each case runs both packages and
// records what actually happened, so the table in the accompanying blog post is
// generated rather than transcribed.
package differences

import (
	v1 "encoding/json"
	"encoding/json/jsontext"
	v2 "encoding/json/v2"
	"time"
)

type Doc struct {
	Name string `json:"name"`
}

type Container struct {
	Items []string       `json:"items"`
	Attrs map[string]int `json:"attrs"`
}

type Timed struct {
	Timeout time.Duration `json:"timeout"`
}

// Case is one observed difference.
type Case struct {
	// Name is the short label used in the report.
	Name string
	// Why explains the practical consequence during a migration.
	Why string
	// Severity ranks how likely this is to bite silently.
	//   "error"  -- v2 fails loudly; you find out at once.
	//   "silent" -- v2 succeeds but produces different data. The dangerous kind.
	Severity string
	// V1 and V2 run the same operation under each package.
	V1 func() (string, error)
	V2 func() (string, error)
	// Pin runs the v2 path with the option(s) that restore v1 behaviour.
	Pin func() (string, error)
	// PinDesc names those options.
	PinDesc string
}

func str(b []byte, err error) (string, error) {
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func unmarshalDoc(fn func(*Doc) error) func() (string, error) {
	return func() (string, error) {
		var d Doc
		if err := fn(&d); err != nil {
			return "", err
		}
		return "Name=" + quote(d.Name), nil
	}
}

func quote(s string) string {
	if s == "" {
		return `""`
	}
	return `"` + s + `"`
}

// Cases returns every difference this project verifies.
func Cases() []Case {
	badUTF8 := Doc{Name: "caf\xff"}
	badUTF8In := []byte("{\"name\":\"caf\xff\"}")
	dupIn := []byte(`{"name":"first","name":"second"}`)
	mixedCaseIn := []byte(`{"NAME":"ada"}`)
	var emptyContainer Container
	timed := Timed{Timeout: 90 * time.Second}

	return []Case{
		{
			Name:     "invalid UTF-8 (marshal)",
			Why:      "v1 silently replaces invalid bytes with U+FFFD, producing JSON that does not round-trip. v2 refuses.",
			Severity: "error",
			V1:       func() (string, error) { return str(v1.Marshal(badUTF8)) },
			V2:       func() (string, error) { return str(v2.Marshal(badUTF8)) },
			Pin:      func() (string, error) { return str(v2.Marshal(badUTF8, jsontext.AllowInvalidUTF8(true))) },
			PinDesc:  "jsontext.AllowInvalidUTF8(true)",
		},
		{
			Name:     "invalid UTF-8 (unmarshal)",
			Why:      "Same corruption on the way in. RFC 8259 requires UTF-8; v1 accepted anything.",
			Severity: "error",
			V1:       unmarshalDoc(func(d *Doc) error { return v1.Unmarshal(badUTF8In, d) }),
			V2:       unmarshalDoc(func(d *Doc) error { return v2.Unmarshal(badUTF8In, d) }),
			Pin: unmarshalDoc(func(d *Doc) error {
				return v2.Unmarshal(badUTF8In, d, jsontext.AllowInvalidUTF8(true))
			}),
			PinDesc: "jsontext.AllowInvalidUTF8(true)",
		},
		{
			Name:     "duplicate object names",
			Why:      "v1 takes the last occurrence. Two parsers disagreeing on which value wins is a classic request-smuggling and auth-bypass primitive, so rejecting is the safer default.",
			Severity: "error",
			V1:       unmarshalDoc(func(d *Doc) error { return v1.Unmarshal(dupIn, d) }),
			V2:       unmarshalDoc(func(d *Doc) error { return v2.Unmarshal(dupIn, d) }),
			Pin: unmarshalDoc(func(d *Doc) error {
				return v2.Unmarshal(dupIn, d, jsontext.AllowDuplicateNames(true))
			}),
			PinDesc: "jsontext.AllowDuplicateNames(true)",
		},
		{
			Name:     "case-insensitive field matching",
			Why:      "THE ONE TO WORRY ABOUT. v1 matched object names case-insensitively as a fallback; v2 is case-sensitive. No error is raised -- the field is simply left at its zero value. A payload that worked yesterday silently loses data.",
			Severity: "silent",
			V1:       unmarshalDoc(func(d *Doc) error { return v1.Unmarshal(mixedCaseIn, d) }),
			V2:       unmarshalDoc(func(d *Doc) error { return v2.Unmarshal(mixedCaseIn, d) }),
			Pin: unmarshalDoc(func(d *Doc) error {
				return v2.Unmarshal(mixedCaseIn, d, v2.MatchCaseInsensitiveNames(true))
			}),
			PinDesc: "v2.MatchCaseInsensitiveNames(true)",
		},
		{
			Name:     "nil slice and nil map",
			Why:      "v1 emits null; v2 emits [] and {}. Consumers that distinguish null from empty -- JSON Schema validators, strongly typed clients, diff-based change detection -- will see this.",
			Severity: "silent",
			V1:       func() (string, error) { return str(v1.Marshal(emptyContainer)) },
			V2:       func() (string, error) { return str(v2.Marshal(emptyContainer)) },
			Pin: func() (string, error) {
				return str(v2.Marshal(emptyContainer, v2.FormatNilSliceAsNull(true), v2.FormatNilMapAsNull(true)))
			},
			PinDesc: "v2.FormatNilSliceAsNull(true), v2.FormatNilMapAsNull(true)",
		},
		{
			Name:     "time.Duration",
			Why:      "The sharpest edge. v1 emitted the nanosecond count because time.Duration is an int64. v2 refuses to guess a representation and errors outright, so any struct carrying a Duration fails to marshal under plain v2.",
			Severity: "error",
			V1:       func() (string, error) { return str(v1.Marshal(timed)) },
			V2:       func() (string, error) { return str(v2.Marshal(timed)) },
			Pin:      func() (string, error) { return str(v2.Marshal(timed, v1.FormatDurationAsNano(true))) },
			PinDesc:  "json.FormatDurationAsNano(true)  // from encoding/json",
		},
	}
}
