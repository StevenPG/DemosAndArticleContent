GO ?= go
export GOTOOLCHAIN = go1.27.1

BENCHTIME ?= 1s
COUNT     ?= 10

.PHONY: all test report bench benchstat vet fmt fmtcheck clean

all: vet test report

test:
	$(GO) test ./... -v

# Print the verified v1-vs-v2 difference table.
report:
	$(GO) run ./cmd/report

# Full benchmark sweep. Takes several minutes.
bench:
	mkdir -p results
	$(GO) test ./bench/ -run '^$$' -bench . -benchmem \
		-benchtime=$(BENCHTIME) -count=$(COUNT) -timeout 40m \
		| tee results/raw.txt

# Requires: go install golang.org/x/perf/cmd/benchstat@latest
benchstat: results/raw.txt
	benchstat -col /impl results/raw.txt | tee results/benchstat.txt

vet:
	$(GO) vet ./...

# A gofmt binary on PATH from an earlier Go release is fine for this project,
# but the pinned toolchain's copy is used for consistency with the sibling
# generic-methods demo.
fmt:
	$$($(GO) env GOROOT)/bin/gofmt -l -w .

fmtcheck:
	@test -z "$$($$($(GO) env GOROOT)/bin/gofmt -l .)" || { echo "gofmt needed:"; $$($(GO) env GOROOT)/bin/gofmt -l .; exit 1; }

clean:
	rm -f results/raw.txt results/benchstat.txt
