// Package config demonstrates the typed-accessor problem that generic methods
// solve. This file is the pre-Go 1.27 shape: because a method could not declare
// its own type parameters, every supported type needed its own method.
//
// This is not a strawman. The same shape appears in:
//
//   - flag.FlagSet, which carries 16 methods for this reason alone
//     (Bool/BoolVar, Int/IntVar, Int64/Int64Var, Uint/UintVar, Uint64/Uint64Var,
//     Float64/Float64Var, String/StringVar, Duration/DurationVar).
//   - github.com/spf13/viper, which exposes 20 typed Get* accessors
//     (GetString, GetInt, GetInt32, GetInt64, GetUint, GetUint8, GetUint16,
//     GetUint32, GetUint64, GetFloat64, GetBool, GetDuration, GetTime,
//     GetIntSlice, GetStringSlice, GetStringMap, ...).
package config

import (
	"fmt"
	"time"
)

// Source is a resolved configuration tree: the merged result of a file, env
// vars and defaults. Values arrive as any because the file format (JSON, YAML,
// TOML) does not know the Go types the program wants.
type Source struct {
	raw map[string]any
}

func New(raw map[string]any) *Source { return &Source{raw: raw} }

// lookup is the shared logic every accessor below has to call. Note how little
// of each accessor is actually about its type.
func (s *Source) lookup(key string) (any, error) {
	v, ok := s.raw[key]
	if !ok {
		return nil, fmt.Errorf("config: missing key %q", key)
	}
	return v, nil
}

// --- The method family. Sixteen methods, one real idea. ---

func (s *Source) GetString(key string) (string, error) {
	v, err := s.lookup(key)
	if err != nil {
		return "", err
	}
	t, ok := v.(string)
	if !ok {
		return "", fmt.Errorf("config: key %q is %T, want string", key, v)
	}
	return t, nil
}

func (s *Source) GetInt(key string) (int, error) {
	v, err := s.lookup(key)
	if err != nil {
		return 0, err
	}
	t, ok := v.(int)
	if !ok {
		return 0, fmt.Errorf("config: key %q is %T, want int", key, v)
	}
	return t, nil
}

func (s *Source) GetInt64(key string) (int64, error) {
	v, err := s.lookup(key)
	if err != nil {
		return 0, err
	}
	t, ok := v.(int64)
	if !ok {
		return 0, fmt.Errorf("config: key %q is %T, want int64", key, v)
	}
	return t, nil
}

func (s *Source) GetBool(key string) (bool, error) {
	v, err := s.lookup(key)
	if err != nil {
		return false, err
	}
	t, ok := v.(bool)
	if !ok {
		return false, fmt.Errorf("config: key %q is %T, want bool", key, v)
	}
	return t, nil
}

func (s *Source) GetFloat64(key string) (float64, error) {
	v, err := s.lookup(key)
	if err != nil {
		return 0, err
	}
	t, ok := v.(float64)
	if !ok {
		return 0, fmt.Errorf("config: key %q is %T, want float64", key, v)
	}
	return t, nil
}

func (s *Source) GetDuration(key string) (time.Duration, error) {
	v, err := s.lookup(key)
	if err != nil {
		return 0, err
	}
	t, ok := v.(time.Duration)
	if !ok {
		return 0, fmt.Errorf("config: key %q is %T, want time.Duration", key, v)
	}
	return t, nil
}

func (s *Source) GetStringSlice(key string) ([]string, error) {
	v, err := s.lookup(key)
	if err != nil {
		return nil, err
	}
	t, ok := v.([]string)
	if !ok {
		return nil, fmt.Errorf("config: key %q is %T, want []string", key, v)
	}
	return t, nil
}

// ...and a GetX + GetXOr pair for every further type the program needs.
//
// Two costs that are easy to miss:
//
//  1. The list is closed. A caller with its own named type -- say a
//     retry.Policy or a units.Bytes -- cannot use this API at all without
//     editing this package.
//  2. Every entry duplicates the missing-key and wrong-type error strings, so
//     they drift. Two of the accessors above already say "want int" style while
//     the rest spell the full type; that drift is realistic, not a typo.

func (s *Source) GetStringOr(key string, def string) string {
	v, err := s.GetString(key)
	if err != nil {
		return def
	}
	return v
}

func (s *Source) GetIntOr(key string, def int) int {
	v, err := s.GetInt(key)
	if err != nil {
		return def
	}
	return v
}

func (s *Source) GetDurationOr(key string, def time.Duration) time.Duration {
	v, err := s.GetDuration(key)
	if err != nil {
		return def
	}
	return v
}

// ...and so on, doubling the family.
