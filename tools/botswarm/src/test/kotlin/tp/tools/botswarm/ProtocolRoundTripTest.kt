package tp.tools.botswarm

import net.rsprot.crypto.rsa.RsaKeyPair
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import tp.engine.World
import tp.server.BootData
import tp.server.WorldNode
import tp.util.CoordGrid
import tp.util.Revision
import java.math.BigInteger
import java.net.ServerSocket
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateCrtKey

/**
 * Test 2 — protocol round trip (brief §9.2, the only networked test). A real rev-242 world on localhost; the botswarm
 * client completes the JS5 handshake + downloads, then login (PoW + RSA + ISAAC + CRC check), then exchanges game
 * packets: it receives the login rebuild and player info, and its walk click moves it server-side.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProtocolRoundTripTest {
    private lateinit var data: BootData
    private lateinit var world: World
    private lateinit var modulus: BigInteger
    private var port = 0

    @BeforeAll
    fun boot() {
        val cacheDir = Path.of(System.getProperty("tp.repoRoot") ?: ".", ".data", "cache", Revision.NUMBER.toString(), "disk")
        data = checkNotNull(WorldNode.loadData(cacheDir)) { "Cache missing or invalid at $cacheDir — run ./gradlew setup" }
        val gen = KeyPairGenerator.getInstance("RSA").apply { initialize(RSA_BITS) }
        val key = gen.generateKeyPair().private as RSAPrivateCrtKey
        modulus = key.modulus
        port = ServerSocket(0).use { it.localPort }
        world = WorldNode.startWorld(data, RsaKeyPair(key.privateExponent, key.modulus), port, worldId = 1, seed = SEED)
    }

    @AfterAll
    fun shutdown() {
        world.stop()
        data.cache.close()
    }

    @Test
    fun `js5, login and game packets round trip`() {
        // JS5: master index + one real group, byte-identical to the cache's container.
        val (master, objGroup) = BotClient("127.0.0.1", port, modulus, IntArray(0)).use { it.js5Fetch(listOf(255 to 255, 2 to OBJ_GROUP)) }
        val crcs = parseMasterIndex(master)
        assertEquals(data.cache.archiveCount, crcs.size)
        assertEquals(data.cache.archive(0)?.crc, crcs[0])
        val stored = checkNotNull(data.cache.store.read(2, OBJ_GROUP))
        assertArrayEquals(stored.copyOf(objGroup.size), objGroup)

        // Wrong CRCs are refused as "client out of date" (login response 6).
        BotClient("127.0.0.1", port, modulus, IntArray(crcs.size)).use { stale ->
            assertEquals(LOGIN_OUT_OF_DATE, stale.login("stale", "pw"))
        }

        BotClient("127.0.0.1", port, modulus, crcs).use { a ->
            BotClient("127.0.0.1", port, modulus, crcs).use { b ->
                assertEquals(LOGIN_OK, a.login("alpha", "pw"))
                assertEquals(LOGIN_OK, b.login("bravo", "pw"))
                assertTrue(a.playerIndex in 1..2047 && b.playerIndex != a.playerIndex)

                // First game packets: the login rebuild (REBUILD_NORMAL_V2 opcode, carries player-info init), then info.
                val first = a.readPacket()
                assertEquals("REBUILD_NORMAL_V2", first.prot?.name)
                assertTrue(readUntil(a, "PLAYER_INFO"), "player info received")

                // A walk click 3 tiles east moves the player server-side (route + 1 tile/tick walking).
                val start = WorldNode.DEFAULT_SPAWN
                a.walkTo(start.x + WALK_TILES, start.z)
                val deadline = System.nanoTime() + WALK_TIMEOUT_NANOS
                var moved: CoordGrid? = null
                while (System.nanoTime() < deadline) {
                    a.readPacket()
                    moved = playerCoord("alpha")
                    if (moved == CoordGrid(start.x + WALK_TILES, start.z, start.level)) break
                }
                assertEquals(CoordGrid(start.x + WALK_TILES, start.z, start.level), moved)
                assertTrue(readUntil(b, "PLAYER_INFO"), "second bot keeps receiving player info")
            }
        }
    }

    private fun playerCoord(name: String): CoordGrid? {
        var found: CoordGrid? = null
        world.forEachPlayer { if (it.username == name) found = it.coord }
        return found
    }

    private fun readUntil(bot: BotClient, prot: String): Boolean {
        repeat(MAX_PACKETS) { if (bot.readPacket().prot?.name == prot) return true }
        return false
    }

    /** Uncompressed container of (crc i32, version i32) per archive slot. */
    private fun parseMasterIndex(container: ByteArray): IntArray {
        val buf = ByteBuffer.wrap(container)
        check(buf.get().toInt() == 0) { "master index should be uncompressed" }
        val length = buf.getInt()
        return IntArray(length / MASTER_ENTRY) {
            val crc = buf.getInt()
            buf.getInt()
            crc
        }
    }

    private companion object {
        const val RSA_BITS = 1024
        const val SEED = 242L
        const val OBJ_GROUP = 10
        const val LOGIN_OK = 2
        const val LOGIN_OUT_OF_DATE = 6
        const val WALK_TILES = 3
        const val MAX_PACKETS = 200
        const val MASTER_ENTRY = 8
        const val WALK_TIMEOUT_NANOS = 10_000_000_000L
    }
}
