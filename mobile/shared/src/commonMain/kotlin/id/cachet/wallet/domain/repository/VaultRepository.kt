package id.cachet.wallet.domain.repository

import id.cachet.wallet.domain.model.VaultArtifact
import id.cachet.wallet.domain.model.VaultPredicate

interface VaultRepository {
    suspend fun upsertArtifacts(artifacts: List<VaultArtifact>)
    suspend fun upsertPredicates(predicates: List<VaultPredicate>)
    suspend fun getAllPredicates(): List<VaultPredicate>
    suspend fun clear()
}
