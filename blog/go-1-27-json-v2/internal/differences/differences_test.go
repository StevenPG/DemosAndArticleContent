package differences

import (
	v1 "encoding/json"
	v2 "encoding/json/v2"
	"strings"
	"testing"
	"time"
)

// TestEveryCaseActuallyDiffers is the guard on the whole catalogue: if a future
// Go release changes one of these behaviours, this test fails rather than the
// blog post quietly becoming wrong.
func TestEveryCaseActuallyDiffers(t *testing.T) {
	for _, c := range Cases() {
		t.Run(c.Name, func(t *testing.T) {
			got1, err1 := c.V1()
			got2, err2 := c.V2()

			r1 := render(got1, err1)
			r2 := render(got2, err2)
			if r1 == r2 {
				t.Fatalf("v1 and v2 agree (%s); this case no longer documents a difference", r1)
			}

			// The pinned form must reproduce v1 exactly.
			gotPin, errPin := c.Pin()
			rPin := render(gotPin, errPin)
			if rPin != r1 {
				t.Fatalf("pinning did not restore v1 behaviour\n  v1:     %s\n  pinned: %s\n  via:    %s", r1, rPin, c.PinDesc)
			}

			// Severity must match what actually happened.
			switch c.Severity {
			case "error":
				if err2 == nil {
					t.Fatalf("severity %q but v2 returned no error: %s", c.Severity, r2)
				}
			case "silent":
				if err2 != nil {
					t.Fatalf("severity %q but v2 errored: %v", c.Severity, err2)
				}
			default:
				t.Fatalf("unknown severity %q", c.Severity)
			}

			t.Logf("\n  v1:     %s\n  v2:     %s\n  pinned: %s  [%s]", r1, r2, rPin, c.PinDesc)
		})
	}
}

func render(s string, err error) string {
	if err != nil {
		return "ERROR: " + err.Error()
	}
	return s
}

// The headline compatibility claim: the v1 package's own behaviour is
// unchanged in 1.27, even though v2 is the engine underneath.
func TestV1PackageBehaviourUnchanged(t *testing.T) {
	// Every one of these was v1 behaviour before 1.27 and must remain so.
	var d Doc
	if err := v1.Unmarshal([]byte(`{"NAME":"ada"}`), &d); err != nil || d.Name != "ada" {
		t.Fatalf("v1 lost case-insensitive matching: %+v %v", d, err)
	}

	var d2 Doc
	if err := v1.Unmarshal([]byte(`{"name":"a","name":"b"}`), &d2); err != nil || d2.Name != "b" {
		t.Fatalf("v1 lost last-wins duplicate handling: %+v %v", d2, err)
	}

	out, err := v1.Marshal(Container{})
	if err != nil || string(out) != `{"items":null,"attrs":null}` {
		t.Fatalf("v1 nil slice/map changed: %s %v", out, err)
	}

	out, err = v1.Marshal(Timed{Timeout: 90 * time.Second})
	if err != nil || string(out) != `{"timeout":90000000000}` {
		t.Fatalf("v1 Duration encoding changed: %s %v", out, err)
	}
}

// DefaultOptionsV1 restores the entire v1 profile through the v2 entry points
// in a single option -- the practical migration lever.
func TestDefaultOptionsV1RestoresEverything(t *testing.T) {
	profile := v1.DefaultOptionsV1()

	var d Doc
	if err := v2.Unmarshal([]byte(`{"NAME":"ada"}`), &d, profile); err != nil || d.Name != "ada" {
		t.Fatalf("case-insensitive not restored: %+v %v", d, err)
	}

	var d2 Doc
	if err := v2.Unmarshal([]byte(`{"name":"a","name":"b"}`), &d2, profile); err != nil || d2.Name != "b" {
		t.Fatalf("duplicates not restored: %+v %v", d2, err)
	}

	out, err := v2.Marshal(Container{}, profile)
	if err != nil || string(out) != `{"items":null,"attrs":null}` {
		t.Fatalf("nil handling not restored: %s %v", out, err)
	}

	out, err = v2.Marshal(Timed{Timeout: 90 * time.Second}, profile)
	if err != nil || string(out) != `{"timeout":90000000000}` {
		t.Fatalf("Duration not restored: %s %v", out, err)
	}
}

// Unknown members are ignored by BOTH by default; v2 merely offers a way to
// reject them. Included so the post does not claim a difference that is not one.
func TestUnknownMembersAreNotADifference(t *testing.T) {
	in := []byte(`{"name":"ada","extra":1}`)

	var a Doc
	if err := v1.Unmarshal(in, &a); err != nil || a.Name != "ada" {
		t.Fatalf("v1: %+v %v", a, err)
	}
	var b Doc
	if err := v2.Unmarshal(in, &b); err != nil || b.Name != "ada" {
		t.Fatalf("v2 default should also ignore unknown members: %+v %v", b, err)
	}

	var c Doc
	err := v2.Unmarshal(in, &c, v2.RejectUnknownMembers(true))
	if err == nil {
		t.Fatal("RejectUnknownMembers(true) should error")
	}
	if !strings.Contains(err.Error(), "unknown object member name") {
		t.Fatalf("unexpected error text: %v", err)
	}
	t.Logf("opt-in strictness: %v", err)
}
