package veriff

import "testing"

func TestValidateSession(t *testing.T) {
	tests := []struct {
		name       string
		session    Session
		wantValid  bool
		wantLevel  string
		wantReason string
	}{
		{
			name:      "gold quality",
			session:   makeSession("approved", 0.97, 0.95, 0.98, 0.02),
			wantValid: true, wantLevel: LevelGold,
		},
		{
			name:      "premium quality",
			session:   makeSession("approved", 0.92, 0.87, 0.80, 0.05),
			wantValid: true, wantLevel: LevelPremium,
		},
		{
			name:      "standard quality",
			session:   makeSession("approved", 0.85, 0.75, 0.80, 0.05),
			wantValid: true, wantLevel: LevelStandard,
		},
		{
			name:      "basic quality",
			session:   makeSession("approved", 0.70, 0.75, 0.60, 0.05),
			wantValid: true, wantLevel: LevelBasic,
		},
		{
			name:       "declined session",
			session:    makeSession("declined", 0.5, 0.5, 0.5, 0.5),
			wantValid:  false,
			wantReason: "session not approved",
		},
		{
			name:       "high risk score",
			session:    makeSession("approved", 0.95, 0.95, 0.95, 0.5),
			wantValid:  false,
			wantReason: "high risk score",
		},
		{
			name:       "low liveness",
			session:    makeSession("approved", 0.90, 0.50, 0.90, 0.05),
			wantValid:  false,
			wantReason: "liveness check insufficient",
		},
		{
			name:      "boundary: confidence exactly 0.80",
			session:   makeSession("approved", 0.80, 0.75, 0.80, 0.05),
			wantValid: true, wantLevel: LevelStandard,
		},
		{
			name:      "boundary: confidence exactly 0.90 with liveness 0.85",
			session:   makeSession("approved", 0.90, 0.85, 0.80, 0.05),
			wantValid: true, wantLevel: LevelPremium,
		},
		{
			name:      "no metrics defaults to 0.85 confidence",
			session:   makeSession("approved", 0, 0, 0, 0),
			wantValid: true, wantLevel: LevelStandard,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ValidateSession(tt.session)
			if result.IsValid != tt.wantValid {
				t.Errorf("IsValid = %v, want %v", result.IsValid, tt.wantValid)
			}
			if tt.wantLevel != "" && result.QualityLevel != tt.wantLevel {
				t.Errorf("QualityLevel = %q, want %q", result.QualityLevel, tt.wantLevel)
			}
			if tt.wantReason != "" && result.Reason != tt.wantReason {
				t.Errorf("Reason = %q, want %q", result.Reason, tt.wantReason)
			}
		})
	}
}

func makeSession(status string, confidence, liveness, authenticity, risk float64) Session {
	s := Session{
		SessionID: "test-session",
		Status:    status,
	}
	s.Verification.OverallConfidence = confidence
	s.Verification.LivenessScore = liveness
	s.Verification.RiskScore = risk
	s.Document.Authenticity = authenticity
	return s
}
