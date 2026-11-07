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
) {
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
            )
        }
    }
}
