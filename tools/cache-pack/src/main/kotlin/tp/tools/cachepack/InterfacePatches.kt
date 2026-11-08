package tp.tools.cachepack

import tp.cache.config.Js5Archive
import tp.cache.js5.Js5Cache
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Patches of EXISTING interfaces (D-096), e.g. the chatbox (162) gaining tabs. `pack/interface-patches/NAME.toml`:
 *
 * ```
 * [[patch]]
 * interface = 162            the interface id
 * name = "chatbox"           its gameval name: new components are `component.chatbox:<name>` for scripts
 * base_components = 569      the component count it must have in the source cache (fails on a cache update)
 *
 * [[set]]                    change fields of an existing component
 * component = 4
 * x = 3                      x y width height hidden text sprite
 *
 * [[component]]              appended from `base_components` in file order; same fields as an .if3 component,
 * name = "tab_mentions"      but `parent` may also be an existing component id
 * ```
 *
 * Every original component must decode and re-encode byte-identically first, so untouched components never change.
 */
internal object InterfacePatches {
    class Patch(
        val source: Path,
        val id: Int,
        val name: String,
        val base: Int,
        val sets: List<MiniToml.Section>,
        val added: List<MiniToml.Section>,
    ) {
        /** New component name to its component index. */
        val ids: Map<String, Int> = added.mapIndexed { i, s -> InterfaceDef.componentName(s) to base + i }.toMap()
    }

    fun parse(path: Path): Patch {
        val sections = MiniToml.parse(Files.readString(path), path.toString())
        val head = sections.singleOrNull { it.name == "patch" }?.values ?: throw IOException("$path: needs exactly one [[patch]]")
        val unknown = sections.filter { it.name !in setOf("patch", "set", "component") }
        if (unknown.isNotEmpty()) throw IOException("$path: unknown section [[${unknown.first().name}]]")
        return Patch(
            path,
            head["interface"] as? Int ?: throw IOException("$path: [[patch]] needs interface"),
            head["name"] as? String ?: throw IOException("$path: [[patch]] needs name"),
            head["base_components"] as? Int ?: throw IOException("$path: [[patch]] needs base_components"),
            sections.filter { it.name == "set" },
            sections.filter { it.name == "component" },
        )
    }

    /** Every component of the patched interface, in id order. */
    fun build(source: Js5Cache, patch: Patch, resolver: Resolver): List<If3Component> {
        val files =
            source.readFiles(Js5Archive.INTERFACES, patch.id)
                ?: throw IOException("${patch.source}: interface ${patch.id} is not in the cache")
        if (files.size != patch.base || files.ids.toList() != (0 until patch.base).toList()) {
            throw IOException("${patch.source}: interface ${patch.id} has ${files.size} components, the patch expects ${patch.base}")
        }
        val packedBase = patch.id shl COMPONENT_SHIFT
        val components =
            files.data
                .mapIndexed { i, data ->
                    val c = If3Codec.decode(data, packedBase) ?: throw IOException("${patch.source}: component $i is not IF3")
                    if (!If3Codec.encode(c).contentEquals(data)) throw IOException("${patch.source}: component $i does not round-trip")
                    c
                }.toMutableList()
        for (set in patch.sets) applySet(components, set, patch)
        for (section in patch.added) {
            val where = "${patch.source}: component '${InterfaceDef.componentName(section)}'"
            components +=
                InterfacePacker.component(section, where, resolver) { parent, w ->
                    when (parent) {
                        is Int -> parent.takeIf { it in components.indices } ?: throw IOException("$w: no component $parent")
                        is String -> patch.ids[parent] ?: throw IOException("$w: unknown parent '$parent'")
                        else -> throw IOException("$w: needs a parent (an existing id or a new component's name)")
                    }
                }
        }
        return components
    }

    private fun applySet(components: List<If3Component>, set: MiniToml.Section, patch: Patch) {
        val v = set.values
        val where = "${patch.source} line ${set.line}"
        val id = v["component"] as? Int ?: throw IOException("$where: [[set]] needs component")
        val c = components.getOrNull(id) ?: throw IOException("$where: no component $id")
        for ((key, value) in v) {
            when (key) {
                "component" -> Unit
                "x" -> c.x = value as Int
                "y" -> c.y = value as Int
                "width" -> c.width = value as Int
                "height" -> c.height = value as Int
                "hidden" -> c.hidden = value as Boolean
                "text" -> c.text = value as String
                "sprite" -> c.sprite = value as Int
                else -> throw IOException("$where: [[set]] can't change '$key'")
            }
        }
    }

    private const val COMPONENT_SHIFT = 16
}
