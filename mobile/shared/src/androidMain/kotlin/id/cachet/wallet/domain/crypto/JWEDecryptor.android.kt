package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.crypto.X25519Decrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

actual class JWEDecryptor actual constructor() {

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    actual fun decrypt(
        jweCompact: String,
        recipientPrivKeyBase64URL: String,
        recipientPubKeyBase64URL: String
    ): ByteArray {
        val privKeyJWK = OctetKeyPair.Builder(Curve.X25519, Base64URL(recipientPubKeyBase64URL))
            .d(Base64URL(recipientPrivKeyBase64URL))
            .build()

        val jweObject = JWEObject.parse(jweCompact)
        jweObject.decrypt(X25519Decrypter(privKeyJWK))

        return jweObject.payload.toBytes()
    }
}
