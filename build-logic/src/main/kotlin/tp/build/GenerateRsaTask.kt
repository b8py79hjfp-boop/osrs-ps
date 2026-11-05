package tp.build

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/**
 * Generates the login RSA keypair (brief §4.1) into `.data/rsa/`. The private key never enters git.
 * Prints the modulus as hex for `~/.rsprox/proxy-targets.yaml`. Existing keys are kept unless `-Pforce`.
 */
@UntrackedTask(because = "Generates secrets on demand.")
public abstract class GenerateRsaTask : DefaultTask() {
    @get:Internal
    public abstract val outputDir: DirectoryProperty

    @get:Input
    public abstract val force: Property<Boolean>

    @TaskAction
    public fun run() {
        val dir = outputDir.get().asFile.apply { mkdirs() }
        val privateFile = File(dir, "private.pem")
        val modulusFile = File(dir, "modulus.hex")

        if (privateFile.isFile && !force.get()) {
            val key = readPrivate(privateFile)
            logger.lifecycle("Keeping existing key ${privateFile.path} (pass -Pforce to regenerate).")
            printModulus(key)
            return
        }

        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(KEY_BITS)
        val pair = gen.generateKeyPair()
        val key = pair.private as RSAPrivateCrtKey
        if (key.modulus.bitLength() != KEY_BITS) throw GradleException("Unexpected modulus size ${key.modulus.bitLength()}")

        privateFile.writeText(pem("PRIVATE KEY", pair.private.encoded))
        File(dir, "public.pem").writeText(pem("PUBLIC KEY", pair.public.encoded))
        modulusFile.writeText(key.modulus.toString(HEX) + "\n")
        runCatching {
            Files.setPosixFilePermissions(privateFile.toPath(), PosixFilePermissions.fromString("rw-------"))
        }
        logger.lifecycle("Wrote ${privateFile.path}, public.pem and modulus.hex.")
        printModulus(key)
    }

    private fun printModulus(key: RSAPrivateCrtKey) {
        logger.lifecycle("RSA modulus (hex) for proxy-targets.yaml:")
        logger.lifecycle(key.modulus.toString(HEX))
    }

    private fun readPrivate(file: File): RSAPrivateCrtKey {
        val body = file.readLines().filterNot { it.startsWith("-----") }.joinToString("")
        val spec = PKCS8EncodedKeySpec(Base64.getDecoder().decode(body))
        return KeyFactory.getInstance("RSA").generatePrivate(spec) as RSAPrivateCrtKey
    }

    private fun pem(type: String, der: ByteArray): String =
        "-----BEGIN $type-----\n" +
            Base64.getMimeEncoder(PEM_LINE, "\n".toByteArray()).encodeToString(der) +
            "\n-----END $type-----\n"

    private companion object {
        /** The OSRS login block is RSA-1024 (brief §4.1). */
        const val KEY_BITS = 1024
        const val HEX = 16
        const val PEM_LINE = 64
    }
}
