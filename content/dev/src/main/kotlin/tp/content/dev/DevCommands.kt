package tp.content.dev

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.util.CoordGrid

/** Developer `::commands` (Phase 3 stand-ins), developers only until each moves onto the registry (commands brief §6). */
public class DevCommands : ContentPlugin {
    private var world: tp.plugin.WorldRef? = null

    override fun ContentScope.register() {
        onWorldStart { world = this }
        onLogin {
            if (rank.atLeast(
                    DEV,
                )
            ) {
                message("Dev: ::run ::tele x z ::item id [n] ::empty ::xp stat n ::max ::kit ::wild ::hp n ::trace")
            }
        }
        onCommand("run", DEV) {
            runMode = !runMode
            message("Run: $runMode")
        }
        onCommand("tele", DEV) { args -> tele(args) }
        onCommand("hp", DEV) { args -> args.getOrNull(0)?.toIntOrNull()?.let { hitpoints = it } }
        onCommand("setlevel", DEV) { args ->
            val stat = args.getOrNull(0)?.toIntOrNull()
            val level = args.getOrNull(1)?.toIntOrNull()
            if (stat != null && level != null && stat in 0 until STAT_COUNT) setLevel(stat, level)
        }
        onCommand("spellbook", DEV) { args ->
            // 0 standard, 1 ancient, 2 lunar, 3 arceuus (varbit `spellbook`).
            val book = args.getOrNull(0)?.toIntOrNull()
            if (book != null && book in 0..LAST_SPELLBOOK) setVarbit(tp.symbols.Varbits.SPELLBOOK, book)
        }
        onCommand("max", DEV) {
            for (stat in 0..LAST_COMBAT_STAT) addXp(stat, MAX_XP)
            message("Combat stats set to 99.")
        }
        onCommand("kit", DEV) {
            transaction("dev ::kit") { KIT.all { (obj, count) -> add(Invs.INV, obj, count) } }
            message("Kit added: weapons, food, potions and runes.")
        }
        onCommand("wild", DEV) { teleport(CoordGrid(WILD_X, WILD_Z)) }
        onCommand("trace", DEV) { message("Tick trace: ${toggleTrace()}") }
        // ::instance <squareX> <squareZ> [w] [l]: copies w x l map squares into a fresh instance (built off-thread,
        // instances.md IN6) and moves you to the same spot inside; ::instance leave tears it down (IN9) and returns you.
        onCommand("instance", DEV) { args -> devInstance(this, args) }
        onCommand("item", DEV) { args -> item(args) }
        // ::if <interface id>: opens it as the main modal (interface layout checks against the real client).
        onCommand("if", DEV) { args ->
            args.getOrNull(0)?.toIntOrNull()?.let { openModal(tp.symbols.Components.Toplevel.MAINMODAL, it) }
        }
        // ::npc <type>: spawns that NPC next to you (no wandering).
        onCommand("npc", DEV) { args ->
            args.getOrNull(0)?.toIntOrNull()?.let { world?.spawnNpc(it, CoordGrid(coord.x + 1, coord.z, coord.level), 0) }
        }
        // ::fight <type>: attacks the nearest NPC of that type within 15 tiles (op 2 "Attack"), as a click would.
        onCommand("fight", DEV) { args ->
            val arg = args.getOrNull(0) ?: return@onCommand
            // ::fight <player name>: attack a nearby player (headless test clients cannot click players in the scene).
            val type =
                arg.toIntOrNull() ?: return@onCommand (
                    playersWithin(FIGHT_RADIUS)
                        .firstOrNull { it.username.equals(arg, ignoreCase = true) && it != this }
                        ?.let { interact(it, ATTACK_OP) } ?: message("No such player nearby.")
                )
            val npc = npcsWithin(coord, FIGHT_RADIUS).filter { it.type == type }.minByOrNull { it.coord.chebyshev(coord) }
            if (npc == null) message("No such NPC nearby.") else interact(npc, ATTACK_OP)
        }
        onCommand("xp", DEV) { args ->
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
        val DEV = tp.util.Rank.DEVELOPER
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

private val DEV_INSTANCE = tp.plugin.AttrKey<tp.plugin.InstanceRef>("dev_instance")
private val DEV_INSTANCE_FROM = tp.plugin.AttrKey<CoordGrid>("dev_instance_from")

private fun devInstance(p: tp.plugin.PlayerRef, args: List<String>) {
    // ::instance drop: tears it down with you inside (tests the lost-instance exit, IN8).
    if (args.firstOrNull() == "drop") {
        p[DEV_INSTANCE]?.destroy()
        p[DEV_INSTANCE] = null
        return
    }
    if (args.firstOrNull() == "leave") {
        val back = p[DEV_INSTANCE_FROM]
        p[DEV_INSTANCE]?.let {
            if (back != null) p.teleport(back) else p.setInstanceExit(null)
            it.destroy()
        }
        p[DEV_INSTANCE] = null
        return
    }
    val sx = args.getOrNull(0)?.toIntOrNull() ?: return p.message("::instance <squareX> <squareZ> [w] [l] | leave")
    val sz = args.getOrNull(1)?.toIntOrNull() ?: return
    val w = args.getOrNull(2)?.toIntOrNull() ?: 1
    val l = args.getOrNull(3)?.toIntOrNull() ?: 1
    val squares = (0 until w).flatMap { dx -> (0 until l).map { dz -> tp.plugin.InstanceSquare(sx + dx, sz + dz, dx, dz) } }
    val target = CoordGrid((sx shl 6) + 32, (sz shl 6) + 32, p.coord.level)
    p[DEV_INSTANCE_FROM] = p.coord
    p.setInstanceExit(p.coord)
    p.buildInstance(w, l, squares) { inst ->
        p[DEV_INSTANCE] = inst
        val inside = inst.fromTemplate(target) ?: return@buildInstance
        p.teleport(inside)
        p.message("Instance ready at $inside (template $target).")
    }
}
