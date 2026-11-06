package tp.hub

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Argon2id password hashing (brief §12), PHC string format `$argon2id$v=19$m=…,t=…,p=…$salt$hash`. Parameters: OWASP
 * Password Storage Cheat Sheet's first Argon2id option (m = 19 MiB, t = 2, p = 1).
 */
internal object Passwords {
    private const val MEMORY_KIB = 19 * 1024
    private const val ITERATIONS = 2
    private const val PARALLELISM = 1
    private const val SALT_BYTES = 16
    private const val HASH_BYTES = 32
    private val random = SecureRandom()
    private val b64 = Base64.getEncoder().withoutPadding()

    fun hash(password: String): String {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val out = derive(password, salt, MEMORY_KIB, ITERATIONS, PARALLELISM)
        return "\$argon2id\$v=19\$m=$MEMORY_KIB,t=$ITERATIONS,p=$PARALLELISM\$${b64.encodeToString(salt)}\$${b64.encodeToString(out)}"
    }

    fun verify(password: String, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != PHC_PARTS || parts[1] != "argon2id") return false
        val params = parts[3].split(',').associate { it.substringBefore('=') to it.substringAfter('=').toInt() }
        val salt = Base64.getDecoder().decode(parts[4])
        val expected = Base64.getDecoder().decode(parts[5])
        val actual = derive(password, salt, params.getValue("m"), params.getValue("t"), params.getValue("p"))
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: String, salt: ByteArray, memory: Int, iterations: Int, parallelism: Int): ByteArray {
        val params =
            Argon2Parameters
                .Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withMemoryAsKB(memory)
                .withIterations(iterations)
                .withParallelism(parallelism)
                .build()
        val generator = Argon2BytesGenerator()
        generator.init(params)
        val out = ByteArray(HASH_BYTES)
        generator.generateBytes(password.toByteArray(Charsets.UTF_8), out)
        return out
    }

    private const val PHC_PARTS = 6
}
