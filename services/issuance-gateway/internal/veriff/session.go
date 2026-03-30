package veriff

// Session represents a Veriff verification session webhook payload.
type Session struct {
	SessionID string `json:"session_id"`
	Status    string `json:"status"`
	Person    struct {
		FirstName   string  `json:"firstName"`
		LastName    string  `json:"lastName"`
		DateOfBirth string  `json:"dateOfBirth"`
		Confidence  float64 `json:"confidence,omitempty"`
	} `json:"person"`
	Document struct {
		Number       string  `json:"number"`
		Type         string  `json:"type"`
		Country      string  `json:"country"`
		Authenticity float64 `json:"authenticity,omitempty"`
	} `json:"document"`
	Verification struct {
		LivenessScore     float64 `json:"livenessScore,omitempty"`
		OverallConfidence float64 `json:"overallConfidence,omitempty"`
		RiskScore         float64 `json:"riskScore,omitempty"`
		Timestamp         string  `json:"timestamp,omitempty"`
	} `json:"verification,omitempty"`
}

// ValidationResult holds the outcome of session quality validation.
type ValidationResult struct {
	IsValid      bool    `json:"isValid"`
	Reason       string  `json:"reason,omitempty"`
	QualityLevel string  `json:"qualityLevel"`
	Confidence   float64 `json:"confidence"`
}

// Verification level constants.
const (
	LevelBasic    = "basic"
	LevelStandard = "standard"
	LevelPremium  = "premium"
	LevelGold     = "gold"
)

// ValidateSession performs quality validation on a Veriff session.
func ValidateSession(s Session) ValidationResult {
	if s.Status != "approved" {
		return ValidationResult{
			IsValid:      false,
			Reason:       "session not approved",
			QualityLevel: "none",
		}
	}

	confidence := s.Verification.OverallConfidence
	if confidence == 0.0 && s.Person.Confidence > 0 {
		confidence = s.Person.Confidence
	}
	if confidence == 0.0 {
		confidence = 0.85 // default for approved sessions without metrics
	}

	var level string
	switch {
	case confidence >= 0.95 && s.Verification.LivenessScore >= 0.90 && s.Document.Authenticity >= 0.95:
		level = LevelGold
	case confidence >= 0.90 && s.Verification.LivenessScore >= 0.85:
		level = LevelPremium
	case confidence >= 0.80:
		level = LevelStandard
	default:
		level = LevelBasic
	}

	if s.Verification.RiskScore > 0.3 {
		return ValidationResult{
			IsValid:      false,
			Reason:       "high risk score",
			QualityLevel: level,
			Confidence:   confidence,
		}
	}

	if s.Verification.LivenessScore > 0 && s.Verification.LivenessScore < 0.7 {
		return ValidationResult{
			IsValid:      false,
			Reason:       "liveness check insufficient",
			QualityLevel: level,
			Confidence:   confidence,
		}
	}

	return ValidationResult{
		IsValid:      true,
		QualityLevel: level,
		Confidence:   confidence,
	}
}
