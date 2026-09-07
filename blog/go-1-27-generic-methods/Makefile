GO ?= go
export GOTOOLCHAIN = go1.27.1

.PHONY: all test demo vet fmt

all: vet test demo

test:
	$(GO) test ./... -v

demo:
	$(GO) run ./cmd/demo

vet:
	$(GO) vet ./...

# gofmt on PATH may predate Go 1.27 and cannot parse generic methods, so use
# the pinned toolchain's own copy.
fmt:
	$$($(GO) env GOROOT)/bin/gofmt -l -w .

fmtcheck:
	@test -z "$$($$($(GO) env GOROOT)/bin/gofmt -l .)" || { echo "gofmt needed:"; $$($(GO) env GOROOT)/bin/gofmt -l .; exit 1; }
