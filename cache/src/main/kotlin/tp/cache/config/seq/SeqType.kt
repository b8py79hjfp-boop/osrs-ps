// Opcode literals in the `when` table are the cache format itself; each is named by its inline comment / KDoc.

package tp.cache.config.seq

import tp.cache.config.ConfigDecoder
import tp.cache.config.UnknownOpcodeException
import tp.util.buf.ReadBuf

/**
 * Animation sequence config, archive 2 group 12. Field names follow RuneLite 1.13.6 `SequenceDefinition`; RS Mod
 * names are given where they differ. Sounds, chat frames and held-item swaps are consumed and dropped.
 *
 * Time units: one client cycle is 20 ms and one game tick is 600 ms (30 cycles), so [frameLengths] are client cycles.
 * Skeletal (keyframe) sequences have no frame table; their length is the keyframe range, whose cycle unit is not
 * documented by any reference, so no tick duration is derived for them (OPEN).
 */
class SeqType internal constructor(val id: Int) {
    /** Per-frame delay in client cycles (op 1), or null for skeletal / empty sequences. */
    var frameLengths: IntArray? = null
        internal set

    /** Frame group of each frame (op 1, first id array; RuneLite stores it in the low 16 bits of `frameIDs`). */
    var frameGroups: IntArray? = null
        internal set

    /** Frame index within its group (op 1, second id array; RuneLite stores it in the high 16 bits of `frameIDs`). */
    var frameIndices: IntArray? = null
        internal set

    /** Op 2 (RuneLite `frameStep`, RS Mod `loops`); -1 when absent. */
    var frameStep: Int = -1
        internal set

    /** Op 3: frame indices (terminated by [INTERLEAVE_END]) used when merging with a walk animation; null when absent. */
    var interleaveLeave: IntArray? = null
        internal set

    /** Op 4. */
    var stretches: Boolean = false
        internal set

    /** Op 5 (RS Mod `priority`): priority when sequences compete (RuneLite `forcedPriority`, default 5). */
    var forcedPriority: Int = DEFAULT_FORCED_PRIORITY
        internal set

    /** Op 8: loop cap; client default 99. */
    var maxLoops: Int = DEFAULT_MAX_LOOPS
        internal set

    /** Op 9 (RS Mod `preanimMove`): movement behaviour while this plays; -1 when absent. */
    var precedenceAnimating: Int = -1
        internal set

    /** Op 10 (RS Mod `postanimMove`): movement behaviour after it ends; -1 when absent. */
    var priority: Int = -1
        internal set

    /** Op 11 (RS Mod `duplicateBehaviour`): what to do when the same sequence restarts; client default 2. */
    var replyMode: Int = DEFAULT_REPLY_MODE
        internal set

    /** Op 13: skeletal animation (keyframe set) id, or -1. */
    var keyframeSet: Int = -1
        internal set

    /** Op 15: first keyframe of the skeletal range, or -1. */
    var keyframeStart: Int = -1
        internal set

    /** Op 15: end of the skeletal range, or -1. */
    var keyframeEnd: Int = -1
        internal set

    /** Op 17: skeletal bone-group ids that take part in walk merging; null when absent. */
    var keyframeWalkMerge: IntArray? = null
        internal set

    /** Op 18 debug name, or null. */
    var debugName: String? = null
        internal set

    /** Op 19: sounds play across world views. */
    var soundsCrossWorldView: Boolean = false
        internal set

    /** Sum of [frameLengths] in client cycles (20 ms each); 0 when there is no frame table. */
    val totalFrameCycles: Int get() = frameLengths?.sum() ?: 0

    companion object {
        /** Sentinel appended to [interleaveLeave] by the client (`SeqType` op 3). */
        const val INTERLEAVE_END = 9_999_999

        /** RuneLite `SequenceDefinition.forcedPriority` default. */
        const val DEFAULT_FORCED_PRIORITY = 5

        /** RuneLite `SequenceDefinition.maxLoops` default. */
        const val DEFAULT_MAX_LOOPS = 99

        /** RuneLite `SequenceDefinition.replyMode` default. */
        const val DEFAULT_REPLY_MODE = 2
    }
}

/**
 * Decoder for [SeqType]. Opcode table: RuneLite 1.13.6 `SequenceLoader.java` with `rev220FrameSounds` and `rev226`
 * both true (op 13 = maya id, 14 = frame sounds with weight byte, 15 = maya range), cross-checked with RS Mod
 * `SeqTypeDecoder` (rev 233), which agrees on every opcode.
 */
object SeqTypeDecoder : ConfigDecoder<SeqType> {
    override val name: String = "seq"

    /** Op 14 frame sound entry: frame u16, sound id u16, weight u8, loops u8, location u8, retain u8. */
    private const val FRAME_SOUND_BYTES = 8

    override fun decode(id: Int, buf: ReadBuf): SeqType {
        val t = SeqType(id)
        while (true) {
            val opcode = buf.g1()
            if (opcode == 0) break
            when (opcode) {
                1 -> {
                    val count = buf.g2()
                    t.frameLengths = IntArray(count) { buf.g2() }
                    t.frameGroups = IntArray(count) { buf.g2() }
                    t.frameIndices = IntArray(count) { buf.g2() }
                }

                2 -> {
                    t.frameStep = buf.g2()
                }

                3 -> {
                    val count = buf.g1()
                    val ids = IntArray(count + 1)
                    for (i in 0 until count) ids[i] = buf.g1()
                    ids[count] = SeqType.INTERLEAVE_END
                    t.interleaveLeave = ids
                }

                4 -> {
                    t.stretches = true
                }

                5 -> {
                    t.forcedPriority = buf.g1()
                }

                // left / right hand item
                6, 7 -> {
                    buf.skip(2)
                }

                8 -> {
                    t.maxLoops = buf.g1()
                }

                9 -> {
                    t.precedenceAnimating = buf.g1()
                }

                10 -> {
                    t.priority = buf.g1()
                }

                11 -> {
                    t.replyMode = buf.g1()
                }

                // chat (interface) frames: u8 count, count u16 + count u16
                12 -> {
                    buf.skip(buf.g1() * 4)
                }

                13 -> {
                    t.keyframeSet = buf.g4()
                }

                14 -> {
                    buf.skip(buf.g2() * FRAME_SOUND_BYTES)
                }

                15 -> {
                    t.keyframeStart = buf.g2()
                    t.keyframeEnd = buf.g2()
                }

                // vertical offset
                16 -> {
                    buf.skip(1)
                }

                17 -> {
                    val count = buf.g1()
                    t.keyframeWalkMerge = IntArray(count) { buf.g1() }
                }

                18 -> {
                    t.debugName = buf.gjstr()
                }

                19 -> {
                    t.soundsCrossWorldView = true
                }

                else -> {
                    throw UnknownOpcodeException(name, id, opcode, buf.position - 1)
                }
            }
        }
        return t
    }
}
