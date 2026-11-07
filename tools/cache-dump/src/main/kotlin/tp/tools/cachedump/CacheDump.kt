package tp.tools.cachedump

import tp.cache.js5.Js5Cache
import tp.util.Revision
import java.nio.file.Path
import kotlin.system.exitProcess

private const val CACHE_DIR_FLAG = "--cache-dir"
private const val ALL_FLAG = "--all"

private val USAGE =
    """
    usage: cache-dump [--cache-dir <dir>] <command> [args]
      summary                              archives, config counts, interfaces, map squares
      config <type> <id>                   one decoded config as `field = value` lines
      config <type> --all                  one line per id with the key fields
      dbrows <table>                       every row of a db table, one line per row (`id: col=values ...`)
      interface <id>                       every component of an interface
      map <squareX> <squareZ>              square summary, level-0 tile settings, loc list
      collision <x> <z> <level> [radius]   ASCII collision render around a tile (default radius 16)
      loc-links <dir>                      doors.tsv (door/gate pairs) and climbs.tsv (ladder/stair links)
      find-locs <name>                     every placement of locs with this exact name (x z level id)
    default cache dir: ${defaultCacheDir()}
    """.trimIndent()

/** Failure with a user-facing message; printed without a stack trace. */
internal class UsageException(message: String) : RuntimeException(message)

/** `.data/cache/<rev>/disk` relative to the working directory (Gradle's `run` uses the repo root). */
internal fun defaultCacheDir(): Path = Path.of(".data", "cache", Revision.NUMBER.toString(), "disk")

fun main(args: Array<String>) {
    exitProcess(run(args.toList()))
}

private fun run(args: List<String>): Int {
    try {
        val (cacheDir, rest) = extractCacheDir(args)
        val command = rest.firstOrNull()
        if (command == null || command == "help" || command == "--help") {
            println(USAGE)
            return if (command == null) 1 else 0
        }
        Js5Cache.open(cacheDir).use { cache -> dispatch(command, rest.drop(1), cache, cacheDir) }
        return 0
    } catch (e: UsageException) {
        System.err.println("error: ${e.message}")
        System.err.println(USAGE)
        return 2
    }
}

private fun extractCacheDir(args: List<String>): Pair<Path, List<String>> {
    val index = args.indexOf(CACHE_DIR_FLAG)
    if (index < 0) return defaultCacheDir() to args
    val value = args.getOrNull(index + 1) ?: throw UsageException("$CACHE_DIR_FLAG needs a value")
    return Path.of(value) to (args.take(index) + args.drop(index + 2))
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // one branch per command; each delegates in a line or two
private fun dispatch(command: String, args: List<String>, cache: Js5Cache, cacheDir: Path) {
    when (command) {
        "runelite-static" -> {
            runeliteStatic(cache, args)
        }

        "flood" -> {
            val radius = if (args.size > FLOOD_RADIUS_ARG) intArg(args, FLOOD_RADIUS_ARG, "radius") else FLOOD_RADIUS
            flood(cache, cacheDir, intArg(args, 0, "x"), intArg(args, 1, "z"), intArg(args, 2, "level"), radius)
        }

        "find-locs" -> {
            findLocs(cache, cacheDir, args.joinToString(" ").ifBlank { throw UsageException("missing loc name") })
        }

        "loc-links" -> {
            LocLinks.write(cache, cacheDir, Path.of(args.getOrNull(0) ?: throw UsageException("missing output dir")))
        }

        "pvp-safe-zones" -> {
            SafeZones.write(cache, cacheDir, Path.of(args.getOrNull(0) ?: throw UsageException("missing output path")))
        }

        "anim-groups" -> {
            AnimGroups.write(cache, Path.of(args.getOrNull(0) ?: throw UsageException("missing output dir")))
        }

        "overlays" -> {
            printOverlays(cache, intArg(args, 0, "squareX"), intArg(args, 1, "squareZ"))
        }

        "summary" -> {
            printSummary(cache, cacheDir)
        }

        "config" -> {
            printConfig(cache, args)
        }

        "dbrows" -> {
            printDbRows(cache, intArg(args, 0, "table id"))
        }

        "interface" -> {
            printInterface(cache, intArg(args, 0, "interface id"))
        }

        "map" -> {
            printMap(cache, cacheDir, intArg(args, 0, "squareX"), intArg(args, 1, "squareZ"))
        }

        "collision" -> {
            printCollision(
                cache,
                cacheDir,
                intArg(args, 0, "x"),
                intArg(args, 1, "z"),
                intArg(args, 2, "level"),
                if (args.size > 3) intArg(args, 3, "radius") else DEFAULT_RADIUS,
            )
        }

        "op-verbs" -> {
            val c =
                tp.cache.config.Configs
                    .load(cache)

            fun top(name: String, texts: Sequence<String>) {
                val counts =
                    texts
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                println("$name (${counts.size} distinct): " + counts.take(VERB_PREVIEW).joinToString { "${it.key}=${it.value}" })
            }
            top("obj iops", c.objs.asSequence().flatMap { it.iops.asSequence().filterNotNull() })
            top(
                "obj ground ops",
                c.objs.asSequence().flatMap {
                    it.groundOps.ops
                        .asSequence()
                        .filterNotNull()
                },
            )
            top(
                "npc ops",
                c.npcs.asSequence().flatMap {
                    it.ops.ops
                        .asSequence()
                        .filterNotNull()
                },
            )
            top(
                "loc ops",
                c.locs.asSequence().flatMap {
                    it.ops.ops
                        .asSequence()
                        .filterNotNull()
                },
            )
        }

        "enums-with-keys" -> {
            val keys = args.map { it.toIntOrNull() ?: throw UsageException("keys must be integers") }.toSet()
            val enums =
                tp.cache.config.Configs
                    .load(cache)
                    .enums
            for (e in enums) {
                val k = e.intValues.keys + e.stringValues.keys
                if (k.containsAll(keys)) {
                    println(
                        "enum ${e.id} in=${e.inputType.toChar()} out=${e.outputType.toChar()} size=${e.size}: " +
                            (e.intValues.entries.take(ENUM_PREVIEW) + e.stringValues.entries.take(ENUM_PREVIEW)).joinToString(),
                    )
                }
            }
        }

        else -> {
            throw UsageException("unknown command '$command'")
        }
    }
}

private fun intArg(args: List<String>, index: Int, name: String): Int {
    val text = args.getOrNull(index) ?: throw UsageException("missing $name")
    return text.toIntOrNull() ?: throw UsageException("$name must be an integer, got '$text'")
}

/** Verbs printed per kind by `op-verbs`. */
private const val VERB_PREVIEW = 30

/** Entries printed per enum by `enums-with-keys`. */
private const val ENUM_PREVIEW = 40

/** Flood-fill cap in tiles when none is given. */
private const val FLOOD_RADIUS = 48

/** Argument index of the optional flood radius (after x, z, level). */
private const val FLOOD_RADIUS_ARG = 3

/** Collision render radius in tiles when none is given. */
private const val DEFAULT_RADIUS = 16

private fun printConfig(cache: Js5Cache, args: List<String>) {
    val configs =
        tp.cache.config.Configs
            .load(cache)
    val printers = configPrinters(configs)
    val typeName = args.getOrNull(0) ?: throw UsageException("missing config type (${printers.keys.joinToString("|")})")
    val printer = printers[typeName] ?: throw UsageException("unknown config type '$typeName' (${printers.keys.joinToString("|")})")
    if (args.getOrNull(1) == ALL_FLAG) {
        printer.all().forEach(::println)
        return
    }
    val id = intArg(args, 1, "id or $ALL_FLAG")
    val lines = printer.one(id) ?: throw UsageException("$typeName $id does not exist")
    lines.forEach(::println)
}

/** RuneLite static data for the release client (a standalone hub serves this directory at /runelite/static). */
private fun runeliteStatic(cache: Js5Cache, args: List<String>) {
    val dir = Path.of(args.getOrNull(0) ?: ".data/runelite-static")
    tp.cache.export.RuneLiteStatic
        .write(
            tp.cache.config.Configs
                .load(cache),
            dir,
        )
    println("wrote RuneLite static data to $dir")
}

/** Level-0 overlay config ids of one map square, north at top (`.` = none; ids in hex, two digits). */
private fun printOverlays(cache: Js5Cache, squareX: Int, squareZ: Int) {
    val files = cache.readFiles(MAPS_ARCHIVE, (squareX shl SQUARE_SHIFT) or squareZ) ?: return println("no square $squareX,$squareZ")
    val terrain =
        tp.cache.map.MapTerrainDecoder
            .decode(files.data[files.ids.indexOf(0)], keepOverlays0 = true)
    val overlays = terrain.overlays0 ?: return
    val ids = sortedSetOf<Int>()
    for (z in tp.cache.map.MapSquare.SIZE - 1 downTo 0) {
        val row = StringBuilder()
        for (x in 0 until tp.cache.map.MapSquare.SIZE) {
            val v = overlays[x * tp.cache.map.MapSquare.SIZE + z].toInt()
            if (v == 0) row.append(" .") else row.append("%2x".format(v - 1)).also { ids += v - 1 }
        }
        println(row)
    }
    println("overlay ids: $ids")
}

private const val MAPS_ARCHIVE = 5
private const val SQUARE_SHIFT = 8
