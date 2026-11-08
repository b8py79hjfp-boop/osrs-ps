package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.plugin.commands.perk
import tp.util.DonatorPerk

/**
 * D-093 Uber donator perks (perks.tsv): "Gain immunity to poison, venom and dragonfire when not in the Wilderness",
 * read by Poison and Dragonfire, and "Regen 1 prayer point every 5 seconds when not in the Wilderness". 0 for every
 * other player, and on worlds with donator boosts off, so the OSRS paths are unchanged there.
 */
internal object DonatorImmunity {
    fun active(p: PlayerRef): Boolean =
        p.perk(DonatorPerk.GAIN_IMMUNITY_TO_POISON_VENOM_AND_DRAGONFIRE_WHEN_NOT_IN_THE) > 0 &&
            p[SharedAttrs.IN_WILDERNESS] != true
}

/** The prayer regen timer, started at login for players whose tier has the perk (a tier change applies at next login). */
public class DonatorCombat : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            if (perk(DonatorPerk.REGEN_1_PRAYER_POINT_EVERY_5_SECONDS_WHEN_NOT_IN_THE_WILDERN) == 0) return@onLogin
            timer(PRAYER_REGEN, REGEN_TICKS, soft = true) {
                if (this[SharedAttrs.IN_WILDERNESS] == true) return@timer
                if (perk(DonatorPerk.REGEN_1_PRAYER_POINT_EVERY_5_SECONDS_WHEN_NOT_IN_THE_WILDERN) == 0) return@timer
                if (level(PRAYER) < baseLevel(PRAYER)) setLevel(PRAYER, level(PRAYER) + 1)
            }
        }
    }

    private companion object {
        val PRAYER_REGEN = TimerId("donator.prayer_regen")
        const val PRAYER = 5

        /** "Every 5 seconds": 8 ticks of 0.6 s (4.8 s), the nearest whole tick count. */
        const val REGEN_TICKS = 8
    }
}
