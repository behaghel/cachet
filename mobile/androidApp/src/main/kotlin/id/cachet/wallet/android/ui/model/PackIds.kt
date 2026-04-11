package id.cachet.wallet.android.ui.model

/**
 * Client-side registry of known pack IDs.
 * Every pack ID used in the app MUST be a constant here.
 *
 * A CI contract test (scripts/check-pack-ids.sh) validates these
 * against the registry pack JSON files to guarantee they exist in the backend.
 */
object PackIds {
    const val CHILDCARE_ES = "pack.childcare.readiness.es"
    const val CHILDCARE_BASE = "pack.childcare.readiness"
    const val CHILDCARE_FR = "pack.childcare.readiness.fr"
    const val CHILDCARE_EE = "pack.childcare.readiness.ee"
    const val SAFE_SELLER = "pack.safe.seller"
    const val IDENTITY_BASIC = "pack.identity.basic"
}
