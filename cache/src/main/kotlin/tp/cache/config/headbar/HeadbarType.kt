// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.headbar

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/** Health bar config, archive 2 group 33. Sprites are render data and are dropped. */
class HeadbarType internal constructor(val id: Int) {
    /** Show priority (op 2, RS Mod `showPriority`); client default 255. */
    var showPriority: Int = DEFAULT_PRIORITY
        internal set

    /** Hide priority (op 3, RS Mod `hidePriority`); client default 255. */
    var hidePriority: Int = DEFAULT_PRIORITY
        internal set

    /** Fade-out cycles: op 11, or 0 via op 4; -1 when absent. */
    var fadeout: Int = -1
        internal set

    /** Cycles the bar stays after the last update (op 5, RS Mod `stickTime`); client default 70. */
    var stickTime: Int = DEFAULT_STICK_TIME
        internal set

    /** Bar width in segments (op 14, RuneLite `healthScale`); client default 30. */
    var segments: Int = DEFAULT_SEGMENTS
        internal set

    /** Padding in pixels (op 15). */
    var padding: Int = 0
        internal set

    companion object {
        /** Client `HeadbarType` default for show/hide priority. */
        const val DEFAULT_PRIORITY = 255

        /** Client `HeadbarType` default stick time (cycles). */
        const val DEFAULT_STICK_TIME = 70

        /** Client `HeadbarType` default segment count. */
        const val DEFAULT_SEGMENTS = 30
    }
}

/** Decoder for [HeadbarType]. Opcode table: RuneLite 1.13.6 `HealthBarLoader.java`, names from RS Mod `HeadbarTypeDecoder`. */
object HeadbarTypeDecoder : ConfigDecoder<HeadbarType> {
    override val name: String = "headbar"

    override fun decode(id: Int, buf: ReadBuf): HeadbarType {
        val t = HeadbarType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                // unknown u16
                1 -> {
                    buf.skip(2)
                }

                2 -> {
                    t.showPriority = buf.g1()
                }

                3 -> {
                    t.hidePriority = buf.g1()
                }

                4 -> {
                    t.fadeout = 0
                }

                5 -> {
                    t.stickTime = buf.g2()
                }

                // unknown u8
                6 -> {
                    buf.skip(1)
                }

                // front / back sprite (big smart)
                7, 8 -> {
                    buf.gSmart2or4Null()
                }

                11 -> {
                    t.fadeout = buf.g2()
                }

                14 -> {
                    t.segments = buf.g1()
                }

                15 -> {
                    t.padding = buf.g1()
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
