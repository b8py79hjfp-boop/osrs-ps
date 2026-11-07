package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.util.CoordGrid

private val SPOT_TIMER = TimerId("fishing.spots")

/**
 * Where fishing spots are and how they move (docs/mechanics/fishing.md FS8, FS9). `fishing-spawns.tsv` (Wiki `ObjectLocLine` pins,
 * `tools/wiki-import/fishing_spawns.py`) lists the tiles a spot of a type has been seen on, grouped by area. A group gets
 * [spotsOf] spots, and after a hidden timer each moves to a free tile of its own group (Wiki "Fishing spot").
 *
 * There is no per-tick world hook in plugin-api, so the logged-in players' timers drive it: the first timer of a tick moves
 * every spot that is due (the world RNG is deterministic, so the result is too). A spot moves by being removed and spawned anew on
 * its next tile; players still fishing at the old one are sent to the new one ([replacement]), as in the game ("if clicked on
 * as it changes, the player will run to the new location").
 */
internal class FishingSpots {
    private class Group(val kind: SpotKind, val npc: Int, val tiles: List<CoordGrid>)

    private class Spot(val group: Group, var tile: Int, var ref: NpcRef?, var moveAt: Long)

    private class Redirect(val to: NpcRef, val until: Long)

    private val spots = ArrayList<Spot>()
    private val moved = HashMap<Int, Redirect>()
    private var adopting: Spot? = null
    private var lastTick = -1L

    fun install(scope: ContentScope) {
        with(scope) {
            onWorldStart {
                // Headless scenarios turn the world's NPCs off (golden tests need empty ground), as content/world Spawns does.
                if (System.getProperty("tp.worldSpawns") == "false") return@onWorldStart
                for (group in load()) {
                    for (i in 0 until spotsOf(group.tiles.size)) {
                        val spot = Spot(group, i * 2, null, if (group.kind in STILL) Long.MAX_VALUE else UNSCHEDULED)
                        adopting = spot
                        spawnNpc(group.npc, group.tiles[spot.tile], 0)
                        adopting = null
                        spots += spot
                    }
                }
            }
            onNpcSpawn { adopting?.let { if (it.group.npc == type) it.ref = this } }
            onLogin { if (!hasTimer(SPOT_TIMER)) timer(SPOT_TIMER, 1, true) { drive(this) } }
        }
    }

    /** The spot that [oldIndex] became when it moved in the last [REDIRECT_TICKS] ticks, or null. */
    fun replacement(oldIndex: Int, now: Long): NpcRef? {
        val r = moved[oldIndex] ?: return null
        if (r.until < now) {
            moved.remove(oldIndex)
            return null
        }
        return r.to
    }

    /** Runs once per tick (the first player timer of the tick): moves the spots whose timer expired. */
    private fun drive(p: PlayerRef) {
        val now = p.tick
        if (now == lastTick) return
        lastTick = now
        for (spot in spots) {
            if (spot.moveAt == Long.MAX_VALUE) continue
            if (spot.moveAt == UNSCHEDULED) spot.moveAt = now + interval(p, spot.group.kind)
            if (spot.moveAt <= now) move(p, spot, now)
        }
    }

    /** Moves [spot] to a random free tile of its group; if every other tile is taken it stays and waits for a new timer. */
    private fun move(p: PlayerRef, spot: Spot, now: Long) {
        spot.moveAt = now + interval(p, spot.group.kind)
        val old = spot.ref ?: return
        val tiles = spot.group.tiles
        var free = 0
        for (t in tiles.indices) if (t != spot.tile && !taken(spot.group, t)) free++
        if (free == 0) return
        var pick = p.random(free)
        var next = -1
        for (t in tiles.indices) {
            if (t == spot.tile || taken(spot.group, t)) continue
            if (pick-- == 0) {
                next = t
                break
            }
        }
        val fresh = p.spawnNpc(spot.group.npc, tiles[next], 0)
        moved[old.index] = Redirect(fresh, now + REDIRECT_TICKS)
        old.despawn()
        spot.ref = fresh
        spot.tile = next
    }

    private fun taken(group: Group, tile: Int): Boolean {
        for (s in spots) if (s.group === group && s.tile == tile) return true
        return false
    }

    /** Ticks until the next move: the window of the spot type, uniform (Wiki "Fishing spot", Mod Ash 19 Dec 2022). */
    private fun interval(p: PlayerRef, kind: SpotKind): Long {
        val window = WINDOWS[kind] ?: DEFAULT_WINDOW
        return (window.first + p.random(window.last - window.first + 1)).toLong()
    }

    private fun load(): List<Group> {
        val text = FishingSpots::class.java.getResource("fishing-spawns.tsv")?.readText() ?: error("fishing-spawns.tsv missing")
        val groups = LinkedHashMap<String, Group>()
        val tiles = LinkedHashMap<String, MutableList<CoordGrid>>()
        for (line in text.lineSequence().drop(1)) {
            val c = line.split('\t')
            if (c.size < COLUMNS) continue
            val key = "${c[KIND]}:${c[GROUP]}"
            val kind = SpotKind.valueOf(c[KIND])
            groups.getOrPut(key) { Group(kind, c[NPC].toInt(), tiles.getOrPut(key) { ArrayList() }) }
            tiles.getValue(key) += CoordGrid(c[X].toInt(), c[Z].toInt(), c[PLANE].toInt())
        }
        return groups.values.toList()
    }

    private companion object {
        const val KIND = 0
        const val NPC = 1
        const val X = 2
        const val Z = 3
        const val PLANE = 4
        const val GROUP = 5
        const val COLUMNS = 6

        /** A spot whose move timer has not been drawn yet. */
        const val UNSCHEDULED = -1L

        /** How long a moved spot still sends its old fishers to the new one. */
        const val REDIRECT_TICKS = 8L

        /**
         * OPEN-FS8: how many spots an area has at once is not published; the Wiki lists the tiles spots were seen on. One spot per
         * two listed tiles (rounded up) leaves free tiles to move to.
         */
        fun spotsOf(tiles: Int): Int = (tiles + 1) / 2

        /** Most spots move after 250 to 530 ticks (Wiki "Fishing spot": "a minimum of 250 ticks and a maximum of 530 ticks"). */
        val DEFAULT_WINDOW = 250..530

        /** Per-type windows: sacred and infernal eels 100 to 300, anglerfish 8 to 830 (Wiki "Fishing spot", Mod Ash 19 Dec 2022). */
        val WINDOWS: Map<SpotKind, IntRange> =
            mapOf(
                SpotKind.INFERNAL_EEL to 100..300,
                SpotKind.SACRED_EEL to 100..300,
                SpotKind.ANGLER to 8..830,
            )

        /** Spots that never move: the karambwan spots and the dark crab spots (Wiki "Fishing spot"). */
        val STILL = setOf(SpotKind.KARAMBWAN, SpotKind.DARK_CRAB)
    }
}
