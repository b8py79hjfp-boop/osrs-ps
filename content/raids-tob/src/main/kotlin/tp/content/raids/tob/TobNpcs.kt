package tp.content.raids.tob

import tp.symbols.Npcs

/**
 * ToB NPC ids by mode (tob/client-contract.md section 4: Normal unsuffixed, Entry `_STORY`, Hard `_HARD`). Ids the
 * symbols do not name are written as their cache ids with the table row they come from.
 */
internal object TobNpcs {
    /** A room boss's spawn form per mode (Entry, Normal, Hard). */
    class ByMode(val entry: Int, val normal: Int, val hard: Int) {
        fun of(mode: String): Int =
            when (mode) {
                TobRaid.ENTRY -> entry
                TobRaid.HARD -> hard
                else -> normal
            }

        val all: Set<Int> get() = setOf(entry, normal, hard)
    }

    val MAIDEN = ByMode(Npcs.TOB_MAIDEN_100_STORY, Npcs.TOB_MAIDEN_100, Npcs.TOB_MAIDEN_100_HARD)

    /** The form that dies last (the room is won when it dies). */
    val MAIDEN_DYING = ByMode(Npcs.TOB_MAIDEN_DYING_B_STORY, Npcs.TOB_MAIDEN_DYING_B, Npcs.TOB_MAIDEN_DYING_B_HARD)
    val BLOAT = ByMode(Npcs.TOB_BLOAT_STORY, Npcs.TOB_BLOAT, Npcs.TOB_BLOAT_HARD)
    val VASILIAS = ByMode(Npcs.NYLOCAS_BOSS_SPAWNING_STORY, Npcs.NYLOCAS_BOSS_SPAWNING, Npcs.NYLOCAS_BOSS_SPAWNING_HARD)

    /** Client contract 4: the Nylocas support pillar (size 3): 8358 / 10790 / 10811. */
    val PILLAR = ByMode(ENTRY_PILLAR, Npcs.TOB_NYLOCAS_SUPPORT, HARD_PILLAR)
    val SOTETSEG = ByMode(Npcs.TOB_SOTETSEG_NONCOMBAT_STORY, Npcs.TOB_SOTETSEG_NONCOMBAT, Npcs.TOB_SOTETSEG_NONCOMBAT_HARD)
    val SOTETSEG_COMBAT = ByMode(Npcs.TOB_SOTETSEG_COMBAT_STORY, Npcs.TOB_SOTETSEG_COMBAT, Npcs.TOB_SOTETSEG_COMBAT_HARD)

    /** Xarpus's first (static) form: 8338 / 10766 / 10770. */
    val XARPUS = ByMode(Npcs.TOB_XARPUS_STATIC_STORY, Npcs.TOB_XARPUS_STATIC, HARD_XARPUS_STATIC)
    val XARPUS_COMBAT = ByMode(Npcs.TOB_XARPUS_COMBAT_STORY, Npcs.TOB_XARPUS_COMBAT, Npcs.TOB_XARPUS_COMBAT_HARD)
    val VERZIK = ByMode(Npcs.VERZIK_INITIAL_STORY, Npcs.VERZIK_INITIAL, Npcs.VERZIK_INITIAL_HARD)

    /** Verzik's phase 3 (8374 / 10835 / 10852): the room is won when it dies. */
    val VERZIK_P3 = ByMode(ENTRY_VERZIK_P3, Npcs.VERZIK_PHASE3, HARD_VERZIK_P3)

    /** Blood spawns and webs keep their cache HP (TobScaling). */
    val UNSCALED: Set<Int> =
        setOf(
            Npcs.MAIDEN_BLOOD_SLUG,
            Npcs.MAIDEN_BLOOD_SLUG_STORY,
            Npcs.MAIDEN_BLOOD_SLUG_HARD,
            Npcs.VERZIK_WEB_NPC,
            Npcs.VERZIK_WEB_NPC_STORY,
            Npcs.VERZIK_WEB_NPC_HARD,
            // OPEN-NY4: support HP is the cache value in every party size.
            Npcs.TOB_NYLOCAS_SUPPORT,
            ENTRY_PILLAR,
            HARD_PILLAR,
        )

    private const val ENTRY_PILLAR = 10790
    private const val HARD_PILLAR = 10811
    private const val HARD_XARPUS_STATIC = 10770
    private const val ENTRY_VERZIK_P3 = 10835
    private const val HARD_VERZIK_P3 = 10852
}
