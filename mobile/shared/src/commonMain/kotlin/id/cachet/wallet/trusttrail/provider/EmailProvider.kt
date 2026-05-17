package id.cachet.wallet.trusttrail.provider

import id.cachet.wallet.trusttrail.model.EmailHeader

/**
 * Abstraction over email inbox access (Gmail, Outlook).
 * Implementations handle OAuth tokens and API calls internally —
 * tokens never leak through this interface.
 */
interface EmailProvider {

    /**
     * Fetch headers only (From domain, Subject, Date) for the given scan depth.
     * No email body content is read at this stage.
     */
    suspend fun fetchHeaders(scanDepthMonths: Int = GmailConfig.DEFAULT_SCAN_DEPTH_MONTHS): List<EmailHeader>

    /**
     * Fetch the full RFC 2822 MIME content for a specific message.
     * Only called for messages the user has consented to process.
     */
    suspend fun fetchFullContent(messageId: String): RawEmail?

    /** Whether the provider has a valid OAuth connection. */
    fun isConnected(): Boolean

    /**
     * Raw email content returned by fetchFullContent.
     * Contains the fields needed for claim extraction and DKIM verification.
     */
    data class RawEmail(
        val messageId: String,
        val from: String,
        val subject: String,
        val textBody: String,
        val htmlBody: String,
        val rawMime: ByteArray? = null,
    )
}
