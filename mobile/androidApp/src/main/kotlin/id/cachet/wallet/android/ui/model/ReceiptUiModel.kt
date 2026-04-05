package id.cachet.wallet.android.ui.model

enum class ReceiptLogStatus { LOGGED, PENDING }

data class ReceiptItem(
    val id: String,
    val title: String,
    val counterparty: String,
    val date: String,
    val predicateCount: Int,
    val logStatus: ReceiptLogStatus,
    val expiresLabel: String
)
