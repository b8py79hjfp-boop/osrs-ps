package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.plugin.choice
import tp.plugin.mesbox
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs

/** The player timer that ends a falcon's wait at its prey. */
private val PREY_TIMER = TimerId("hunter.falcon")

/**
 * Falconry (docs/mechanics/hunter.md HU35): Matthias lends a falconer's glove with a gyr falcon for 500 coins; "Catch" on a
 * spotted, dark or dashing kebbit sends the falcon, which either catches it (the falcon waits at the prey, the glove is empty) or
 * returns; "Retrieve" on the falcon gives the kebbit's loot and xp and the falcon back on the glove.
 */
public class HunterFalconry : ContentPlugin {
    /** A falcon waiting at its catch. */
    private class Prey(val falcon: NpcRef, val creature: Creature)

    override fun ContentScope.register() {
        val kebbits = HunterData.BY_NPC.filterValues { it.kind == TrapKind.FALCON }.keys
        for (npc in kebbits) onOpNpc(npc, OP_FIRST) { kebbit -> send(kebbit) }
        for (falcon in FALCONS) onOpNpc(falcon, OP_FIRST) { f -> retrieve(f) }
        onOpNpc(Npcs.HUNTING_NPC_FALCONER, OP_FIRST) { rent(player, ask = true) }
        onOpNpc(Npcs.HUNTING_NPC_FALCONER_NOBIRD, OP_FIRST) { rent(player, ask = true) }
        onOpNpc(Npcs.HUNTING_NPC_FALCONER, OP_QUICK) { rent(player, ask = false) }
        onOpNpc(Npcs.HUNTING_NPC_FALCONER_NOBIRD, OP_QUICK) { rent(player, ask = false) }
        onLogout { dropPrey() }
    }

    // ---- Matthias

    /** The talk menu: true when the player chose to rent a falcon. */
    private suspend fun PlayerScript.menu(p: PlayerRef): Boolean {
        val pick = choice("Matthias", "Rent a falcon (500 coins).", "Invest in the falconry (500,000 coins).", "Give the falcon back.")
        if (pick == PICK_RETURN) giveBack(p)
        if (pick == PICK_INVEST) invest(p)
        return pick == PICK_RENT
    }

    private suspend fun PlayerScript.rent(p: PlayerRef, ask: Boolean) {
        if (ask && !menu(p)) return
        if (p.inv(Invs.INV).total(GLOVE) + p.inv(Invs.INV).total(GLOVE_EMPTY) > 0 ||
            wornGlove(p) != NONE
        ) {
            return p.message("You already have a falcon.")
        }
        val free = p.saved(KEY_INVESTED) == 1L
        val paid =
            p.transaction("hunter falcon rent") { (free || remove(Invs.INV, Objs.COINS, RENT_FEE)) && add(Invs.INV, GLOVE, 1) }
        if (!paid) return mesbox("You need 500 coins and some free space for a falcon.")
        p.message("Matthias gives you a falcon on a falconer's glove. Wear it to hunt.")
    }

    private suspend fun PlayerScript.invest(p: PlayerRef) {
        if (p.saved(KEY_INVESTED) == 1L) return mesbox("You have already invested in my falconry.")
        if (!p.transaction("hunter falcon invest") { remove(Invs.INV, Objs.COINS, INVEST_FEE) }) return mesbox("You need 500,000 coins.")
        p.setSaved(KEY_INVESTED, 1L)
        mesbox("Thank you. Falcons are free for you from now on.")
    }

    private fun giveBack(p: PlayerRef) {
        val gone =
            p.transaction("hunter falcon return") {
                var ok = false
                for (glove in intArrayOf(GLOVE, GLOVE_EMPTY)) {
                    if (p.inv(Invs.INV).total(glove) > 0) ok = remove(Invs.INV, glove, 1) || ok
                }
                ok
            }
        if (!gone) p.message("Take the glove off first.")
    }

    // ---- sending the falcon

    private fun wornGlove(p: PlayerRef): Int =
        p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT).let { if (it == GLOVE || it == GLOVE_EMPTY) it else NONE }

    /** "Catch" on a kebbit: the falcon flies there (a tick for every tile), then the catch is rolled. */
    private suspend fun PlayerScript.send(kebbit: NpcRef) {
        val p = player
        val creature = HunterData.BY_NPC[kebbit.type]?.takeIf { it.kind == TrapKind.FALCON } ?: return
        val problem =
            when {
                p.level(Stats.HUNTER) < creature.level -> "You need a Hunter level of ${creature.level} to catch this."
                wornGlove(p) == NONE -> "You need to wear a falconer's glove with a falcon to catch this."
                wornGlove(p) == GLOVE_EMPTY || p[PREY] != null -> "Your falcon is not on your glove."
                !kebbit.isInWorld -> "The kebbit has gone."
                else -> null
            }
        if (problem != null) return p.message(problem)
        p.stopInteraction()
        p.face(kebbit)
        p.anim(Seqs.FALCON_LAUNCH)
        delay(FLIGHT_BASE_TICKS + p.distanceTo(kebbit))
        if (!kebbit.isInWorld || wornGlove(p) != GLOVE) return
        if (!p.statRandom(Stats.HUNTER, creature.low, creature.high)) return p.message("The falcon fails to catch the kebbit.")
        val glove = p.transaction("hunter falcon glove") { replaceAt(Invs.WORN, Skilling.WEAPON_SLOT, GLOVE, GLOVE_EMPTY, 1) }
        if (!glove) return
        val at = kebbit.coord
        kebbit.respawnAfter(CREATURE_RESPAWN_TICKS)
        p[PREY] = Prey(p.spawnNpc(FALCONS[0], at), creature)
        p.timer(PREY_TIMER, PREY_TICKS, true) { leave() }
    }

    /** "Retrieve" on the falcon at its prey: the loot goes into the backpack and what does not fit is discarded (Wiki, Aug 2026). */
    private suspend fun PlayerScript.retrieve(falcon: NpcRef) {
        val p = player
        val prey = p[PREY]
        if (prey == null || prey.falcon != falcon) return p.message("That is not your falcon.")
        p.stopInteraction()
        p.stopTimer(PREY_TIMER)
        p[PREY] = null
        prey.falcon.despawn()
        p.transaction("hunter falcon glove") { replaceAt(Invs.WORN, Skilling.WEAPON_SLOT, GLOVE_EMPTY, GLOVE, 1) }
        for (row in prey.creature.loot) {
            val count = row.min + p.random(row.max - row.min + 1)
            p.transaction("hunter falcon loot") { add(Invs.INV, row.obj, count) }
        }
        p.addXp(Stats.HUNTER, prey.creature.xp)
        HunterRumours.caught(p, prey.creature)
    }

    /** The falcon leaves its prey after a while: "Your falcon has left its prey. You see it heading back toward the falconer." */
    private fun PlayerRef.leave() {
        stopTimer(PREY_TIMER)
        dropPrey()
        message("Your falcon has left its prey. You see it heading back toward the falconer.")
    }

    private fun PlayerRef.dropPrey() {
        val prey = this[PREY] ?: return
        prey.falcon.despawn()
        this[PREY] = null
    }

    private companion object {
        val PREY = AttrKey<Prey>("hunter.falcon.prey")
        const val OP_FIRST = 1
        const val OP_QUICK = 3
        const val NONE = -1
        const val PICK_RENT = 1
        const val PICK_INVEST = 2
        const val PICK_RETURN = 3

        /** Cache objs 10024 "Falconer's glove" with a falcon and 10023 without one (Wiki "Falconer's glove": version 2 / version 1). */
        const val GLOVE = 10024
        const val GLOVE_EMPTY = 10023

        /** Wiki "Falconry": 500 coins per rental, or a one-time 500,000 coins. */
        const val RENT_FEE = 500
        const val INVEST_FEE = 500_000
        const val KEY_INVESTED = "hunter.falcon.invested"

        /** OPEN-HU19: the Wiki gives no flight time ("running up ... will decrease the bird's travel time"): one tick per tile plus two. */
        const val FLIGHT_BASE_TICKS = 2

        /** OPEN-HU19: how long the falcon waits at its prey ("within a short time"), and how long the kebbit is away. */
        const val PREY_TICKS = 100
        const val CREATURE_RESPAWN_TICKS = 25

        /** The gyr falcon npcs with the "Retrieve" op (cache 1342 .. 1344). */
        val FALCONS = intArrayOf(Npcs.HUNTING_FALCON_ONSPEEDY, Npcs.HUNTING_FALCON_ONSPEEDY2, Npcs.HUNTING_FALCON_ONSILENT)
    }
}
