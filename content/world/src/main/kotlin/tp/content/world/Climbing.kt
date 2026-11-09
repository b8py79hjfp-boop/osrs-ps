package tp.content.world

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerScript
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Ladders, trapdoors and stairs (docs/mechanics/doors-and-climbing.md DC5–DC7): `climbs.tsv` (`cache-dump loc-links`)
 * maps each placement and op to the tile it leads to. Climbing plays the ladder animation for a tick, then moves the
 * player (RS Mod `LadderScript`: anim, delay(1), telejump). A loc offering both directions under a lone "Climb" op asks.
 * Content-specific handlers for the same loc and op win ([ContentScope.onOpLocFallback]).
 */
public class Climbing : ContentPlugin {
    private class Link(val op: Int, val to: CoordGrid)

    override fun ContentScope.register() {
        for ((loc, op) in HANDLED) onOpLocFallback(loc, op) { l -> climb(l, op) }
        // DC8: "Top-floor" / "Bottom-floor" (e.g. Lumbridge Castle's staircase 56230) follow the staircases to the last floor.
        for (name in CHAIN_NAMES) {
            onOpLocNamed(name, "Top-floor") { l -> toEnd(l, up = true) }
            onOpLocNamed(name, "Bottom-floor") { l -> toEnd(l, up = false) }
        }
    }

    /**
     * DC8: from [loc], take its link up (or down), then the staircase placed near that landing (within [CHAIN_REACH]
     * tiles) that goes on the same way, until none does; the player lands where the last link leads.
     */
    private suspend fun PlayerScript.toEnd(loc: LocRef, up: Boolean) {
        var coord = loc.coord
        var id = loc.id
        var dest: CoordGrid? = null
        repeat(MAX_FLOORS) {
            val link =
                LINKS[key(coord, id)]?.firstOrNull { if (up) it.to.level > coord.level else it.to.level < coord.level } ?: return@repeat
            dest = link.to
            val next =
                PLACEMENTS.firstOrNull { (c, i) ->
                    c.level == link.to.level &&
                        maxOf(kotlin.math.abs(c.x - link.to.x), kotlin.math.abs(c.z - link.to.z)) <= CHAIN_REACH &&
                        LINKS[key(c, i)]?.any { if (up) it.to.level > c.level else it.to.level < c.level } == true
                } ?: return@repeat
            coord = next.first
            id = next.second
        }
        val to = dest ?: return player.message("Nothing interesting happens.")
        delay(1)
        player.teleport(to)
    }

    private suspend fun PlayerScript.climb(loc: LocRef, op: Int) {
        val links = LINKS[key(loc.coord, loc.id)] ?: return
        val up = links.firstOrNull { it.op != op && (it.to.level > loc.coord.level || it.to.z < loc.coord.z - DUNGEON_HALF) }
        val down = links.firstOrNull { it.op != op && (it.to.level < loc.coord.level || it.to.z > loc.coord.z + DUNGEON_HALF) }
        // DC6: "Climb" on a loc that also has Climb-up and Climb-down asks which way (RS Mod `climbOption`).
        if (OPTION_OPS.contains(loc.id to op) && up != null && down != null) {
            val stairs = loc.id in STAIRS
            player.choiceDialog(
                if (stairs) "Climb up or down the stairs?" else "Climb up or down the ladder?",
                if (stairs) listOf("Climb up the stairs.", "Climb down the stairs.") else listOf("Climb-up.", "Climb-down."),
            ) { pick -> teleport(if (pick == 1) up.to else down.to) }
            return
        }
        val link = links.firstOrNull { it.op == op } ?: return
        val goingDown = link.to.level < loc.coord.level || link.to.z > loc.coord.z + DUNGEON_HALF
        if (loc.id !in STAIRS) player.anim(if (goingDown) Seqs.HUMAN_PICKUPFLOOR else Seqs.HUMAN_REACHFORLADDER)
        delay(1)
        player.teleport(link.to)
    }

    private companion object {
        const val DUNGEON_HALF = 3200
        val LINKS = HashMap<Long, MutableList<Link>>()
        val HANDLED = HashSet<kotlin.Pair<Int, Int>>()

        /** Every linked placement (tile, loc id), for following a staircase chain (DC8). */
        val PLACEMENTS = ArrayList<kotlin.Pair<CoordGrid, Int>>()

        /** Loc names whose "Top-floor" / "Bottom-floor" ops follow the chain (DC8). */
        val CHAIN_NAMES = listOf("Staircase", "Stairs")

        /** DC8: how far from a landing the next staircase of a chain may stand (a 2x2 staircase's far corner + 1). */
        const val CHAIN_REACH = 4

        /** DC8: the most links a chain follows (the tallest buildings have four floors). */
        const val MAX_FLOORS = 4

        /** Loc + op pairs whose op text is a lone "Climb" while the loc also has explicit directions. */
        val OPTION_OPS = HashSet<kotlin.Pair<Int, Int>>()

        /** Locs named like stairs (no ladder animation; the stair dialogue wording). */
        val STAIRS = HashSet<Int>()

        fun key(c: CoordGrid, id: Int): Long = (c.packed.toLong() shl Int.SIZE_BITS) or id.toLong()

        init {
            val text =
                Climbing::class.java
                    .getResourceAsStream("climbs.tsv")
                    ?.bufferedReader()
                    ?.readLines()
                    .orEmpty()
            for (line in text.drop(1)) {
                val f = line.split('\t')
                val at = CoordGrid(f[0].toInt(), f[1].toInt(), f[2].toInt())
                val id = f[3].toInt()
                val op = f[4].toInt()
                if (!LINKS.containsKey(key(at, id))) PLACEMENTS += at to id
                LINKS.getOrPut(key(at, id)) { ArrayList() } +=
                    Link(op, CoordGrid(f[DEST_X].toInt(), f[DEST_Z].toInt(), f[DEST_LEVEL].toInt()))
                HANDLED += id to op
                if (f.size > STAIRS_COLUMN && f[STAIRS_COLUMN] == "1") STAIRS += id
                if (f.size > OPTION_COLUMN && f[OPTION_COLUMN] == "1") OPTION_OPS += id to op
            }
        }

        const val DEST_X = 5
        const val DEST_Z = 6
        const val DEST_LEVEL = 7
        const val STAIRS_COLUMN = 8
        const val OPTION_COLUMN = 9
    }
}
