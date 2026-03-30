package credential

import "testing"

func TestCalculateAge(t *testing.T) {
	tests := []struct {
		name string
		dob  string
		want int // approximate — we just check it's reasonable
	}{
		{"valid date", "1990-01-15", 36},
		{"invalid format", "not-a-date", 0},
		{"empty", "", 0},
		{"leap year birthday", "2000-02-29", 26},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got := CalculateAge(tt.dob)
			// Allow ±1 year tolerance since test may run near a birthday
			if tt.want > 0 && (got < tt.want-1 || got > tt.want+1) {
				t.Errorf("CalculateAge(%q) = %d, want ~%d", tt.dob, got, tt.want)
			}
			if tt.want == 0 && got != 0 {
				t.Errorf("CalculateAge(%q) = %d, want 0", tt.dob, got)
			}
		})
	}
}
