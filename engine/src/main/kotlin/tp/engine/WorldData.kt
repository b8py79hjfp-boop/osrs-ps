package tp.engine

import org.rsmod.routefinder.collision.CollisionFlagMap
import tp.cache.config.Configs
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.engine.map.StaticLocs
import java.nio.file.Path

/** Immutable cache-derived data a world runs on; built once per JVM and shared by every world (brief §7). */
class WorldData(
    val configs: Configs,
    val collision: CollisionFlagMap,
    val locs: StaticLocs,
    /** Glyph advances (256) of the dialogue font `q8_full` (RuneLite `FontID.QUILL_8`); empty if absent. */
    val dialogueFont: IntArray = IntArray(0),
    /** Obj gameval names (cache archive 24 group 0: file id = obj id, UTF-8 name), e.g. 440 -> `iron_ore`; empty if absent. */
    val objGamevals: Map<Int, String> = emptyMap(),
    /** Client script id -> its argument counts (int, string, long), from each script's trailer; empty if not loaded. */
    val scriptArgs: Map<Int, IntArray> = emptyMap(),
) {
    /**
     * Why [args] don't fit client script [id] (wrong count or kinds), or null. The client drops such a call silently, so
     * the server logs it (a broken interface otherwise only shows as an empty or stuck window).
     */
    fun scriptArgMismatch(id: Int, args: List<Any>): String? {
        val want = scriptArgs[id] ?: return if (scriptArgs.isEmpty()) null else "client script $id does not exist"
        val ints = args.count { it is Int }
        val strings = args.count { it is String }
        val longs = args.count { it is Long }
        if (ints == want[0] && strings == want[1] && longs == want[2]) return null
        return "client script $id takes ${want[0]} int, ${want[1]} string, ${want[2]} long args; sent $ints / $strings / $longs"
    }

    companion object {
        /** Cache archive of font metrics (RuneLite `FontLoader`: 256 advance bytes + ascent). */
        private const val FONTS_ARCHIVE = 13
        private const val QUILL_8 = 497
        private const val GLYPHS = 256

        private fun font(cache: Js5Cache, id: Int): IntArray {
            val data = cache.readFiles(FONTS_ARCHIVE, id)?.data?.firstOrNull() ?: return IntArray(0)
            if (data.size < GLYPHS) return IntArray(0)
            return IntArray(GLYPHS) { data[it].toInt() and BYTE_MASK }
        }

        private const val BYTE_MASK = 0xFF

        /** Cache archive of gameval names and its obj group (symbol-gen `FLAT`: group 0 = `Objs`). */
        private const val GAMEVALS_ARCHIVE = 24
        private const val GAMEVAL_OBJS = 0

        fun objGamevals(cache: Js5Cache): Map<Int, String> {
            val files = cache.readFiles(GAMEVALS_ARCHIVE, GAMEVAL_OBJS) ?: return emptyMap()
            return (0 until files.size).associate { files.ids[it] to String(files.data[it], Charsets.UTF_8) }
        }

        /** Cache archive of client scripts. */
        private const val SCRIPTS_ARCHIVE = 12

        /** The script trailer (RuneLite `ScriptLoader`, rev 237+): u16 switch-table length last, before it the tables. */
        private const val TRAILER_SWITCH_LENGTH = 2

        /** Before the switch tables: instruction count (4) and six u16s: local int/string/long, then arg int/string/long. */
        private const val TRAILER_FIXED = 16
        private const val ARGS_OFFSET = 10

        /** [scriptArgs] for every script in [cache]; a group too short to hold a trailer is skipped. */
        fun scriptArgs(cache: Js5Cache): Map<Int, IntArray> {
            val index = cache.archive(SCRIPTS_ARCHIVE)?.index ?: return emptyMap()
            val out = HashMap<Int, IntArray>(index.groupIds.size * 2)
            for (id in index.groupIds) {
                val data = cache.readFiles(SCRIPTS_ARCHIVE, id)?.data?.firstOrNull() ?: continue
                if (data.size < TRAILER_FIXED + TRAILER_SWITCH_LENGTH) continue
                val switchLength = u16(data, data.size - TRAILER_SWITCH_LENGTH)
                val fixed = data.size - TRAILER_SWITCH_LENGTH - switchLength - TRAILER_FIXED
                if (fixed < 0) continue
                val at = fixed + ARGS_OFFSET
                out[id] = intArrayOf(u16(data, at), u16(data, at + 2), u16(data, at + 4))
            }
            return out
        }

        private fun u16(b: ByteArray, at: Int): Int =
            ((b[at].toInt() and BYTE_MASK) shl Byte.SIZE_BITS) or (b[at + 1].toInt() and BYTE_MASK)

        /** The dialogue font's glyph advances ([dialogueFont]). */
        fun dialogueFont(cache: Js5Cache): IntArray = font(cache, QUILL_8)

        /** Decodes configs, map, collision and the loc index from an open cache (keys.json beside the disk dir). */
        fun load(cache: Js5Cache, cacheDir: Path): WorldData {
            val configs = Configs.load(cache)
            val map = GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))
            return WorldData(
                configs,
                CollisionBuilder.build(map, configs.locs),
                StaticLocs.build(map, configs.locs),
                dialogueFont(cache),
                objGamevals(cache),
                scriptArgs(cache),
            )
        }
    }
}
