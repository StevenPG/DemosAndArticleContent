// Package limits pins down what generic methods still cannot do, by compiling
// the invalid programs in testdata and asserting on the compiler's real output.
//
// These are not paraphrased error messages. Every string quoted in the
// accompanying blog post comes from running this test.
package limits

import (
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
)

type restriction struct {
	file string
	// wantErr is a substring that must appear in the compiler's output.
	wantErr string
	// why explains the rule the case demonstrates.
	why string
}

var restrictions = []restriction{
	{
		file:    "01-generic-interface-method.txt",
		wantErr: "interface method must have no type parameters",
		why:     "An interface method may not declare type parameters. This is the wall that a generic Repository port runs into.",
	},
	{
		file:    "02-generic-method-implements-interface.txt",
		wantErr: "does not implement Store",
		why:     "A generic method does not satisfy a non-generic interface method, even when the shapes look compatible.",
	},
	{
		file:    "03-generic-method-value-in-interface.txt",
		wantErr: "without instantiation",
		why:     "An uninstantiated generic method has no single func type, so it cannot be taken as a method value. Note the compiler calls it a generic *function* here.",
	},
}

func TestRestrictionsStillApply(t *testing.T) {
	for _, r := range restrictions {
		t.Run(r.file, func(t *testing.T) {
			out, err := compile(t, r.file)
			if err == nil {
				t.Fatalf("expected a compile error, but the program built cleanly.\nrule: %s", r.why)
			}
			if !strings.Contains(out, r.wantErr) {
				t.Fatalf("compiler output did not contain %q.\nrule: %s\ngot:\n%s", r.wantErr, r.why, out)
			}
			t.Logf("%s\ncompiler says:\n%s", r.why, strings.TrimSpace(out))
		})
	}
}

// compile copies one testdata program into a throwaway module and builds it
// with the Go 1.27 toolchain, returning the combined output.
func compile(t *testing.T, name string) (string, error) {
	t.Helper()

	src, err := os.ReadFile(filepath.Join("testdata", name))
	if err != nil {
		t.Fatalf("read testdata: %v", err)
	}

	dir := t.TempDir()
	mod := "module limitscheck\n\ngo 1.27\n\ntoolchain go1.27.1\n"
	if err := os.WriteFile(filepath.Join(dir, "go.mod"), []byte(mod), 0o644); err != nil {
		t.Fatalf("write go.mod: %v", err)
	}
	if err := os.WriteFile(filepath.Join(dir, "p.go"), src, 0o644); err != nil {
		t.Fatalf("write source: %v", err)
	}

	cmd := exec.Command("go", "build", "./...")
	cmd.Dir = dir
	cmd.Env = append(os.Environ(), "GOTOOLCHAIN=go1.27.1", "GOFLAGS=")
	out, err := cmd.CombinedOutput()
	return string(out), err
}
