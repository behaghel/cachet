package dkim

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

const validDKIMEmail = `DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed;
 d=care.com; s=selector1; t=1714000000;
 h=from:to:subject:date;
 bh=abc123hash==;
 b=signaturedata==
From: noreply@care.com
To: alice@example.com
Subject: Booking Confirmation - Tuesday Jan 14
Date: Sat, 25 Apr 2026 10:30:00 +0000
Message-ID: <abc123@care.com>

Dear Alice, your booking has been confirmed.
`

const noDKIMEmail = `From: bob@example.com
To: alice@example.com
Subject: Hello
Date: Sat, 25 Apr 2026 10:00:00 +0000

Just a plain email.
`

const multipleDKIMEmail = `DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed;
 d=care.com; s=selector1;
 h=from:to:subject;
 bh=hash1==; b=sig1==
DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/simple;
 d=amazonses.com; s=hsbnp7p3ensaochzwyq5wwmceodymuwv;
 h=from:to:subject:date;
 bh=hash2==; b=sig2==
From: noreply@care.com
To: alice@example.com
Subject: Payment Receipt
Date: Sat, 25 Apr 2026 11:00:00 +0000

Payment details here.
`

func TestParse_ValidDKIMEmail(t *testing.T) {
	result, err := Parse([]byte(validDKIMEmail))
	require.NoError(t, err)

	assert.Equal(t, "noreply@care.com", result.From)
	assert.Equal(t, "alice@example.com", result.To)
	assert.Equal(t, "Booking Confirmation - Tuesday Jan 14", result.Subject)
	assert.Equal(t, "<abc123@care.com>", result.MessageID)
	assert.False(t, result.Date.IsZero())

	require.Len(t, result.DKIMSignatures, 1)
	dkim := result.DKIMSignatures[0]
	assert.Equal(t, "care.com", dkim.Domain)
	assert.Equal(t, "selector1", dkim.Selector)
	assert.Equal(t, "rsa-sha256", dkim.Algorithm)
	assert.Equal(t, []string{"from", "to", "subject", "date"}, dkim.HeaderFields)
	assert.Equal(t, "abc123hash==", dkim.BodyHash)
	assert.Equal(t, "signaturedata==", dkim.Signature)
	assert.Equal(t, time.Unix(1714000000, 0), dkim.Timestamp)

	// Raw bytes preserved for verification
	assert.Equal(t, []byte(validDKIMEmail), result.RawBytes)
}

func TestParse_NoDKIMEmail(t *testing.T) {
	result, err := Parse([]byte(noDKIMEmail))
	require.NoError(t, err)

	assert.Empty(t, result.DKIMSignatures)
	assert.Equal(t, "bob@example.com", result.From)
	assert.Equal(t, "Hello", result.Subject)
}

func TestParse_MultipleDKIMSignatures(t *testing.T) {
	result, err := Parse([]byte(multipleDKIMEmail))
	require.NoError(t, err)

	require.Len(t, result.DKIMSignatures, 2)
	assert.Equal(t, "care.com", result.DKIMSignatures[0].Domain)
	assert.Equal(t, "amazonses.com", result.DKIMSignatures[1].Domain)
	assert.Equal(t, "hsbnp7p3ensaochzwyq5wwmceodymuwv", result.DKIMSignatures[1].Selector)
}

func TestParse_EmptyInput(t *testing.T) {
	_, err := Parse([]byte{})
	assert.Error(t, err)
}

func TestParse_MalformedDKIMHeader(t *testing.T) {
	raw := `DKIM-Signature: garbage-not-tag-value-pairs
From: test@example.com
To: test@example.com
Subject: test

body
`
	result, err := Parse([]byte(raw))
	require.NoError(t, err)

	// Should produce a DKIMInfo with empty fields, not crash
	require.Len(t, result.DKIMSignatures, 1)
	assert.Empty(t, result.DKIMSignatures[0].Domain)
	assert.Empty(t, result.DKIMSignatures[0].Selector)
}

func TestParseDKIMSignatureValue_FoldedWhitespace(t *testing.T) {
	// DKIM-Signature values often have folded whitespace in the b= tag
	value := `v=1; a=rsa-sha256; d=example.com; s=sel;
	 h=from:to; bh=abc==;
	 b=long signature
	 that spans multiple
	 lines==`

	info := parseDKIMSignatureValue(value)
	assert.Equal(t, "example.com", info.Domain)
	assert.Equal(t, "longsignaturethatspansmultiplelines==", info.Signature)
}
