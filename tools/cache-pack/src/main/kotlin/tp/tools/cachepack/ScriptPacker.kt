package tp.tools.cachepack

import me.filby.neptune.clientscript.compiler.ClientScriptCompiler
import me.filby.neptune.clientscript.compiler.SymbolMapper
import me.filby.neptune.clientscript.compiler.configuration.ClientScriptCompilerFeatureSet
import me.filby.neptune.clientscript.compiler.writer.BinaryFileScriptWriter
import me.filby.neptune.clientscript.compiler.writer.BinaryScriptWriter.DebugMode
import me.filby.neptune.runescript.compiler.diagnostics.Diagnostics
import me.filby.neptune.runescript.compiler.diagnostics.DiagnosticsHandler
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Compile errors from Neptune, already printed. */
internal class ScriptCompileException(message: String) : IOException(message)

/**
 * Compiles the pack's `.cs2` sources with Neptune's ClientScript 2 compiler (vendored under `tools/cache-pack/neptune`, MIT).
 *
 * Layout under [work]: `symbols/` (generated `.sym` files, see [CacheSymbols.writeSymFiles], plus the curated tables and the
 * packs' own), `library/` (Neptune library sources: command and legacy-proc signatures, never written out), `src/` (the pack
 * scripts) and `out/<id>` (compiled scripts, one file per script id).
 */
internal object ScriptPacker {
    /** Rev 242 client features: the thresholds of Neptune's `getDefaultFeaturesForVersion` for `client_version = 242`. */
    private val FEATURES =
        ClientScriptCompilerFeatureSet(
            dbFindReturnsCount = true,
            ccCreateAssertNewArg = true,
            prefixPostfixExpressions = false,
            arraysV2 = true,
            simplifiedTypeCodes = true,
            longSupport = true,
            foldJoinedStringConstants = true,
            stringTemplates = true,
        )

    class Result(val compiled: Map<Int, ByteArray>)

    /**
     * Compiles [sources]; [scriptIds] maps every `[trigger,name]` (new and legacy) to its id and [commands] every command name
     * to its opcode. Returns the bytes of each script written, keyed by id. Library stubs are never written.
     */
    fun compile(work: Path, sources: List<Path>, scriptIds: Map<String, Int>, commands: Map<String, Int>): Result {
        val src = work.resolve("src")
        val out = work.resolve("out")
        for (dir in listOf(src, out)) {
            if (Files.exists(dir)) {
                Files.walk(dir).use { s ->
                    s.sorted(Comparator.reverseOrder()).filter { it != dir }.forEach(Files::delete)
                }
            }
            Files.createDirectories(dir)
        }
        for (s in sources) Files.copy(s, src.resolve(s.fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)

        val mapper = SymbolMapper()
        commands.forEach { (name, id) -> mapper.putCommand(id, name) }
        scriptIds.forEach { (name, id) -> mapper.putScript(id, name) }

        val sourcePaths = listOf(src, work.resolve("library"))
        val writer = BinaryFileScriptWriter(out, mapper, sourcePaths, DebugMode.NONE, FEATURES)
        val compiler =
            ClientScriptCompiler(
                sourcePaths,
                listOf(work.resolve("library")),
                writer,
                FEATURES,
                listOf(work.resolve("symbols"), work.resolve("symbols-pack")),
                mapper,
            )
        compiler.diagnosticsHandler = ThrowingHandler
        compiler.setup()
        compiler.run()

        val compiled = HashMap<Int, ByteArray>()
        Files.newDirectoryStream(out).use { files ->
            for (f in files) compiled[f.fileName.toString().toInt()] = Files.readAllBytes(f)
        }
        return Result(compiled)
    }

    /** Prints every diagnostic like Neptune's own handler, then throws instead of exiting the JVM. */
    private object ThrowingHandler : DiagnosticsHandler {
        override fun Diagnostics.handleParse() = report()

        override fun Diagnostics.handleTypeChecking() = report()

        override fun Diagnostics.handleCodeGeneration() = report()

        private fun Diagnostics.report() {
            for (d in diagnostics) {
                val at = d.sourceLocation
                System.err.println("${at.name}:${at.line}:${at.column}: ${d.type}: ${d.message.format(*d.messageArgs.toTypedArray())}")
            }
            if (hasErrors()) throw ScriptCompileException("ClientScript compilation failed (${diagnostics.count()} diagnostics above)")
        }
    }
}
