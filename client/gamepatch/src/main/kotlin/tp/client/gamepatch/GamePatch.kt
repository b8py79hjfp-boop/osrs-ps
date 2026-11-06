package tp.client.gamepatch

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess

/**
 * One by-value constant rewrite in the game client (brief §4.2 change set 2). [match] selects string constants (LDC
 * operands and field ConstantValues); [expected] is how many sites must match, so a client update that moves or
 * duplicates a constant fails the build instead of silently patching too much or too little.
 */
class Rule(val name: String, val match: Regex, val replacement: String, val expected: Int)

class PatchReport(val counts: Map<String, Int>) {
    fun failures(rules: List<Rule>): List<String> =
        rules.mapNotNull { r ->
            val n = counts[r.name] ?: 0
            if (n == r.expected) null else "${r.name}: matched $n site(s), expected ${r.expected}"
        }
}

/**
 * Rewrites string constants of an injected-client jar. Classes are rewritten with ASM (no frame recomputation:
 * replacing an LDC string keeps stack shapes). Nothing is found by obfuscated name.
 */
object GamePatcher {
    /** Counts matches without writing anything. */
    fun scan(jar: Path, rules: List<Rule>): PatchReport {
        val counts = HashMap<String, Int>()
        forEachClass(jar) { _, bytes -> rewrite(bytes, rules, counts) }
        return PatchReport(counts)
    }

    /** Writes [output] with every rule applied; fails (throws) unless each rule matched exactly as expected. */
    fun patch(input: Path, output: Path, rules: List<Rule>): PatchReport {
        val report = scan(input, rules)
        val failures = report.failures(rules)
        check(failures.isEmpty()) { "game-client patch does not apply cleanly:\n  " + failures.joinToString("\n  ") }
        Files.createDirectories(output.toAbsolutePath().parent)
        ZipInputStream(Files.newInputStream(input)).use { zin ->
            ZipOutputStream(Files.newOutputStream(output)).use { zout ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    var bytes = zin.readAllBytes()
                    if (e.name.endsWith(".class")) bytes = rewrite(bytes, rules, HashMap())
                    zout.putNextEntry(ZipEntry(e.name))
                    zout.write(bytes)
                    zout.closeEntry()
                }
            }
        }
        return report
    }

    private fun forEachClass(jar: Path, action: (String, ByteArray) -> Unit) {
        ZipInputStream(Files.newInputStream(jar)).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                if (e.name.endsWith(".class")) action(e.name, zin.readAllBytes())
            }
        }
    }

    private fun rewrite(bytes: ByteArray, rules: List<Rule>, counts: MutableMap<String, Int>): ByteArray {
        val reader = ClassReader(bytes)
        val writer = ClassWriter(0)

        fun replace(value: Any?): Any? {
            if (value !is String) return value
            val rule = rules.firstOrNull { it.match.matches(value) } ?: return value
            counts.merge(rule.name, 1, Int::plus)
            return rule.match.replace(value, rule.replacement)
        }
        reader.accept(
            object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitField(access: Int, name: String?, descriptor: String?, signature: String?, value: Any?): FieldVisitor? =
                    super.visitField(access, name, descriptor, signature, replace(value))

                override fun visitMethod(
                    access: Int,
                    name: String?,
                    descriptor: String?,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor =
                    object : MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        override fun visitLdcInsn(value: Any?) = super.visitLdcInsn(replace(value))
                    }
            },
            0,
        )
        return writer.toByteArray()
    }
}

/**
 * Rules for this release: the RSA modulus (the client's only 256-hex-digit constant, found by shape and value) plus
 * the URL rewrites from [config] (client/gamepatch/patches.json), whose `{site}` placeholder becomes [site].
 */
fun rules(config: Path, modulusHex: String, site: String): List<Rule> {
    require(modulusHex.matches(Regex("[0-9a-f]{1,256}"))) { "modulus must be lower-case hex" }
    val modulusRule = Rule("rsa-modulus", Regex("[0-9a-f]{256}"), modulusHex, expected = 1)
    val urls =
        Json.parseToJsonElement(Files.readString(config)).jsonObject.getValue("urls").jsonArray.map { e ->
            val o = e.jsonObject
            Rule(
                o.getValue("name").jsonPrimitive.content,
                Regex(Regex.escape(o.getValue("from").jsonPrimitive.content)),
                o
                    .getValue("to")
                    .jsonPrimitive.content
                    .replace("{site}", site),
                o.getValue("count").jsonPrimitive.int,
            )
        }
    return listOf(modulusRule) + urls
}

/**
 * `gamepatch <injected-client.jar> <out.jar> [--site https://example.com] [--modulus .data/rsa/modulus.hex]
 * [--config client/gamepatch/patches.json] [--scan]`. With `--scan` it only prints what each rule matches.
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        System.err.println("usage: gamepatch <injected-client.jar> <out.jar> [--site url] [--modulus file] [--config file] [--scan]")
        exitProcess(2)
    }
    val opts =
        args
            .drop(2)
            .zipWithNext()
            .filter { it.first.startsWith("--") }
            .toMap()
    val rules =
        rules(
            Path.of(opts["--config"] ?: "client/gamepatch/patches.json"),
            Files.readString(Path.of(opts["--modulus"] ?: ".data/rsa/modulus.hex")).trim().lowercase(),
            opts["--site"] ?: "http://127.0.0.1:8080",
        )
    val input = Path.of(args[0])
    if ("--scan" in args) {
        val report = GamePatcher.scan(input, rules)
        for (r in rules) println("${r.name}: ${report.counts[r.name] ?: 0} (expected ${r.expected})")
        return
    }
    GamePatcher.patch(input, Path.of(args[1]), rules)
    println("patched ${rules.size} rules -> ${args[1]}")
}
