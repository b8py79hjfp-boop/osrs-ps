package tp.content.skills

import tp.plugin.PlayerRef
import tp.symbols.Varbits

/**
 * The state of the Barbarian Training miniquest (docs/mechanics/barbarian-training.md BT1), kept in the cache varbits the client
 * reads (`brut_*`, all 2 bits wide except the 6 bit miniquest varbit). The values are this server's own steps (OPEN-BT1: the
 * client's journal script that reads them is not decoded); 0 always means "not started". Read by Fishing (the barbarian rod
 * spots), Firemaking (a bow lights logs) and [BarbarianTraining] (Otto, the anvil, the mixes).
 */
internal object BarbarianProgress {
    /** Miniquest varbit `brut_miniquest` (6 bits): 1 once Otto has been asked about his training ("You think so?"). */
    const val STARTED = 1

    /** Rod fishing (`brut_fishing_r`): Otto has told the player to take the rod from under his bed. */
    const val ROD_TAUGHT = 1

    /** Rod fishing: the player has caught a leaping fish. */
    const val ROD_DONE = 2

    /** Rod fishing: Otto has accepted the lesson (the herblore lesson follows it). */
    const val ROD_ACKNOWLEDGED = 3

    /** Firemaking (`brut_fire`): Otto has taught lighting logs with a bow. */
    const val FIRE_TAUGHT = 1

    /** Firemaking: a set of oak logs has been lit with a bow. */
    const val FIRE_BOW_LIT = 2

    /** Firemaking: Otto has accepted the lesson; pyre ships are next (not implemented, OPEN-BT3). */
    const val FIRE_ACKNOWLEDGED = 3

    /** Herblore (`brut_herb_potion`): Otto has asked for an attack mix. */
    const val HERB_TAUGHT = 1

    /** Herblore: the attack mix was handed in; the training is done. */
    const val HERB_DONE = 2

    /** Spears (`brut_smith_spear`): Otto has explained spears. */
    const val SPEAR_TAUGHT = 1

    /** Spears: a spear was made on the barbarian anvil. */
    const val SPEAR_MADE = 2

    /** Hastae (`brut_smith_hasta`): Otto has explained hastae (after a spear was made). */
    const val HASTA_TAUGHT = 1

    /** Hastae: a hasta was made on the barbarian anvil; the smithing training is done. */
    const val HASTA_MADE = 2

    fun started(p: PlayerRef): Boolean = p.varbit(Varbits.BRUT_MINIQUEST) >= STARTED

    /** Whether Otto has taught rod fishing, so the barbarian spots and the bed work (Wiki "Barbarian Training" > Heavy rod fishing). */
    fun rodTaught(p: PlayerRef): Boolean = p.varbit(Varbits.BRUT_FISHING_R) >= ROD_TAUGHT

    /** Whether Otto has taught firemaking with a bow (Wiki "Barbarian Training" > Lighting fires with a bow). */
    fun bowTaught(p: PlayerRef): Boolean = p.varbit(Varbits.BRUT_FIRE) >= FIRE_TAUGHT

    /** A leaping fish was caught with the rod: the lesson is complete (Otto: "I've fished with a barbarian rod!"). */
    fun caughtLeapingFish(p: PlayerRef) {
        if (p.varbit(Varbits.BRUT_FISHING_R) == ROD_TAUGHT) p.setVarbit(Varbits.BRUT_FISHING_R, ROD_DONE)
    }

    /** Oak logs were lit with a bow (Wiki: "After lighting your first set of oak logs, speak to Otto"). */
    fun litWithBow(p: PlayerRef) {
        if (p.varbit(Varbits.BRUT_FIRE) == FIRE_TAUGHT) p.setVarbit(Varbits.BRUT_FIRE, FIRE_BOW_LIT)
    }
}
