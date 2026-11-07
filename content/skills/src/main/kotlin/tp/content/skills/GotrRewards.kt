@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EventBus
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The Rewards Guardian and Apprentice Felix (docs/mechanics/guardians-of-the-rift.md GR13-GR15): one elemental and
 * one catalytic
 * point (or 25 abyssal pearls) buy a roll of the Wiki's reward tables; Felix runs the Temple Supplies shop
 * (`shops.tsv`).
 */
public class GotrRewards : ContentPlugin {
    private lateinit var bus: EventBus

    override fun ContentScope.register() {
        bus = events
        onOpLoc(Locs.GOTR_REWARD_RIFT, OP_SEARCH) { search(player, 1) }
        onOpLoc(Locs.GOTR_REWARD_RIFT, OP_CHECK) { check(player) }
        onOpLoc(Locs.GOTR_REWARD_RIFT, OP_BIG_SEARCH) { search(player, GotrConfig.BIG_SEARCH) }
        onUseOnLoc(Objs.ABYSSAL_PEARL, Locs.GOTR_REWARD_RIFT) { pearlSearch(player) }
        onUseOnLoc(Objs.NEEDLE, Locs.GOTR_REWARD_RIFT) { needle(player) }
        onOpNpc(Npcs.GOTR_REWARD_TRADER, OP_TALK) { felix(it) }
    }

    // ---- the rift

    private fun check(p: PlayerRef) {
        p.stopInteraction()
        val e = points(p, GuardiansOfTheRift.POINTS_ELEMENTAL)
        val c = points(p, GuardiansOfTheRift.POINTS_CATALYTIC)
        val n = p.saved(GuardiansOfTheRift.SEARCHES) ?: 0L
        p.message("You have $e elemental and $c catalytic points. You have searched the rift $n times.")
    }

    private fun points(p: PlayerRef, key: String): Long = p.saved(key) ?: 0L

    private suspend fun PlayerScript.search(p: PlayerRef, times: Int) {
        p.stopInteraction()
        var done = 0
        while (done < times) {
            val e = points(p, GuardiansOfTheRift.POINTS_ELEMENTAL)
            val c = points(p, GuardiansOfTheRift.POINTS_CATALYTIC)
            if (e < 1 || c < 1) {
                if (done == 0) p.message("You need an elemental point and a catalytic point to search the rift.")
                break
            }
            if (p.inv(Invs.INV).freeSlots == 0) {
                p.message("You don't have enough inventory space.")
                break
            }
            p.setSaved(GuardiansOfTheRift.POINTS_ELEMENTAL, e - 1)
            p.setSaved(GuardiansOfTheRift.POINTS_CATALYTIC, c - 1)
            reward(p)
            done++
            delay(SEARCH_TICKS)
        }
    }

    private fun pearlSearch(p: PlayerRef) {
        p.stopInteraction()
        if (p.inv(Invs.INV).total(Objs.ABYSSAL_PEARL) <
            GotrConfig.PEARL_ROLL_COST
        ) {
            return p.message("You need ${GotrConfig.PEARL_ROLL_COST} abyssal pearls to search the rift.")
        }
        if (!p.transaction("gotr pearls") { remove(Invs.INV, Objs.ABYSSAL_PEARL, GotrConfig.PEARL_ROLL_COST) }) return
        reward(p)
    }

    /** Wiki transcript: with the unlocked abyssal needle a plain needle is warped into another one. */
    private fun needle(p: PlayerRef) {
        p.stopInteraction()
        if (p.varbit(Varbits.GOTR_UNLOCKED_NEEDLE) == 0) return p.message("Nothing interesting happens.")
        val done = p.transaction("gotr needle") { remove(Invs.INV, Objs.NEEDLE, 1) && add(Invs.INV, Objs.ABYSSAL_NEEDLE, 1) }
        if (done) p.message("You place the needle inside the rift. You feel something strange touch your hands...")
    }

    // ---- the reward tables (Wiki "Rewards Guardian")

    /**
     * One search: the rare table is rolled separately, then one row of the main table (denominator 140, or 125 with
     * every pouch / the
     * colossal pouch).
     */
    private fun reward(p: PlayerRef) {
        p.setSaved(GuardiansOfTheRift.SEARCHES, ((p.saved(GuardiansOfTheRift.SEARCHES) ?: 0L) + 1).coerceAtMost(MAX_SEARCHES))
        rare(p)
        val missing =
            RunecraftPouchData.POUCHES
                .take(PLAIN_POUCHES)
                .filter { !owns(p, it) }
                .toMutableList()
        val ownsColossal = owns(p, RunecraftPouchData.POUCHES.last())
        val pouchRoll = missing.isNotEmpty() && !ownsColossal
        var r = p.random(if (pouchRoll) MAIN_WITH_POUCH else MAIN_WITHOUT_POUCH)
        if (pouchRoll) {
            if (r < POUCH_WEIGHT) return givePouch(p, missing[p.random(missing.size)])
            r -= POUCH_WEIGHT
        }
        for ((weight, action) in main(p)) {
            if (r < weight) return action()
            r -= weight
        }
    }

    private fun owns(p: PlayerRef, pouch: Pouch): Boolean =
        listOf(Invs.INV, Invs.BANK).any { inv ->
            p.inv(inv).total(pouch.obj) +
                p.inv(inv).total(pouch.degraded) >
                0
        }

    private fun givePouch(p: PlayerRef, pouch: Pouch) {
        if (!GotrAltars.give(p, pouch.obj, 1)) return
        // Wiki: "prepacked with 2, 4, 6, or 8 pure essence in proportion to its size".
        val packed = (RunecraftPouchData.POUCHES.indexOf(pouch) + 1) * 2
        p.setVarbit(pouch.size, packed)
        p.setVarbit(pouch.type, PouchEssence.PURE.type)
        p.message("You find a ${pouch.name} pouch in the rift.")
    }

    private fun rare(p: PlayerRef) {
        if (p.varbit(Varbits.GOTR_BOOK_OBTAINED) == 0 && p.random(ATLAX_ONE_IN) == 0) {
            p.setVarbit(Varbits.GOTR_BOOK_OBTAINED, 1)
            drop(p, Objs.GOTR_BOOK, 1)
        }
        if (p.random(CATALYTIC_TALISMAN_ONE_IN) == 0) drop(p, Objs.CATALYTIC_TALISMAN, 1)
        if (p.varbit(Varbits.GOTR_UNLOCKED_NEEDLE) == 0 && p.random(NEEDLE_ONE_IN) == 0) {
            p.setVarbit(Varbits.GOTR_UNLOCKED_NEEDLE, 1)
            drop(p, Objs.ABYSSAL_NEEDLE, 1)
        }
        if (p.random(LANTERN_ONE_IN) == 0) drop(p, Objs.ABYSSAL_LANTERN, 1)
        // "There is a 1/400 chance of receiving a dye" (1/1200 of each colour).
        if (p.random(DYE_ONE_IN) ==
            0
        ) {
            drop(p, intArrayOf(Objs.ABYSSAL_RED_DYE, Objs.ABYSSAL_GREEN_DYE, Objs.ABYSSAL_BLUE_DYE)[p.random(DYE_COLOURS)], 1)
        }
    }

    private fun main(p: PlayerRef): List<Pair<Int, () -> Unit>> {
        val rows = ArrayList<Pair<Int, () -> Unit>>()
        for (rune in RUNE_ROWS) {
            val temple = RunecraftData.TEMPLES.first { it.name == rune.name }
            rows += rune.weight to { drop(p, temple.rune, scaled(p, temple, rune.low + p.random(rune.high - rune.low + 1))) }
        }
        rows += TALISMAN_WEIGHT to { talisman(p) }
        rows += PEARL_WEIGHT to { drop(p, Objs.ABYSSAL_PEARL, PEARLS_LOW + p.random(PEARLS_HIGH - PEARLS_LOW + 1)) }
        rows += INTRICATE_WEIGHT to { drop(p, Objs.GOTR_INTRICATE_POUCH, 1) }
        rows += 1 to { drop(p, Objs.ABYSSAL_ASHES, 1) }
        rows += 1 to { drop(p, Objs.NEEDLE, 1) }
        return rows
    }

    /**
     * Wiki comment on the runes table: below the required level the amount is `amount x (level / required)^4`, at
     * least one.
     */
    private fun scaled(p: PlayerRef, temple: Temple, amount: Int): Int {
        val level = p.level(Stats.RUNECRAFT)
        if (level >= temple.level) return amount
        val f = level.toDouble() / temple.level
        return (amount * f * f * f * f).toInt().coerceAtLeast(1)
    }

    /**
     * Wiki "Talismans": out of 35, law 1 and death 1 (an air talisman without their quests), elemental 1,
     * air/water/earth/fire 3, the rest
     * 4.
     */
    private fun talisman(p: PlayerRef) {
        val temples = RunecraftData.TEMPLES

        fun t(name: String) = temples.first { it.name == name }.talisman
        val table =
            listOf(
                1 to (if (RunecraftQuests.TROLL_STRONGHOLD.done(p)) Objs.LAW_TALISMAN else Objs.AIR_TALISMAN),
                1 to (if (RunecraftQuests.MOURNINGS_END_II.done(p)) Objs.DEATH_TALISMAN else Objs.AIR_TALISMAN),
                1 to Objs.ELEMENTAL_TALISMAN,
                3 to t("air"),
                3 to t("water"),
                3 to t("earth"),
                3 to t("fire"),
                4 to t("mind"),
                4 to t("body"),
                4 to t("chaos"),
                4 to t("cosmic"),
                4 to t("nature"),
            )
        var r = p.random(table.sumOf { it.first })
        for ((w, obj) in table) {
            if (r < w) {
                val plain = obj == Objs.ELEMENTAL_TALISMAN || obj == Objs.LAW_TALISMAN
                return drop(p, if (plain) obj else p.noted(obj), 1)
            }
            r -= w
        }
    }

    private fun drop(p: PlayerRef, obj: Int, count: Int) {
        p.transaction("gotr reward") { addOrDrop(Invs.INV, obj, count) }
        p.message("You find ${if (count > 1) "$count x " else "a "}${p.objName(obj)} in the rift.")
    }

    // ---- Apprentice Felix

    private suspend fun PlayerScript.felix(felix: NpcRef) {
        val p = player
        p.stopInteraction()
        chatNpc(felix, "Hey, ${p.username}. Got any abyssal pearls you want to trade?")
        while (true) {
            when (
                choice(
                    "Select an option",
                    "Let's see what you have.",
                    "What's that rift guardian doing over there?",
                    "Why are you selling things here?",
                    "Could you tell me more about this place.",
                )
            ) {
                1 -> {
                    chatPlayer("Let's see what you have.")
                    bus.publish(OpenShopRequest(p, shopName = "Temple Supplies"))
                    return
                }

                2 -> {
                    chatPlayer("What's that rift guardian doing over there?")
                    chatNpc(felix, "They're great, aren't they? I don't know their name, if they even have one, so I just call them Rifty.")
                    chatNpc(
                        felix,
                        "That rift they have is a folded-space storage rift, just like the ones Traiborn uses! " +
                            "Small on the outside, but big on the inside!",
                    )
                    chatPlayer("But what's the rift for?")
                    chatNpc(
                        felix,
                        "The guardians use it to store things. If you help hold back the Abyss, they'll let " +
                            "you take things from the rift in return.",
                    )
                }

                3 -> {
                    chatPlayer("Why are you selling things here?")
                    chatNpc(
                        felix,
                        "Sedridor told us to make sure there was plenty of incentive for people to help us. He " +
                            "also wants us to gather abyssal pearls for research purposes. I thought this would be " +
                            "a good way of doing both!",
                    )
                    chatPlayer("So you sell things in exchange for abyssal pearls?")
                    chatNpc(
                        felix,
                        "Exactly! I've got some interesting things from the Wizards' Tower, along with some " +
                            "cool stuff the Guardians of the Rift gave us. Want to take a look?",
                    )
                }

                else -> {
                    chatPlayer("Could you tell me more about this place.")
                    chatNpc(
                        felix,
                        "Well, I only really know what the Great Guardian told us. This is the Temple of the " +
                            "Eye, or at least, what's left of it. It was built long ago to house the Eye, a " +
                            "powerful Saradominist artefact.",
                    )
                    chatNpc(
                        felix,
                        "Apparently, servants of some 'Fallen Empire' desired this artefact. However, in their " +
                            "attempt to steal it, they accidently opened a rift into a deadly area of the Abyss " +
                            "called the Scar.",
                    )
                    chatNpc(
                        felix,
                        "Were it not for the Justiciar of the Lion, the rift would have endangered all of " +
                            "Gielinor. She used the Eye to close it and then created the Guardians of the Rift to " +
                            "protect this temple.",
                    )
                    chatNpc(
                        felix,
                        "Of course, the rift is open again now, but with our help, the Guardians of the Rift " +
                            "are keeping it under control.",
                    )
                }
            }
            chatNpc(felix, "Anyway, did you want to trade?")
        }
    }

    private class Rune(val name: String, val weight: Int, val low: Int, val high: Int)

    private companion object {
        const val OP_SEARCH = 1
        const val OP_CHECK = 2
        const val OP_BIG_SEARCH = 3
        const val OP_TALK = 1

        /** Wiki "Rewards Guardian": "1 reward for every 3 ticks". */
        const val SEARCH_TICKS = 3

        /** Wiki: "The maximum number of searches that can be stored is 65,535." */
        const val MAX_SEARCHES = 65_535L

        /** Wiki main table: weights out of 140 (125 without the pouch row). */
        const val MAIN_WITH_POUCH = 140
        const val MAIN_WITHOUT_POUCH = 125
        const val POUCH_WEIGHT = 15
        const val PLAIN_POUCHES = 4
        const val TALISMAN_WEIGHT = 16
        const val PEARL_WEIGHT = 18
        const val INTRICATE_WEIGHT = 5
        const val PEARLS_LOW = 14
        const val PEARLS_HIGH = 16

        /**
         * Wiki rare table: Atlax's diary 1/20 (once), catalytic talisman 1/200, abyssal needle 1/300 (once), lantern
         * 1/700, dyes 1/400 in
         * all.
         */
        const val ATLAX_ONE_IN = 20
        const val CATALYTIC_TALISMAN_ONE_IN = 200
        const val NEEDLE_ONE_IN = 300
        const val LANTERN_ONE_IN = 700
        const val DYE_ONE_IN = 400
        const val DYE_COLOURS = 3

        /** Wiki runes table: quantity range and weight (out of the main table) per rune. */
        val RUNE_ROWS =
            listOf(
                Rune("air", 4, 400, 500),
                Rune("water", 4, 400, 500),
                Rune("earth", 4, 400, 500),
                Rune("fire", 4, 400, 500),
                Rune("mind", 4, 250, 400),
                Rune("body", 4, 80, 150),
                Rune("chaos", 10, 61, 150),
                Rune("cosmic", 10, 20, 30),
                Rune("nature", 10, 28, 150),
                Rune("law", 10, 5, 120),
                Rune("death", 10, 5, 120),
                Rune("blood", 10, 5, 120),
            )
    }
}
