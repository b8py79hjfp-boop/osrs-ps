package tp.content.combat

import tp.symbols.Npcs

/**
 * ToB modes from an NPC id (tob/client-contract.md section 4: Entry ids are the `_STORY` block 10766-10866, Hard the
 * `_HARD` block; Normal the original 8338-8389 range) and the Entry damage rule (tob.md TB12: "~50% less damage").
 */
internal object TobModes {
    const val ENTRY = 0
    const val NORMAL = 1
    const val HARD = 2

    fun of(type: Int): Int =
        when (type) {
            in ENTRY_IDS -> ENTRY
            in HARD_IDS -> HARD
            else -> NORMAL
        }

    /**
     * ToB HP by party size for an NPC a boss script spawns (tob.md TB10 / TB11, the rule raids-tob applies to room NPCs):
     * Normal / Hard 75% for 1-3, 87.5% for 4; Entry x1.9 / 2.7 / 3.4 / 4.0 for 2-5 (cache HP = 5 players / 1 player).
     */
    fun scale(npc: tp.plugin.NpcRef) {
        val n = (npc[tp.plugin.SharedAttrs.RAID_SCALE] ?: return).coerceIn(1, SCALE_ENTRY.size)
        val permille = if (of(npc.type) == ENTRY) SCALE_ENTRY[n - 1] else SCALE_NORMAL[n - 1]
        TfaKit.setPool(npc, maxOf(1, npc.baseLevel(HITPOINTS) * permille / PER_MILLE))
    }

    private val SCALE_NORMAL = intArrayOf(750, 750, 750, 875, 1000)
    private val SCALE_ENTRY = intArrayOf(1000, 1900, 2700, 3400, 4000)
    private const val PER_MILLE = 1000
    private const val HITPOINTS = 3

    /** Entry deals half (TB12; the Maiden's Entry max hit 18 against Normal's 36 agrees). */
    fun damage(mode: Int, normal: Int): Int = if (mode == ENTRY) normal / 2 else normal

    /** Client contract 4: every `_STORY` NPC id. */
    private val ENTRY_IDS: Set<Int> =
        setOf(
            Npcs.TOB_XARPUS_STATIC_STORY,
            Npcs.TOB_XARPUS_FEEDING_STORY,
            Npcs.TOB_XARPUS_COMBAT_STORY,
            Npcs.XARPUS_DEATH_STORY,
            Npcs.TOB_BLOAT_STORY,
            Npcs.TOB_SOTETSEG_NONCOMBAT_STORY,
            Npcs.TOB_SOTETSEG_COMBAT_STORY,
            Npcs.TOB_SOTETSEG_CREEPER_STORY,
            Npcs.MAIDEN_ELEMENTAL_STORY,
            Npcs.MAIDEN_BLOOD_SLUG_STORY,
            Npcs.VERZIK_INITIAL_STORY,
            Npcs.VERZIK_WEB_NPC_STORY,
        ) + (ENTRY_NYLO_FIRST..ENTRY_NYLO_LAST) + (ENTRY_MAIDEN_FIRST..ENTRY_MAIDEN_LAST) + (ENTRY_VERZIK_FIRST..ENTRY_VERZIK_LAST)

    /** Client contract 4: every `_HARD` NPC id. */
    private val HARD_IDS: Set<Int> =
        setOf(
            Npcs.TOB_BLOAT_HARD,
            Npcs.TOB_XARPUS_COMBAT_HARD,
            Npcs.TOB_SOTETSEG_NONCOMBAT_HARD,
            Npcs.TOB_SOTETSEG_COMBAT_HARD,
            Npcs.TOB_SOTETSEG_CREEPER_HARD,
            Npcs.MAIDEN_ELEMENTAL_HARD,
            Npcs.MAIDEN_BLOOD_SLUG_HARD,
            Npcs.VERZIK_INITIAL_HARD,
            Npcs.VERZIK_INITIAL_HARD_BASE,
            Npcs.VERZIK_INITIAL_HARD_QUICKSTART,
        ) + (HARD_XARPUS_FIRST..HARD_XARPUS_LAST) + (HARD_NYLO_FIRST..HARD_NYLO_LAST) + (HARD_MAIDEN_FIRST..HARD_MAIDEN_LAST) +
            (HARD_VERZIK_FIRST..HARD_VERZIK_LAST)

    // Client contract 4 id blocks.
    private const val ENTRY_NYLO_FIRST = 10774
    private const val ENTRY_NYLO_LAST = 10790
    private const val ENTRY_MAIDEN_FIRST = 10814
    private const val ENTRY_MAIDEN_LAST = 10821
    private const val ENTRY_VERZIK_FIRST = 10830
    private const val ENTRY_VERZIK_LAST = 10846
    private const val HARD_XARPUS_FIRST = 10770
    private const val HARD_XARPUS_LAST = 10773
    private const val HARD_NYLO_FIRST = 10791
    private const val HARD_NYLO_LAST = 10811
    private const val HARD_MAIDEN_FIRST = 10822
    private const val HARD_MAIDEN_LAST = 10829
    private const val HARD_VERZIK_FIRST = 10847
    private const val HARD_VERZIK_LAST = 10863
}
