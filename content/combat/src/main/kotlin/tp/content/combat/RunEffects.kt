package tp.content.combat

import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Varbits

/**
 * Run energy modifiers (docs/mechanics/run-energy.md RE4-RE6): the stamina potion's drain reduction and the graceful
 * outfit's restore boost, applied through the engine's drain / restore permille.
 */
internal object RunEffects {
    /** Wiki "Stamina potion": 70% less drain for 2 minutes; another dose resets the timer. */
    private const val STAMINA_DRAIN_PERMILLE = 300
    private const val NORMAL_PERMILLE = 1000

    /** Varbit `stamina_duration` (the orb's timer, RuneLite `TimersAndBuffsPlugin`) counts 10-tick units: 2 min = 20. */
    private const val STAMINA_UNITS = 20
    private const val UNIT_TICKS = 10
    private val STAMINA = TimerId("stamina")

    /** RE4: a dose restores 20% (2,000 units) and starts / resets the 2-minute effect; the run orb turns orange. */
    fun stamina(p: PlayerRef) {
        p.runEnergy += STAMINA_RESTORE
        p.runDrainPermille = STAMINA_DRAIN_PERMILLE
        p.setVarbit(Varbits.STAMINA_ACTIVE, 1)
        p.setVarbit(Varbits.STAMINA_DURATION, STAMINA_UNITS)
        p.timer(STAMINA, UNIT_TICKS, soft = true) {
            val left = varbit(Varbits.STAMINA_DURATION) - 1
            setVarbit(Varbits.STAMINA_DURATION, maxOf(0, left))
            if (left <= 0) {
                setVarbit(Varbits.STAMINA_ACTIVE, 0)
                runDrainPermille = NORMAL_PERMILLE
                stopTimer(STAMINA)
            }
        }
    }

    private const val STAMINA_RESTORE = 2000

    /**
     * RE5 (Wiki "Graceful outfit"): restore +3% per hood, cape, gloves, boots and +4% per top, legs; the full set adds
     * another 10% (+30% in all). Any recolour counts (name starts with "Graceful").
     */
    fun graceful(p: PlayerRef) {
        var boost = 0
        var pieces = 0
        for ((slot, percent) in GRACEFUL_SLOTS) {
            if (p.objName(p.worn(slot)).startsWith("Graceful")) {
                boost += percent
                pieces++
            }
        }
        if (pieces == GRACEFUL_SLOTS.size) boost += FULL_SET_BONUS
        p.runRestorePermille = NORMAL_PERMILLE + boost * PERCENT
    }

    private const val FULL_SET_BONUS = 10
    private const val PERCENT = 10

    private val GRACEFUL_SLOTS =
        listOf(Wear.HEAD to 3, Wear.CAPE to 3, Wear.BODY to 4, Wear.LEGS to 4, Wear.HANDS to 3, Wear.FEET to 3)
}
