package id.cachet.wallet.testfixtures

import id.cachet.wallet.domain.sync.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeConnectivityObserver(initialOnline: Boolean = true) : ConnectivityObserver {
    private val _isOnline = MutableStateFlow(initialOnline)
    override val isOnline: StateFlow<Boolean> = _isOnline

    fun setOnline(online: Boolean) { _isOnline.value = online }
    fun goOffline() = setOnline(false)
    fun goOnline() = setOnline(true)
}
