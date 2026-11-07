package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * A Pollnivneach NPC that can be blackjacked (docs/mechanics/thieving.md TH12): the Thieving [level] to pickpocket it, the
 * [xp] and [coins] of one pickpocket while it is knocked out, and the low end of the knock-out roll (the high end is
 * [Blackjack.KNOCK_HIGH]).
 */
internal class BlackjackTarget(val level: Int, val xp: Double, val coins: Int, val knockLow: Int)

/** A knocked-out NPC: unconscious until [until], with [left] guaranteed pickpockets. */
private class KnockedOut(val until: Long, var left: Int)

/** Data and shared rules of blackjacking, used by [Blackjacking] and by [Pickpocketing]'s "Pickpocket" op. */
internal object Blackjack {
    /**
     * Pollnivneach NPCs by cache npc id (Wiki "Bandit (Pollnivneach)", "Menaphite Thug"): the bearded level 41 bandit (npcs 736 and
     * 737) needs 45 Thieving for 65 xp and 40 coins; the level 56 bandit (734, 735) 55 for 84.3 and 50; the Menaphite Thug (3549,
     * 3550) 65 for 137.5 and 60. The knock-out charts read low 80, 80 and 78 (high 240 for all).
     */
    val TARGETS: Map<Int, BlackjackTarget> =
        mapOf(
            736 to BlackjackTarget(45, 65.0, 40, 80),
            737 to BlackjackTarget(45, 65.0, 40, 80),
            734 to BlackjackTarget(55, 84.3, 50, 80),
            735 to BlackjackTarget(55, 84.3, 50, 80),
            3549 to BlackjackTarget(65, 137.5, 60, 78),
            3550 to BlackjackTarget(65, 137.5, 60, 78),
        )

    /** The high end of every knock-out chart (Wiki "Bandit (Pollnivneach)", "Menaphite Thug": `high1 = 240`). */
    const val KNOCK_HIGH = 240

    /** Wiki "Bandit (Pollnivneach)": "Knocking out either bandit rewards 10 Thieving experience" (applied to the thug too, OPEN-TH12). */
    const val KNOCK_XP = 10.0

    /** Wiki "Blackjack": "Once you successfully knock out an NPC, you can guarantee two pickpockets". */
    const val GUARANTEED_PICKPOCKETS = 2

    /**
     * Ticks a knocked-out NPC stays unconscious (OPEN-TH12: not on the Wiki; two pickpockets two ticks apart, "experience drops every
     * two ticks", fit inside it with one tick to spare).
     */
    const val KNOCKOUT_TICKS = 5L

    /** Ticks between two guaranteed pickpockets (Wiki "Pickpocketing": NPCs may be pickpocketed every two ticks). */
    const val PICKPOCKET_TICKS = 2

    /** How far a second NPC can see the knock-out (OPEN-TH12: the Wiki only says "line of sight to a second bandit"). */
    const val WITNESS_RADIUS = 8

    /** Ticks a lured NPC keeps following the player (OPEN-TH12: "Lure" is not described on the Wiki). */
    const val LURE_TICKS = 30

    /** Worn-inventory slot of the weapon (cache wear position 3). */
    const val WEAPON_SLOT = 3

    /**
     * The Feud gate (Wiki "Blackjack": "the player must first have progressed enough in The Feud to learn how to use a blackjack").
     * The quest is not implemented (content/quests has no Feud), so the gate is off: with `false` anyone can blackjack. Set to `true`
     * to refuse everyone until a Feud stage is available (OPEN-TH12).
     */
    const val REQUIRE_THE_FEUD = false

    /**
     * Every blackjack the cache has: oak 4599 / (o) 6408 / (d) 6410, willow 4600 / (o) 6412 / (d) 6414, maple 6416 / (o) 6418 / (d) 6420
     * (Wiki "Blackjack": the type does not matter for luring and knocking out).
     */
    val BLACKJACKS: Set<Int> = setOf(4599, 6408, 6410, 4600, 6412, 6414, 6416, 6418, 6420)

    val KNOCKED_OUT = AttrKey<Any>("thieving.knocked_out")
    val LURED = AttrKey<Any>("thieving.lured")

    fun targetOf(type: Int): BlackjackTarget? = TARGETS[type]

    fun knockOut(npc: NpcRef, now: Long) {
        npc[KNOCKED_OUT] = KnockedOut(now + KNOCKOUT_TICKS, GUARANTEED_PICKPOCKETS)
    }

    /** The knocked-out record of [npc] if it is still unconscious at [now]. */
    fun state(npc: NpcRef, now: Long): Any? = (npc[KNOCKED_OUT] as? KnockedOut)?.takeIf { it.until > now && it.left > 0 }

    /** Uses one guaranteed pickpocket of [state]; false if none is left. */
    fun use(state: Any?): Boolean {
        val s = state as? KnockedOut ?: return false
        if (s.left <= 0) return false
        s.left--
        return true
    }

    /** Gives one used guaranteed pickpocket back (the loot did not fit). */
    fun refund(state: Any?) {
        (state as? KnockedOut)?.let { it.left++ }
    }
}

/**
 * Pickpocketing [npc] (a blackjack target): while it is knocked out the pickpocket cannot fail and gives the coins directly, the
 * Rogue outfit doubling them (Wiki "Bandit (Pollnivneach)": "while they are knocked out coins are received directly"; "Blackjack": two
 * guaranteed pickpockets, shown with the bury-bone animation). A conscious target is not modelled (OPEN-TH12).
 */
internal suspend fun PlayerScript.blackjackPickpocket(npc: NpcRef, t: BlackjackTarget) {
    val p = player
    val name = npc.displayName.lowercase()
    if (p.level(Stats.THIEVING) < t.level) {
        p.message("You need a Thieving level of ${t.level} to pickpocket the $name.")
        return
    }
    val state = Blackjack.state(npc, p.tick)
    if (state == null || !Blackjack.use(state)) {
        p.message("Nothing interesting happens.")
        return
    }
    p.stopInteraction()
    p.face(npc)
    p.anim(Seqs.HUMAN_PICKUPFLOOR)
    val coins = if (ThievingBonuses.rogueDoubles(p)) t.coins * 2 else t.coins
    if (!p.transaction("blackjack") { add(Invs.INV, Objs.COINS, coins) }) {
        Blackjack.refund(state)
        p.message("You don't have enough inventory space.")
        return
    }
    p.message("You pick the $name's pocket.")
    p.addXp(Stats.THIEVING, t.xp)
    delay(Blackjack.PICKPOCKET_TICKS)
}

/** "Lure" and "Knock-Out" on the Pollnivneach bandits and Menaphite Thugs (ops 4 and 5 of the cache npcs). */
public class Blackjacking : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpcOption("Knock-Out") { npc -> knockOut(npc) }
        onOpNpcOption("Lure") { npc -> lure(npc) }
    }

    private suspend fun PlayerScript.lure(npc: NpcRef) {
        val p = player
        val t = Blackjack.targetOf(npc.type) ?: return p.message("Nothing interesting happens.")
        if (gated(t)) return
        p.stopInteraction()
        p.face(npc)
        npc[Blackjack.LURED] = p.tick
        // OPEN-TH12: the NPC follows the player for LURE_TICKS (the Wiki only says "lure a bandit to an empty house").
        npc.interact(p) { _ ->
            delay(Blackjack.LURE_TICKS)
            npc.stopInteraction()
            npc[Blackjack.LURED] = null
        }
    }

    private suspend fun PlayerScript.knockOut(npc: NpcRef) {
        val p = player
        val t = Blackjack.targetOf(npc.type) ?: return p.message("Nothing interesting happens.")
        if (gated(t)) return
        val name = npc.displayName.lowercase()
        p.stopInteraction()
        p.face(npc)
        if (p.inv(Invs.WORN).obj(Blackjack.WEAPON_SLOT) !in Blackjack.BLACKJACKS) {
            return p.message("You need to be wielding a blackjack to knock out the $name.")
        }
        if (npc.target != null && npc[Blackjack.LURED] == null) return p.message("You can't knock-out the $name during combat.")
        if (witnessed(p, npc)) return p.message("Perhaps I shouldn't do this here, I think another bandit will see me.")
        if (!p.statRandom(Stats.THIEVING, t.knockLow, Blackjack.KNOCK_HIGH)) {
            // OPEN-TH12: a failed knock-out makes the NPC aggressive in the game; combat is not driven from here.
            p.message("You fail to knock out the $name.")
            return
        }
        npc.stopInteraction()
        npc[Blackjack.LURED] = null
        Blackjack.knockOut(npc, p.tick)
        npc.frozenUntil = p.tick + Blackjack.KNOCKOUT_TICKS
        npc.anim(Seqs.STUNNED_BLACKJACK)
        npc.spotAnim(SpotAnims.STUNNED_BLACKJACK)
        p.message("You smack the $name over the head and render them unconscious.")
        p.addXp(Stats.THIEVING, Blackjack.KNOCK_XP)
    }

    /** The level and Feud gate shared by both ops; true (after a message) if the player may not. */
    private fun PlayerScript.gated(t: BlackjackTarget): Boolean {
        val p = player
        if (Blackjack.REQUIRE_THE_FEUD) {
            p.message("You have not learned how to use a blackjack yet.")
            return true
        }
        if (p.level(Stats.THIEVING) < t.level) {
            p.message("You need a Thieving level of ${t.level} to do that.")
            return true
        }
        return false
    }

    /** Whether the player has line of sight to a second conscious blackjack target near [npc] (Wiki "Bandit (Pollnivneach)"). */
    private fun witnessed(p: tp.plugin.PlayerRef, npc: NpcRef): Boolean =
        p.npcsWithin(p.coord, Blackjack.WITNESS_RADIUS).any {
            it != npc && Blackjack.targetOf(it.type) != null && Blackjack.state(it, p.tick) == null && p.hasLineOfSight(it)
        }
}
