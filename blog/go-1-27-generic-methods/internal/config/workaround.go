package config

import "fmt"

// The pre-1.27 escape hatch: a package-level generic FUNCTION that takes the
// receiver as its first argument. Generics existed since 1.18 -- what did not
// exist was the ability to hang them off a method.
//
// This works, and plenty of libraries shipped it. What it costs:
//
//   - It reads backwards. The subject of the sentence is the key, but the
//     receiver has been demoted to an argument: Lookup[int](src, "port")
//     instead of src.Get[int]("port").
//   - It is undiscoverable. Typing "src." in an editor lists the method set,
//     and this helper is not in it. Callers find it by reading docs, not by
//     autocompletion -- which is how most people actually learn an API.
//   - It cannot be part of a fluent chain, because it is not a method.
//   - Every such helper competes for names at package scope. This one has to be
//     called Lookup rather than Get, because Get is more useful as a method.
func Lookup[T any](s *Source, key string) (T, error) {
	var zero T
	v, err := s.lookup(key)
	if err != nil {
		return zero, err
	}
	t, ok := v.(T)
	if !ok {
		return zero, fmt.Errorf("config: key %q is %T, want %T", key, v, zero)
	}
	return t, nil
}
