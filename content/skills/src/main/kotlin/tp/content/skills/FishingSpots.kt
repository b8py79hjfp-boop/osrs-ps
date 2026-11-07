package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.util.CoordGrid

/**
 * Where fishing spots are and how they move (docs/mechanics/fishing.md FS8, FS9). `fishing-spawns.tsv` (Wiki `ObjectLocLine` pins,
 * `tools/wiki-import/fishing_spawns.py`) lists the tiles a spot of a type has been seen on, grouped by area. A group gets
 * [spotsOf] spots, and after a hidden timer each moves to a free tile of its own group (Wiki "Fishing spot").
 *
 * The world tick hook drives it (spots keep moving with nobody online): each tick it moves every spot that is due, drawing
 * from the world RNG through the spot's own NPC (so the result is deterministic). A spot moves by being removed and spawned anew on
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
            onWorldTick { drive(tick) }
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

    /** Runs once per tick (world tick hook): moves the spots whose timer expired. */
    private fun drive(now: Long) {
        for (spot in spots) {
            if (spot.moveAt == Long.MAX_VALUE) continue
            val ref = spot.ref ?: continue
            if (spot.moveAt == UNSCHEDULED) spot.moveAt = now + interval(ref, spot.group.kind)
            if (spot.moveAt <= now) move(ref, spot, now)
        }
    }

    /** Moves [spot], now the NPC [old], to a random free tile of its group; if all other tiles are taken it stays and draws a new timer. */
    private fun move(old: NpcRef, spot: Spot, now: Long) {
        spot.moveAt = now + interval(old, spot.group.kind)
        val tiles = spot.group.tiles
        var free = 0
        for (t in tiles.indices) if (t != spot.tile && !taken(spot.group, t)) free++
        if (free == 0) return
        var pick = old.random(free)
        var next = -1
        for (t in tiles.indices) {
            if (t == spot.tile || taken(spot.group, t)) continue
            if (pick-- == 0) {
                next = t
                break
            }
        }
        val fresh = old.spawnNpc(spot.group.npc, tiles[next], 0)
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
    private fun interval(rng: EntityRef, kind: SpotKind): Long {
        val window = WINDOWS[kind] ?: DEFAULT_WINDOW
        return (window.first + rng.random(window.last - window.first + 1)).toLong()
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
