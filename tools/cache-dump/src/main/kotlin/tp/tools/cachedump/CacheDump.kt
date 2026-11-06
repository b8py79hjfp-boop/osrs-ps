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
      interface <id>                       every component of an interface
      map <squareX> <squareZ>              square summary, level-0 tile settings, loc list
      collision <x> <z> <level> [radius]   ASCII collision render around a tile (default radius 16)
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

private fun dispatch(command: String, args: List<String>, cache: Js5Cache, cacheDir: Path) {
    when (command) {
        "summary" -> {
            printSummary(cache, cacheDir)
        }

        "config" -> {
            printConfig(cache, args)
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

        "enums-with-keys" -> {
            val keys = args.map { it.toIntOrNull() ?: throw UsageException("keys must be integers") }.toSet()
            val enums = tp.cache.config.Configs.load(cache).enums
            for (e in enums) {
                val k = e.intValues.keys + e.stringValues.keys
                if (k.containsAll(keys)) println("enum ${e.id} in=${e.inputType.toChar()} out=${e.outputType.toChar()} size=${e.size}: " + (e.intValues.entries.take(40) + e.stringValues.entries.take(40)).joinToString())
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
