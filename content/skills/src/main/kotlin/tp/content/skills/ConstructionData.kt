@file:Suppress("MagicNumber") // data table: plank prices from the OSRS Wiki "Sawmill operator" (docs/mechanics/construction.md)

package tp.content.skills

import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * A plank the sawmill operator makes: [log] (unnoted) and [cost] coins give one [plank]. Prices: Wiki "Sawmill operator"
 * (Plank 100, Oak 250, Teak 500, Mahogany 1,500, Camphor 2,500, Ironwood 5,000, Rosewood 7,500).
 */
internal class PlankType(val name: String, val log: Int, val plank: Int, val cost: Int)

/** One row of `construction-furniture.tsv`: a piece of furniture built on a hotspot (tools/wiki-import/construction.py). */
internal class Furniture(
    val name: String,
    val level: Int,
    val xp: Double,
    val room: String,
    val hotspot: String,
    val locs: List<Int>,
    val flatpack: Int,
    val ticks: Int,
    val tools: List<Int>,
    val inputs: List<Ingredient>,
) {
    /** The same row with other built loc ids (the Wiki ids of a row are replaced by `construction-locs.tsv`). */
    fun withLocs(ids: List<Int>): Furniture = Furniture(name, level, xp, room, hotspot, ids, flatpack, ticks, tools, inputs)
}

/** One row of `construction-upgrades.tsv`: furniture [from] becomes [to] for [inputs] and [xp] at [level] (Wiki "Upgrade" recipes). */
internal class Upgrade(val from: String, val to: String, val level: Int, val xp: Double, val inputs: List<Ingredient>)

/**
 * One portal chamber / portal nexus destination (`poh-teleports.tsv`): the teleport spell's [name], Magic [level], [xp] of one
 * cast, base [runes] (obj to count; a portal costs 100 times, a nexus slot 1,000 times), the [destination] tile, the [quest]
 * the Wiki lists (not enforced, teleports.md OPEN-TP5) and the loc id of the directed [portals] for the teak, mahogany and
 * marble frame.
 */
internal class PohTeleport(
    val name: String,
    val level: Int,
    val xp: Double,
    val runes: List<Pair<Int, Int>>,
    val destination: CoordGrid,
    val quest: String,
    val portals: List<Int>,
)

/** One row of `construction-rooms.tsv`: a room an estate agent or the build menu adds for [cost] coins at [level]. */
internal class RoomType(val name: String, val level: Int, val cost: Int)

internal object ConstructionData {
    /** Sawmill planks in menu order (Wiki "Sawmill operator" table). */
    val PLANKS: List<PlankType> =
        listOf(
            PlankType("Plank", Objs.LOGS, Objs.WOODPLANK, 100),
            PlankType("Oak plank", Objs.OAK_LOGS, Objs.PLANK_OAK, 250),
            PlankType("Teak plank", Objs.TEAK_LOGS, Objs.PLANK_TEAK, 500),
            PlankType("Mahogany plank", Objs.MAHOGANY_LOGS, Objs.PLANK_MAHOGANY, 1500),
            PlankType("Camphor plank", Objs.CAMPHOR_LOGS, Objs.PLANK_CAMPHOR, 2500),
            PlankType("Ironwood plank", Objs.IRONWOOD_LOGS, Objs.PLANK_IRONWOOD, 5000),
            PlankType("Rosewood plank", Objs.ROSEWOOD_LOGS, Objs.PLANK_ROSEWOOD, 7500),
        )

    private const val FURNITURE_COLUMNS = 10
    private const val ROOM_COLUMNS = 3

    /**
     * Reads `construction-furniture.tsv` and applies `construction-extra.tsv` (docs/mechanics/construction.md CO13): a row of the
     * extra table replaces the main table's row of the same name when that has no built loc id, and is added when the main
     * table has no such row.
     */
    fun furniture(): List<Furniture> {
        val extra = furnitureRows("construction-extra.tsv").associateBy { it.name }
        val locs = rows("construction-locs.tsv").associate { "${it[0].lowercase()}|${it[1].lowercase()}" to it[2] }
        val base =
            furnitureRows("construction-furniture.tsv")
                .map { f -> extra[f.name]?.takeIf { f.locs.isEmpty() } ?: f }
                .map { f ->
                    locs["${f.name.lowercase()}|${f.room.lowercase()}"]?.let {
                        f.withLocs(
                            if (it ==
                                NO_LOCS
                            ) {
                                emptyList()
                            } else {
                                ints(it)
                            },
                        )
                    }
                        ?: f
                }
        val known = base.mapTo(HashSet()) { it.name }
        return base + extra.values.filter { it.name !in known }
    }

    /**
     * Reads `construction-upgrades.tsv`: in-place upgrades of built furniture (pools, workbenches, jewellery boxes, portal nexus;
     * docs/mechanics/construction.md CO29). A piece named in the `to` column cannot be built from nothing.
     */
    fun upgrades(): List<Upgrade> =
        rows("construction-upgrades.tsv").map { c ->
            require(c.size == UPGRADE_COLUMNS) { "construction-upgrades.tsv row has ${c.size} columns: $c" }
            Upgrade(
                c[0],
                c[1],
                c[2].toInt(),
                c[3].toDouble(),
                c[4].split(',').map {
                    Ingredient(it.substringBefore(':').toInt(), it.substringAfter(':').toInt())
                },
            )
        }

    /** Reads `poh-teleports.tsv`: the portal chamber / portal nexus destinations (docs/mechanics/construction.md CO26). */
    fun teleports(): List<PohTeleport> =
        rows("poh-teleports.tsv").map { c ->
            require(c.size == TELEPORT_COLUMNS) { "poh-teleports.tsv row has ${c.size} columns: $c" }
            PohTeleport(
                name = c[0],
                level = c[1].toInt(),
                xp = c[2].toDouble(),
                runes = c[3].split(',').map { it.substringBefore(':').toInt() to it.substringAfter(':').toInt() },
                destination = CoordGrid(c[4].toInt(), c[5].toInt(), 0),
                quest = c[6],
                portals = listOf(c[7].toInt(), c[8].toInt(), c[9].toInt()),
            )
        }

    /** A `construction-locs.tsv` locs cell meaning "this Wiki row has wrong ids: not buildable". */
    private const val NO_LOCS = "-"
    private const val UPGRADE_COLUMNS = 5
    private const val TELEPORT_COLUMNS = 10

    /** Furniture name to flat-pack obj (`construction-flatpacks.tsv`: cache objs `poh_flatpack_*` matched by exact name, CO14). */
    private val flatpacks: Map<String, Int> by lazy { rows("construction-flatpacks.tsv").associate { it[0] to it[1].toInt() } }

    private fun furnitureRows(file: String): List<Furniture> =
        rows(file).map { c ->
            require(c.size == FURNITURE_COLUMNS) { "$file row has ${c.size} columns: $c" }
            Furniture(
                name = c[0],
                level = c[1].toInt(),
                xp = c[2].toDouble(),
                room = c[3],
                hotspot = c[4],
                locs = ints(c[5]),
                flatpack = flatpacks[c[0]] ?: c[6].toInt(),
                ticks = c[7].toInt(),
                tools = ints(c[8]),
                inputs =
                    c[9].split(',').filter { it.isNotEmpty() }.map {
                        val (obj, count) = it.split(':')
                        Ingredient(obj.toInt(), count.toInt())
                    },
            )
        }

    /** Reads `construction-rooms.tsv`. */
    fun rooms(): List<RoomType> =
        rows("construction-rooms.tsv").map { c ->
            require(c.size == ROOM_COLUMNS) { "construction-rooms.tsv row has ${c.size} columns: $c" }
            RoomType(c[0], c[1].toInt(), c[2].toInt())
        }

    private fun ints(text: String): List<Int> = text.split(',').filter { it.isNotEmpty() }.map { it.toInt() }

    private fun rows(file: String): List<List<String>> {
        val text = ConstructionData::class.java.getResource(file)?.readText() ?: error("$file missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split('\t') }
            .toList()
    }
}
