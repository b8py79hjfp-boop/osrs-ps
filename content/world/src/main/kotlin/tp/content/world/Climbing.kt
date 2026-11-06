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
