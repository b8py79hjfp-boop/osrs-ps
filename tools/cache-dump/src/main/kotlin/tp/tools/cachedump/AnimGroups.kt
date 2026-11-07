package tp.tools.cachedump

import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes the raw data for NPC combat animations (docs/mechanics/npc-combat.md N10): every seq's skeleton group (the
 * frame archive of its first frame, or its keyframe set) and every NPC's stand / walk seqs. tools/wiki-import/npc_anims.py
 * joins them with the seq gameval names. One TSV each.
 */
internal object AnimGroups {
    fun write(cache: Js5Cache, out: Path) {
        val configs = Configs.load(cache)
        Files.createDirectories(out)
        val skeletons = HashMap<Int, Int>()

        // A frame archive's files start with the u16 id of their frame base (skeleton): seqs of one skeleton share it.
        fun skeleton(archive: Int): Int =
            skeletons.getOrPut(archive) {
                val first = cache.readFiles(FRAMES_ARCHIVE, archive)?.data?.firstOrNull()
                if (first == null || first.size < 2) -1 else ((first[0].toInt() and BYTE) shl BYTE_BITS) or (first[1].toInt() and BYTE)
            }
        Files.newBufferedWriter(out.resolve("seq-groups.tsv")).use { w ->
            w.write("seq\tgroup\tframes\n")
            for (seq in configs.seqs) {
                // Opcode 1 stores frame files, then their archives (the high 16 bits): the decoder's `frameIndices` are archives.
                val group =
                    seq.frameIndices
                        ?.firstOrNull()
                        ?.let(::skeleton)
                        ?.takeIf { it >= 0 }
                        ?: seq.keyframeSet.takeIf { it >= 0 }?.let { KEYFRAME_BASE + it }
                w.write("${seq.id}\t${group ?: -1}\t${seq.totalFrameCycles}\n")
            }
        }
        Files.newBufferedWriter(out.resolve("npc-seqs.tsv")).use { w ->
            w.write("npc\tname\tstand\twalk\n")
            for (npc in configs.npcs) w.write("${npc.id}\t${npc.name}\t${npc.standSeq}\t${npc.walkSeq}\n")
        }
    }

    /** JS5 archive 0: animation frames. */
    private const val FRAMES_ARCHIVE = 0
    private const val BYTE = 0xFF
    private const val BYTE_BITS = 8

    /** Keyframe-set skeletons are kept apart from frame bases. */
    private const val KEYFRAME_BASE = 1_000_000
}
