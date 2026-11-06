package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.DbTables
import tp.symbols.Varps

/**
 * Music tab track list (docs/mechanics/gameframe-buttons.md M1): clicking a track plays it. The tracks are the rows of
 * cache db table `music` (RS Mod `MusicRepository` reads the same table); their `midi` cell is the id sent to the client.
 */
public class MusicTab : ContentPlugin {
    private class Track(val sortName: String, val midi: Int)

    /** Playable tracks in list order. */
    private var tracks: List<Track> = emptyList()

    /** Per unlock varp (index 0 = `musicmulti_1`): the bits of every track stored in it. */
    private var unlockBits: IntArray = IntArray(UNLOCK_VARPS.size)

    override fun ContentScope.register() {
        onWorldStart {
            val rows = dbRows(DbTables.Music.ID)
            val all = ArrayList<Track>()
            for (row in rows) {
                val midi = row.int(DbTables.Music.COL_MIDI) ?: continue
                val varpIndex = row.int(DbTables.Music.COL_VARIABLE, component = 0)
                val bit = row.int(DbTables.Music.COL_VARIABLE, component = 1)
                if (varpIndex != null && bit != null && varpIndex in 1..UNLOCK_VARPS.size) {
                    unlockBits[varpIndex - 1] = unlockBits[varpIndex - 1] or (1 shl bit)
                }
                if (row.int(DbTables.Music.COL_HIDDEN) == 1) continue
                all += Track(row.string(DbTables.Music.COL_SORTNAME) ?: continue, midi)
            }
            // OPEN-M1b: the client builds the list itself; the slot of a click is assumed to be the track's position
            // when sorted by the table's `sortname` column (case-insensitive), which is how the list reads in game.
            tracks = all.sortedBy { it.sortName.lowercase() }
        }
        onLogin { enable() }
        onButton(Components.Music.JUKEBOX) { click -> tracks.getOrNull(click.slot)?.let { playMusic(it.midi) } }
    }

    private fun PlayerRef.enable() {
        // OPEN-M1a: every track counts as unlocked (the client greys out a track whose unlock bit is unset), so the bits
        // of all tracks are set; real unlocks (area visits, quests) are not tracked yet.
        for (i in UNLOCK_VARPS.indices) {
            val merged = varp(UNLOCK_VARPS[i]) or unlockBits[i]
            if (merged != varp(UNLOCK_VARPS[i])) setVarp(UNLOCK_VARPS[i], merged)
        }
        if (tracks.isNotEmpty()) ifEvents(Components.Music.JUKEBOX, 0..tracks.lastIndex, IfEvents.op(1))
    }

    private companion object {
        /** Unlock varps `musicmulti_1..28`: a track's `variable` cell is (index, bit) into this list (RS Mod `unlockVarps`). */
        val UNLOCK_VARPS: IntArray =
            intArrayOf(
                Varps.MUSICMULTI_1,
                Varps.MUSICMULTI_2,
                Varps.MUSICMULTI_3,
                Varps.MUSICMULTI_4,
                Varps.MUSICMULTI_5,
                Varps.MUSICMULTI_6,
                Varps.MUSICMULTI_7,
                Varps.MUSICMULTI_8,
                Varps.MUSICMULTI_9,
                Varps.MUSICMULTI_10,
                Varps.MUSICMULTI_11,
                Varps.MUSICMULTI_12,
                Varps.MUSICMULTI_13,
                Varps.MUSICMULTI_14,
                Varps.MUSICMULTI_15,
                Varps.MUSICMULTI_16,
                Varps.MUSICMULTI_17,
                Varps.MUSICMULTI_18,
                Varps.MUSICMULTI_19,
                Varps.MUSICMULTI_20,
                Varps.MUSICMULTI_21,
                Varps.MUSICMULTI_22,
                Varps.MUSICMULTI_23,
                Varps.MUSICMULTI_24,
                Varps.MUSICMULTI_25,
                Varps.MUSICMULTI_26,
                Varps.MUSICMULTI_27,
                Varps.MUSICMULTI_28,
            )
    }
}
