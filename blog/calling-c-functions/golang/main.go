// Call the C `mathlib` shared library from Go using cgo.
//
// cgo lets you write C directly in a special comment above `import "C"`. The
// #cgo directives below tell the toolchain where the header and the prebuilt
// shared library live, and bake an rpath in so the .dylib/.so is found at run
// time. ${SRCDIR} expands to this package's directory.
//
// Run:  go run .   (after building the C library: cd ../c-library && make)
package main

/*
#cgo CFLAGS: -I${SRCDIR}/../c-library/include
#cgo LDFLAGS: -L${SRCDIR}/../c-library/build -lmathlib -Wl,-rpath,${SRCDIR}/../c-library/build
#include <stdlib.h>
#include "mathlib.h"
*/
import "C"

import (
	"fmt"
	"unsafe"
)

// greet wraps the C greet()/free_string() pair so the C-owned string is freed.
func greet(name string) string {
	cName := C.CString(name) // Go string -> C char* (malloc'd by cgo)
	defer C.free(unsafe.Pointer(cName))

	ptr := C.greet(cName)
	defer C.free_string(ptr) // hand the returned buffer back to C
	return C.GoString(ptr)   // copies into a Go string
}

func main() {
	fmt.Println("== scalars ==")
	fmt.Println("add(2, 3)        =", int(C.add(2, 3)))
	fmt.Println("fibonacci(20)    =", int64(C.fibonacci(20)))

	fmt.Println("\n== strings ==")
	fmt.Println(`greet("Ada")     =`, greet("Ada"))

	fmt.Println("\n== structs & arrays ==")
	a := C.Point{x: 0.0, y: 0.0}
	b := C.Point{x: 3.0, y: 4.0}
	fmt.Println("distance(a, b)   =", float64(C.distance(a, b)))

	m := C.midpoint(a, b)
	fmt.Printf("midpoint(a, b)   = Point{x: %v, y: %v}\n", float64(m.x), float64(m.y))

	numbers := []C.int{1, 2, 3, 4, 5, 6, 7, 8, 9, 10}
	sum := C.sum_array(&numbers[0], C.size_t(len(numbers)))
	fmt.Println("sum_array(1..10) =", int64(sum))
}
