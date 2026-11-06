package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
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

/** The player timer that runs a player's laid traps once a tick. */
private val TRAP_TIMER = TimerId("hunter.traps")

/**
 * Hunter (docs/mechanics/hunter.md). Bird snares and box traps are laid from the backpack ("Lay") as a loc on the player's
 * tile. Every 3 ticks a set trap looks for a creature of its kind within 2 tiles (the Wiki: "the trap hunts the NPC"); the
 * catch is rolled with the creature's Skilling success chart ([PlayerRef.statRandom]). A caught creature leaves the world for
 * a while and the trap shows its catch until the owner "Check"s or "Reset"s it; a failed or collapsed trap is "Dismantle"d or
 * "Reset". Butterflies and implings are caught from the NPC with a butterfly net and an empty jar.
 */
public class Hunter : ContentPlugin {
    /** One trap laid by a player. */
    private class Trap(val coord: CoordGrid, val kind: TrapKind) {
        var state: Int = SET
        var laidAt: Long = 0
        var expiresAt: Long = 0
        var settleAt: Long = 0
        var creature: Creature? = null
    }

    /** The creature npcs of one world that traps can catch, in spawn order (so the first in range is deterministic). */
    private class Wildlife(val npcs: LinkedHashSet<NpcRef> = LinkedHashSet())

    override fun ContentScope.register() {
        val trapped = HunterData.BY_NPC.filterValues { it.kind != TrapKind.NET }.keys
        onNpcSpawn {
            if (type in trapped) (worldAttr(WILDLIFE) ?: Wildlife().also { setWorldAttr(WILDLIFE, it) }).npcs += this
        }
        onItemOp(Objs.HUNTING_OJIBWAY_BIRD_SNARE, OP_LAY) { click -> lay(click, TrapKind.BIRD) }
        onItemOp(Objs.HUNTING_BOX_TRAP, OP_LAY) { click -> lay(click, TrapKind.BOX) }
        for (kind in listOf(TrapKind.BIRD, TrapKind.BOX)) {
            onOpLoc(setLoc(kind), 1) { loc -> dismantle(loc) }
            onOpLoc(setLoc(kind), 2) { loc -> investigate(loc) }
            onOpLoc(failedLoc(kind), 1) { loc -> dismantle(loc) }
            onOpLoc(failedLoc(kind), 2) { loc -> collect(loc, reset = true, loot = false) }
        }
        for (loc in FULL_LOCS) {
            onOpLoc(loc, 1) { l -> collect(l, reset = false, loot = true) }
            onOpLoc(loc, 2) { l -> collect(l, reset = true, loot = true) }
        }
        onOpNpcOption(OP_CATCH) { npc -> net(npc) }
        onLogout { clearTraps() }
    }

    // ---- laying

    private fun PlayerRef.lay(click: ButtonClick, kind: TrapKind) {
        val p = this
        val item = itemOf(kind)
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != item || p.isDelayed) return
        val problem = layProblem(p, kind)
        if (problem != null) return p.message(problem)
        p.startScript {
            p.anim(if (kind == TrapKind.BIRD) Seqs.HUNTING_SETTING_TRAP_SMALL else Seqs.HUMAN_CREATEFIRE)
            delay(LAY_TICKS)
            // Everything is checked again after the delay: the tile, the limit and the item may have changed.
            if (layProblem(p, kind) != null || !p.transaction("hunter lay") { remove(Invs.INV, item, 1) }) return@startScript
            val trap = Trap(p.coord, kind)
            trap.laidAt = p.tick
            trap.expiresAt = p.tick + TRAP_LIFETIME_TICKS
            traps(p).add(trap)
            p.addLoc(setLoc(kind), trap.coord, TRAP_SHAPE, 0)
            if (!p.hasTimer(TRAP_TIMER)) p.timer(TRAP_TIMER, 1, true) { runTraps() }
        }
    }

    /** Why [p] cannot lay a trap here, or null. */
    private fun layProblem(p: PlayerRef, kind: TrapKind): String? {
        val limit = HunterData.trapLimit(p.baseLevel(Stats.HUNTER), p[SharedAttrs.IN_WILDERNESS] == true)
        return when {
            traps(p).size >= limit -> "You can't have more than $limit traps laid at your Hunter level."
            p.locAt(p.coord, TRAP_SHAPE) != null -> "You can't lay a ${trapName(kind)} here."
            else -> null
        }
    }

    // ---- the trap tick

    /** Runs every tick for a player with traps: lures, settles and collapses their traps. */
    private fun PlayerRef.runTraps() {
        val list = this[TRAPS]
        if (list == null || list.isEmpty()) return stopTimer(TRAP_TIMER)
        val now = tick
        for (trap in list) {
            if (trap.state == CATCHING && now >= trap.settleAt) {
                trap.state = FULL
                addLoc(fullLoc(trap), trap.coord, TRAP_SHAPE, 0)
            }
            if (trap.state != SET) continue
            // A creature entering on the final tick is decided first, so it delays the collapse (Wiki "Black chinchompa").
            if ((now - trap.laidAt) % LURE_TICKS == 0L && coord != trap.coord) lure(trap)
            if (trap.state == SET && now >= trap.expiresAt) {
                trap.state = FAILED
                addLoc(failedLoc(trap.kind), trap.coord, TRAP_SHAPE, 0)
            }
        }
    }

    /** One lure attempt of a set trap: the first creature of its kind in range is caught or evades it. */
    private fun PlayerRef.lure(trap: Trap) {
        val wildlife = worldAttr(WILDLIFE) ?: return
        val found =
            wildlife.npcs.firstOrNull { npc ->
                val creature = HunterData.BY_NPC[npc.type]
                npc.isInWorld && creature != null && creature.kind == trap.kind && npc.coord.level == trap.coord.level &&
                    npc.coord.chebyshev(trap.coord) <= LURE_RANGE
            } ?: return
        val creature = HunterData.BY_NPC.getValue(found.type)
        if (level(Stats.HUNTER) >= creature.level && statRandom(Stats.HUNTER, creature.low, creature.high)) {
            found.respawnAfter(CREATURE_RESPAWN_TICKS)
            trap.creature = creature
            trap.state = CATCHING
            trap.settleAt = tick + SETTLE_TICKS
            addLoc(catchingLoc(creature), trap.coord, TRAP_SHAPE, 0)
        } else {
            trap.state = FAILED
            addLoc(failedLoc(trap.kind), trap.coord, TRAP_SHAPE, 0)
        }
    }

    // ---- trap ops

    private suspend fun PlayerScript.dismantle(loc: LocRef) {
        val p = player
        val trap = owned(p, loc) ?: return
        p.stopInteraction()
        p.anim(Seqs.HUMAN_HUNTING_DISMANTLE_NET)
        delay(PICKUP_TICKS)
        if (!traps(p).contains(trap) || trap.state == CATCHING) return
        if (!p.transaction("hunter dismantle") { add(Invs.INV, itemOf(trap.kind), 1) }) return p.message(FULL_MESSAGE)
        remove(p, trap)
    }

    private fun PlayerScript.investigate(loc: LocRef) {
        val trap = owned(player, loc) ?: return
        player.message("The ${trapName(trap.kind)} is set and waiting for something to wander into it.")
    }

    /** "Check" (loot, trap back to the backpack) or "Reset" (loot, trap laid again) on a full trap; "Reset" on a failed one. */
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
            p.transaction("hunter collect") {
                var ok = true
                creature?.loot?.forEachIndexed { i, row -> ok = ok && add(Invs.INV, row.obj, amounts[i]) }
                if (!reset) ok = ok && add(Invs.INV, itemOf(trap.kind), 1)
                ok
            }
        if (!given) return p.message(FULL_MESSAGE)
        if (creature != null) p.addXp(Stats.HUNTER, creature.xp)
        if (reset) {
            trap.state = SET
            trap.creature = null
            trap.laidAt = p.tick
            trap.expiresAt = p.tick + TRAP_LIFETIME_TICKS
            p.addLoc(setLoc(trap.kind), trap.coord, TRAP_SHAPE, 0)
        } else {
            remove(p, trap)
        }
    }

    /** [loc]'s trap if it is [p]'s (a trap belongs to the player who laid it), else tells them. */
    private fun owned(p: PlayerRef, loc: LocRef): Trap? {
        val trap = traps(p).firstOrNull { it.coord == loc.coord }
        if (trap == null) p.message("This isn't your trap.")
        return trap
    }

    private fun remove(p: PlayerRef, trap: Trap) {
        traps(p).remove(trap)
        p.deleteLoc(trap.coord, TRAP_SHAPE)
    }

    /** A logout takes the player's traps out of the world (they are not saved). */
    private fun PlayerRef.clearTraps() {
        val list = this[TRAPS] ?: return
        for (trap in list) deleteLoc(trap.coord, TRAP_SHAPE)
        list.clear()
    }

    // ---- butterflies and implings

    /** "Catch": a butterfly net in the weapon slot and an empty jar in the backpack, one roll every [NET_TICKS] ticks. */
    private fun PlayerScript.net(npc: NpcRef) {
        val p = player
        val creature = HunterData.BY_NPC[npc.type]?.takeIf { it.kind == TrapKind.NET }
        if (creature == null) {
            p.message("Nothing interesting happens.")
            return
        }
        if (!npc.isInWorld) return Skilling.stop(p)
        val clock = Skilling.clock(p, Skilling.locKey(creature.npcs.first(), npc.index))
        val problem = netProblem(p, creature)
        if (problem != null) {
            p.message(problem)
            return Skilling.stop(p)
        }
        keepInteraction()
        val tick = p.tick
        if (clock.fresh && clock.actionDelay < tick) {
            clock.actionDelay = tick + NET_TICKS
            p.face(npc)
            p.anim(Seqs.HUMAN_BUTTERFLYNET_SWING)
            return
        }
        if (clock.actionDelay > tick) return
        clock.actionDelay = tick + NET_TICKS
        p.anim(Seqs.HUMAN_BUTTERFLYNET_SWING)
        val magic = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT) == Objs.II_MAGIC_BUTTERFLY_NET
        val caught =
            if (magic) {
                p.statRandom(Stats.HUNTER, creature.low2, creature.high2)
            } else {
                p.statRandom(Stats.HUNTER, creature.low, creature.high)
            }
        if (caught) catch(p, npc, creature)
    }

    private fun netProblem(p: PlayerRef, creature: Creature): String? {
        val weapon = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT)
        return when {
            p.level(Stats.HUNTER) < creature.level -> {
                "You need a Hunter level of ${creature.level} to catch this."
            }

            weapon != Objs.HUNTING_BUTTERFLY_NET && weapon != Objs.II_MAGIC_BUTTERFLY_NET -> {
                "You need to be wielding a butterfly net to catch this."
            }

            p.inv(Invs.INV).total(jarFor(creature)) <= 0 -> {
                "You need an empty jar to catch this."
            }

            else -> {
                null
            }
        }
    }

    private fun catch(p: PlayerRef, npc: NpcRef, creature: Creature) {
        val stored =
            p.transaction("hunter net") {
                remove(Invs.INV, jarFor(creature), 1) && add(Invs.INV, creature.loot.first().obj, 1)
            }
        if (!stored) return p.message(FULL_MESSAGE)
        npc.respawnAfter(CREATURE_RESPAWN_TICKS)
        p.addXp(Stats.HUNTER, creature.xp)
        p.message("You catch the ${creature.name.lowercase()}.")
        Skilling.stop(p)
        p.stopInteraction()
    }

    // ---- helpers

    private fun traps(p: PlayerRef): ArrayList<Trap> = p[TRAPS] ?: ArrayList<Trap>().also { p[TRAPS] = it }

    private fun itemOf(kind: TrapKind): Int = if (kind == TrapKind.BIRD) Objs.HUNTING_OJIBWAY_BIRD_SNARE else Objs.HUNTING_BOX_TRAP

    private fun trapName(kind: TrapKind): String = if (kind == TrapKind.BIRD) "bird snare" else "box trap"

    private fun jarFor(creature: Creature): Int = if (creature.name == BUTTERFLY) Objs.BUTTERFLY_JAR else Objs.II_IMPLING_JAR

    private fun setLoc(kind: TrapKind): Int = if (kind == TrapKind.BIRD) Locs.HUNTING_OJIBWAY_TRAP else Locs.HUNTING_BOXTRAP_EMPTY

    private fun failedLoc(kind: TrapKind): Int =
        if (kind == TrapKind.BIRD) Locs.HUNTING_OJIBWAY_TRAP_BROKEN else Locs.HUNTING_BOXTRAP_FAILED

    private fun fullLoc(trap: Trap): Int =
        when (trap.creature?.name) {
            CHINCHOMPA_GREY -> Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA
            CHINCHOMPA_RED -> Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BIG
            CHINCHOMPA_BLACK -> Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BLACK
            else -> Locs.HUNTING_OJIBWAY_TRAP_FULL_COLOURED
        }

    private fun catchingLoc(creature: Creature): Int =
        when (creature.name) {
            CHINCHOMPA_GREY -> Locs.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_N
            CHINCHOMPA_RED -> Locs.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BIG_N
            CHINCHOMPA_BLACK -> Locs.HUNTING_BOXTRAP_TRAPPING_CHINCHOMPA_BLACK_N
            else -> Locs.HUNTING_OJIBWAY_TRAP_TRAPPING_COLOURED
        }

    private companion object {
        val TRAPS = AttrKey<ArrayList<Trap>>("hunter.traps")
        val WILDLIFE = AttrKey<Wildlife>("hunter.wildlife")

        const val OP_LAY = "Lay"
        const val OP_CATCH = "Catch"
        const val FULL_MESSAGE = "You don't have enough inventory space."

        /** Trap states: waiting, a creature is being caught, holding a catch, failed or collapsed. */
        const val SET = 0
        const val CATCHING = 1
        const val FULL = 2
        const val FAILED = 3

        /** Loc shape 10 (centrepiece, straight): the shape of every trap loc in the cache. */
        const val TRAP_SHAPE = 10

        /** Ticks to lay a trap: the skilling timer of laying is four ticks (Wiki "Tick manipulation": "default duration of four ticks"). */
        const val LAY_TICKS = 4

        /** Ticks to pick up a trap and stall the player (Wiki "Tick manipulation": "Picking up a trap takes three ticks"). */
        const val PICKUP_TICKS = 3

        /** A set trap looks for a creature every 3 ticks (Wiki "Box trap" Mechanics, Mod Ash: "approximately every 3 cycles"). */
        const val LURE_TICKS = 3L

        /** Creatures within 2 tiles of the trap (a 5x5 square) can be lured (Wiki "Box trap" Mechanics). */
        const val LURE_RANGE = 2

        /** OPEN-HU1: the Wiki gives no trap lifetime; a trap collapses 100 ticks (one minute) after it was set. */
        const val TRAP_LIFETIME_TICKS = 100L

        /** OPEN-HU2: ticks the "trapping" loc shows before the catch is checkable (the Wiki gives no timing). */
        const val SETTLE_TICKS = 3L

        /** OPEN-HU3: ticks a caught creature is away before it respawns at its spawn (the Wiki gives none for Hunter creatures). */
        const val CREATURE_RESPAWN_TICKS = 25

        /** OPEN-HU6: ticks between catch rolls with a butterfly net (the Wiki only says the player "continues trying"). */
        const val NET_TICKS = 3L

        /** Wiki page names of the chinchompa creature rows in hunter.tsv. */
        const val CHINCHOMPA_GREY = "Chinchompa (Hunter)"
        const val CHINCHOMPA_RED = "Carnivorous chinchompa"
        const val CHINCHOMPA_BLACK = "Black chinchompa (Hunter)"
        const val BUTTERFLY = "Black warlock"

        /** The full-trap locs, which carry the "Check" and "Reset" ops (cache loc configs). */
        val FULL_LOCS =
            listOf(
                Locs.HUNTING_OJIBWAY_TRAP_FULL_COLOURED,
                Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA,
                Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BIG,
                Locs.HUNTING_BOXTRAP_FULL_CHINCHOMPA_BLACK,
            )
    }
}
