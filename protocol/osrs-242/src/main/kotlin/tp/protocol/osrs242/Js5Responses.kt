package tp.protocol.osrs242

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.api.js5.Js5Service
import tp.cache.js5.Js5Cache
import tp.cache.js5.Js5Container
import tp.cache.js5.Js5DiskStore

/**
 * Every JS5 response, pre-encoded once at boot into direct buffers (brief §4.3). Groups are served as stored:
 * containers verbatim, minus the 2-byte version trailer on non-index groups (RS Mod `Js5Store`; the client strips it).
 * The master index (255/255) is built from our [Js5Cache]: per archive slot, i32 CRC then i32 version ("versioned"
 * format, RS Mod `MasterIndexFormat.VERSIONED`), wrapped in an uncompressed container.
 */
internal class Js5Responses private constructor(
    private val responses: Map<Int, ByteBuf>,
    /** Per archive slot, the CRC the client must report at login (0 for empty slots). */
    val archiveCrcs: IntArray,
) : Js5GroupProvider {
    val groupCount: Int get() = responses.size
    val totalBytes: Long get() = responses.values.sumOf { it.readableBytes().toLong() }

    override fun provide(archive: Int, group: Int): ByteBuf? = responses[key(archive, group)]

    companion object {
        private const val MASTER = Js5DiskStore.MASTER_ARCHIVE
        private const val VERSION_TRAILER = 2
        private const val CONTAINER_HEADER = 5
        private const val MASTER_ENTRY_BYTES = 8
        private const val GROUP_SHIFT = 16

        /** prepareJs5Buffer adds a 0xFF block marker every 512 payload bytes, plus an 8-byte header. */
        private const val BLOCK = 512
        private const val RESPONSE_HEADER = 8

        private fun key(archive: Int, group: Int): Int = (archive shl GROUP_SHIFT) or group

        /** Encodes every group on the common pool (I/O-light, CPU-bound copies); boot-time only. */
        fun build(cache: Js5Cache): Js5Responses {
            val map = java.util.concurrent.ConcurrentHashMap<Int, ByteBuf>(cache.groupCount * 2)
            val crcs = IntArray(cache.archiveCount)
            val jobs = ArrayList<Pair<Int, Int>>(cache.groupCount)
            for (info in cache.archives) {
                if (info == null) continue
                crcs[info.archive] = info.crc
                jobs += MASTER to info.archive
                for (group in info.index.groupIds) jobs += info.archive to group
            }
            jobs.parallelStream().forEach { (archive, group) ->
                val raw = cache.store.read(archive, group) ?: return@forEach
                val data =
                    if (archive == MASTER) {
                        raw
                    } else {
                        val length = Js5Container.containerLength(raw)
                        if (raw.size - length >= VERSION_TRAILER) raw.copyOf(length) else raw
                    }
                map[key(archive, group)] = encode(archive, group, data)
            }
            map[key(MASTER, MASTER)] = encode(MASTER, MASTER, masterIndex(cache))
            return Js5Responses(HashMap(map), crcs)
        }

        private fun masterIndex(cache: Js5Cache): ByteArray {
            val body = Unpooled.buffer(cache.archiveCount * MASTER_ENTRY_BYTES)
            for (slot in 0 until cache.archiveCount) {
                val info = cache.archive(slot)
                body.writeInt(info?.crc ?: 0)
                body.writeInt(info?.version ?: 0)
            }
            val out = ByteArray(CONTAINER_HEADER + body.readableBytes())
            Unpooled.wrappedBuffer(out).apply {
                writerIndex(0)
                writeByte(Js5Container.NONE)
                writeInt(body.readableBytes())
                writeBytes(body)
            }
            return out
        }

        private fun encode(archive: Int, group: Int, data: ByteArray): ByteBuf {
            val input = Unpooled.wrappedBuffer(data)
            val output = Unpooled.directBuffer(data.size + RESPONSE_HEADER + data.size / BLOCK + 1)
            Js5Service.prepareJs5Buffer(archive, group, input, output)
            return Unpooled.unreleasableBuffer(output)
        }
    }
}
