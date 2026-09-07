// Package options covers the migration lever: how to adopt encoding/json/v2
// without changing behaviour on day one, and how to tighten it deliberately
// afterwards.
//
// The key fact for anyone upgrading to Go 1.27: encoding/json is now
// implemented on top of v2, but its BEHAVIOUR is unchanged. v1 semantics are
// preserved through options that the v1 package applies for you. You inherit
// the new implementation automatically; you do not inherit the new semantics
// unless you import encoding/json/v2 yourself.
package options

import (
	v1 "encoding/json"
	"encoding/json/jsontext"
	v2 "encoding/json/v2"
)

// LegacyProfile is the single option that makes v2 behave like v1 across the
// board. Use it when moving a codebase to the v2 API mechanically, so the
// import change and the behaviour change land in separate commits.
func LegacyProfile() v2.Options {
	return v1.DefaultOptionsV1()
}

// StrictProfile is the opposite end: everything v2 offers, turned up.
//
// RejectUnknownMembers is not on by default in v2, and turning it on is a
// compatibility decision, not a safety one -- it makes your service reject
// payloads from a newer client that added a field. Appropriate for internal
// APIs with lockstep deploys; usually wrong for public ones.
func StrictProfile() v2.Options {
	return v2.JoinOptions(
		v2.RejectUnknownMembers(true),
		v2.Deterministic(true),
	)
}

// InteropProfile is the one worth reaching for in most services: v2's strict
// defaults, with the two v1 behaviours restored that tend to break real
// traffic rather than indicate a bug.
//
//   - MatchCaseInsensitiveNames: restores v1's fallback matching. Without it,
//     a producer sending "userId" against a `json:"userid"` tag silently
//     yields a zero value.
//   - FormatDurationAsNano: v2 refuses to marshal time.Duration at all, so any
//     struct carrying one fails outright without this.
func InteropProfile() v2.Options {
	return v2.JoinOptions(
		v2.MatchCaseInsensitiveNames(true),
		v1.FormatDurationAsNano(true),
	)
}

// SecurityProfile keeps v2's strict parsing but tolerates nothing extra. It is
// the profile to use on an untrusted boundary.
//
// Both of these are already v2 defaults; they are named explicitly here
// because a profile that relies on defaults is a profile that changes when the
// defaults do.
func SecurityProfile() v2.Options {
	return v2.JoinOptions(
		jsontext.AllowDuplicateNames(false),
		jsontext.AllowInvalidUTF8(false),
	)
}
