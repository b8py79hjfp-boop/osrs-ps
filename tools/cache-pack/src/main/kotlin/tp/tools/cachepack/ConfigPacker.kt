package tp.tools.cachepack

import tp.cache.config.ConfigGroup
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * `inv`, `varp` and `varbit` definitions from `pack/configs/NAME.toml`:
 *
 * ```
 * [[inventory]]  id = "inv.spawn_results"  size = 60  scope = "Temp"  stack = "Always"  protect = false
 * [[varp]]       id = "varp.spawn_menu"    scope = "Perm"  transmit = "Never"             client_code = 0 (optional)
 * [[varbit]]     id = "varbit.spawn_include_nulls"  varp = "varp.spawn_menu"  startBit = 0  endBit = 0
 * ```
 *
 * The cache stores only what the client reads: an inv's slot count (config op 2), a varp's client code (op 5, usually none) and
 * a varbit's varp and bit range (op 1) -- `InvTypeDecoder`, `VarpTypeDecoder`, `VarbitTypeDecoder`. Every other key (`scope`,
 * `stack`, `protect`, `transmit`) is server-side; it is kept as the `attributes` column of `custom-symbols.tsv`.
 */
internal object ConfigPacker {
    class InvDef(val name: String, val size: Int, val attributes: String)

    class VarpDef(val name: String, val clientCode: Int, val attributes: String)

    class VarbitDef(val name: String, val varp: String, val startBit: Int, val endBit: Int)

    class Defs(val invs: List<InvDef>, val varps: List<VarpDef>, val varbits: List<VarbitDef>)

    fun parse(files: List<Path>): Defs {
        val invs = ArrayList<InvDef>()
        val varps = ArrayList<VarpDef>()
        val varbits = ArrayList<VarbitDef>()
        for (file in files) {
            for (s in MiniToml.parse(Files.readString(file), file.toString())) {
                val where = "$file:${s.line}"
                when (s.name) {
                    "inventory" -> {
                        invs +=
                            InvDef(name(s, "inv", where), int(s, "size", where, required = true), attributes(s, SERVER_KEYS_INV))
                    }

                    "varp" -> {
                        varps += VarpDef(name(s, "varp", where), int(s, "client_code", where), attributes(s, SERVER_KEYS_VARP))
                    }

                    "varbit" -> {
                        varbits +=
                            VarbitDef(
                                name(s, "varbit", where),
                                (s.values["varp"] as? String)?.removePrefix("varp.") ?: throw IOException("$where: varbit needs varp"),
                                int(s, "startBit", where, required = true),
                                int(s, "endBit", where, required = true),
                            )
                    }

                    "" -> {
                        if (s.values.isNotEmpty()) throw IOException("$where: values outside a section")
                    }

                    else -> {
                        throw IOException("$where: unknown section [${s.name}]")
                    }
                }
            }
        }
        return Defs(invs, varps, varbits)
    }

    fun encodeInv(def: InvDef): ByteArray =
        WriteBuf()
            .p1(OP_INV_SIZE)
            .p2(def.size)
            .p1(0)
            .toByteArray()

    fun encodeVarp(def: VarpDef): ByteArray {
        val out = WriteBuf()
        if (def.clientCode != 0) out.p1(OP_VARP_CLIENT_CODE).p2(def.clientCode)
        return out.p1(0).toByteArray()
    }

    fun encodeVarbit(varp: Int, def: VarbitDef): ByteArray {
        require(def.endBit in def.startBit until VARP_BITS) { "varbit ${def.name}: bad bit range ${def.startBit}..${def.endBit}" }
        return WriteBuf()
            .p1(OP_VARBIT)
            .p2(varp)
            .p1(def.startBit)
            .p1(def.endBit)
            .p1(0)
            .toByteArray()
    }

    private fun name(s: MiniToml.Section, prefix: String, where: String): String {
        val id = s.values["id"] as? String ?: throw IOException("$where: missing id")
        if (!id.startsWith("$prefix.")) throw IOException("$where: id '$id' must start with '$prefix.'")
        return id.removePrefix("$prefix.")
    }

    private fun int(s: MiniToml.Section, key: String, where: String, required: Boolean = false): Int =
        when (val v = s.values[key]) {
            null -> if (required) throw IOException("$where: missing $key") else 0
            is Int -> v
            else -> throw IOException("$where: $key must be an integer")
        }

    private fun attributes(s: MiniToml.Section, keys: Set<String>): String =
        s.values
            .filterKeys { it in keys }
            .entries
            .joinToString(";") { "${it.key}=${it.value}" }

    val GROUP_INV = ConfigGroup.INV
    val GROUP_VARP = ConfigGroup.VARP
    val GROUP_VARBIT = ConfigGroup.VARBIT

    private val SERVER_KEYS_INV = setOf("scope", "stack", "protect")
    private val SERVER_KEYS_VARP = setOf("scope", "transmit")

    /** Config opcodes: inv op 2 = size, varp op 5 = client code, varbit op 1 = varp + bits. Decoders cite RuneLite / RS Mod. */
    private const val OP_INV_SIZE = 2
    private const val OP_VARP_CLIENT_CODE = 5
    private const val OP_VARBIT = 1

    /** A varp holds 32 bits. */
    private const val VARP_BITS = 32
}
