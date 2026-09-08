// Package streaming shows what encoding/json/jsontext makes possible: working
// on JSON as syntax, without unmarshaling it into Go values.
//
// This is the capability v1 did not have. json.Decoder could stream tokens, but
// it could not re-emit them with the structure preserved, and json.RawMessage
// still required you to model the document well enough to know where the raw
// parts were. jsontext gives you a token reader and a token writer that share a
// syntax state machine, so a transform can be written for documents whose shape
// you do not know.
package streaming

import (
	"encoding/json/jsontext"
	"errors"
	"fmt"
	"io"
	"strings"
)

// Redact copies JSON from r to w, replacing the value of every object member
// whose name is in names with "[REDACTED]". Structure, ordering, numbers and
// every other value are preserved byte for byte.
//
// Nothing is unmarshaled: memory stays proportional to the deepest nesting
// level and the largest single scalar, not to the size of the document. That
// matters for the case this exists to serve -- a multi-megabyte telemetry or
// audit payload passing through a logging path that must not persist PII.
func Redact(r io.Reader, w io.Writer, names ...string) error {
	set := make(map[string]bool, len(names))
	for _, n := range names {
		set[n] = true
	}

	d := jsontext.NewDecoder(r)
	e := jsontext.NewEncoder(w)

	if err := transform(d, e, set); err != nil {
		return err
	}
	return nil
}

func transform(d *jsontext.Decoder, e *jsontext.Encoder, redact map[string]bool) error {
	switch d.PeekKind() {
	case jsontext.KindBeginObject:
		if _, err := d.ReadToken(); err != nil {
			return err
		}
		if err := e.WriteToken(jsontext.BeginObject); err != nil {
			return err
		}
		for d.PeekKind() != jsontext.KindEndObject {
			name, err := d.ReadToken()
			if err != nil {
				return err
			}
			key := name.String()
			if err := e.WriteToken(jsontext.String(key)); err != nil {
				return err
			}
			if redact[key] {
				// Discard the value without materialising it, and write the
				// placeholder in its place. SkipValue walks the whole subtree,
				// so redacting an object or array works the same as a scalar.
				if err := d.SkipValue(); err != nil {
					return err
				}
				if err := e.WriteToken(jsontext.String("[REDACTED]")); err != nil {
					return err
				}
				continue
			}
			if err := transform(d, e, redact); err != nil {
				return err
			}
		}
		if _, err := d.ReadToken(); err != nil {
			return err
		}
		return e.WriteToken(jsontext.EndObject)

	case jsontext.KindBeginArray:
		if _, err := d.ReadToken(); err != nil {
			return err
		}
		if err := e.WriteToken(jsontext.BeginArray); err != nil {
			return err
		}
		for d.PeekKind() != jsontext.KindEndArray {
			if err := transform(d, e, redact); err != nil {
				return err
			}
		}
		if _, err := d.ReadToken(); err != nil {
			return err
		}
		return e.WriteToken(jsontext.EndArray)

	default:
		// A scalar: copy the raw bytes through. Numbers keep their exact
		// original formatting, which a float64 round-trip would destroy.
		v, err := d.ReadValue()
		if err != nil {
			return err
		}
		return e.WriteValue(v)
	}
}

// MaxDepth walks a document and reports its deepest nesting level, reading one
// token at a time. Useful as a cheap guard in front of an unmarshal: deeply
// nested input is a well-known way to burn CPU or stack in a JSON parser.
func MaxDepth(r io.Reader) (int, error) {
	d := jsontext.NewDecoder(r)
	max := 0
	for {
		_, err := d.ReadToken()
		if errors.Is(err, io.EOF) {
			return max, nil
		}
		if err != nil {
			return max, err
		}
		if got := d.StackDepth(); got > max {
			max = got
		}
	}
}

// DescribeError extracts the JSON Pointer that v2 and jsontext attach to
// syntactic failures, so an error can say WHERE the document is wrong rather
// than only that it is.
//
// Note errors.AsType, itself new in Go 1.27: a generic replacement for the
// declare-then-errors.As dance.
func DescribeError(err error) string {
	if err == nil {
		return ""
	}
	serr, ok := errors.AsType[*jsontext.SyntacticError](err)
	if !ok {
		return err.Error()
	}
	ptr := serr.JSONPointer
	loc := string(ptr)
	if loc == "" {
		loc = "(document root)"
	}
	var b strings.Builder
	fmt.Fprintf(&b, "at %s", loc)
	if last := ptr.LastToken(); last != "" {
		fmt.Fprintf(&b, " (member %q)", last)
	}
	fmt.Fprintf(&b, ": %v", serr.Err)
	return b.String()
}
