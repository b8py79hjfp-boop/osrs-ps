package tp.tools.botswarm

import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import net.rsprot.buffer.extensions.p1Alt2
import net.rsprot.buffer.extensions.p2Alt2
import net.rsprot.buffer.extensions.p4Alt1
import net.rsprot.buffer.extensions.p4Alt2
import net.rsprot.buffer.extensions.p4Alt3
import net.rsprot.crypto.cipher.IsaacRandom
import net.rsprot.protocol.game.outgoing.prot.GameServerProt
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Headless rev-242 client (brief §7 botswarm, §9 test 2). The client side of the RSProt 242 codecs, written as their
 * inverse: login layout from `LoginBlockDecoder`/`GameLoginDecoder`/`DesktopLoginCrcDecoder`, PoW from
 * `Sha256ChallengeVerifier`, framing from `OutgoingMessageEncoder`, JS5 from `Js5Service.prepareJs5Buffer`.
 * Blocking sockets: a bot is a test/load tool, not a server path.
 */
class BotClient(
    private val host: String,
    private val port: Int,
    private val rsaModulus: BigInteger,
    /** Per archive id, the CRC from the server's master index (JS5 255/255). */
    private val archiveCrcs: IntArray,
) : AutoCloseable {
    private var socket: Socket? = null
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private lateinit var encodeCipher: IsaacRandom
    private lateinit var decodeCipher: IsaacRandom
    private val random = SecureRandom()

    /** Assigned player index after a successful login. */
    var playerIndex: Int = -1
        private set

    // ---------------------------------------------------------------- JS5

    /** JS5 handshake, then fetch [archive]/[group]. Returns the container bytes (header + payload, no trailer). */
    fun js5Fetch(requests: List<Pair<Int, Int>>): List<ByteArray> {
        connect()
        output.writeByte(INIT_JS5)
        output.writeInt(REVISION)
        repeat(JS5_INIT_INTS) { output.writeInt(0) }
        output.flush()
        val status = input.readUnsignedByte()
        if (status != STATUS_OK) throw IOException("JS5 handshake rejected: $status")
        for ((archive, group) in requests) {
            output.writeByte(JS5_URGENT)
            output.writeByte(archive)
            output.writeShort(group)
        }
        output.flush()
        return requests.map { (archive, group) -> readJs5Response(archive, group) }
    }

    private fun readJs5Response(archive: Int, group: Int): ByteArray {
        val a = input.readUnsignedByte()
        val g = input.readUnsignedShort()
        if (a != archive || g != group) throw IOException("JS5 response for $a/$g, expected $archive/$group")
        val header = ByteArray(CONTAINER_HEADER)
        var read = JS5_HEADER // block position: archive + group already consumed

        fun nextByte(): Int {
            if (read == JS5_BLOCK) {
                val marker = input.readUnsignedByte()
                if (marker != JS5_BLOCK_MARKER) throw IOException("Missing JS5 block marker ($marker)")
                read = 1
            }
            read++
            return input.readUnsignedByte()
        }
        for (i in header.indices) header[i] = nextByte().toByte()
        val compression = header[0].toInt() and 0xFF
        val length = BigInteger(1, header.copyOfRange(1, CONTAINER_HEADER)).toInt()
        val total = CONTAINER_HEADER + length + if (compression == 0) 0 else Int.SIZE_BYTES
        val out = ByteArray(total)
        System.arraycopy(header, 0, out, 0, CONTAINER_HEADER)
        for (i in CONTAINER_HEADER until total) out[i] = nextByte().toByte()
        return out
    }

    // ---------------------------------------------------------------- login

    /** Full login: handshake, GAMELOGIN, proof of work, response. Returns the login response code (2 = OK). */
    fun login(username: String, password: String): Int {
        connect()
        output.writeByte(INIT_GAME)
        output.flush()
        val status = input.readUnsignedByte()
        if (status != STATUS_OK) throw IOException("Game handshake rejected: $status")
        val sessionId = input.readLong()

        val seed = IntArray(SEED_INTS) { random.nextInt() }
        val block = loginBlock(username, password, seed, sessionId)
        output.writeByte(GAMELOGIN)
        output.writeShort(block.size)
        output.write(block)
        output.flush()

        var response = input.readUnsignedByte()
        if (response == PROOF_OF_WORK) {
            solveProofOfWork()
            response = input.readUnsignedByte()
        }
        if (response != LOGIN_OK) return response
        input.readUnsignedByte() // size (counts 3 header bytes of the next game packet: RSProt OutgoingMessageEncoder quirk)
        input.skipNBytes(AUTHENTICATOR_BYTES.toLong())
        input.readUnsignedByte() // staff mod level
        input.readUnsignedByte() // player mod
        playerIndex = input.readUnsignedShort()
        input.skipNBytes(OK_TRAILING_BYTES.toLong())
        encodeCipher = IsaacRandom(seed)
        decodeCipher = IsaacRandom(IntArray(SEED_INTS) { seed[it] + ISAAC_SERVER_OFFSET })
        return response
    }

    private fun solveProofOfWork() {
        input.readUnsignedShort() // var-short size
        val type = input.readUnsignedByte()
        if (type != POW_SHA256) throw IOException("Unknown PoW type $type")
        val version = input.readUnsignedByte()
        val difficulty = input.readUnsignedByte()
        val salt = readJstr()
        val base = Integer.toHexString(version) + Integer.toHexString(difficulty) + salt
        val sha = MessageDigest.getInstance("SHA-256")
        // Solved on one platform thread: on virtual threads the hashing pins every carrier and starves the bots that are
        // already in game (they stop reading and the server drops them as idle).
        val nonce =
            POW_SOLVER
                .submit<Long> {
                    var n = 0L
                    while (leadingZeroBits(sha.digest((base + java.lang.Long.toHexString(n)).toByteArray(Charsets.UTF_8))) < difficulty) n++
                    n
                }.get()
        output.writeByte(POW_REPLY)
        output.writeShort(Long.SIZE_BYTES)
        output.writeLong(nonce)
        output.flush()
    }

    private fun leadingZeroBits(hash: ByteArray): Int {
        var bits = 0
        for (b in hash) {
            val v = b.toInt() and 0xFF
            if (v == 0) {
                bits += Byte.SIZE_BITS
                continue
            }
            return bits + Integer.numberOfLeadingZeros(v) - (Int.SIZE_BITS - Byte.SIZE_BITS)
        }
        return bits
    }

    private fun loginBlock(username: String, password: String, seed: IntArray, sessionId: Long): ByteArray {
        val rsaPlain =
            bytes {
                writeByte(1) // RSA check byte
                seed.forEach(::writeInt)
                writeLong(sessionId)
                writeByte(OTP_NONE)
                writeInt(0)
                writeByte(AUTH_PASSWORD)
                writeJstr(password)
            }
        val rsa = BigInteger(rsaPlain).modPow(RSA_PUBLIC_EXPONENT, rsaModulus).toByteArray()
        val xteaPlain =
            bytes {
                writeJstr(username)
                writeByte(CLIENT_SETTINGS_RESIZABLE)
                writeShort(CLIENT_WIDTH)
                writeShort(CLIENT_HEIGHT)
                write(ByteArray(UUID_BYTES))
                writeJstr("") // site settings
                writeInt(0) // affiliate
                writeByte(0) // deep links
                writeHostPlatformStats()
                writeByte(CLIENT_TYPE_DESKTOP) // second client type
                writeInt(0) // reflection checker const
                writeCrcs(this)
            }
        return bytes {
            writeInt(REVISION)
            writeInt(SUB_VERSION)
            writeInt(0) // server version
            writeByte(CLIENT_TYPE_DESKTOP)
            writeByte(0) // platform
            writeByte(0) // external authenticator
            writeShort(rsa.size)
            write(rsa)
            // No jxSessionId section: matches the rev-242 Java client (RuneLite) — D-005 OPEN-B.
            write(xteaEncrypt(xteaPlain, seed))
        }
    }

    /** Inverse of RSProt `DesktopLoginCrcDecoder.decodeLive`: same archive order, RSProt's own p4/p4Alt1-3 writers. */
    private fun writeCrcs(out: DataOutputStream) {
        val buf = Unpooled.buffer(CRC_ORDER.size * Int.SIZE_BYTES)
        for ((archive, alt) in CRC_ORDER) {
            val crc = archiveCrcs.getOrElse(archive) { 0 }
            when (alt) {
                0 -> buf.writeInt(crc)
                1 -> buf.p4Alt1(crc)
                2 -> buf.p4Alt2(crc)
                else -> buf.p4Alt3(crc)
            }
        }
        out.write(ByteBufUtil.getBytes(buf))
    }

    private fun DataOutputStream.writeHostPlatformStats() {
        writeByte(HOST_STATS_VERSION)
        repeat(HOST_STATS_FIXED_PREFIX) { writeByte(0) } // os type, os64, osVersion (2)
        writeByte(0) // java vendor
        repeat(JAVA_VERSION_PARTS) { writeByte(0) }
        writeByte(1) // applet flag (1 = not applet)
        writeShort(0) // max memory
        writeByte(1) // processors
        writeByte(0)
        writeShort(0) // system memory (g3)
        writeShort(0) // system speed
        repeat(GPU_STRINGS) { writeJstr2("") }
        writeByte(0) // driver month
        writeShort(0) // driver year
        repeat(CPU_STRINGS) { writeJstr2("") }
        writeByte(0)
        writeByte(0)
        repeat(CPU_FEATURE_INTS) { writeInt(0) }
        writeInt(0) // cpu signature
        writeJstr2("") // client name
        writeJstr2("") // device name
    }

    // ---------------------------------------------------------------- game

    /** A server packet: opcode + payload. */
    class Packet(val opcode: Int, val payload: ByteArray) {
        val prot: GameServerProt? get() = PROTS[opcode]
    }

    /** Reads one ISAAC-framed server packet (sizes from RSProt's `GameServerProt`). Blocks until available. */
    fun readPacket(): Packet {
        var opcode = (input.readUnsignedByte() - decodeCipher.nextInt()) and 0xFF
        if (opcode >= LARGE_OPCODE) {
            val low = (input.readUnsignedByte() - decodeCipher.nextInt()) and 0xFF
            opcode = ((opcode and LARGE_OPCODE_MASK) shl Byte.SIZE_BITS) or low
        }
        val prot = PROTS[opcode] ?: throw IOException("Unknown server opcode $opcode")
        val size =
            when (prot.size) {
                VAR_BYTE -> input.readUnsignedByte()
                VAR_SHORT -> input.readUnsignedShort()
                else -> prot.size
            }
        val payload = ByteArray(size)
        input.readFully(payload)
        return Packet(opcode, payload)
    }

    /** MOVE_GAMECLICK (opcode 55, var-byte): g1Alt2 keyCombination, g2 x, g2Alt2 z (RSProt `MoveGameClickDecoder`). */
    fun walkTo(x: Int, z: Int, ctrl: Boolean = false) {
        val buf = Unpooled.buffer(MOVE_CLICK_BYTES)
        buf.p1Alt2(if (ctrl) 1 else 0)
        buf.writeShort(x)
        buf.p2Alt2(z)
        sendClientPacket(MOVE_GAMECLICK, ByteBufUtil.getBytes(buf))
    }

    /** OPPLAYER2 (opcode 8, fixed 3 bytes): g1Alt2 controlKey, g2 index (RSProt `OpPlayer2Decoder`): "Attack" in the Wilderness. */
    fun attackPlayer(index: Int) {
        val buf = Unpooled.buffer(OPPLAYER_BYTES)
        buf.p1Alt2(0)
        buf.writeShort(index)
        sendFixedPacket(OPPLAYER2, ByteBufUtil.getBytes(buf))
    }

    /** IF_BUTTONX (opcode 71, fixed 9 bytes): g4 combined component, g2 sub, g2 obj, g1 op (RSProt `IfButtonXDecoder`). */
    fun button(component: Int, op: Int, sub: Int = NONE, obj: Int = NONE) {
        val buf = Unpooled.buffer(IF_BUTTONX_BYTES)
        buf.writeInt(component)
        buf.writeShort(sub)
        buf.writeShort(obj)
        buf.writeByte(op)
        sendFixedPacket(IF_BUTTONX, ByteBufUtil.getBytes(buf))
    }

    /** Reads packets until LOGOUT_TRANSFER (opcode 91: pjstr host, g2 world, g4 flags); null if [maxPackets] pass first. */
    fun awaitTransfer(maxPackets: Int = TRANSFER_WAIT_PACKETS): Pair<String, Int>? {
        repeat(maxPackets) {
            val packet = readPacket()
            if (packet.opcode == LOGOUT_TRANSFER) {
                val buf = ByteBuffer.wrap(packet.payload)
                val host = StringBuilder()
                while (true) {
                    val c = buf.get().toInt() and 0xFF
                    if (c == 0) break
                    host.append(c.toChar())
                }
                return host.toString() to (buf.getShort().toInt() and 0xFFFF)
            }
        }
        return null
    }

    private fun sendFixedPacket(opcode: Int, payload: ByteArray) {
        output.writeByte((opcode + encodeCipher.nextInt()) and 0xFF)
        output.write(payload)
        output.flush()
    }

    fun cheat(command: String) {
        sendClientPacket(
            CLIENT_CHEAT,
            bytes {
                writeJstr(command)
                writeByte(0) // autocomplete flag (RSProt reads it unconditionally; RSProx conditionally — D-005 OPEN)
            },
        )
    }

    private fun sendClientPacket(opcode: Int, payload: ByteArray) {
        output.writeByte((opcode + encodeCipher.nextInt()) and 0xFF)
        output.writeByte(payload.size) // all Phase 2 bot packets are var-byte
        output.write(payload)
        output.flush()
    }

    // ---------------------------------------------------------------- plumbing

    private fun connect() {
        close()
        val s = Socket()
        s.tcpNoDelay = true
        s.soTimeout = SOCKET_TIMEOUT_MS
        s.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
        socket = s
        input = DataInputStream(s.getInputStream().buffered())
        output = DataOutputStream(s.getOutputStream().buffered())
    }

    override fun close() {
        socket?.close()
        socket = null
    }

    private fun readJstr(): String {
        val sb = StringBuilder()
        while (true) {
            val c = input.readUnsignedByte()
            if (c == 0) return sb.toString()
            sb.append(c.toChar())
        }
    }

    companion object {
        const val REVISION = 242
        private const val SUB_VERSION = 1
        private const val INIT_GAME = 14
        private const val INIT_JS5 = 15
        private const val GAMELOGIN = 16
        private const val POW_REPLY = 19
        private const val STATUS_OK = 0
        private const val LOGIN_OK = 2
        private const val PROOF_OF_WORK = 69
        private const val POW_SHA256 = 0
        private const val JS5_URGENT = 1
        private const val JS5_INIT_INTS = 4
        private const val JS5_HEADER = 3
        private const val JS5_BLOCK = 512
        private const val JS5_BLOCK_MARKER = 0xFF
        private const val CONTAINER_HEADER = 5
        private const val SEED_INTS = 4

        /** The server decodes with seed+50 for its encoder; the client mirrors (OSRS ISAAC convention, RSProt `StreamCipherPair`). */
        private const val ISAAC_SERVER_OFFSET = 50
        private const val AUTHENTICATOR_BYTES = 5
        private const val OK_TRAILING_BYTES = 1 + 8 + 8 + 8
        private const val OTP_NONE = 2
        private const val AUTH_PASSWORD = 0
        private const val CLIENT_TYPE_DESKTOP = 1
        private const val CLIENT_SETTINGS_RESIZABLE = 0x2
        private const val CLIENT_WIDTH = 765
        private const val CLIENT_HEIGHT = 503
        private const val UUID_BYTES = 24
        private const val HOST_STATS_VERSION = 9
        private const val HOST_STATS_FIXED_PREFIX = 4
        private const val JAVA_VERSION_PARTS = 3
        private const val GPU_STRINGS = 4
        private const val CPU_STRINGS = 2
        private const val CPU_FEATURE_INTS = 3
        private const val LARGE_OPCODE = 0x80
        private const val LARGE_OPCODE_MASK = 0x7F
        private const val VAR_BYTE = -1
        private const val VAR_SHORT = -2
        private const val MOVE_GAMECLICK = 55
        private const val CLIENT_CHEAT = 23

        /** OPPLAYER2 opcode and fixed size (RSProt `GameClientProt`). */
        private const val OPPLAYER2 = 8
        private const val OPPLAYER_BYTES = 3

        /** IF_BUTTONX opcode and fixed size (RSProt `GameClientProt`); 0xFFFF = no sub/obj. */
        private const val IF_BUTTONX = 71
        private const val IF_BUTTONX_BYTES = 9
        private const val NONE = 0xFFFF

        /** LOGOUT_TRANSFER server opcode (RSProt `GameServerProtId`). */
        private const val LOGOUT_TRANSFER = 91
        private const val TRANSFER_WAIT_PACKETS = 10_000
        private const val SOCKET_TIMEOUT_MS = 30_000

        private val POW_SOLVER =
            java.util.concurrent.Executors
                .newSingleThreadExecutor { r -> Thread(r, "pow").apply { isDaemon = true } }
        private const val MOVE_CLICK_BYTES = 5
        private val RSA_PUBLIC_EXPONENT: BigInteger = BigInteger.valueOf(65537)

        private val PROTS: Map<Int, GameServerProt> = GameServerProt.entries.filter { it.opcode >= 0 }.associateBy { it.opcode }

        /** (archive, transform) in `DesktopLoginCrcDecoder.decodeLive` order; transform 0 = g4, 1..3 = g4Alt1..3. */
        private val CRC_ORDER: List<Pair<Int, Int>> =
            listOf(
                2 to 3,
                15 to 2,
                8 to 3,
                12 to 2,
                22 to 1,
                19 to 3,
                1 to 1,
                7 to 1,
                17 to 0,
                14 to 3,
                5 to 0,
                13 to 1,
                11 to 2,
                20 to 2,
                3 to 3,
                6 to 1,
                18 to 1,
                21 to 3,
                10 to 1,
                4 to 0,
                16 to 1,
                0 to 3,
                9 to 1,
            )

        private fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
            val bos = ByteArrayOutputStream()
            DataOutputStream(bos).use(block)
            return bos.toByteArray()
        }

        private fun DataOutputStream.writeJstr(s: String) {
            write(s.toByteArray(Charsets.ISO_8859_1))
            writeByte(0)
        }

        private fun DataOutputStream.writeJstr2(s: String) {
            writeByte(0)
            writeJstr(s)
        }

        /** Standard XTEA encipher (32 rounds) over whole 8-byte blocks; the tail stays plain (mirrors RSProt `xteaDecrypt`). */
        private fun xteaEncrypt(data: ByteArray, key: IntArray): ByteArray {
            val out = data.copyOf()
            val buf = java.nio.ByteBuffer.wrap(out)
            for (block in 0 until out.size / XTEA_BLOCK) {
                val off = block * XTEA_BLOCK
                var v0 = buf.getInt(off)
                var v1 = buf.getInt(off + Int.SIZE_BYTES)
                var sum = 0
                repeat(XTEA_ROUNDS) {
                    v0 += (((v1 shl XTEA_LEFT) xor (v1 ushr XTEA_RIGHT)) + v1) xor (sum + key[sum and 3])
                    sum += XTEA_DELTA
                    v1 += (((v0 shl XTEA_LEFT) xor (v0 ushr XTEA_RIGHT)) + v0) xor (sum + key[(sum ushr XTEA_KEY_SHIFT) and 3])
                }
                buf.putInt(off, v0)
                buf.putInt(off + Int.SIZE_BYTES, v1)
            }
            return out
        }

        private const val XTEA_BLOCK = 8
        private const val XTEA_ROUNDS = 32
        private const val XTEA_DELTA = -0x61c88647
        private const val XTEA_LEFT = 4
        private const val XTEA_RIGHT = 5
        private const val XTEA_KEY_SHIFT = 11
    }
}
