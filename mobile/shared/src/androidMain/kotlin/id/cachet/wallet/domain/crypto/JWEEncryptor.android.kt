package id.cachet.wallet.domain.crypto

import com.nimbusds.jose.EncryptionMethod
import com.nimbusds.jose.JWEAlgorithm
import com.nimbusds.jose.JWEHeader
import com.nimbusds.jose.JWEObject
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.X25519Encrypter
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.OctetKeyPair
import com.nimbusds.jose.util.Base64URL
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

actual class JWEEncryptor actual constructor() {

    init {
        // Register BouncyCastle for X25519 support on older Android API levels
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    actual fun encrypt(plaintext: ByteArray, recipientPubKeyBase64URL: String): String {
        // Build an OKP JWK from the raw X25519 public key
        val pubKeyJWK = OctetKeyPair.Builder(Curve.X25519, Base64URL(recipientPubKeyBase64URL))
            .build()

        val header = JWEHeader.Builder(JWEAlgorithm.ECDH_ES_A256KW, EncryptionMethod.A256GCM)
            .build()

        val jweObject = JWEObject(header, Payload(plaintext))
        jweObject.encrypt(X25519Encrypter(pubKeyJWK))

        return jweObject.serialize()
    }
}
