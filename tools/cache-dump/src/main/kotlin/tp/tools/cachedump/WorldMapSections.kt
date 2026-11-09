package tp.tools.cachedump

import tp.cache.js5.Js5Cache
import tp.util.buf.ReadBuf
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * The world map's patchwork (docs/mechanics/npc-spawns.md SP15). Each world map (archive 19, group 1 `compositemap`, one
 * file per map: the Wiki's `mapID`) draws game map squares and 8x8 zones at display positions that can differ from where
 * they are in the game (dungeons patched together). Writes one TSV row per piece:
 * `map kind display_square_x display_square_z display_zone_x display_zone_z source_square_x source_square_z source_zone_x
 * source_zone_z min_level levels`; `kind` is `square` (zones `-`) or `zone`.
 * Format: RuneLite 1.13.6 `WorldMapCompositeLoader` / `WorldMapDataLoader` (rev 238+ layout: no group/file ids).
 */
internal object WorldMapSections {
    private const val WORLDMAP_ARCHIVE = 19
    private const val COMPOSITE_GROUP = 1
    private const val TYPE_SQUARE = 0
    private const val TYPE_ZONE = 1

    fun write(cache: Js5Cache, out: Path) {
        val files = cache.readFiles(WORLDMAP_ARCHIVE, COMPOSITE_GROUP) ?: throw IOException("no world map composite group")
        val rows = ArrayList<String>()
        for ((i, map) in files.ids.withIndex()) {
            val buf = ReadBuf(files.data[i])
            repeat(buf.g2()) { rows += square(map, buf) }
            repeat(buf.g2()) { rows += zone(map, buf) }
        }
        val header =
            "map\tkind\tdisplay_square_x\tdisplay_square_z\tdisplay_zone_x\tdisplay_zone_z\tsource_square_x\tsource_square_z\t" +
                "source_zone_x\tsource_zone_z\tmin_level\tlevels"
        Files.write(out, listOf(header) + rows)
        println("${files.ids.size} world maps, ${rows.size} pieces -> $out")
    }

    private fun square(map: Int, buf: ReadBuf): String {
        check(buf.g1() == TYPE_SQUARE) { "map $map: expected a square piece" }
        val minLevel = buf.g1()
        val levels = buf.g1()
        val sx = buf.g2()
        val sz = buf.g2()
        val dx = buf.g2()
        val dz = buf.g2()
        return listOf(map, "square", dx, dz, "-", "-", sx, sz, "-", "-", minLevel, levels).joinToString("\t")
    }

    private fun zone(map: Int, buf: ReadBuf): String {
        check(buf.g1() == TYPE_ZONE) { "map $map: expected a zone piece" }
        val minLevel = buf.g1()
        val levels = buf.g1()
        val sx = buf.g2()
        val sz = buf.g2()
        val szx = buf.g1()
        val szz = buf.g1()
        val dx = buf.g2()
        val dz = buf.g2()
        val dzx = buf.g1()
        val dzz = buf.g1()
        return listOf(map, "zone", dx, dz, dzx, dzz, sx, sz, szx, szz, minLevel, levels).joinToString("\t")
    }
}
