package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.CoordGrid

/** The player timer that collapses the pits a player set and left. */
private val PIT_TIMER = TimerId("hunter.pitfall")

/**
 * Pitfall trapping (docs/mechanics/hunter.md HU43 .. HU46): spined larupia, horned graahk, sabre-toothed kyatt, sunlight and
 * moonlight antelope. The 25 pits of the map are multi-locs drawn per player from the varbits `HUNT_PITFALL_STATE1..25`, so a pit
 * set by one player is seen by that player only (cache loc 19253 `multiLocs`): the parent id names the pit and the op is chosen from
 * the player's own state ("Trap" on an empty pit, "Jump" / "Dismantle" on a set one, "Dismantle" on a collapsed one).
 */
public class HunterPitfalls : ContentPlugin {
    /** What one player has going: when each pit was set, the teased creature and the last pit it refused to cross. */
    private class Session {
        val setAt = LongArray(PITS)
        var teased: NpcRef? = null
        var teasedUntil: Long = 0
        var lastMiss: Int = -1
    }

    override fun ContentScope.register() {
        for (pit in 0 until PITS) {
            val parent = parentOf(pit)
            onOpLoc(parent, OP_JUMP) { loc -> jump(player, pit, loc) }
            onOpLoc(parent, OP_DISMANTLE) { loc -> dismantle(player, pit, loc) }
            onOpLoc(parent, OP_TRAP) { loc -> trap(player, pit, loc) }
        }
        onOpNpcOption(OP_TEASE) { npc -> tease(player, npc) }
        onLogin { clearPits() }
        onLogout { clearPits() }
    }

    // ---- setting the pit

    private suspend fun PlayerScript.trap(p: PlayerRef, pit: Int, loc: LocRef) {
        val problem = trapProblem(p, pit, loc)
        if (problem != null) return p.message(problem)
        p.stopInteraction()
        p.anim(Seqs.HUNTING_SETTING_TRAP_SMALL)
        delay(TRAP_TICKS)
        // The state, the limit and the logs are checked again after the delay.
        if (trapProblem(p, pit, loc) != null) return
        val logs = logsOf(p)
        if (logs < 0 || !p.transaction("hunter pitfall set") { remove(Invs.INV, logs, 1) }) return
        p.setVarbit(varbitOf(pit), SET)
        session(p).setAt[pit] = p.tick
        HunterShared.addExtra(p, 1)
        if (!p.hasTimer(PIT_TIMER)) p.timer(PIT_TIMER, 1, true) { runPits() }
    }

    /** Why [p] cannot set pit [pit], or null. */
    private fun trapProblem(p: PlayerRef, pit: Int, loc: LocRef): String? {
        val creature = creatureOf(pit, loc.coord)
        val level = p.baseLevel(Stats.HUNTER)
        val limit = HunterData.trapLimit(level, false)
        return when {
            p.varbit(varbitOf(pit)) != EMPTY -> "Someone has already set a trap here."
            p.level(Stats.HUNTER) < creature.level -> "You need a Hunter level of ${creature.level} to set this trap."
            !hasKnife(p) -> "You need a knife to set a pitfall trap."
            logsOf(p) < 0 -> "You need logs to set a pitfall trap."
            HunterShared.normal(p) + HunterShared.extra(p) >= limit -> "You can't have more than $limit traps laid at your Hunter level."
            else -> null
        }
    }

    private fun hasKnife(p: PlayerRef): Boolean = p.inv(Invs.INV).let { it.total(Objs.KNIFE) > 0 || it.total(Objs.FLETCHING_KNIFE) > 0 }

    /**
     * The backpack logs that cost least (Wiki "Pitfall": "the lowest tier of logs will be used first"; redwood and arctic pine
     * cannot be used), or -1. The value is the tie-break for "tier".
     */
    private fun logsOf(p: PlayerRef): Int {
        val inv = p.inv(Invs.INV)
        var best = -1
        for (slot in 0 until inv.size) {
            val obj = inv.obj(slot)
            if (obj < 0 || p.unnoted(obj) != obj) continue
            val name = p.objName(obj).lowercase()
            val log = name == "logs" || (name.endsWith(" logs") && !name.contains("pyre") && !name.contains("forestry"))
            if (!log || name == "redwood logs" || name == "arctic pine logs") continue
            if (best < 0 || p.objCost(obj) < p.objCost(best)) best = obj
        }
        return best
    }

    // ---- teasing and jumping

    /** "Tease" with a teasing stick in the backpack or a hunter's spear in hand: the creature is angry for [TEASE_TICKS]. */
    private suspend fun PlayerScript.tease(p: PlayerRef, npc: NpcRef) {
        val creature =
            HunterData.BY_NPC[npc.type]?.takeIf { it.kind == TrapKind.PITFALL } ?: return p.message("Nothing interesting happens.")
        val inv = p.inv(Invs.INV)
        val armed = inv.total(Objs.HUNTING_TEASING_STICK) > 0 || p.inv(Invs.WORN).obj(WEAPON_SLOT) == Objs.HG_HUNTER_SPEAR
        if (!armed) return p.message("You need a teasing stick or a hunter's spear to tease this animal.")
        if (p.level(Stats.HUNTER) < creature.level) return p.message("You need a Hunter level of ${creature.level} to tease this animal.")
        p.stopInteraction()
        p.face(npc)
        p.anim(Seqs.HUNTING_TEASING_ANIMAL)
        val s = session(p)
        s.teased = npc
        s.teasedUntil = p.tick + TEASE_TICKS
    }

    /**
     * "Jump" over a set pit: the player is carried across to the far side, and a creature that was teased and is close behind
     * follows over the pit and is caught by the creature's catch roll. A creature never crosses the same pit twice in a row
     * (Wiki "Pitfall"), so the pit it just refused is skipped.
     */
    private suspend fun PlayerScript.jump(p: PlayerRef, pit: Int, loc: LocRef) {
        if (p.varbit(varbitOf(pit)) != SET) return
        val land = landing(p.coord, loc.coord)
        if (land == null || !p.isWalkable(land)) return p.message("You can't jump over the pit from here.")
        p.stopInteraction()
        p.anim(Seqs.HUMAN_SPOT_JUMP)
        p.exactMove(p.coord, land, 0, JUMP_TICKS * CLIENT_CYCLES_PER_TICK, angleOf(land.x - p.coord.x, land.z - p.coord.z))
        delay(JUMP_TICKS)
        val s = session(p)
        val npc = s.teased?.takeIf { it.isInWorld && s.teasedUntil >= p.tick && it.distanceTo(p) <= FOLLOW_RANGE } ?: return
        s.teased = null
        val creature = HunterData.BY_NPC[npc.type] ?: return
        if (pit == s.lastMiss) return
        if (p.level(Stats.HUNTER) >= creature.level && p.statRandom(Stats.HUNTER, creature.low, creature.high)) {
            npc.respawnAfter(if (creature.name.endsWith("antelope")) ANTELOPE_RESPAWN_TICKS else CREATURE_RESPAWN_TICKS)
            p.setVarbit(varbitOf(pit), FULL)
            s.lastMiss = -1
        } else {
            s.lastMiss = pit
        }
    }

    // ---- dismantling

    /** "Dismantle": a set pit is taken down (no refund of the logs, OPEN-HU27); a collapsed one gives the loot and the xp. */
    private suspend fun PlayerScript.dismantle(p: PlayerRef, pit: Int, loc: LocRef) {
        val state = p.varbit(varbitOf(pit))
        if (state == EMPTY) return
        p.stopInteraction()
        p.anim(Seqs.HUMAN_HUNTING_DISMANTLE_NET)
        delay(DISMANTLE_TICKS)
        if (p.varbit(varbitOf(pit)) != state) return
        if (state >= FULL) {
            val creature = creatureOf(pit, loc.coord)
            if (!giveLoot(p, creature)) return p.message("You don't have enough inventory space.")
            p.addXp(Stats.HUNTER, creature.xp)
            HunterRumours.caught(p, creature)
        }
        free(p, pit)
    }

    private fun giveLoot(p: PlayerRef, creature: Creature): Boolean {
        val amounts = creature.loot.map { it.min + p.random(it.max - it.min + 1) }
        val fur = FURS[creature.name]?.let { if (p.random(FUR_DENOMINATOR) < FUR_NUMERATOR) it.first else it.second }
        return p.transaction("hunter pitfall loot") {
            var ok = true
            creature.loot.forEachIndexed { i, row -> ok = ok && add(Invs.INV, row.obj, amounts[i]) }
            if (fur != null) ok = ok && add(Invs.INV, fur, 1)
            ok
        }
    }

    private fun free(p: PlayerRef, pit: Int) {
        p.setVarbit(varbitOf(pit), EMPTY)
        session(p).setAt[pit] = 0
        HunterShared.addExtra(p, -1)
    }

    // ---- upkeep

    /** A pit that was set and left collapses after [PIT_LIFETIME_TICKS] (OPEN-HU1); a collapsed pit with a catch stays. */
    private fun PlayerRef.runPits() {
        val s = this[SESSION]
        if (s == null || (0 until PITS).none { s.setAt[it] > 0 }) return stopTimer(PIT_TIMER)
        for (pit in 0 until PITS) {
            if (s.setAt[pit] > 0 && varbit(varbitOf(pit)) == SET && tick - s.setAt[pit] >= PIT_LIFETIME_TICKS) free(this, pit)
        }
    }

    /** Pits are not kept over a logout (Wiki "Pitfall": a trap trick pit "won't decay until the player logs out"). */
    private fun PlayerRef.clearPits() {
        for (pit in 0 until PITS) {
            if (varbit(varbitOf(pit)) != EMPTY) {
                setVarbit(varbitOf(pit), EMPTY)
                HunterShared.addExtra(this, -1)
            }
        }
        this[SESSION] = null
    }

    private fun session(p: PlayerRef): Session = p[SESSION] ?: Session().also { p[SESSION] = it }

    // ---- geometry and tables

    /** The tile on the far side of [pit] from [from]: one step beyond the pit along the longer axis of the approach. */
    private fun landing(from: CoordGrid, pit: CoordGrid): CoordGrid? {
        val dx = pit.x - from.x
        val dz = pit.z - from.z
        if (dx == 0 && dz == 0) return null
        return if (kotlin.math.abs(dx) >= kotlin.math.abs(dz)) {
            CoordGrid(pit.x + dx.coerceIn(-1, 1), pit.z, pit.level)
        } else {
            CoordGrid(pit.x, pit.z + dz.coerceIn(-1, 1), pit.level)
        }
    }

    /** Client facing angle for a step (0 south, 512 west, 1024 north, 1536 east; Refs.kt exactMove). */
    private fun angleOf(dx: Int, dz: Int): Int =
        when {
            kotlin.math.abs(dx) >= kotlin.math.abs(dz) -> if (dx > 0) ANGLE_EAST else ANGLE_WEST
            else -> if (dz > 0) ANGLE_NORTH else ANGLE_SOUTH
        }

    private fun parentOf(pit: Int): Int =
        if (pit <
            FIRST_VARLAMORE
        ) {
            Locs.HUNTING_PITFALL_1 + pit
        } else {
            Locs.HUNTING_PITFALL_17 + (pit - FIRST_VARLAMORE)
        }

    private fun varbitOf(pit: Int): Int =
        if (pit < FIRST_VARLAMORE) Varbits.HUNT_PITFALL_STATE1 + pit else Varbits.HUNT_PITFALL_STATE17 + (pit - FIRST_VARLAMORE)

    /**
     * The creature of pit [pit]: the map's 25 pits are the kyatt's (1 .. 6), the larupia's (7 .. 11), the graahk's (12 .. 16; cache
     * loc `multiLocs` of each pit lists that creature's collapsed trap) and the antelopes' (17 .. 25: the sunlight antelope's pits are
     * in the Avium Savannah, east of x 1700, the moonlight antelope's in the Burrow below the Hunter Guild).
     */
    private fun creatureOf(pit: Int, at: CoordGrid): Creature =
        HunterData.BY_NAME.getValue(
            when {
                pit < KYATT_END -> "Sabre-toothed kyatt"
                pit < LARUPIA_END -> "Spined larupia"
                pit < FIRST_VARLAMORE -> "Horned graahk"
                at.x >= AVIUM_MIN_X -> "Sunlight antelope"
                else -> "Moonlight antelope"
            },
        )

    private companion object {
        val SESSION = AttrKey<Session>("hunter.pitfall.session")

        const val OP_JUMP = 1
        const val OP_DISMANTLE = 2
        const val OP_TRAP = 3
        const val OP_TEASE = "Tease"

        /** The map's pits (Varbits `HUNT_PITFALL_STATE1..25`) and where the lists of creatures change (cache loc `multiLocs`). */
        const val PITS = 25
        const val KYATT_END = 6
        const val LARUPIA_END = 11
        const val FIRST_VARLAMORE = 16

        /** The sunlight antelope's pits lie east of this x (Wiki "Pitfall" locations: x 1738 .. 1751, the Burrow is at x 1559). */
        const val AVIUM_MIN_X = 1700

        /** A pit's varbit values, from the multi-loc 19253: an empty pit, a set pit, a pit with a creature in it (two orientations). */
        const val EMPTY = 0
        const val SET = 1
        const val FULL = 3

        const val WEAPON_SLOT = 3

        /** Ticks to set a pit and to dismantle one (Wiki "Tick manipulation": laying takes four ticks, picking up three). */
        const val TRAP_TICKS = 4
        const val DISMANTLE_TICKS = 3

        /** OPEN-HU25: the Wiki gives no timing for a teased creature's anger, the jump or how near it must follow. */
        const val TEASE_TICKS = 30L
        const val JUMP_TICKS = 2
        const val FOLLOW_RANGE = 8

        /** Client cycles per game tick (Refs.kt exactMove: 20 ms cycles). */
        const val CLIENT_CYCLES_PER_TICK = 30
        const val ANGLE_SOUTH = 0
        const val ANGLE_WEST = 512
        const val ANGLE_NORTH = 1024
        const val ANGLE_EAST = 1536

        /** OPEN-HU1: the Wiki gives no lifetime for a set pit. */
        const val PIT_LIFETIME_TICKS = 100L

        /** Wiki "Sunlight antelope": the respawn "has been decreased from 54 seconds to 27 seconds" (45 ticks); OPEN-HU3 for the rest. */
        const val ANTELOPE_RESPAWN_TICKS = 45
        const val CREATURE_RESPAWN_TICKS = 25

        /** OPEN-HU26: the Wiki says only that the chance of the undamaged fur "scales with Hunter level"; half of the catches here. */
        const val FUR_NUMERATOR = 1
        const val FUR_DENOMINATOR = 2

        /** Undamaged and tatty fur of the large cats (Wiki drop tables: "Common" rows; cache objs `HUNTING_FUR_*`). */
        val FURS: Map<String, Pair<Int, Int>> =
            mapOf(
                "Spined larupia" to (Objs.HUNTING_FUR_JAGUAR_PERFECT to Objs.HUNTING_FUR_JAGUAR_SHABBY),
                "Horned graahk" to (Objs.HUNTING_FUR_LEOPARD_PERFECT to Objs.HUNTING_FUR_LEOPARD_SHABBY),
                "Sabre-toothed kyatt" to (Objs.HUNTING_FUR_TIGER_PERFECT to Objs.HUNTING_FUR_TIGER_SHABBY),
            )
    }
}
