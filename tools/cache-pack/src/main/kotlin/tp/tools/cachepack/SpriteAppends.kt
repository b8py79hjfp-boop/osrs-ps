package tp.tools.cachepack

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.name

/**
 * Frames appended to an existing sprite group (D-095), e.g. our chat icons after the 60 official `mod_icons` frames:
 * `pack/sprites/<group id>/` holds `base-frames.txt` (the frame count the group must have in the source cache, so a
 * cache update that adds frames fails the pack instead of shifting our indices) and `NN_name.png` files appended in name
 * order, where NN must equal the frame index each one gets (the server refers to them by that number).
 */
internal object SpriteAppends {
    /** Every pack's sprite directories, applied; one pack per group. */
    fun all(source: Js5Cache, packs: List<PackSource>): List<Pair<Int, ByteArray>> {
        val out = packs.flatMap { it.spriteDirs }.map { apply(source, it) }
        out.groupBy { it.first }.filterValues { it.size > 1 }.keys.firstOrNull()?.let {
            throw IOException("Sprite group $it is extended by more than one pack")
        }
        return out
    }

    fun apply(source: Js5Cache, dir: Path): Pair<Int, ByteArray> {
        val group = dir.name.toIntOrNull() ?: throw IOException("$dir: sprite directories are named by group id")
        val raw =
            source.readFiles(Js5Archive.SPRITES, group)?.data?.firstOrNull()
                ?: throw IOException("$dir: sprite group $group is not in the cache")
        val sprites = SpriteGroup.decode(raw)
        if (!sprites.encode().contentEquals(raw)) throw IOException("$dir: sprite group $group does not round-trip; refusing to append")
        val base = Files.readString(dir.resolve("base-frames.txt")).trim().toInt()
        if (sprites.frames.size != base) {
            throw IOException("$dir: group $group has ${sprites.frames.size} frames, base-frames.txt says $base (cache changed?)")
        }
        val pngs = Files.list(dir).use { s -> s.filter { it.extension == "png" }.sorted().toList() }
        for (png in pngs) {
            val wanted = png.name.substringBefore('_').toIntOrNull() ?: throw IOException("$png: name must start with its frame index")
            val image = ImageIO.read(png.toFile()) ?: throw IOException("$png: not a PNG")
            val index = sprites.append(image)
            if (index != wanted) throw IOException("$png: becomes frame $index, its name says $wanted")
        }
        return group to sprites.encode()
    }
}
