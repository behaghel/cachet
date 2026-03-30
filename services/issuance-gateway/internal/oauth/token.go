package oauth

import (
	"crypto/rsa"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/golang-jwt/jwt/v5"
	"github.com/google/uuid"

	"github.com/cachet-id/cachet/generated/go/models"
)

// IssueToken creates a signed JWT access token.
func IssueToken(signingKey *rsa.PrivateKey, clientID, scope string) (models.TokenResponse, error) {
	now := time.Now()
	expiresAt := now.Add(time.Hour)

	claims := jwt.MapClaims{
		"sub":       clientID,
		"client_id": clientID,
		"scope":     scope,
		"iat":       now.Unix(),
		"exp":       expiresAt.Unix(),
		"jti":       uuid.New().String(),
	}

	token := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	accessToken, err := token.SignedString(signingKey)
	if err != nil {
		return models.TokenResponse{}, fmt.Errorf("sign token: %w", err)
	}

	return models.TokenResponse{
		AccessToken: accessToken,
		TokenType:   models.Bearer,
		ExpiresIn:   3600,
		Scope:       scope,
	}, nil
}

// ValidateBearer extracts and validates a JWT bearer token from the Authorization header.
func ValidateBearer(r *http.Request, publicKey *rsa.PublicKey) (*jwt.Token, error) {
	authHeader := r.Header.Get("Authorization")
	if !strings.HasPrefix(authHeader, "Bearer ") {
		return nil, fmt.Errorf("missing or invalid authorization header")
	}

	tokenString := strings.TrimPrefix(authHeader, "Bearer ")
	token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
		if _, ok := token.Method.(*jwt.SigningMethodRSA); !ok {
			return nil, fmt.Errorf("unexpected signing method: %v", token.Header["alg"])
		}
		return publicKey, nil
	})
	if err != nil {
		return nil, fmt.Errorf("invalid token: %w", err)
	}
	if !token.Valid {
		return nil, fmt.Errorf("token not valid")
	}
	return token, nil
}
