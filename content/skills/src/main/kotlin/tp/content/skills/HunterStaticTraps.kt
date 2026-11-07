package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/** The player timer that runs a player's deadfalls and net traps once a tick. */
private val STATIC_TIMER = TimerId("hunter.static")

/**
 * Deadfalls (kebbits, pyre fox; set on a boulder with a knife and logs) and net traps (salamanders; set on a young tree with the
 * net trap item). Both are fixed locs of the map that change into the trap and back (docs/mechanics/hunter.md HU28, HU29): the
 * tile's loc is replaced by the trap's states and `revertLoc` restores the map's boulder or young tree. Catching reuses the box
 * trap rules (a lure attempt every 3 ticks within 2 tiles, the creature's Skilling success chart).
 */
public class HunterStaticTraps : ContentPlugin {
    /**
     * The locs of one kind of static trap. [restore] is the map loc the trap is set on, [set] the set trap, [failing] the loc of a
     * failed catch, [failed] the loc that stays after a failure (-1: the trap is gone),
     * and [catching] / [full] by creature page name.
     */
    private class Kit(
        val deadfall: Boolean,
        val restore: List<Int>,
        val set: Int,
        val failing: Int,
        val failed: Int,
        val catching: Map<String, Int>,
        val full: Map<String, Int>,
    )

    /** One trap laid by a player. */
    private class Trap(val coord: CoordGrid, val shape: Int, val angle: Int, val kit: Kit) {
        var state: Int = SET
        var laidAt: Long = 0
        var expiresAt: Long = Long.MAX_VALUE
        var settleAt: Long = 0
        var creature: Creature? = null
    }

    private class Wildlife(val npcs: LinkedHashSet<NpcRef> = LinkedHashSet())

    override fun ContentScope.register() {
        val wild = HunterData.BY_NPC.filterValues { it.kind == TrapKind.DEADFALL || it.kind == TrapKind.NETTRAP }.keys
        onNpcSpawn {
            if (type in wild) (worldAttr(WILD) ?: Wildlife().also { setWorldAttr(WILD, it) }).npcs += this
        }
        for (kit in KITS) {
            for (loc in kit.restore) {
                onOpLoc(loc, OP_FIRST) { l -> layTrap(player, kit, l) }
                if (!kit.deadfall) onUseOnLoc(Objs.NET_TRAP, loc) { l -> layTrap(player, kit, l) }
            }
            onOpLoc(kit.set, OP_FIRST) { l -> dismantle(l) }
            onOpLoc(kit.set, OP_SECOND) { l -> investigate(l) }
            if (kit.failed >= 0) {
                onOpLoc(kit.failed, OP_FIRST) { l -> dismantle(l) }
                onOpLoc(kit.failed, OP_SECOND) { l -> collect(l, reset = true, loot = false) }
            }
            for (loc in kit.full.values.toSet()) {
                onOpLoc(loc, OP_FIRST) { l -> collect(l, reset = kit.deadfall, loot = true) }
                if (!kit.deadfall) onOpLoc(loc, OP_SECOND) { l -> collect(l, reset = true, loot = true) }
            }
        }
        onLogout { clearTraps() }
    }

    // ---- laying

    private suspend fun PlayerScript.layTrap(p: PlayerRef, kit: Kit, loc: LocRef) {
        val problem = layProblem(p, kit, loc)
        if (problem != null) return p.message(problem)
        p.stopInteraction()
        p.anim(if (kit.deadfall) Seqs.HUMAN_PICKUPTABLE else Seqs.HUNTING_SETTING_TRAP_SMALL)
        delay(LAY_TICKS)
        // Everything is checked again after the delay: the loc, the limit and the items may have changed.
        if (layProblem(p, kit, loc) != null) return
        val logs = if (kit.deadfall) logsOf(p) else Objs.NET_TRAP
        val keep = kit.deadfall && p.random(PRESERVE_DENOMINATOR) < PRESERVE_NUMERATOR
        val paid = keep || p.transaction("hunter static lay") { remove(Invs.INV, logs, 1) }
        if (!paid) return
        val trap = Trap(loc.coord, loc.shape, loc.angle, kit)
        trap.laidAt = p.tick
        if (!kit.deadfall) {
            trap.expiresAt = p.tick + NET_LIFETIME_TICKS
            HunterShared.addExtra(p, 1)
        }
        traps(p).add(trap)
        p.addLoc(kit.set, trap.coord, trap.shape, trap.angle)
        if (!p.hasTimer(STATIC_TIMER)) p.timer(STATIC_TIMER, 1, true) { runTraps() }
    }

    /** Why [p] cannot set [kit] on [loc], or null. */
    private fun layProblem(p: PlayerRef, kit: Kit, loc: LocRef): String? {
        val inv = p.inv(Invs.INV)
        val level = p.baseLevel(Stats.HUNTER)
        val needed = kit.minLevel()
        val deadfalls = traps(p).count { it.kit.deadfall }
        return when {
            p.locAt(loc.coord, loc.shape)?.id != loc.id -> {
                "Someone has already set a trap here."
            }

            p.level(Stats.HUNTER) < needed -> {
                "You need a Hunter level of $needed to set this trap."
            }

            kit.deadfall && !hasKnife(p) -> {
                "You need a knife to set a deadfall."
            }

            kit.deadfall && logsOf(p) < 0 -> {
                "You need logs to set a deadfall."
            }

            !kit.deadfall && inv.total(Objs.NET_TRAP) <= 0 -> {
                "You need a net trap to set this."
            }

            kit.deadfall && deadfalls >= DEADFALL_LIMIT -> {
                "You can't have more than $DEADFALL_LIMIT deadfall traps set."
            }

            !kit.deadfall && HunterShared.normal(p) + HunterShared.extra(p) >= HunterData.trapLimit(level, wilderness(p)) -> {
                "You can't have more than ${HunterData.trapLimit(level, wilderness(p))} traps laid at your Hunter level."
            }

            else -> {
                null
            }
        }
    }

    private fun wilderness(p: PlayerRef): Boolean = p[SharedAttrs.IN_WILDERNESS] == true

    private fun hasKnife(p: PlayerRef): Boolean = p.inv(Invs.INV).let { it.total(Objs.KNIFE) > 0 || it.total(Objs.FLETCHING_KNIFE) > 0 }

    /** The first log in the backpack a deadfall can be built from (Wiki "Deadfall": any log but redwood and arctic pine), or -1. */
    private fun logsOf(p: PlayerRef): Int {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            val obj = inv.obj(slot)
            if (obj < 0 || p.unnoted(obj) != obj) continue
            val name = p.objName(obj).lowercase()
            val log = name == "logs" || (name.endsWith(" logs") && !name.contains("pyre") && !name.contains("forestry"))
            if (log && name != "redwood logs" && name != "arctic pine logs") return obj
        }
        return -1
    }

    private fun Kit.minLevel(): Int =
        if (deadfall) DEADFALL_MIN_LEVEL else full.keys.minOf { HunterData.BY_NAME.getValue(it).level }

    // ---- the trap tick

    private fun PlayerRef.runTraps() {
        val list = this[TRAPS]
        if (list == null || list.isEmpty()) return stopTimer(STATIC_TIMER)
        val now = tick
        val iter = list.iterator()
        while (iter.hasNext()) {
            val trap = iter.next()
            if (trap.state == CATCHING && now >= trap.settleAt && !settle(trap)) {
                iter.remove()
                continue
            }
            if (trap.state != SET) {
                if (trap.state == FAILED && now >= trap.expiresAt) expire(this, trap, iter)
                continue
            }
            val age = now - trap.laidAt
            val first = if (trap.kit.deadfall) DEADFALL_FIRST_TICKS else 0L
            if (age >= first && (age - first) % LURE_TICKS == 0L && coord != trap.coord) lure(trap)
            if (trap.state == SET && now >= trap.expiresAt) expire(this, trap, iter)
        }
    }

    /** Ends a catching animation: the full trap, the failed trap, or (no failed loc: a deadfall) nothing, false. */
    private fun PlayerRef.settle(trap: Trap): Boolean {
        val creature = trap.creature
        trap.state = if (creature == null) FAILED else FULL
        val next = if (creature == null) trap.kit.failed else trap.kit.full[creature.name] ?: trap.kit.failed
        if (next < 0) {
            revertLoc(trap.coord, trap.shape)
            return false
        }
        addLoc(next, trap.coord, trap.shape, trap.angle)
        return true
    }

    /** A net trap that nobody used for a minute reverts to its item on the ground (Wiki "Net trap"). */
    private fun expire(p: PlayerRef, trap: Trap, iter: MutableIterator<Trap>) {
        p.revertLoc(trap.coord, trap.shape)
        p.transaction("hunter net trap expire") { createOnGround(Objs.NET_TRAP, 1, trap.coord, p.username, NET_GROUND_TICKS) }
        HunterShared.addExtra(p, -1)
        iter.remove()
    }

    private fun PlayerRef.lure(trap: Trap) {
        val wildlife = worldAttr(WILD) ?: return
        val wanted = trap.kit.full.keys
        val found =
            wildlife.npcs.firstOrNull { npc ->
                val creature = HunterData.BY_NPC[npc.type]
                npc.isInWorld && creature != null && creature.name in wanted && npc.coord.level == trap.coord.level &&
                    npc.coord.chebyshev(trap.coord) <= LURE_RANGE
            } ?: return
        val creature = HunterData.BY_NPC.getValue(found.type)
        trap.state = CATCHING
        trap.settleAt = tick + SETTLE_TICKS
        if (level(Stats.HUNTER) >= creature.level && statRandom(Stats.HUNTER, creature.low, creature.high)) {
            found.respawnAfter(CREATURE_RESPAWN_TICKS)
            trap.creature = creature
            addLoc(trap.kit.catching.getValue(creature.name), trap.coord, trap.shape, trap.angle)
        } else {
            trap.creature = null
            addLoc(trap.kit.failing, trap.coord, trap.shape, trap.angle)
        }
    }

    // ---- ops

    private suspend fun PlayerScript.dismantle(loc: LocRef) {
        val p = player
        val trap = owned(p, loc) ?: return
        p.stopInteraction()
        p.anim(Seqs.HUMAN_HUNTING_DISMANTLE_NET)
        delay(PICKUP_TICKS)
        if (!traps(p).contains(trap) || trap.state == CATCHING) return
        if (!trap.kit.deadfall && !p.transaction("hunter net dismantle") { add(Invs.INV, Objs.NET_TRAP, 1) }) return p.message(FULL_MESSAGE)
        remove(p, trap)
    }

    private fun PlayerScript.investigate(loc: LocRef) {
        val trap = owned(player, loc) ?: return
        player.message("The ${if (trap.kit.deadfall) "deadfall" else "net trap"} is set and waiting for something to wander into it.")
    }

    /** "Check" (loot) and "Reset" on a full trap; "Reset" on a failed net trap. */
    private suspend fun PlayerScript.collect(loc: LocRef, reset: Boolean, loot: Boolean) {
        val p = player
        val trap = owned(p, loc) ?: return
        val creature = trap.creature.takeIf { loot && trap.state == FULL }
        if (loot && creature == null) return
        p.stopInteraction()
        p.anim(Seqs.HUMAN_HUNTING_DISMANTLE_NET)
        delay(PICKUP_TICKS)
        if (!traps(p).contains(trap) || trap.state == CATCHING) return
        val amounts = creature?.loot?.map { it.min + p.random(it.max - it.min + 1) }.orEmpty()
        val given =
            p.transaction("hunter static collect") {
                var ok = true
                creature?.loot?.forEachIndexed { i, row -> ok = ok && add(Invs.INV, row.obj, amounts[i]) }
                if (!reset && !trap.kit.deadfall) ok = ok && add(Invs.INV, Objs.NET_TRAP, 1)
                ok
            }
        if (!given) return p.message(FULL_MESSAGE)
        if (creature != null) {
            p.addXp(Stats.HUNTER, creature.xp)
            HunterRumours.caught(p, creature)
        }
        if (reset && !trap.kit.deadfall) {
            trap.state = SET
            trap.creature = null
            trap.laidAt = p.tick
            trap.expiresAt = p.tick + NET_LIFETIME_TICKS
            p.addLoc(trap.kit.set, trap.coord, trap.shape, trap.angle)
        } else {
            remove(p, trap)
        }
    }

    private fun owned(p: PlayerRef, loc: LocRef): Trap? {
        val trap = traps(p).firstOrNull { it.coord == loc.coord }
        if (trap == null) p.message("This isn't your trap.")
        return trap
    }

    private fun remove(p: PlayerRef, trap: Trap) {
        if (traps(p).remove(trap) && !trap.kit.deadfall) HunterShared.addExtra(p, -1)
        p.revertLoc(trap.coord, trap.shape)
    }

    /** A logout takes the player's traps out of the world (they are not saved). */
    private fun PlayerRef.clearTraps() {
        val list = this[TRAPS] ?: return
        for (trap in list) revertLoc(trap.coord, trap.shape)
        list.clear()
        HunterShared.addExtra(this, -HunterShared.extra(this))
    }

    private fun traps(p: PlayerRef): ArrayList<Trap> = p[TRAPS] ?: ArrayList<Trap>().also { p[TRAPS] = it }

    private companion object {
        val TRAPS = AttrKey<ArrayList<Trap>>("hunter.static.traps")
        val WILD = AttrKey<Wildlife>("hunter.static.wildlife")

        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val FULL_MESSAGE = "You don't have enough inventory space."

        /** Trap states: waiting, a catch or a failure is playing, holding a catch, failed. */
        const val SET = 0
        const val CATCHING = 1
        const val FULL = 2
        const val FAILED = 3

        /** Ticks to set a trap (Wiki "Tick manipulation": four ticks) and to take one down (three ticks). */
        const val LAY_TICKS = 4
        const val PICKUP_TICKS = 3

        /** Lure cadence and range (Wiki "Box trap", Mechanics), OPEN-HU1: the same for deadfalls and net traps. */
        const val LURE_TICKS = 3L
        const val LURE_RANGE = 2

        /** Wiki "Deadfall", Aug 2026: "Deadfall traps begin attempting catches after 8 ticks". */
        const val DEADFALL_FIRST_TICKS = 8L

        /** Wiki "Deadfall": two deadfalls at once; a 75% chance to keep the log when setting one. */
        const val DEADFALL_LIMIT = 2
        const val PRESERVE_NUMERATOR = 3
        const val PRESERVE_DENOMINATOR = 4

        /** The lowest Hunter level of a deadfall creature: wild kebbit (Wiki "Deadfall"). */
        const val DEADFALL_MIN_LEVEL = 23

        /** Wiki "Net trap": it reverts to its item "after approximately a minute" and the item lies there another minute (OPEN-HU1). */
        const val NET_LIFETIME_TICKS = 100L
        const val NET_GROUND_TICKS = 100

        /** OPEN-HU2 / HU3: how long the catching or failing animation shows, and how long a creature is away (as in Hunter.kt). */
        const val SETTLE_TICKS = 3L
        const val CREATURE_RESPAWN_TICKS = 25

        private fun net(
            up: Int,
            set: Int,
            failing: Int,
            failed: Int,
            creature: String,
            catching: Int,
            full: Int,
        ) = Kit(false, listOf(up), set, failing, failed, mapOf(creature to catching), mapOf(creature to full))

        /**
         * The static traps: the deadfall on the boulder (cache locs 19215 .. 20652; the full loc is the creature's boulder) and the
         * net traps of the five salamander colours (cache locs `HUNTING_SAPLING_*`, names in the symbols).
         */
        val KITS: List<Kit> =
            listOf(
                Kit(
                    true,
                    listOf(Locs.HUNTING_DEADFALL_BOULDER),
                    Locs.HUNTING_DEADFALL_TRAP,
                    Locs.HUNTING_DEADFALL_FAILING,
                    -1,
                    mapOf(
                        "Wild kebbit" to Locs.HUNTING_DEADFALL_TRAPPING_CLAW,
                        "Barb-tailed kebbit" to Locs.HUNTING_DEADFALL_TRAPPING_BARBED,
                        "Prickly kebbit" to Locs.HUNTING_DEADFALL_TRAPPING_SPIKE,
                        "Sabre-toothed kebbit" to Locs.HUNTING_DEADFALL_TRAPPING_SABRE,
                        "Pyre fox" to Locs.HUNTING_DEADFALL_TRAPPING_FENNEC,
                    ),
                    mapOf(
                        "Wild kebbit" to Locs.HUNTING_DEADFALL_FULL_CLAW,
                        "Barb-tailed kebbit" to Locs.HUNTING_DEADFALL_FULL_BARBED,
                        "Prickly kebbit" to Locs.HUNTING_DEADFALL_FULL_SPIKE,
                        "Sabre-toothed kebbit" to Locs.HUNTING_DEADFALL_FULL_SABRE,
                        "Pyre fox" to Locs.HUNTING_DEADFALL_FULL_FENNEC,
                    ),
                ),
                net(
                    Locs.HUNTING_SAPLING_UP_SWAMP,
                    Locs.HUNTING_SAPLING_NET_SET_SWAMP,
                    Locs.HUNTING_SAPLING_FAILING_SWAMP,
                    Locs.HUNTING_SAPLING_NET_RESET_SWAMP,
                    "Swamp lizard (Hunter)",
                    Locs.HUNTING_SAPLING_CATCHING_GREEN,
                    Locs.HUNTING_SAPLING_FULL_GREEN,
                ),
                net(
                    Locs.HUNTING_SAPLING_UP_ORANGE,
                    Locs.HUNTING_SAPLING_NET_SET_ORANGE,
                    Locs.HUNTING_SAPLING_FAILING_ORANGE,
                    Locs.HUNTING_SAPLING_NET_RESET_ORANGE,
                    "Orange salamander (Hunter)",
                    Locs.HUNTING_SAPLING_CATCHING_ORANGE,
                    Locs.HUNTING_SAPLING_FULL_ORANGE,
                ),
                net(
                    Locs.HUNTING_SAPLING_UP_RED,
                    Locs.HUNTING_SAPLING_NET_SET_RED,
                    Locs.HUNTING_SAPLING_FAILING_RED,
                    Locs.HUNTING_SAPLING_NET_RESET_RED,
                    "Red salamander (Hunter)",
                    Locs.HUNTING_SAPLING_CATCHING_RED,
                    Locs.HUNTING_SAPLING_FULL_RED,
                ),
                net(
                    Locs.HUNTING_SAPLING_UP_BLACK,
                    Locs.HUNTING_SAPLING_NET_SET_BLACK,
                    Locs.HUNTING_SAPLING_FAILING_BLACK,
                    Locs.HUNTING_SAPLING_NET_RESET_BLACK,
                    "Black salamander (Hunter)",
                    Locs.HUNTING_SAPLING_CATCHING_BLACK,
                    Locs.HUNTING_SAPLING_FULL_BLACK,
                ),
                net(
                    Locs.HUNTING_SAPLING_UP_MOUNTAIN,
                    Locs.HUNTING_SAPLING_NET_SET_MOUNTAIN,
                    Locs.HUNTING_SAPLING_FAILING_MOUNTAIN,
                    Locs.HUNTING_SAPLING_NET_RESET_MOUNTAIN,
                    "Tecu salamander (Hunter)",
                    Locs.HUNTING_SAPLING_CATCHING_MOUNTAIN,
                    Locs.HUNTING_SAPLING_FULL_MOUNTAIN,
                ),
            )
    }
}
