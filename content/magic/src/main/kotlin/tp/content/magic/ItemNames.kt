package tp.content.magic

import tp.plugin.WorldRef

/** Cache obj lookup by display name for the spell tables, which list the Wiki's item names. */
internal object ItemNames {
    /** The first unnoted obj called [name] (cache name, exact), or -1. */
    fun id(world: WorldRef, name: String): Int = world.objIdsNamed(name).firstOrNull() ?: -1
}
