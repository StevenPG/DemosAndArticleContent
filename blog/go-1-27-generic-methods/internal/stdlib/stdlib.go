// Package stdlib grounds the argument in the standard library itself, rather
// than in invented examples.
package stdlib

import (
	"flag"
	"reflect"
	"strings"
)

// TypedFlagMethods returns the flag.FlagSet methods that exist purely because
// a method could not declare a type parameter before Go 1.27.
//
// Each is a per-type copy of one idea: "define a flag of type X". With generic
// methods the whole family reduces to two methods:
//
//	func (f *FlagSet) Of[T FlagType](name string, value T, usage string) *T
//	func (f *FlagSet) VarOf[T FlagType](p *T, name string, value T, usage string)
//
// (This is illustrative. The real flag package cannot change: its API is
// covered by the Go 1 compatibility promise, so the family is permanent.)
func TypedFlagMethods() []string {
	families := []string{
		"Bool", "Int", "Int64", "Uint", "Uint64", "Float64", "String", "Duration",
	}
	t := reflect.TypeOf(&flag.FlagSet{})

	var found []string
	for _, base := range families {
		for _, name := range []string{base, base + "Var"} {
			if _, ok := t.MethodByName(name); ok {
				found = append(found, name)
			}
		}
	}
	return found
}

// ViperTypedAccessors lists the typed Get* accessors on github.com/spf13/viper's
// Viper type as of v1.x. Recorded as data rather than imported, so this module
// stays dependency-free.
//
// Source: https://pkg.go.dev/github.com/spf13/viper
func ViperTypedAccessors() []string {
	return []string{
		"GetBool", "GetDuration", "GetFloat64", "GetInt", "GetInt32", "GetInt64",
		"GetIntSlice", "GetSizeInBytes", "GetString", "GetStringMap",
		"GetStringMapString", "GetStringMapStringSlice", "GetStringSlice",
		"GetTime", "GetUint", "GetUint8", "GetUint16", "GetUint32", "GetUint64",
	}
}

// AnyReturningStdlibMethods records standard-library methods that return any
// because the method could not be generic. These are the APIs that would look
// different if generic methods had existed in Go 1.0.
func AnyReturningStdlibMethods() []string {
	return []string{
		"sync.Map.Load", "sync.Map.LoadOrStore", "sync.Map.LoadAndDelete",
		"sync.Map.Swap", "sync/atomic.Value.Load", "sync/atomic.Value.Swap",
		"context.Context.Value",
	}
}

// Summary renders a one-line tally used by cmd/demo.
func Summary() string {
	var b strings.Builder
	b.WriteString("flag.FlagSet typed methods: ")
	b.WriteString(strings.Join(TypedFlagMethods(), ", "))
	return b.String()
}
