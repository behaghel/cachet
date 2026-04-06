package id.cachet.wallet.testfixtures

import id.cachet.wallet.network.RelayClient
import id.cachet.wallet.network.RelaySession

class FakeRelayClient(
    var session: RelaySession = RelaySession(
        sessionId = "fake-relay-session",
        requestUri = "/sessions/fake-relay-session/request",
        responseUri = "/sessions/fake-relay-session/response"
    ),
    var requestPayload: ByteArray = ByteArray(0),
    var responsePayload: ByteArray? = null
) : RelayClient {

    val postedResponses = mutableListOf<ByteArray>()

    override suspend fun createSession(requestPayload: ByteArray): RelaySession = session

    override suspend fun fetchRequest(requestUri: String): ByteArray = requestPayload

    override suspend fun postResponse(responseUri: String, payload: ByteArray) {
        postedResponses.add(payload)
    }

    override suspend fun pollResponse(responseUri: String): ByteArray? = responsePayload
}
