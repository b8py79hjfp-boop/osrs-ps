package tp.content.raids.tob

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.EntityRef
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Spawns each ToB room's NPCs when the room starts and names the NPCs that must die for it to be won. The fights are
 * boss scripts in content/combat. Spawn tiles are the rooms' template tiles (OPEN-TB7: the server spawn table is not
 * in the cache; chosen inside each arena's free flood fill, client-contract 5).
 */
internal object TobBosses {
    fun spawn(run: RaidRun, any: EntityRef, room: String) {
        val mode = run.scaling.mode
        when (room) {
            "maiden" -> Raids.spawn(any, run, TobNpcs.MAIDEN.of(mode), MAIDEN)
            "bloat" -> Raids.spawn(any, run, TobNpcs.BLOAT.of(mode), BLOAT)
            "nylocas" -> for (at in PILLARS) Raids.spawn(any, run, TobNpcs.PILLAR.of(mode), at)
            "sotetseg" -> Raids.spawn(any, run, TobNpcs.SOTETSEG.of(mode), SOTETSEG)
            "xarpus" -> Raids.spawn(any, run, TobNpcs.XARPUS.of(mode), XARPUS)
            "verzik" -> Raids.spawn(any, run, TobNpcs.VERZIK.of(mode), VERZIK)
        }
    }

    /** The NPC types whose deaths win [room] (every mode's forms). */
    fun required(room: String): Set<Int> = REQUIRED[room].orEmpty()

    /** 0, 1, 2 for Verzik's phase 1, 2, 3 forms; -1 otherwise. */
    fun verzikPhase(type: Int): Int =
        when (type) {
            in VERZIK_P1 -> 0
            in VERZIK_P2 -> 1
            in VERZIK_P3 -> 2
            else -> -1
        }

    /** South-west tiles (template). */
    val MAIDEN = CoordGrid(3162, 4444)
    val BLOAT = CoordGrid(3288, 4440)
    val PILLARS = listOf(CoordGrid(3289, 4242), CoordGrid(3298, 4242), CoordGrid(3289, 4251), CoordGrid(3298, 4251))
    val VASILIAS = CoordGrid(3294, 4246)
    val SOTETSEG = CoordGrid(3277, 4325)
    val XARPUS = CoordGrid(3169, 4386, 1)
    val VERZIK = CoordGrid(3166, 4312)

    private val VERZIK_P1 = setOf(Npcs.VERZIK_PHASE1, ENTRY_VERZIK_P1, HARD_VERZIK_P1)
    private val VERZIK_P2 = setOf(Npcs.VERZIK_PHASE2, ENTRY_VERZIK_P2, HARD_VERZIK_P2)
    private val VERZIK_P3 = TobNpcs.VERZIK_P3.all

    private val REQUIRED: Map<String, Set<Int>> =
        mapOf(
            "maiden" to TobNpcs.MAIDEN_DYING.all + MAIDEN_FORMS,
            "bloat" to TobNpcs.BLOAT.all,
            "nylocas" to VASILIAS_FORMS,
            "sotetseg" to TobNpcs.SOTETSEG_COMBAT.all,
            "xarpus" to TobNpcs.XARPUS_COMBAT.all,
            "verzik" to VERZIK_P3,
        )

    /** Client contract 4: the Maiden's 30% forms 8363 / 10817 / 10825 (she dies from them). */
    private val MAIDEN_FORMS: Set<Int> get() = setOf(Npcs.TOB_MAIDEN_30, Npcs.TOB_MAIDEN_30_STORY, Npcs.TOB_MAIDEN_30_HARD)

    /** Client contract 4: Vasilias's melee / magic / ranged forms per mode. */
    private val VASILIAS_FORMS: Set<Int> get() =
        setOf(Npcs.NYLOCAS_BOSS_MELEE, Npcs.NYLOCAS_BOSS_MAGIC, Npcs.NYLOCAS_BOSS_RANGED) + (ENTRY_VASILIAS..ENTRY_VASILIAS + 2) +
            (HARD_VASILIAS..HARD_VASILIAS + 2)

    private const val ENTRY_VERZIK_P1 = 10831
    private const val HARD_VERZIK_P1 = 10848
    private const val ENTRY_VERZIK_P2 = 10833
    private const val HARD_VERZIK_P2 = 10850
    private const val ENTRY_VASILIAS = 10787
    private const val HARD_VASILIAS = 10808
}
