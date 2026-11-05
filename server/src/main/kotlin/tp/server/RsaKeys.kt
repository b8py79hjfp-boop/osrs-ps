package tp.server

import net.rsprot.crypto.rsa.RsaKeyPair
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** Loads the login RSA key written by `./gradlew generateRsa` (PKCS#8 PEM). The private key never enters git. */
internal object RsaKeys {
    fun load(pem: Path): RsaKeyPair {
        require(Files.isRegularFile(pem)) { "Missing $pem — run ./gradlew generateRsa" }
        val body = Files.readAllLines(pem).filterNot { it.startsWith("-----") }.joinToString("")
        val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))) as RSAPrivateKey
        return RsaKeyPair(key.privateExponent, key.modulus)
    }
}
