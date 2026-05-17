package id.cachet.wallet.trusttrail.model

/**
 * How the authenticity of an email claim was established.
 */
enum class TrustLevel {
    /**
     * DKIM signature re-verified on-device against DNS public key.
     * Strongest proof: we independently confirmed the email wasn't tampered.
     * Used for Gmail (which preserves DKIM body hashes).
     */
    CRYPTOGRAPHIC,

    /**
     * MTA's Authentication-Results header reports dkim=pass.
     * Weaker: we trust the receiving mail server's attestation.
     * Used for Outlook (which breaks DKIM body hashes during storage).
     * Also used as fallback when on-device DKIM verification fails but MTA saw it pass.
     */
    MTA_ATTESTED,
}
