package tp.content.dev

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.util.CoordGrid

/** Developer `::commands` (Phase 3 stand-ins; an admin rights check arrives with accounts in Phase 5). */
public class DevCommands : ContentPlugin {
    private var world: tp.plugin.WorldRef? = null

    override fun ContentScope.register() {
        onWorldStart { world = this }
        onLogin { message("Dev: ::pos ::run ::tele x z ::item id [n] ::empty ::xp stat n ::max ::kit ::wild ::hp n ::trace") }
        onCommand("pos") { message("$coord tick $tick pid $pid") }
        onCommand("run") {
            runMode = !runMode
            message("Run: $runMode")
        }
        onCommand("tele") { args -> tele(args) }
        onCommand("hp") { args -> args.getOrNull(0)?.toIntOrNull()?.let { hitpoints = it } }
        onCommand("setlevel") { args ->
            val stat = args.getOrNull(0)?.toIntOrNull()
            val level = args.getOrNull(1)?.toIntOrNull()
            if (stat != null && level != null && stat in 0 until STAT_COUNT) setLevel(stat, level)
        }
        onCommand("spellbook") { args ->
            // 0 standard, 1 ancient, 2 lunar, 3 arceuus (varbit `spellbook`).
            val book = args.getOrNull(0)?.toIntOrNull()
            if (book != null && book in 0..LAST_SPELLBOOK) setVarbit(tp.symbols.Varbits.SPELLBOOK, book)
        }
        onCommand("max") {
            for (stat in 0..LAST_COMBAT_STAT) addXp(stat, MAX_XP)
            message("Combat stats set to 99.")
        }
        onCommand("kit") {
            transaction("dev ::kit") { KIT.all { (obj, count) -> add(Invs.INV, obj, count) } }
            message("Kit added: weapons, food, potions and runes.")
        }
        onCommand("wild") { teleport(CoordGrid(WILD_X, WILD_Z)) }
        onCommand("trace") { message("Tick trace: ${toggleTrace()}") }
        onCommand("item") { args -> item(args) }
        // ::if <interface id>: opens it as the main modal (interface layout checks against the real client).
        onCommand("if") { args ->
            args.getOrNull(0)?.toIntOrNull()?.let { openModal(tp.symbols.Components.Toplevel.MAINMODAL, it) }
        }
        // ::npc <type>: spawns that NPC next to you (no wandering).
        onCommand("npc") { args ->
            args.getOrNull(0)?.toIntOrNull()?.let { world?.spawnNpc(it, CoordGrid(coord.x + 1, coord.z, coord.level), 0) }
        }
        // ::fight <type>: attacks the nearest NPC of that type within 15 tiles (op 2 "Attack"), as a click would.
        onCommand("fight") { args ->
            val type = args.getOrNull(0)?.toIntOrNull() ?: return@onCommand
            val npc = npcsWithin(coord, FIGHT_RADIUS).filter { it.type == type }.minByOrNull { it.coord.chebyshev(coord) }
            if (npc == null) message("No such NPC nearby.") else interact(npc, ATTACK_OP)
        }
        onCommand("empty") {
            val inv = inv(Invs.INV)
            transaction("dev ::empty") {
                (0 until inv.size).all { slot -> inv.obj(slot) < 0 || removeAt(Invs.INV, slot, inv.obj(slot), inv.count(slot)) }
            }
        }
        onCommand("xp") { args ->
            val stat = args.getOrNull(0)?.toIntOrNull()
            val xp = args.getOrNull(1)?.toDoubleOrNull()
            if (stat == null || xp == null || stat !in 0 until STAT_COUNT) {
                message("Usage: ::xp stat amount")
            } else {
                message("Levels gained: ${addXp(stat, xp)}")
            }
        }
    }

    private fun PlayerRef.tele(args: List<String>) {
        val x = args.getOrNull(0)?.toIntOrNull()
        val z = args.getOrNull(1)?.toIntOrNull()
        val level = args.getOrNull(2)?.toIntOrNull() ?: coord.level
        if (x == null || z == null) {
            message("Usage: ::tele x z [level]")
        } else {
            teleport(CoordGrid(x, z, level))
        }
    }

    private fun PlayerRef.item(args: List<String>) {
        val id = args.getOrNull(0)?.toIntOrNull()
        val count = args.getOrNull(1)?.toIntOrNull() ?: 1
        if (id == null) {
            message("Usage: ::item id [count]")
        } else if (!transaction("dev ::item") { add(Invs.INV, id, count) }) {
            message("Couldn't add $count x $id (unknown obj or no space).")
        }
    }

    private companion object {
        const val FIGHT_RADIUS = 15
        const val ATTACK_OP = 2

        /** Arceuus, the last spellbook varbit value. */
        const val LAST_SPELLBOOK = 3
        const val STAT_COUNT = 24
        const val MAX_XP = 13_034_431.0

        /** Attack..Magic are stats 0..6. */
        const val LAST_COMBAT_STAT = 6

        /** Main Wilderness, level 36, multi-combat (wilderness.md). */
        const val WILD_X = 3300
        const val WILD_Z = 3800

        @Suppress("MagicNumber") // obj ids and counts of a dev PvP kit
        val KIT: List<Pair<Int, Int>> =
            listOf(
                4151 to 1,
                1215 to 1,
                11802 to 1,
                13652 to 1,
                9185 to 1,
                9144 to 200,
                385 to 6,
                3144 to 4,
                6685 to 2,
                560 to 500,
                565 to 500,
                555 to 500,
                9075 to 100,
                557 to 500,
            )
    }
}
