package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Sunfire runes (docs/mechanics/runecraft.md RC23): pure or daeyalt essence, a fire rune and a sunfire splinter each, used on a
 * Shrine of Ralos. Level 33, 9 xp per pure essence (13.5 per daeyalt), 2x runes per essence at level 49 and 3x at 98 (Wiki
 * "Sunfire rune", "Runecraft", Multiple runes).
 */
internal object RunecraftSunfire {
    /** Cache loc "Shrine of Ralos" (Sun version, op 1 "Bask"; Wiki "Shrine of Ralos" id1 52405), the same loc as PrayerAltars. */
    private const val SHRINE = 52405

    /** Wiki "Sunfire rune", Runecraft info: level 33 Runecraft. */
    private const val LEVEL = 33

    /** Wiki "Sunfire rune" recipe (pure essence): 9 xp per essence. */
    private const val XP_PURE = 9.0

    /** Wiki "Sunfire rune" recipe (daeyalt essence): 13.5 xp per essence. */
    private const val XP_DAEYALT = 13.5

    /** Wiki "Runecraft", Multiple runes (Sunfire row): 2x at 49, 3x at 98. */
    private val MULTIPLES = listOf(49, 98)

    fun register(scope: ContentScope) {
        for (item in listOf(RunecraftData.PURE_ESSENCE, Objs.BLANKRUNE_DAEYALT, Objs.FIRERUNE, Objs.SUNFIRESPLINTER)) {
            scope.onUseOnLoc(item, SHRINE) { craft(player) }
        }
    }

    /** RC23: every pure essence, then daeyalt essence, that a fire rune and a splinter each cover becomes sunfire runes. */
    private fun craft(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val pure = inv.total(RunecraftData.PURE_ESSENCE).toInt()
        val daeyalt = inv.total(Objs.BLANKRUNE_DAEYALT).toInt()
        val fire = inv.total(Objs.FIRERUNE).toInt()
        val splinters = inv.total(Objs.SUNFIRESPLINTER).toInt()
        if (pure + daeyalt == 0) return p.message("You have no pure essence to bind.")
        if (fire == 0) return p.message("You have no fire runes to empower.")
        if (splinters == 0) return p.message("You have no sunfire splinters to bind.")
        val current = p.level(Stats.RUNECRAFT)
        if (current < LEVEL) return p.message("You need a Runecraft level of $LEVEL to craft sunfire runes.")
        val used = minOf(pure + daeyalt, fire, splinters)
        val usedPure = minOf(pure, used)
        val usedDaeyalt = used - usedPure
        val runes = used * (1 + MULTIPLES.count { current >= it })
        val done =
            p.transaction("runecraft sunfire") {
                (usedPure == 0 || remove(Invs.INV, RunecraftData.PURE_ESSENCE, usedPure)) &&
                    (usedDaeyalt == 0 || remove(Invs.INV, Objs.BLANKRUNE_DAEYALT, usedDaeyalt)) &&
                    remove(Invs.INV, Objs.FIRERUNE, used) &&
                    remove(Invs.INV, Objs.SUNFIRESPLINTER, used) &&
                    add(Invs.INV, Objs.SUNFIRERUNE, runes)
            }
        if (!done) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into sunfire runes.")
        p.addXp(Stats.RUNECRAFT, usedPure * XP_PURE + usedDaeyalt * XP_DAEYALT)
    }
}
