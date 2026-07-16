package orders

import "testing"

// Validation is plain code, so the test is plain too: table-driven, stdlib
// only — the Go counterpart of testing Bean Validation annotations.
func TestCreateOrderRequestValidate(t *testing.T) {
	valid := CreateOrderRequest{
		CustomerEmail: "jane@example.com",
		Item:          "widget",
		Quantity:      2,
		TotalCents:    1999,
	}

	tests := []struct {
		name      string
		mutate    func(*CreateOrderRequest)
		wantField string
	}{
		{"valid request", func(r *CreateOrderRequest) {}, ""},
		{"blank email", func(r *CreateOrderRequest) { r.CustomerEmail = "" }, "customerEmail"},
		{"malformed email", func(r *CreateOrderRequest) { r.CustomerEmail = "not-an-email" }, "customerEmail"},
		{"blank item", func(r *CreateOrderRequest) { r.Item = "" }, "item"},
		{"zero quantity", func(r *CreateOrderRequest) { r.Quantity = 0 }, "quantity"},
		{"excessive quantity", func(r *CreateOrderRequest) { r.Quantity = 1001 }, "quantity"},
		{"non-positive total", func(r *CreateOrderRequest) { r.TotalCents = 0 }, "totalCents"},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := valid
			tt.mutate(&req)
			errs := req.Validate()
			if tt.wantField == "" {
				if len(errs) != 0 {
					t.Fatalf("expected no errors, got %v", errs)
				}
				return
			}
			if _, ok := errs[tt.wantField]; !ok {
				t.Fatalf("expected error on %q, got %v", tt.wantField, errs)
			}
		})
	}
}
