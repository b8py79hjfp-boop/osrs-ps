package tp.tools.cachepack

import java.io.IOException

/** One row of `custom-symbols.tsv`: what the pack added, under which name, with which id. */
internal data class CustomSymbol(val type: String, val name: String, val id: Int, val attributes: String = "")

/**
 * Ids this run hands out, by namespace. Namespaces are the symbol types the server and the compiler both use:
 * `interface`, `component` (`interface:component` -> packed id), `clientscript`, `proc`, `inv`, `varp`, `varbit`.
 */
internal class CustomIds {
    val symbols = ArrayList<CustomSymbol>()
    private val byNamespace = HashMap<String, MutableMap<String, Int>>()

    fun add(type: String, name: String, id: Int, attributes: String = "") {
        val table = byNamespace.getOrPut(type) { LinkedHashMap() }
        if (table.putIfAbsent(name, id) != null) throw IOException("Duplicate $type '$name' in the pack sources")
        symbols += CustomSymbol(type, name, id, attributes)
    }

    fun get(type: String, name: String): Int? = byNamespace[type]?.get(name)

    fun all(type: String): Map<String, Int> = byNamespace[type].orEmpty()
}

/**
 * Turns `namespace.name` references from the pack TOML into ids. Looks first at what the pack itself defines, then at the
 * cache's gamevals, then at the curated tables. Anything else is an error: a typo must not become a literal string.
 */
internal class Resolver(
    private val cache: CacheSymbols,
    private val custom: CustomIds,
    private val curated: Map<String, Map<String, Int>>,
) {
    /** True when [text] has the shape of a reference (`component.x:y`, `inv.name`, ...), whether or not it resolves. */
    fun looksLikeReference(text: String): Boolean {
        val dot = text.indexOf('.')
        return dot > 0 && text.substring(0, dot) in NAMESPACES && REFERENCE_BODY.matches(text.substring(dot + 1))
    }

    fun resolve(reference: String): Int {
        val dot = reference.indexOf('.')
        val prefix = reference.substring(0, dot)
        val name = reference.substring(dot + 1)
        val cacheNamespace = ALIASES[prefix] ?: prefix
        val customNamespace = if (prefix == "clientscript") "clientscript" else prefix
        return custom.get(customNamespace, name)
            ?: cache.lookup(cacheNamespace, name)
            ?: (curated[cacheNamespace] ?: curated[prefix])?.get(name)
            ?: throw IOException("Unknown reference '$reference'")
    }

    companion object {
        private val REFERENCE_BODY = Regex("""[A-Za-z0-9_:,]+""")

        /** Reference prefix to the cache namespace it reads, where the two differ. */
        private val ALIASES = mapOf("graphic" to "sprite", "font" to "fontmetrics")

        val NAMESPACES: Set<String> =
            setOf(
                "clientscript",
                "component",
                "interface",
                "inv",
                "varp",
                "varbit",
                "varc",
                "obj",
                "npc",
                "loc",
                "seq",
                "spotanim",
                "sprite",
                "graphic",
                "dbrow",
                "dbtable",
                "jingle",
                "font",
                "fontmetrics",
            )
    }
}
