package tp.tools.cachepack

import net.runelite.cache.definitions.loaders.ScriptLoader
import net.runelite.cache.definitions.savers.ScriptSaver
import net.runelite.cache.script.Instructions
import net.runelite.cache.script.assembler.Assembler
import net.runelite.cache.script.disassembler.Disassembler
import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import tp.util.Revision
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Patches of EXISTING client scripts (D-094), e.g. the chatbox line builder. A pack keeps only a small diff
 * (`pack/cs2/patch/NAME.diff`), never the script itself: at pack time the source cache's script is disassembled with
 * RuneLite's cache library (BSD-2), the diff is applied, and the result is re-assembled over the same id. The
 * disassemble/assemble round trip is byte-identical for an unpatched script (checked by [roundTrip]).
 *
 * Diff format: header lines `# script <id>` and `# base-sha256 <hex of the source script group's data>`, then unified-diff
 * hunks (`@@` lines; `' '` context, `-` removed, `+` added; `---`/`+++` and other `#` lines are ignored). Each hunk's
 * old side (context and removed lines) must appear exactly once; whitespace runs compare as one space. A different
 * base hash (a new revision) fails the pack, so a patch is never applied to a script it wasn't written for.
 */
internal object ScriptPatches {
    class Hunk(val old: List<String>, val new: List<String>)

    class Patch(val source: Path, val script: Int, val baseSha256: String, val hunks: List<Hunk>)

    fun parse(path: Path): Patch {
        var script = -1
        var base = ""
        val hunks = ArrayList<Hunk>()
        var old: MutableList<String>? = null
        var new: MutableList<String>? = null

        fun flush() {
            val o = old ?: return
            hunks += Hunk(o, new ?: ArrayList())
        }
        for (line in Files.readAllLines(path)) {
            when {
                line.startsWith("# script ") -> {
                    script = line.removePrefix("# script ").trim().toInt()
                }

                line.startsWith("# base-sha256 ") -> {
                    base = line.removePrefix("# base-sha256 ").trim().lowercase()
                }

                line.startsWith("#") || line.startsWith("---") || line.startsWith("+++") -> {
                    Unit
                }

                line.startsWith("@@") -> {
                    flush()
                    old = ArrayList()
                    new = ArrayList()
                }

                old == null -> {
                    if (line.isNotBlank()) throw IOException("$path: text before the first @@ hunk")
                }

                line.startsWith("+") -> {
                    new?.add(line.substring(1))
                }

                line.startsWith("-") -> {
                    old?.add(line.substring(1))
                }

                else -> {
                    val text = if (line.startsWith(" ")) line.substring(1) else line
                    old?.add(text)
                    new?.add(text)
                }
            }
        }
        flush()
        if (script < 0 || base.isEmpty() || hunks.isEmpty()) throw IOException("$path: needs '# script', '# base-sha256' and hunks")
        return Patch(path, script, base, hunks)
    }

    /** The patched script's bytes for [patch], from the untouched [source] cache. */
    fun apply(source: Js5Cache, patch: Patch): ByteArray {
        val raw = scriptBytes(source, patch.script) ?: throw IOException("${patch.source}: script ${patch.script} is not in the cache")
        val hash = sha256(raw)
        if (hash != patch.baseSha256) {
            throw IOException("${patch.source}: script ${patch.script} has sha256 $hash, the patch was written for ${patch.baseSha256}")
        }
        var lines = disassemble(patch.script, raw).lines()
        for ((n, hunk) in patch.hunks.withIndex()) lines = applyHunk(lines, hunk, "${patch.source} hunk ${n + 1}")
        return assemble(lines.joinToString("\n"))
    }

    /** Disassembles and re-assembles [id] unchanged: true when the bytes come back identical. */
    fun roundTrip(source: Js5Cache, id: Int): Boolean {
        val raw = scriptBytes(source, id) ?: return false
        return assemble(disassemble(id, raw)).contentEquals(raw)
    }

    private fun applyHunk(lines: List<String>, hunk: Hunk, where: String): List<String> {
        val want = hunk.old.map(::norm)
        val norm = lines.map(::norm)
        val at = (0..lines.size - want.size).filter { i -> want.indices.all { norm[i + it] == want[it] } }
        if (at.size != 1) throw IOException("$where: its context matches ${at.size} places (needs exactly 1)")
        val i = at[0]
        return lines.subList(0, i) + hunk.new + lines.subList(i + want.size, lines.size)
    }

    private fun norm(line: String): String = line.trim().replace(SPACES, " ")

    private fun scriptBytes(source: Js5Cache, id: Int): ByteArray? = source.readFiles(Js5Archive.CLIENTSCRIPTS, id)?.data?.firstOrNull()

    private fun disassemble(id: Int, raw: ByteArray): String =
        Disassembler().disassemble(ScriptLoader().configureForRevision(Revision.NUMBER).load(id, raw))

    private fun assemble(text: String): ByteArray {
        val instructions = Instructions().apply { init() }
        val def = Assembler(instructions).assemble(text.byteInputStream())
        // Rev 242 scripts carry long arguments (`.long_arg_count`), so the saver writes the long-capable layout.
        return ScriptSaver(true).save(def)
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private val SPACES = Regex("\\s+")
}
