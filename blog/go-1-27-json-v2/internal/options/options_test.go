package options

import (
	v2 "encoding/json/v2"
	"strings"
	"testing"
	"time"
)

type Payload struct {
	UserID  string        `json:"userid"`
	Timeout time.Duration `json:"timeout"`
	Tags    []string      `json:"tags"`
}

func TestLegacyProfileIsABehaviourNoOp(t *testing.T) {
	in := []byte(`{"userID":"u1","timeout":5000000000}`)

	var got Payload
	if err := v2.Unmarshal(in, &got, LegacyProfile()); err != nil {
		t.Fatalf("legacy profile should accept v1-shaped input: %v", err)
	}
	if got.UserID != "u1" {
		t.Fatalf("case-insensitive matching not restored: %+v", got)
	}
	if got.Timeout != 5*time.Second {
		t.Fatalf("duration not restored: %+v", got)
	}

	// And on the way out, nil slice marshals as null like v1.
	out, err := v2.Marshal(Payload{}, LegacyProfile())
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(out), `"tags":null`) {
		t.Fatalf("nil slice not restored: %s", out)
	}
}

func TestPlainV2RejectsWhatLegacyAccepts(t *testing.T) {
	in := []byte(`{"userID":"u1","timeout":5000000000}`)

	var got Payload
	err := v2.Unmarshal(in, &got)
	// Duration is the loud failure; the case mismatch would have been silent.
	if err == nil {
		t.Fatalf("expected plain v2 to reject time.Duration, got %+v", got)
	}
	if !strings.Contains(err.Error(), "no default representation") {
		t.Fatalf("unexpected error: %v", err)
	}
	t.Logf("plain v2: %v", err)
}

func TestInteropProfileFixesBothRealBreakages(t *testing.T) {
	in := []byte(`{"userID":"u1","timeout":5000000000}`)

	var got Payload
	if err := v2.Unmarshal(in, &got, InteropProfile()); err != nil {
		t.Fatalf("interop profile: %v", err)
	}
	if got.UserID != "u1" || got.Timeout != 5*time.Second {
		t.Fatalf("interop profile did not restore both: %+v", got)
	}

	// But it keeps v2's strictness where it counts.
	var d Payload
	if err := v2.Unmarshal([]byte(`{"userid":"a","userid":"b"}`), &d, InteropProfile()); err == nil {
		t.Fatal("interop profile should still reject duplicate names")
	}
}

func TestStrictProfileRejectsUnknownMembers(t *testing.T) {
	in := []byte(`{"userid":"u1","tags":[],"newFieldFromNewerClient":true}`)

	var got Payload
	if err := v2.Unmarshal(in, &got); err != nil {
		t.Fatalf("default v2 should ignore unknown members: %v", err)
	}

	var strict Payload
	err := v2.Unmarshal(in, &strict, StrictProfile())
	if err == nil {
		t.Fatal("strict profile should reject the unknown member")
	}
	t.Logf("strict profile: %v", err)
}

func TestSecurityProfileRejectsMalformedInput(t *testing.T) {
	var got Payload

	if err := v2.Unmarshal([]byte(`{"userid":"a","userid":"b"}`), &got, SecurityProfile()); err == nil {
		t.Fatal("want duplicate-name rejection")
	}
	if err := v2.Unmarshal([]byte("{\"userid\":\"a\xff\"}"), &got, SecurityProfile()); err == nil {
		t.Fatal("want invalid-UTF-8 rejection")
	}
}
