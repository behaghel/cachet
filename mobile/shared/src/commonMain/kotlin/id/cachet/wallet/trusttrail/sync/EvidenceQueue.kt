package id.cachet.wallet.trusttrail.sync

import id.cachet.wallet.trusttrail.model.EvidenceBundle

/**
 * Queue for evidence bundles that failed to submit (offline, server error).
 * Drained when connectivity returns.
 *
 * Implementations: InMemoryEvidenceQueue (tests), SQLDelight (production).
 */
interface EvidenceQueue {
    fun enqueue(bundle: EvidenceBundle)
    fun peek(): EvidenceBundle?
    fun markCompleted()
    fun pendingCount(): Int
    suspend fun drainTo(submit: suspend (EvidenceBundle) -> Boolean)
}

/**
 * In-memory implementation for testing.
 */
class InMemoryEvidenceQueue : EvidenceQueue {
    private val queue = ArrayDeque<EvidenceBundle>()

    override fun enqueue(bundle: EvidenceBundle) {
        queue.addLast(bundle)
    }

    override fun peek(): EvidenceBundle? = queue.firstOrNull()

    override fun markCompleted() {
        queue.removeFirstOrNull()
    }

    override fun pendingCount(): Int = queue.size

    override suspend fun drainTo(submit: suspend (EvidenceBundle) -> Boolean) {
        while (queue.isNotEmpty()) {
            val bundle = queue.first()
            val success = submit(bundle)
            if (success) {
                queue.removeFirst()
            } else {
                break // stop draining on failure, keep remaining
            }
        }
    }
}
