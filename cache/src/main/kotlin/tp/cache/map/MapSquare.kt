package tp.cache.map

import tp.cache.js5.XteaKey
import tp.util.buf.ReadBuf
import java.io.IOException

/** A 64x64 map square id: `(x shl 8) or z` with x, z in 0..255 (client "region id"; `m{x}_{z}` / `l{x}_{z}` groups). */
@JvmInline
value class MapSquareKey(val id: Int) {
    val x: Int get() = id ushr AXIS_BITS
    val z: Int get() = id and AXIS_MASK
    val baseX: Int get() = x * MapSquare.SIZE
    val baseZ: Int get() = z * MapSquare.SIZE

    override fun toString(): String = "MapSquare($x, $z)"

    companion object {
        private const val AXIS_BITS = 8
        private const val AXIS_MASK = 0xFF

        /** Map squares per axis (world is 256x256 squares of 64 tiles = 16384 tiles). */
        const val AXIS_COUNT = 256

        fun of(x: Int, z: Int): MapSquareKey = MapSquareKey((x shl AXIS_BITS) or z)
    }
}

/**
 * One decoded map square: per-tile settings for all 4 levels and the static loc spawns.
 *
 * [tileSettings] index = `(level * 64 + x) * 64 + z`, bit meanings in [TileSettings].
 * Locs are parallel arrays: [locIds] and [locInfo] (level, x, z, shape, angle packed by [packLoc]).
 */
class MapSquare(
    val key: MapSquareKey,
    val tileSettings: ByteArray,
    val locIds: IntArray,
    val locInfo: IntArray,
    /** Bytes of the undecoded terrain extension section (D-013), 0 when absent. */
    val terrainExtensionBytes: Int = 0,
) {
    val locCount: Int get() = locIds.size

    fun settings(level: Int, x: Int, z: Int): Int = tileSettings[tileIndex(level, x, z)].toInt() and 0xFF

    companion object {
        /** Tiles per map-square side. */
        const val SIZE = 64
        const val LEVELS = 4

        fun tileIndex(level: Int, x: Int, z: Int): Int = (level * SIZE + x) * SIZE + z

        // Loc info packing (ours): bits 0-5 z, 6-11 x, 12-13 level, 14-18 shape, 19-20 angle.
        private const val X_SHIFT = 6
        private const val LEVEL_SHIFT = 12
        private const val SHAPE_SHIFT = 14
        private const val ANGLE_SHIFT = 19
        private const val COORD_MASK = 0x3F
        private const val LEVEL_MASK = 0x3
        private const val SHAPE_MASK = 0x1F
        private const val ANGLE_MASK = 0x3

        fun packLoc(level: Int, x: Int, z: Int, shape: Int, angle: Int): Int =
            z or (x shl X_SHIFT) or (level shl LEVEL_SHIFT) or (shape shl SHAPE_SHIFT) or (angle shl ANGLE_SHIFT)

        fun locZ(info: Int): Int = info and COORD_MASK

        fun locX(info: Int): Int = (info ushr X_SHIFT) and COORD_MASK

        fun locLevel(info: Int): Int = (info ushr LEVEL_SHIFT) and LEVEL_MASK

        fun locShape(info: Int): Int = (info ushr SHAPE_SHIFT) and SHAPE_MASK

        fun locAngle(info: Int): Int = (info ushr ANGLE_SHIFT) and ANGLE_MASK
    }
}

/** Tile setting bits from the terrain file (RS Mod `MapTileDefinition`; client `Tiles_renderFlags`). */
object TileSettings {
    /** Tile is blocked for walking (water, cliffs...). */
    const val BLOCK_MAP_SQUARE = 0x1

    /** "Bridge": the tile column is shifted down one level for collision and rendering. Read from level 1 only. */
    const val LINK_BELOW = 0x2

    /** Roof removal area (client hides roofs while the player stands here). */
    const val REMOVE_ROOFS = 0x4
}

/**
 * Terrain file decoder. Per level, x, z: u16 opcodes until 0 (no height) or 1 (u8 height);
 * 2..49 overlay (+ i16 id), 50..81 settings = opcode - 49, >= 82 underlay = opcode - 81.
 * Source: RuneLite 1.13.6 `cache/.../loaders/MapLoader.java`; RS Mod `MapTileDecoder` (identical layout).
 *
 * Trailer (observed in cache 2757, unnamed by any reference — DECISIONS D-013, OPEN): one u8 after the tiles.
 * 0 = end. 1 = an extension section follows, which we keep only as a byte count. Older caches have no trailer.
 * Only settings are kept: the server needs no heights or floor types.
 */
object MapTerrainDecoder {
    private const val OP_END = 0
    private const val OP_HEIGHT = 1
    private const val OP_OVERLAY_LAST = 49
    private const val OP_SETTINGS_LAST = 81
    private const val SETTINGS_BASE = 49
    private const val TRAILER_NONE = 0
    private const val TRAILER_EXTENSION = 1

    /** Decoded settings plus the size of the undecoded extension section (0 when absent). */
    class Terrain(val settings: ByteArray, val extensionBytes: Int)

    fun decode(data: ByteArray): Terrain {
        val buf = ReadBuf(data)
        val settings = ByteArray(MapSquare.LEVELS * MapSquare.SIZE * MapSquare.SIZE)
        for (level in 0 until MapSquare.LEVELS) {
            for (x in 0 until MapSquare.SIZE) {
                for (z in 0 until MapSquare.SIZE) {
                    while (true) {
                        val opcode = buf.g2()
                        if (opcode == OP_END) break
                        if (opcode == OP_HEIGHT) {
                            buf.g1()
                            break
                        }
                        when {
                            opcode <= OP_OVERLAY_LAST -> {
                                buf.g2s()
                            }

                            opcode <= OP_SETTINGS_LAST -> {
                                settings[MapSquare.tileIndex(level, x, z)] = (opcode - SETTINGS_BASE).toByte()
                            }

                            else -> {
                                Unit
                            } // underlay id = opcode - 81, not needed
                        }
                    }
                }
            }
        }
        if (!buf.isReadable) return Terrain(settings, 0)
        return when (val trailer = buf.g1()) {
            TRAILER_NONE -> {
                if (buf.isReadable) throw IOException("${buf.remaining} bytes after terrain trailer 0") else Terrain(settings, 0)
            }

            TRAILER_EXTENSION -> {
                Terrain(settings, buf.remaining)
            }

            else -> {
                throw IOException("Unknown terrain trailer $trailer with ${buf.remaining} bytes left")
            }
        }
    }
}

/**
 * Loc file (`l{x}_{z}`) decoder: repeated (id delta: extended smart, then positions: smart deltas of
 * `level shl 12 | x shl 6 | z`, each followed by a u8 `shape shl 2 | angle`), both lists 0-terminated.
 * Source: RuneLite 1.13.6 `cache/.../loaders/LocationsLoader.java`; RS Mod `MapLocListDecoder`.
 */
object MapLocDecoder {
    private const val POS_X_SHIFT = 6
    private const val POS_LEVEL_SHIFT = 12
    private const val POS_COORD_MASK = 0x3F
    private const val POS_LEVEL_MASK = 0x3
    private const val ATTR_SHAPE_SHIFT = 2
    private const val ATTR_ANGLE_MASK = 0x3

    fun decode(data: ByteArray): Pair<IntArray, IntArray> {
        val buf = ReadBuf(data)
        var ids = IntArray(INITIAL)
        var infos = IntArray(INITIAL)
        var n = 0
        var id = -1
        while (true) {
            val idDelta = buf.gExtended1or2()
            if (idDelta == 0) break
            id += idDelta
            var pos = 0
            while (true) {
                val posDelta = buf.gSmart1or2()
                if (posDelta == 0) break
                pos += posDelta - 1
                val attrs = buf.g1()
                if (n == ids.size) {
                    ids = ids.copyOf(n * 2)
                    infos = infos.copyOf(n * 2)
                }
                ids[n] = id
                infos[n] =
                    MapSquare.packLoc(
                        level = (pos ushr POS_LEVEL_SHIFT) and POS_LEVEL_MASK,
                        x = (pos ushr POS_X_SHIFT) and POS_COORD_MASK,
                        z = pos and POS_COORD_MASK,
                        shape = attrs ushr ATTR_SHAPE_SHIFT,
                        angle = attrs and ATTR_ANGLE_MASK,
                    )
                n++
            }
        }
        if (buf.isReadable) throw IOException("${buf.remaining} unread bytes in loc file")
        return ids.copyOf(n) to infos.copyOf(n)
    }

    private const val INITIAL = 256
}

/** XTEA keys per map square, from OpenRS2 `keys.json`. Cache 2757 has none; the path stays (brief §1). */
class MapKeys(private val keys: Map<Int, XteaKey>) {
    operator fun get(square: MapSquareKey): XteaKey = keys[square.id] ?: XteaKey.ZERO

    val size: Int get() = keys.size

    companion object {
        val NONE: MapKeys = MapKeys(emptyMap())
    }
}
