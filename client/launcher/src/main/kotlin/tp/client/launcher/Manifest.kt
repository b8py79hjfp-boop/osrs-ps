package tp.client.launcher

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** One file the client needs: [path] relative to the update base URL, checked by [sha256] after download. */
class Artifact(val name: String, val path: String, val sha256: String, val size: Long)

/**
 * The signed update manifest (like RuneLite's bootstrap.json). The launcher only acts on a manifest whose detached
 * signature verifies against the public key built into it, so a compromised mirror or update server can't push code.
 */
class Manifest(val version: String, val revision: Int, val mainClass: String, val jvmArgs: List<String>, val artifacts: List<Artifact>) {
    fun toJson(): String =
        buildString {
            append("{\"version\":").append(q(version))
            append(",\"revision\":").append(revision)
            append(",\"mainClass\":").append(q(mainClass))
            append(",\"jvmArgs\":[").append(jvmArgs.joinToString(",") { q(it) }).append(']')
            append(",\"artifacts\":[")
            append(
                artifacts.joinToString(",") {
                    "{\"name\":${q(it.name)},\"path\":${q(it.path)},\"sha256\":${q(it.sha256)},\"size\":${it.size}}"
                },
            )
            append("]}")
        }

    companion object {
        fun parse(text: String): Manifest {
            val o = MiniJson.parse(text) as Map<*, *>
            return Manifest(
                o["version"] as String,
                (o["revision"] as Number).toInt(),
                o["mainClass"] as String,
                (o["jvmArgs"] as List<*>).map { it as String },
                (o["artifacts"] as List<*>).map {
                    val a = it as Map<*, *>
                    Artifact(a["name"] as String, a["path"] as String, a["sha256"] as String, (a["size"] as Number).toLong())
                },
            )
        }

        private fun q(s: String) = MiniJson.quote(s)
    }
}

/**
 * ECDSA P-256 / SHA-256 manifest signatures. Ed25519 would be the first choice but needs Java 15+, and the bundled
 * runtime is 11 (D-028). Keys are Base64 X.509 (public) / PKCS#8 (private); signatures are Base64 DER.
 */
object ManifestSigning {
    private const val ALGORITHM = "SHA256withECDSA"
    private const val CURVE = "secp256r1"

    fun generate(): Pair<String, String> {
        val gen = KeyPairGenerator.getInstance("EC")
        gen.initialize(ECGenParameterSpec(CURVE))
        val pair = gen.generateKeyPair()
        val enc = Base64.getEncoder()
        return enc.encodeToString(pair.public.encoded) to enc.encodeToString(pair.private.encoded)
    }

    fun publicKey(base64: String): PublicKey =
        KeyFactory
            .getInstance(
                "EC",
            ).generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(base64.trim())))

    fun privateKey(base64: String): PrivateKey =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64.trim())))

    fun sign(data: ByteArray, key: PrivateKey): String {
        val s = Signature.getInstance(ALGORITHM)
        s.initSign(key)
        s.update(data)
        return Base64.getEncoder().encodeToString(s.sign())
    }

    fun verify(data: ByteArray, signatureBase64: String, key: PublicKey): Boolean =
        runCatching {
            val s = Signature.getInstance(ALGORITHM)
            s.initVerify(key)
            s.update(data)
            s.verify(Base64.getDecoder().decode(signatureBase64.trim()))
        }.getOrDefault(false)
}
