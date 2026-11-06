package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.PolygonAreas
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.DbTables
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * Music tab and area music (docs/mechanics/music.md, gameframe-buttons.md M1). The tracks are the rows of cache db
 * table `music` (RS Mod `MusicRepository` reads the same table); a row's `midi` cell is the id sent to the client, its
 * `variable` cell the unlock bit, its `area` cell the music area the track plays in and `area_default` the area it
 * is the classic-mode track of.
 */
public class MusicTab : ContentPlugin {
    private class Track(
        val sortName: String,
        val displayName: String,
        val unlockHint: String,
        val midi: Int,
        /** Index into [UNLOCK_VARPS], or -1 if the track cannot be unlocked (its `variable` cell is unset). */
        val varpIndex: Int,
        val bitMask: Int,
        val automatic: Boolean,
        val hidden: Boolean,
        /** Music area of the `area` cell, 0 if unset. */
        val area: Int,
    )

    /** Per-player music state; one instance per player, mutated in place so the per-tick poll does not allocate. */
    private class State {
        /** Packed tile of the last poll, -1 before the first one. */
        var tileKey: Int = -1

        /** Music area of the player's tile at the last poll (0 = none). */
        var area: Int = 0

        /** Index into [tracks] of the track last started, -1 if none. */
        var current: Int = -1
    }

    /** Every table row that has a midi, in row-id order. */
    private var tracks: List<Track> = emptyList()

    /** Listed tracks (hidden rows left out) in list order. */
    private var listed: List<Track> = emptyList()

    /** Per music area: indexes into [tracks] of its listed tracks (modern area mode). */
    private var areaTracks: Map<Int, IntArray> = emptyMap()

    /** Per music area: index into [tracks] of its `area_default` track (classic area mode). */
    private var areaDefault: Map<Int, Int> = emptyMap()

    /** Music area polygons; a polygon's name is the area number. Empty until the geometry is supplied (OPEN-MU1). */
    private var geometry: PolygonAreas = PolygonAreas.fromJson("[]")

    override fun ContentScope.register() {
        onWorldStart { load(this) }
        onLogin { enable() }
        onButton(Components.Music.JUKEBOX) { click -> listed.getOrNull(click.slot)?.let { select(it) } }
        onButton(Components.Music.AREA) { setVarp(Varps.MUSICPLAY, MODE_AREA) }
        onButton(Components.Music.SHUFFLE) { setVarp(Varps.MUSICPLAY, MODE_SHUFFLE) }
        onButton(Components.Music.SINGLE) { setVarp(Varps.MUSICPLAY, MODE_SINGLE) }
        onButton(Components.Music.SKIP) { skip() }
        onCommand("unlockmusic") {
            for (track in tracks) unlockBit(track)
            message("All music tracks unlocked.")
        }
        onCommand("musicarea") { args -> args.getOrNull(0)?.toIntOrNull()?.let { enterArea(it) } }
    }

    private fun load(world: tp.plugin.WorldRef) {
        val rows = world.dbRows(DbTables.Music.ID)
        val all = ArrayList<Track>()
        val defaults = HashMap<Int, Int>()
        val byArea = HashMap<Int, MutableList<Int>>()
        for (row in rows) {
            val midi = row.int(DbTables.Music.COL_MIDI) ?: continue
            val varpIndex = row.int(DbTables.Music.COL_VARIABLE, component = 0)
            val bit = row.int(DbTables.Music.COL_VARIABLE, component = 1)
            val unlockable = varpIndex != null && bit != null && varpIndex in 1..UNLOCK_VARPS.size
            val track =
                Track(
                    sortName = row.string(DbTables.Music.COL_SORTNAME) ?: continue,
                    displayName = row.string(DbTables.Music.COL_DISPLAYNAME) ?: continue,
                    unlockHint = row.string(DbTables.Music.COL_UNLOCKHINT) ?: "somewhere.",
                    midi = midi,
                    varpIndex = if (unlockable) varpIndex!! - 1 else -1,
                    bitMask = if (unlockable) 1 shl bit!! else 0,
                    automatic = row.int(DbTables.Music.COL_AUTOMATIC_UNLOCK) == 1,
                    hidden = row.int(DbTables.Music.COL_HIDDEN) == 1,
                    area = row.int(DbTables.Music.COL_AREA) ?: 0,
                )
            all += track
            row.int(DbTables.Music.COL_AREA_DEFAULT)?.let { defaults[it] = all.lastIndex }
            if (track.area != 0 && !track.hidden) byArea.getOrPut(track.area) { ArrayList() } += all.lastIndex
        }
        tracks = all
        // OPEN-M1b: the client builds the list itself; the slot of a click is assumed to be the track's position
        // when sorted by the table's `sortname` column (case-insensitive), which is how the list reads in game.
        listed = all.filter { !it.hidden }.sortedBy { it.sortName.lowercase() }
        areaTracks = byArea.mapValues { it.value.toIntArray() }
        areaDefault = defaults
        geometry =
            PolygonAreas.fromJson(
                MusicTab::class.java.getResource("music-areas.json")?.readText() ?: error("music-areas.json missing"),
            )
    }

    private fun PlayerRef.enable() {
        val state = State()
        this[STATE] = state
        // Tracks flagged `automatic_unlock` are unlocked from the start, without the unlock text.
        for (track in tracks) if (track.automatic) unlockBit(track)
        // OPEN-MU3: the cache has the varbit `use_previous_music_mode_on_login`; without it the mode is reset to Area (RS Mod).
        if (varbit(Varbits.USE_PREVIOUS_MUSIC_MODE_ON_LOGIN) == 0) setVarp(Varps.MUSICPLAY, MODE_AREA)
        if (listed.isNotEmpty()) ifEvents(Components.Music.JUKEBOX, 0..listed.lastIndex, IfEvents.op(1))
        timer(AREA_POLL, 1, soft = true) { poll() }
    }

    /** Once per tick: only a change of tile looks up the area (an int compare otherwise). */
    private fun PlayerRef.poll() {
        val state = this[STATE] ?: return stopTimer(AREA_POLL)
        val c = coord
        val key = (((c.level shl Z_BITS) or c.z) shl X_BITS) or c.x
        if (key == state.tileKey) return
        state.tileKey = key
        val area = geometry.areaAt(c)?.toIntOrNull() ?: 0
        if (area == state.area) return
        state.area = area
        // Music unlocks and area changes run through the area queue (scripts.md Q11).
        if (area != 0) areaQueue { enterArea(area) }
    }

    /** The player entered music [area]: unlock its tracks and, in Area mode, play one (area mode semantics: OPEN-MU2). */
    private fun PlayerRef.enterArea(area: Int) {
        val classic = varbit(Varbits.MUSIC_AREA_MODE) == AREA_MODE_CLASSIC
        if (classic) {
            // Classic mode: only the area's default track; it does not unlock the modern tracks (RS Mod `enterArea`).
            val index = areaDefault[area] ?: return
            unlock(tracks[index])
            if (varp(Varps.MUSICPLAY) == MODE_AREA) play(index)
        } else {
            val indexes = areaTracks[area] ?: return
            for (i in indexes) unlock(tracks[i])
            if (varp(Varps.MUSICPLAY) == MODE_AREA) play(pickOther(indexes))
        }
    }

    /** A click on the list: locked tracks show their unlock hint, others play. */
    private fun PlayerRef.select(track: Track) {
        if (!isUnlocked(track)) return message("$LOCKED_PREFIX${track.unlockHint}")
        play(tracks.indexOf(track))
    }

    /** Skip Track: the next track of the current mode; Single mode has none (OPEN-MU4). */
    private fun PlayerRef.skip() {
        val state = this[STATE] ?: return
        when (varp(Varps.MUSICPLAY)) {
            MODE_AREA -> {
                val indexes = areaTracks[state.area]
                if (varbit(Varbits.MUSIC_AREA_MODE) != AREA_MODE_CLASSIC && indexes != null) play(pickOther(indexes))
            }

            MODE_SHUFFLE -> {
                val unlocked = tracks.indices.filter { !tracks[it].hidden && isUnlocked(tracks[it]) && it != state.current }
                if (unlocked.isNotEmpty()) play(unlocked[random(unlocked.size)])
            }
        }
    }

    /** A random member of [indexes] other than the track now playing (the only one if it is the sole member). */
    private fun PlayerRef.pickOther(indexes: IntArray): Int {
        val current = this[STATE]?.current ?: -1
        val candidates = indexes.count { it != current }
        if (candidates == 0) return indexes[0]
        var n = random(candidates)
        for (i in indexes) {
            if (i == current) continue
            if (n-- == 0) return i
        }
        return indexes[0]
    }

    private fun PlayerRef.play(index: Int) {
        val state = this[STATE] ?: return
        // Playing the track that is already playing does not restart it (RS Mod `MusicPlayer.play`).
        if (state.current == index) return
        state.current = index
        playMusic(tracks[index].midi)
        ifText(Components.Music.NOW_PLAYING_TEXT, tracks[index].displayName)
    }

    private fun PlayerRef.isUnlocked(track: Track): Boolean =
        track.varpIndex >= 0 && varp(UNLOCK_VARPS[track.varpIndex]) and track.bitMask != 0

    /** Sets the track's unlock bit; true if it was locked. */
    private fun PlayerRef.unlockBit(track: Track): Boolean {
        if (track.varpIndex < 0 || isUnlocked(track)) return false
        setVarp(UNLOCK_VARPS[track.varpIndex], varp(UNLOCK_VARPS[track.varpIndex]) or track.bitMask)
        return true
    }

    private fun PlayerRef.unlock(track: Track) {
        if (!unlockBit(track)) return
        // The `music_unlock_text_toggle` varbit turns the text off (RS Mod `MusicPlayer.sendUnlockMessage`).
        if (varbit(Varbits.MUSIC_UNLOCK_TEXT_TOGGLE) ==
            0
        ) {
            message("You have unlocked a new music track: $NAME_COLOUR${track.displayName}</col>.")
        }
    }

    private companion object {
        val AREA_POLL = TimerId("music_area_poll")
        val STATE = AttrKey<State>("music_state")

        /** `musicplay` varp values (RS Mod `MusicPlayMode`): Manual = the Single button. */
        const val MODE_SINGLE = 0
        const val MODE_AREA = 1
        const val MODE_SHUFFLE = 2

        /** `music_area_mode` varbit: 0 modern (all tracks of an area), 1 classic (RS Mod `MusicAreaMode`). */
        const val AREA_MODE_CLASSIC = 1

        /** Bits of a tile coordinate in the poll key: x and z are 14 bits (map 0..16383), level 2 bits. */
        const val X_BITS = 14
        const val Z_BITS = 14

        /** Red name colour of the unlock text (RS Mod `MusicPlayer.sendUnlockMessage`, opaque chat). */
        const val NAME_COLOUR = "<col=e00a19>"

        /** OPEN-MU5: wording of the locked-track message is not sourced; it appends the row's `unlockhint` cell. */
        const val LOCKED_PREFIX = "You have not unlocked this track yet. It is unlocked "

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
