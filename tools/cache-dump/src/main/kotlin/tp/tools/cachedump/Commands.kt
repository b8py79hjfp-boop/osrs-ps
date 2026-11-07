package tp.tools.cachedump

import org.rsmod.routefinder.flag.CollisionFlag
import tp.cache.config.Configs
import tp.cache.interfaces.InterfaceTypes
import tp.cache.js5.Js5Cache
import tp.cache.map.CollisionBuilder
import tp.cache.map.GameMap
import tp.cache.map.MapKeysLoader
import tp.cache.map.MapSquare
import tp.cache.map.MapSquareKey
import tp.cache.map.TileSettings
import java.nio.file.Path

/** `keys.json` sits next to the `disk` directory (`.data/cache/<rev>/keys.json`). */
private fun loadMap(cache: Js5Cache, cacheDir: Path): GameMap =
    GameMap.load(cache, MapKeysLoader.load(cacheDir.resolveSibling("keys.json")))

internal fun printSummary(cache: Js5Cache, cacheDir: Path) {
    println("archives: ${cache.populatedArchiveCount} populated of ${cache.archiveCount} slots, ${cache.groupCount} groups")
    cache.archives.forEachIndexed { slot, info ->
        if (info == null) {
            println("  [$slot] empty")
        } else {
            println(
                "  [$slot] crc=${info.crc} version=${info.version} groups=${info.index.groupCount}",
            )
        }
    }
    println("configs:")
    Configs.load(cache).summary().forEach { (name, count) -> println("  $name = $count") }
    val interfaces = InterfaceTypes.load(cache)
    println(
        "interfaces: ${interfaces.interfaceCount} interfaces, ${interfaces.componentCount} components " +
            "(${interfaces.if3ComponentCount} IF3)",
    )
    val map = loadMap(cache, cacheDir)
    println("map: ${map.squares.size} squares, ${map.locCount} locs")
    println("  skippedGroups = ${map.skippedGroups}")
    println("  extendedSquares = ${map.extendedSquares}")
}

internal fun printInterface(cache: Js5Cache, interfaceId: Int) {
    val interfaces = InterfaceTypes.load(cache)
    val type = interfaces[interfaceId] ?: throw UsageException("interface $interfaceId does not exist")
    println("interface $interfaceId: ${type.components.size} components")
    for (component in type.components.values.sortedBy { it.componentId }) {
        println(
            "packed=${component.packedId} component=${component.componentId} type=${component.type} " +
                "isIf3=${component.isIf3} parent=${component.parentId} hidden=${component.isHidden} " +
                "actions=${formatValue(component.actions)} text=\"${component.text}\"",
        )
    }
}

/** Legend glyph for a nonzero tile-settings value above the single hex digit range. */
private const val SETTINGS_OVERFLOW_GLYPH = '*'

private const val HEX_RADIX = 16

private fun settingsGlyph(settings: Int): Char =
    when {
        settings == 0 -> '.'
        settings < HEX_RADIX -> Character.forDigit(settings, HEX_RADIX)
        else -> SETTINGS_OVERFLOW_GLYPH
    }

internal fun printMap(cache: Js5Cache, cacheDir: Path, squareX: Int, squareZ: Int) {
    val configs = Configs.load(cache)
    val key = MapSquareKey.of(squareX, squareZ)
    val square = loadMap(cache, cacheDir).squares.firstOrNull { it.key == key } ?: throw UsageException("$key is not in the cache")
    println("$key id=${key.id} base=(${key.baseX}, ${key.baseZ}) locs=${square.locCount} extensionBytes=${square.terrainExtensionBytes}")
    println(
        "level 0 tile settings (north at top; hex digit = bit mask: ${TileSettings.BLOCK_MAP_SQUARE}=block, " +
            "${TileSettings.LINK_BELOW}=bridge, ${TileSettings.REMOVE_ROOFS}=roof; * = value above 15; . = 0):",
    )
    for (z in MapSquare.SIZE - 1 downTo 0) {
        println(String(CharArray(MapSquare.SIZE) { x -> settingsGlyph(square.settings(0, x, z)) }))
    }
    println("locs: id name x z level shape angle")
    for (i in 0 until square.locCount) {
        val info = square.locInfo[i]
        val id = square.locIds[i]
        println(
            "  $id \"${configs.locs[id]?.name ?: "?"}\" ${square.key.baseX + MapSquare.locX(info)} " +
                "${square.key.baseZ + MapSquare.locZ(info)} ${MapSquare.locLevel(info)} " +
                "${MapSquare.locShape(info)} ${MapSquare.locAngle(info)}",
        )
    }
}

/** Every wall-edge bit (the eight direction flags, 0x1..0x80). */
private const val WALL_EDGE_MASK = 0xFF

private const val UNALLOCATED = -1

private fun collisionGlyph(flags: Int): Char =
    when {
        flags == UNALLOCATED -> '?'
        flags and CollisionFlag.BLOCK_WALK != 0 -> '~'
        flags and CollisionFlag.LOC != 0 -> 'O'
        flags and WALL_EDGE_MASK != 0 -> '+'
        flags and CollisionFlag.GROUND_DECOR != 0 -> 'd'
        else -> '.'
    }

internal fun printCollision(cache: Js5Cache, cacheDir: Path, x: Int, z: Int, level: Int, radius: Int) {
    val configs = Configs.load(cache)
    val flags = CollisionBuilder.build(loadMap(cache, cacheDir), configs.locs)
    println("collision around ($x, $z, $level) radius $radius; ? unallocated, ~ block walk, O loc, + wall edge, d ground decor, . free")
    for (rowZ in z + radius downTo z - radius) {
        println(String(CharArray(2 * radius + 1) { col -> collisionGlyph(flags[x - radius + col, rowZ, level]) }))
    }
    println("centre flags = 0x${Integer.toHexString(flags[x, z, level])}")
}

internal fun printDbRows(cache: Js5Cache, tableId: Int) {
    val configs = Configs.load(cache)
    for (row in configs.dbRows) {
        if (row.tableId != tableId) continue
        println(
            "${row.id}: " +
                row.columns.entries
                    .sortedBy { it.key }
                    .joinToString(" ") { (col, v) -> "$col=${formatValue(v)}" },
        )
    }
}

/** Every placement of a loc named [name] in the map: `x z level id`, sorted. */
internal fun findLocs(cache: Js5Cache, cacheDir: Path, name: String) {
    val configs = Configs.load(cache)
    val map = loadMap(cache, cacheDir)
    val rows = ArrayList<String>()
    for (square in map.squares) {
        for (i in 0 until square.locCount) {
            val id = square.locIds[i]
            if (configs.locs[id]?.name != name) continue
            val info = square.locInfo[i]
            rows += "${square.key.baseX + MapSquare.locX(info)} ${square.key.baseZ + MapSquare.locZ(info)} ${MapSquare.locLevel(info)} $id"
        }
    }
    rows.sorted().forEach(::println)
    println("${rows.size} placements")
}

/** Tile key packing for [flood]: x above 20 bits of z. */
private const val PACK_SHIFT = 20

/** Walkable area reachable from a tile (4-way steps, within [radius]): bounding box and tile count. */
internal fun flood(cache: Js5Cache, cacheDir: Path, x: Int, z: Int, level: Int, radius: Int) {
    val configs = Configs.load(cache)
    val flags = CollisionBuilder.build(loadMap(cache, cacheDir), configs.locs)
    val steps = org.rsmod.routefinder.StepValidator(flags)
    val seen = HashSet<Long>()
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(x to z)
    seen.add((x.toLong() shl PACK_SHIFT) or z.toLong())
    var minX = x
    var maxX = x
    var minZ = z
    var maxZ = z
    while (queue.isNotEmpty()) {
        val (cx, cz) = queue.removeFirst()
        minX = minOf(minX, cx)
        maxX = maxOf(maxX, cx)
        minZ = minOf(minZ, cz)
        maxZ = maxOf(maxZ, cz)
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val nx = cx + dx
            val nz = cz + dz
            if (kotlin.math.abs(nx - x) > radius || kotlin.math.abs(nz - z) > radius) continue
            if (!steps.canTravel(level, cx, cz, dx, dz)) continue
            if (seen.add((nx.toLong() shl PACK_SHIFT) or nz.toLong())) queue.add(nx to nz)
        }
    }
    println("area from ($x, $z, $level): x $minX..$maxX z $minZ..$maxZ, ${seen.size} tiles")
}
