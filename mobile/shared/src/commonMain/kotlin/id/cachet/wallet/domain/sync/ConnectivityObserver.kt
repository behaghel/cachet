package id.cachet.wallet.domain.sync

import kotlinx.coroutines.flow.StateFlow

/**
 * Observes network connectivity changes.
 * Platform-specific implementations provide real connectivity state;
 * tests use FakeConnectivityObserver.
 */
interface ConnectivityObserver {
    /** Current connectivity state. true = online, false = offline. */
    val isOnline: StateFlow<Boolean>
}
