@file:Suppress("MagicNumber") // data: Wiki "Ticket dispenser" / "Brimhaven Agility Arena" (docs/mechanics/agility.md AG14)

package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.commands.perk
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.DonatorPerk

/**
 * The ticket dispensers of the Brimhaven Agility Arena (docs/mechanics/agility.md AG14; Wiki "Ticket dispenser", "Brimhaven Agility
 * Arena"). Every minute one of the 24 dispensers is the active one, the same for everybody. Tagging it gives 30 Agility xp for every 10
 * levels (boosts count, at most 300), one Agility arena ticket and one Brimhaven voucher (two of each with the Karamja elite diary,
 * 10% of the time); a player can claim an activation once. The flashing arrow over the active pillar needs a hint-arrow message
 * that plugin-api lacks (OPEN-AG15), and the obstacles of the arena are not implemented (OPEN-AG15).
 */
public class BrimhavenArena : ContentPlugin {
    /** The dispenser that is active during [minute] (the world tick divided by [ACTIVATION_TICKS]). */
    private class Active(val minute: Long, val dispenser: Int)

    override fun ContentScope.register() {
        onOpLoc(Locs.AGILITY_TICKETPILLAR, TAG) { tag(player, it.coord.x, it.coord.z) }
        onOpLoc(Locs.AGILITYARENA_POISONDARTS, TAG) { tag(player, it.coord.x, it.coord.z) }
    }

    private fun tag(p: PlayerRef, x: Int, z: Int) {
        val minute = p.tick / ACTIVATION_TICKS
        val active =
            p.worldAttr(ACTIVE)?.takeIf { it.minute == minute }
                ?: Active(minute, p.random(DISPENSERS.size)).also { p.setWorldAttr(ACTIVE, it) }
        val here = DISPENSERS.indexOfFirst { it.first == x && it.second == z }
        if (here != active.dispenser) {
            p.message("You can only get a ticket when the flashing arrow is above the pillar.")
            return
        }
        if (p[CLAIMED] == minute) {
            p.message("You can only get one ticket at a time, wait till the arrow moves again.")
            return
        }
        p[CLAIMED] = minute
        // Wiki: 30 xp for every 10 Agility levels, boosts and drains included, maxing out at 300 (level 100).
        // D-093 donator perk (perks.tsv row "Bonus exp when exchanging Agility tickets"): flat xp on top.
        val base = minOf(p.level(Stats.AGILITY) / LEVELS_PER_STEP * XP_PER_STEP, MAX_XP)
        p.addXp(Stats.AGILITY, (base + p.perk(DonatorPerk.BONUS_EXP_WHEN_EXCHANGING_AGILITY_TICKETS)).toDouble())
        // Wiki "Brimhaven Agility Arena" Rewards: with the elite Karamja diary a 10% chance of two tickets and vouchers.
        val count = if (p.varbit(Varbits.KARAMJA_DIARY_ELITE_COMPLETE) == 1 && p.random(DOUBLE_CHANCE_DENOMINATOR) == 0) 2 else 1
        p.transaction("agility arena ticket") {
            addOrDrop(Invs.INV, Objs.AGILITYARENA_TICKET_NEW, count)
            addOrDrop(Invs.INV, Objs.AGILITYARENA_VOUCHER, count)
            true
        }
        p.message("You have received an Agility Arena Ticket and Brimhaven Voucher!")
    }

    private companion object {
        const val TAG = 1

        /** Wiki "Ticket dispenser": the active dispenser moves every minute (100 ticks). */
        const val ACTIVATION_TICKS = 100

        /** Wiki: 30 xp for every 10 levels, at most 300. */
        const val LEVELS_PER_STEP = 10
        const val XP_PER_STEP = 30
        const val MAX_XP = 300

        /** Wiki: the Karamja elite diary gives a 10% chance of two tickets and vouchers. */
        const val DOUBLE_CHANCE_DENOMINATOR = 10

        val ACTIVE = AttrKey<Active>("agility.arena.active")
        val CLAIMED = AttrKey<Long>("agility.arena.claimed")

        /** The 24 ticket dispensers (locs 3608 and the dart ones 3581) of cache 2757, x/z only (24 of the 25 pillars have one, Wiki). */
        val DISPENSERS =
            listOf(
                2761 to 9546,
                2761 to 9557,
                2761 to 9568,
                2761 to 9579,
                2761 to 9590,
                2772 to 9546,
                2772 to 9557,
                2772 to 9568,
                2772 to 9579,
                2772 to 9590,
                2783 to 9546,
                2783 to 9557,
                2783 to 9568,
                2783 to 9579,
                2783 to 9590,
                2794 to 9546,
                2794 to 9557,
                2794 to 9568,
                2794 to 9579,
                2794 to 9590,
                2805 to 9546,
                2805 to 9557,
                2805 to 9568,
                2805 to 9579,
            )
    }
}
