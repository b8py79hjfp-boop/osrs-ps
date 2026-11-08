package tp.tools.cachepack

import tp.util.Revision
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

private val USAGE =
    """
    usage: cache-pack [pack|verify] [options]
      pack      (default) pack the content modules' pack/ sources into a copy of the source cache, then verify it
      verify    verify an existing packed cache against the source cache
      script <disk> <id>   list the instructions of one compiled script (core opcode names, commands by number)
    options:
      --source <dir>     source cache directory with main_file_cache.* (default .data/cache/${Revision.NUMBER}/disk); never modified
      --out <dir>        output root (default .data/cache/${Revision.NUMBER}-packed):
                         disk/, keys.json, custom-symbols.tsv, pack.properties, work/
      --pack-dir <dir>   an extra pack/ directory (repeatable); content/<module>/src/main/resources/pack is always searched
      --repo <dir>       repository root for the content search (default: the working directory)
    """.trimIndent()

private class UsageException(message: String) : RuntimeException(message)

fun main(args: Array<String>) {
    exitProcess(run(args.toList()))
}

private fun run(args: List<String>): Int {
    try {
        val command = args.firstOrNull()?.takeIf { !it.startsWith("--") } ?: "pack"
        val rest = if (args.firstOrNull() == command) args.drop(1) else args
        if (command == "help" || "--help" in rest) {
            println(USAGE)
            return 0
        }
        if (command == "script") {
            dumpScript(rest.getOrNull(0) ?: throw UsageException("script needs <disk> <id>"), rest.getOrNull(1)?.toIntOrNull())
            return 0
        }
        val options = parseOptions(rest)
        val repo = options.single("--repo")?.let(Path::of) ?: Path.of("")
        val source = options.single("--source")?.let(Path::of) ?: Path.of(".data", "cache", Revision.NUMBER.toString(), "disk")
        val out = options.single("--out")?.let(Path::of) ?: Path.of(".data", "cache", "${Revision.NUMBER}-packed")
        val packs =
            PackSource.discover(repo.toAbsolutePath()) + (options["--pack-dir"].orEmpty()).map { PackSource(Path.of(it)) }
        return when (command) {
            "pack" -> pack(PackOptions(source, out, packs))
            "verify" -> verify(source, out)
            else -> throw UsageException("unknown command '$command'")
        }
    } catch (e: UsageException) {
        System.err.println("error: ${e.message}")
        System.err.println(USAGE)
        return 2
    }
}

private fun pack(options: PackOptions): Int {
    println("pack sources: ${options.packs.joinToString { it.toString() }}")
    val report = Packer.run(options)
    println("wrote ${report.groupsWritten.size} groups: ${report.groupsWritten.joinToString()}")
    for ((archive, crc, version) in report.indexes) println("archive $archive index: crc $crc version $version")
    for ((namespace, count) in report.duplicateNames) println("note: $count duplicate $namespace gameval names ignored (first id wins)")
    println("new ids:")
    for (s in report.customSymbols.filter { it.type != "component" }.sortedWith(compareBy({ it.type }, { it.id }))) {
        println("  ${s.type} ${s.name} = ${s.id}${if (s.attributes.isNotEmpty()) "  (${s.attributes})" else ""}")
    }
    println("  ${report.customSymbols.count { it.type == "component" }} components (see custom-symbols.tsv)")
    return verify(options.sourceDisk, options.outRoot)
}

private fun verify(sourceDisk: Path, outRoot: Path): Int {
    val symbols = outRoot.resolve("custom-symbols.tsv")
    if (!Files.isRegularFile(symbols)) throw UsageException("$symbols not found: run `pack` first")
    val custom =
        Files.readAllLines(symbols).filter { it.isNotBlank() && !it.startsWith("#") }.map {
            val p = it.split('\t')
            CustomSymbol(p[0], p[1], p[2].toInt(), p.getOrElse(3) { "" })
        }
    val patched =
        outRoot
            .resolve("pack.properties")
            .takeIf {
                java.nio.file.Files
                    .exists(it)
            }?.let {
                java.nio.file.Files
                    .readAllLines(it)
            }?.firstOrNull { it.startsWith("patchedScripts=") }
            ?.substringAfter('=')
            ?.split(',')
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()
    val result = Verifier.verify(sourceDisk, outRoot.resolve("disk"), custom, patched)
    (result.lines + result.failures).forEach(::println)
    println(if (result.ok) "verification passed" else "verification FAILED (${result.failures.size})")
    return if (result.ok) 0 else 1
}

private fun dumpScript(disk: String, id: Int?) {
    if (id == null) throw UsageException("script needs <disk> <id>")
    tp.cache.js5.Js5Cache.open(Path.of(disk)).use { cache ->
        val data = cache.readGroup(tp.cache.config.Js5Archive.CLIENTSCRIPTS, id) ?: throw UsageException("script $id does not exist")
        val names =
            me.filby.neptune.clientscript.compiler.ClientScriptOpcode.entries
                .associateBy { it.id }
        val shape =
            ScriptScan.forEachInstruction(data) { opcode, operand ->
                println("  ${names[opcode]?.name?.lowercase() ?: "cmd $opcode"}  $operand")
            }
        println(
            "script $id: ${shape.walkedInstructions} instructions; locals int/string/long ${shape.localInts}/${shape.localStrings}/" +
                "${shape.localLongs}; args ${shape.intArgs}/${shape.stringArgs}/${shape.longArgs}; ${data.size} bytes",
        )
    }
}

private fun parseOptions(args: List<String>): Map<String, List<String>> {
    val out = LinkedHashMap<String, MutableList<String>>()
    var i = 0
    while (i < args.size) {
        val flag = args[i]
        if (!flag.startsWith("--")) throw UsageException("unexpected argument '$flag'")
        out.getOrPut(flag) { ArrayList() } += args.getOrNull(i + 1) ?: throw UsageException("$flag needs a value")
        i += 2
    }
    return out
}

private fun Map<String, List<String>>.single(flag: String): String? = this[flag]?.lastOrNull()
