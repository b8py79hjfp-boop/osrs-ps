package tp.content.diaries

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.commands.perk
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varps
import tp.util.DonatorPerk

/**
 * The tiers' Antique lamps (docs/mechanics/achievement-diaries.md AD9): "Rub" opens the experience interface (240); the player
 * picks a skill of at least the lamp's minimum level and confirms, and the lamp is exchanged for the experience. The client
 * enforces the level and availability itself from two interface varps (client scripts 3806, 3807, 3809); the server checks again.
 */
internal class DiaryLamps(private val diaries: Diaries, private val statOfKey: Map<Int, Int>) {
    private class Lamp(val slot: Int, val obj: Int, val info: LampInfo) {
        /** Stat the player picked, or -1. */
        var stat: Int = -1
    }

    private class LampInfo(val xp: Int, val minLevel: Int)

    private val infoOf: Map<Int, LampInfo> =
        diaries.data.areas
            .flatMap { area ->
                area.rewards.mapIndexed { tier, r -> diaries.lampObj(area.index, tier) to LampInfo(r.lampXp, r.lampMinLevel) }
            }.toMap()

    /** Every lamp obj a tier hands out (for the item-op registrations). */
    val lampObjs: Set<Int> get() = infoOf.keys

    /** Fails the world start when the cache enum of the skill components no longer has the 24 skills (a revision bump). */
    fun verify() {
        check(
            statOfKey.size == SKILL_COMPONENTS,
        ) { "cache enum $COMPONENT_STAT_ENUM has ${statOfKey.size} skills, expected $SKILL_COMPONENTS" }
    }

    /** "Rub" on a lamp in backpack slot [click]`.slot`: opens the interface with the lamp's minimum level and every skill allowed. */
    fun rub(player: PlayerRef, click: ButtonClick) {
        val info = infoOf[click.obj] ?: return
        if (player.inv(Invs.INV).obj(click.slot) != click.obj) return
        player[LAMP] = Lamp(click.slot, click.obj, info)
        player.setVarp(Varps.IF1, info.minLevel)
        player.setVarp(Varps.IF2, ALL_SKILLS_MASK)
        player.openModal(Components.Toplevel.MAINMODAL, Interfaces.XPREWARD)
    }

    /** A skill button: [child] is the component's index in interface 240; enum 681 maps it (minus one) to the stat. */
    fun select(player: PlayerRef, child: Int) {
        val lamp = player[LAMP] ?: return
        val stat = statOfKey[child - 1] ?: return
        if (stat == Stats.SAILING) return player.message(SAILING_MESSAGE)
        if (player.baseLevel(stat) < lamp.info.minLevel) {
            return player.message("You need level ${lamp.info.minLevel} ${Stats.name(stat)} to choose that option.")
        }
        lamp.stat = stat
    }

    /** Confirm: takes the lamp out of its slot and gives the experience (AD9). */
    fun confirm(player: PlayerRef) {
        val lamp = player[LAMP] ?: return
        if (lamp.stat < 0) return player.message("You need to choose which skill you wish to be advanced.")
        if (player.inv(Invs.INV).obj(lamp.slot) != lamp.obj) return
        if (!player.transaction("diary lamp") { removeAt(Invs.INV, lamp.slot, lamp.obj, 1) }) return
        player[LAMP] = null
        player.closeModal()
        // D-093 donator perk (perks.tsv row "Bonus exp from effigies/lamps").
        val bonus = player.perk(DonatorPerk.BONUS_EXP_FROM_EFFIGIES_LAMPS)
        player.addXp(lamp.stat, if (bonus == 0) lamp.info.xp.toDouble() else lamp.info.xp.toDouble() * (PERCENT + bonus) / PERCENT)
    }

    fun closed(player: PlayerRef) {
        player[LAMP] = null
    }

    companion object {
        /** Percent denominator for the donator lamp bonus. */
        private const val PERCENT = 100

        /** Cache enum 681: component index of interface 240 minus one to the stat id (client script 3806). */
        const val COMPONENT_STAT_ENUM = 681

        /** Interface 240's skill buttons are its components 2..25 (24 skills; the Wiki's "Skills" order, enum 681 keys 1..24). */
        const val FIRST_SKILL_COMPONENT = 2
        const val SKILL_COMPONENTS = 24

        /**
         * Value of the interface varp `if2`: bit n set = the skill whose position in cache enum 81 is n can be picked (client
         * script 3809 tests `testbit(if2, enum81[stat])`, positions 1..24), so bits 1..24.
         */
        const val ALL_SKILLS_MASK = (1 shl (SKILL_COMPONENTS + 1)) - 2

        /** Client script 8948 answers the Sailing skill with this line. */
        const val SAILING_MESSAGE = "The Sailing skill is not currently available."

        private val LAMP = AttrKey<Lamp>("diary_lamp")
    }
}
