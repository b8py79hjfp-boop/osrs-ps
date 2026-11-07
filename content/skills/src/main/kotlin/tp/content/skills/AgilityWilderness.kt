@file:Suppress("MagicNumber") // data: Wiki "Agility dispenser" rates and loot (docs/mechanics/agility.md AG12)

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs

/** Saved-state keys of the Wilderness course (they survive logout, as the Wiki says of the fee): fee paid, streak, ticket to tag. */
private const val SAVED_PAID = "agility.wild.paid"
private const val SAVED_STREAK = "agility.wild.streak"
private const val SAVED_PENDING = "agility.wild.pending"

/** Wiki "Agility dispenser": the reward fee is 150,000 coins. */
private const val FEE = 150_000

/** Wiki "Agility dispenser": logging out inside the course removes 10 laps from the streak. */
private const val LOGOUT_LAP_PENALTY = 10L

/** The dispenser restores run energy when tagged (Wiki "Wilderness Agility Course"); the amount is OPEN-AG13, taken as all of it. */
private const val FULL_RUN_ENERGY = 10_000

/** Called when a lap is finished (rocks): the ticket can be tagged, and a paid streak grows by one lap. */
internal fun wildernessLapDone(p: PlayerRef) {
    p.setSaved(SAVED_PENDING, 1)
    if (p.saved(SAVED_PAID) == 1L) p.setSaved(SAVED_STREAK, (p.saved(SAVED_STREAK) ?: 0L) + 1)
}

/** Called when the first obstacle is crossed: Wiki "Agility dispenser" - starting a lap without tagging resets the streak completely. */
internal fun wildernessLapStarted(p: PlayerRef) {
    if (p.saved(SAVED_PENDING) == 1L) {
        p.setSaved(SAVED_PENDING, 0)
        p.setSaved(SAVED_STREAK, 0)
    }
}

/**
 * The Wilderness agility dispenser (loc 53224, options Tag / Pay / Redeem; docs/mechanics/agility.md AG12): tagging after a lap
 * gives one Wilderness agility ticket, and with the 150,000 coin fee paid the streak's loot; Pay takes the fee; Redeem turns the
 * inventory's tickets into Agility xp at the Wiki's bonus brackets. The loot goes into the inventory (the looting bag the Wiki
 * requires is not implemented: OPEN-AG14); the medium clue scroll chance is not given (no clue scroll content: OPEN-AG14).
 */
public class WildernessAgility : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.WILDY_AGILITY_PILLAR, TAG) { tag(player) }
        onOpLoc(Locs.WILDY_AGILITY_PILLAR, PAY) { pay(player) }
        onOpLoc(Locs.WILDY_AGILITY_PILLAR, REDEEM) { redeem(player) }
        onLogout {
            if (saved(SAVED_PAID) == 1L) setSaved(SAVED_STREAK, ((saved(SAVED_STREAK) ?: 0L) - LOGOUT_LAP_PENALTY).coerceAtLeast(0L))
        }
    }

    private fun tag(p: PlayerRef) {
        p.runEnergy = FULL_RUN_ENERGY
        if (p.saved(SAVED_PENDING) != 1L) {
            p.message("You have no lap to tag.")
            return
        }
        p.setSaved(SAVED_PENDING, 0)
        p.transaction("wilderness agility ticket") { addOrDrop(Invs.INV, Objs.WILDY_AGILITY_TOKEN, 1) }
        if (p.saved(SAVED_PAID) == 1L) dispense(p, (p.saved(SAVED_STREAK) ?: 0L).toInt())
    }

    private fun pay(p: PlayerRef) {
        if (p.saved(SAVED_PAID) == 1L) {
            p.message("You have already paid the fee.")
            return
        }
        val paid = p.transaction("wilderness agility fee") { remove(Invs.INV, Objs.COINS, FEE) }
        if (!paid) {
            p.message("You need $FEE coins in your inventory to pay the fee.")
            return
        }
        p.setSaved(SAVED_PAID, 1)
        p.setSaved(SAVED_STREAK, 0)
        // Wiki "Agility dispenser": paying gives a looting bag when the player has none in the inventory or the bank.
        if (p.inv(Invs.INV).total(Objs.LOOTING_BAG) + p.inv(Invs.INV).total(Objs.LOOTING_BAG_OPEN) +
            p.inv(Invs.BANK).total(Objs.LOOTING_BAG) + p.inv(Invs.BANK).total(Objs.LOOTING_BAG_OPEN) == 0L
        ) {
            p.transaction("wilderness agility looting bag") { addOrDrop(Invs.INV, Objs.LOOTING_BAG, 1) }
        }
    }

    private fun redeem(p: PlayerRef) {
        val tickets = p.inv(Invs.INV).total(Objs.WILDY_AGILITY_TOKEN).toInt()
        if (tickets == 0) {
            p.message("You have no tickets to redeem.")
            return
        }
        if (!p.transaction("wilderness agility redeem") { remove(Invs.INV, Objs.WILDY_AGILITY_TOKEN, tickets) }) return
        p.addXp(Stats.AGILITY, tickets * ticketXp(tickets))
    }

    /** One noted resource and one noted armour roll, plus an unnoted blighted supply with a free slot (Wiki "Agility dispenser"). */
    private fun dispense(p: PlayerRef, streak: Int) {
        val bracket = BRACKETS.first { streak <= it.lastLap }
        val resource = pickWeighted(p, bracket.resources, RESOURCE_TOTAL)
        val armour = pickWeighted(p, bracket.armour, bracket.armour.sumOf { it.weight })
        val supply = if (p.inv(Invs.INV).freeSlots > 0) pickWeighted(p, SUPPLIES, SUPPLY_TOTAL) else null
        p.transaction("wilderness agility loot") {
            val noted = { obj: Int -> p.noted(obj) }
            addOrDrop(Invs.INV, noted(resource.obj), resource.min + p.random(resource.max - resource.min + 1))
            addOrDrop(Invs.INV, noted(armour.obj), 1)
            if (supply != null) addOrDrop(Invs.INV, supply.obj, 1)
            true
        }
    }

    private fun pickWeighted(p: PlayerRef, rows: List<Row>, total: Int): Row {
        var roll = p.random(total)
        for (row in rows) {
            roll -= row.weight
            if (roll < 0) return row
        }
        return rows.last()
    }

    private class Row(val obj: Int, val weight: Int, val min: Int = 1, val max: Int = 1)

    /** A streak bracket: its last lap, the resource quantities and the armour weights (Wiki "Agility dispenser" tables). */
    private class Bracket(val lastLap: Int, val resources: List<Row>, val armour: List<Row>)

    /** Wiki "Wilderness agility ticket": 200 xp each, +5% from 11, +10% from 51, +15% from 101 tickets (applied to all of them). */
    private fun ticketXp(tickets: Int): Double =
        when {
            tickets > 100 -> 230.0
            tickets > 50 -> 220.0
            tickets > 10 -> 210.0
            else -> 200.0
        }

    private companion object {
        const val TAG = 1
        const val PAY = 2
        const val REDEEM = 3

        /** Wiki resource rolls are out of 31 (8, 8, 8 and 7). */
        const val RESOURCE_TOTAL = 31

        /** Wiki "All Laps": the unnoted supply is out of 16 (5, 5, 5 and 1). */
        const val SUPPLY_TOTAL = 16

        val SUPPLIES =
            listOf(
                Row(Objs.BLIGHTED_ANGLERFISH, 5),
                Row(Objs.BLIGHTED_MANTARAY, 5),
                Row(Objs.BLIGHTED_KARAMBWAN, 5),
                Row(Objs.BLIGHTED_4DOSE2RESTORE, 1),
            )

        private fun resources(food: IntRange, restore: IntRange) =
            listOf(
                Row(Objs.BLIGHTED_ANGLERFISH, 8, food.first, food.last),
                Row(Objs.BLIGHTED_MANTARAY, 8, food.first, food.last),
                Row(Objs.BLIGHTED_KARAMBWAN, 8, food.first, food.last),
                Row(Objs.BLIGHTED_4DOSE2RESTORE, 7, restore.first, restore.last),
            )

        private val ADAMANT_FULL_HELM = Objs.ADAMANT_FULL_HELM
        private val ADAMANT_PLATEBODY = Objs.ADAMANT_PLATEBODY
        private val ADAMANT_PLATELEGS = Objs.ADAMANT_PLATELEGS
        private val MITHRIL_CHAINBODY = Objs.MITHRIL_CHAINBODY
        private val MITHRIL_PLATELEGS = Objs.MITHRIL_PLATELEGS
        private val MITHRIL_PLATESKIRT = Objs.MITHRIL_PLATESKIRT
        private val RUNE_MED_HELM = Objs.RUNE_MED_HELM
        private val STEEL_PLATEBODY = Objs.STEEL_PLATEBODY
        private val RUNE_CHAINBODY = Objs.RUNE_CHAINBODY
        private val RUNE_KITESHIELD = Objs.RUNE_KITESHIELD

        /** Wiki "Agility dispenser" lap brackets 1-15, 16-30, 31-60 and 61+ (resources: food and restore quantities; armour: weights). */
        val BRACKETS =
            listOf(
                Bracket(
                    15,
                    resources(3..6, 1..2),
                    listOf(
                        Row(ADAMANT_FULL_HELM, 1),
                        Row(ADAMANT_PLATEBODY, 2),
                        Row(ADAMANT_PLATELEGS, 1),
                        Row(MITHRIL_CHAINBODY, 1),
                        Row(MITHRIL_PLATELEGS, 1),
                        Row(MITHRIL_PLATESKIRT, 1),
                        Row(RUNE_MED_HELM, 2),
                        Row(STEEL_PLATEBODY, 1),
                    ),
                ),
                Bracket(
                    30,
                    resources(6..11, 2..4),
                    listOf(
                        Row(ADAMANT_FULL_HELM, 1),
                        Row(ADAMANT_PLATEBODY, 1),
                        Row(ADAMANT_PLATELEGS, 1),
                        Row(MITHRIL_CHAINBODY, 1),
                        Row(MITHRIL_PLATELEGS, 1),
                        Row(MITHRIL_PLATESKIRT, 1),
                        Row(RUNE_CHAINBODY, 1),
                        Row(RUNE_KITESHIELD, 1),
                        Row(RUNE_MED_HELM, 1),
                    ),
                ),
                Bracket(
                    60,
                    resources(10..16, 3..6),
                    listOf(
                        Row(ADAMANT_FULL_HELM, 1),
                        Row(ADAMANT_PLATEBODY, 1),
                        Row(ADAMANT_PLATELEGS, 1),
                        Row(MITHRIL_PLATELEGS, 1),
                        Row(MITHRIL_PLATESKIRT, 1),
                        Row(RUNE_CHAINBODY, 2),
                        Row(RUNE_KITESHIELD, 2),
                        Row(RUNE_MED_HELM, 1),
                    ),
                ),
                Bracket(
                    Int.MAX_VALUE,
                    resources(14..20, 4..8),
                    listOf(
                        Row(ADAMANT_FULL_HELM, 1),
                        Row(ADAMANT_PLATEBODY, 2),
                        Row(ADAMANT_PLATELEGS, 1),
                        Row(MITHRIL_PLATELEGS, 1),
                        Row(MITHRIL_PLATESKIRT, 1),
                        Row(RUNE_CHAINBODY, 6),
                        Row(RUNE_KITESHIELD, 6),
                        Row(RUNE_MED_HELM, 2),
                    ),
                ),
            )
    }
}
