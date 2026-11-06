package tp.client.gamepatch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Brief §9 test 5 (release-client build check), game-client half: every patch rule matches exactly its declared
 * number of sites, and the patched client contains our modulus and site, with none of the values it replaced.
 * (The fork half, RuneLite building against the pinned tag, is `./gradlew :client:checkFork`.)
 * Needs `./gradlew setupClient` (and the RSA key from `generateRsa`); skipped without them.
 */
class ReleaseClientBuildCheckTest {
    private val root =
        generateSequence(
            Path.of("").toAbsolutePath(),
        ) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
    private val injected = root.resolve(".data/client/artifacts/injected-client-1.13.6.jar")
    private val modulus = root.resolve(".data/rsa/modulus.hex")

    @Test
    fun `game client patches apply exactly once and leave no Jagex values`() {
        assumeTrue(Files.isRegularFile(injected) && Files.isRegularFile(modulus), "run ./gradlew setupClient generateRsa first")
        val ours = Files.readString(modulus).trim().lowercase()
        val site = "https://play.example.test"
        val rules = rules(root.resolve("client/gamepatch/patches.json"), ours, site)
        val out = Files.createTempFile("patched-client", ".jar")
        try {
            val report = GamePatcher.patch(injected, out, rules)
            assertEquals(emptyList<String>(), report.failures(rules))

            val text = allClassBytes(out).toString(Charsets.ISO_8859_1)
            assertTrue(ours in text) { "our modulus is in the patched client" }
            assertTrue("$site/support" in text) { "our site replaced the support links" }
            assertTrue(Regex("[0-9a-f]{256}").findAll(text).all { it.value == ours }) { "no other 1024-bit modulus left" }
            // URLs only: UI text such as "Visit runescape.com and vote" is not a connection.
            val jagexUrl = Regex("""https?://[^\s\u0000-\u001f"]*(runescape\.com|jagex\.com|osrs\.game)""")
            assertEquals(emptyList<String>(), jagexUrl.findAll(text).map { it.value }.toList())
            assertTrue(".runescape.com" !in text) { "no runescape.com host suffix left for URL concatenation" }
        } finally {
            Files.deleteIfExists(out)
        }
    }

    private fun allClassBytes(jar: Path): ByteArray {
        val all = java.io.ByteArrayOutputStream()
        ZipInputStream(Files.newInputStream(jar)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name.endsWith(".class")) all.write(z.readAllBytes())
            }
        }
        return all.toByteArray()
    }
}
