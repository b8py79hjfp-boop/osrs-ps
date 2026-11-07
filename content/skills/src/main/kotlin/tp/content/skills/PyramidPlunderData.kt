@file:Suppress("MagicNumber") // data tables of the Wiki "Pyramid Plunder" pages, one value per room

package tp.content.skills

import tp.symbols.Objs
import tp.util.CoordGrid

/** What a placed loc of the pyramid is (rows of `pyramid-plunder-locs.tsv`, read from the cache's map square 30,69). */
internal enum class PpKind { URN, CHEST, SARCOPHAGUS, DOOR, TRAP }

/** One placed loc: room number 1..8, [index] (urn slot 1..15, door 1..4, else 0) and its origin tile. */
internal class PpSpot(val room: Int, val kind: PpKind, val index: Int)

/** One artefact line of a loot table: [obj] with weight [weight] out of [PpData.LOOT_DENOMINATOR]. */
internal class PpLoot(val obj: Int, val weight: Int)

/**
 * Pyramid Plunder tables (docs/mechanics/pyramid-plunder.md). Every array is indexed by room number minus one; rooms are
 * numbered in the order of the Wiki (PP4).
 */
internal object PpData {
    /** Eight rooms (Wiki "Pyramid Plunder"). */
    const val ROOMS = 8

    /** Wiki "Pyramid Plunder", Requirements: 21 Thieving, +10 per room, not boostable. */
    val LEVEL = intArrayOf(21, 31, 41, 51, 61, 71, 81, 91)

    /** Wiki "Pyramid Plunder", Experience: Thieving xp for searching an urn (check plus search). */
    val URN_XP = doubleArrayOf(60.0, 90.0, 150.0, 215.0, 300.0, 450.0, 675.0, 825.0)

    /** Wiki "Urn (Pyramid Plunder)", Thieving info "Check": a third of the urn xp. */
    val CHECK_XP = doubleArrayOf(20.0, 30.0, 50.0, 70.0, 100.0, 150.0, 225.0, 275.0)

    /** Wiki "Pyramid Plunder", Experience: Thieving xp for the grand gold chest (none when the swarm appears). */
    val CHEST_XP = doubleArrayOf(40.0, 60.0, 100.0, 140.0, 200.0, 300.0, 450.0, 550.0)

    /** Wiki "Pyramid Plunder", Experience: tomb door xp (room 8 has none: its doors leave). Halved with a lockpick. */
    val DOOR_XP = doubleArrayOf(40.0, 60.0, 100.0, 140.0, 200.0, 300.0, 450.0, 0.0)

    /** Wiki "Pyramid Plunder", Experience: Strength xp for opening the sarcophagus. */
    val SARCOPHAGUS_XP = doubleArrayOf(20.0, 30.0, 50.0, 70.0, 100.0, 150.0, 225.0, 275.0)

    /** Wiki "Speartrap": 10 Thieving xp for passing the trap. */
    const val TRAP_XP = 10.0

    /** Wiki "Pyramid Plunder", Sceptre: the sceptre rolls 1 in these for the chest and the sarcophagus (same for both). */
    val SCEPTRE_ODDS = intArrayOf(4200, 2800, 1600, 950, 800, 750, 650, 650)

    /** Weights of [CHEST_LOOT] / [SARCOPHAGUS_LOOT] / the urn lines are quarters (the Wiki rarities are 1/4, 1/2). */
    const val LOOT_DENOMINATOR = 4

    /** Wiki "Grand Gold Chest", Loot: the artefact lines of each room (the sceptre is rolled apart, see [SCEPTRE_ODDS]). */
    val CHEST_LOOT: List<List<PpLoot>> =
        listOf(
            listOf(PpLoot(Objs.NTK_SEAL_STONE, 1), PpLoot(Objs.NTK_SCARAB_STONE, 2), PpLoot(Objs.NTK_STATUETTE_STONE, 1)),
            listOf(PpLoot(Objs.NTK_SCARAB_STONE, 2), PpLoot(Objs.NTK_STATUETTE_STONE, 2)),
            listOf(PpLoot(Objs.NTK_SCARAB_STONE, 1), PpLoot(Objs.NTK_STATUETTE_STONE, 2), PpLoot(Objs.NTK_SEAL_GOLD, 1)),
            listOf(PpLoot(Objs.NTK_STATUETTE_STONE, 2), PpLoot(Objs.NTK_SEAL_GOLD, 2)),
            listOf(PpLoot(Objs.NTK_STATUETTE_STONE, 1), PpLoot(Objs.NTK_SEAL_GOLD, 2), PpLoot(Objs.NTK_SCARAB_GOLD, 1)),
            listOf(PpLoot(Objs.NTK_SEAL_GOLD, 2), PpLoot(Objs.NTK_SCARAB_GOLD, 2)),
            listOf(PpLoot(Objs.NTK_SEAL_GOLD, 1), PpLoot(Objs.NTK_SCARAB_GOLD, 2), PpLoot(Objs.NTK_STATUETTE_GOLD, 1)),
            listOf(PpLoot(Objs.NTK_SCARAB_GOLD, 2), PpLoot(Objs.NTK_STATUETTE_GOLD, 2)),
        )

    /** Wiki "Sarcophagus (Pyramid Plunder)", Loot: the artefact lines of each room. */
    val SARCOPHAGUS_LOOT: List<List<PpLoot>> =
        listOf(
            listOf(PpLoot(Objs.NTK_SCARAB_POTTERY, 1), PpLoot(Objs.NTK_STATUETTE_POTTERY, 2), PpLoot(Objs.NTK_SEAL_STONE, 1)),
            listOf(PpLoot(Objs.NTK_STATUETTE_POTTERY, 2), PpLoot(Objs.NTK_SEAL_STONE, 2)),
            listOf(PpLoot(Objs.NTK_STATUETTE_POTTERY, 1), PpLoot(Objs.NTK_SEAL_STONE, 2), PpLoot(Objs.NTK_SCARAB_STONE, 1)),
            listOf(PpLoot(Objs.NTK_SEAL_STONE, 2), PpLoot(Objs.NTK_SCARAB_STONE, 2)),
            listOf(PpLoot(Objs.NTK_SEAL_STONE, 1), PpLoot(Objs.NTK_SCARAB_STONE, 2), PpLoot(Objs.NTK_STATUETTE_STONE, 1)),
            listOf(PpLoot(Objs.NTK_SCARAB_STONE, 2), PpLoot(Objs.NTK_STATUETTE_STONE, 2)),
            listOf(PpLoot(Objs.NTK_SCARAB_STONE, 1), PpLoot(Objs.NTK_STATUETTE_STONE, 2), PpLoot(Objs.NTK_SEAL_GOLD, 1)),
            listOf(PpLoot(Objs.NTK_STATUETTE_STONE, 2), PpLoot(Objs.NTK_SEAL_GOLD, 2)),
        )

    /**
     * Wiki "Pyramid Plunder", Rewards: the first and last room each artefact appears in. The Wiki gives no per-urn rates
     * ("Unconfirmed which urns drop which artefacts and at what rate"), so an urn gives an artefact of its room's range,
     * every one equally likely (OPEN-PP5).
     */
    private val URN_ARTEFACTS: List<Triple<Int, Int, Int>> =
        listOf(
            Triple(Objs.NTK_IVORY_COMB, 1, 2),
            Triple(Objs.NTK_SCARAB_POTTERY, 1, 1),
            Triple(Objs.NTK_STATUETTE_POTTERY, 1, 3),
            Triple(Objs.NTK_SEAL_STONE, 1, 5),
            Triple(Objs.NTK_SCARAB_STONE, 1, 7),
            Triple(Objs.NTK_STATUETTE_STONE, 1, 8),
            Triple(Objs.NTK_SEAL_GOLD, 3, 8),
            Triple(Objs.NTK_SCARAB_GOLD, 5, 8),
            Triple(Objs.NTK_STATUETTE_GOLD, 7, 8),
        )

    /** The artefacts an urn of [room] (1..8) can hold. */
    fun urnLoot(room: Int): List<Int> = URN_ARTEFACTS.filter { room in it.second..it.third }.map { it.first }

    /** The cache's tile where a player arrives in each room: inside the entry exit door, past which the speartrap waits (PP5). */
    val ARRIVAL: List<CoordGrid> =
        listOf(
            CoordGrid(1927, 4477, 0),
            CoordGrid(1954, 4477, 0),
            CoordGrid(1977, 4471, 0),
            CoordGrid(1927, 4453, 0),
            CoordGrid(1965, 4444, 0),
            CoordGrid(1927, 4424, 0),
            CoordGrid(1943, 4421, 0),
            CoordGrid(1974, 4420, 0),
        )

    /** The first tile of the room proper, beyond the speartrap on the way from [ARRIVAL] (PP5). */
    val PAST_TRAP: List<CoordGrid> =
        listOf(
            CoordGrid(1927, 4471, 0),
            CoordGrid(1954, 4472, 0),
            CoordGrid(1977, 4464, 0),
            CoordGrid(1932, 4452, 0),
            CoordGrid(1960, 4444, 0),
            CoordGrid(1927, 4429, 0),
            CoordGrid(1944, 4426, 0),
            CoordGrid(1974, 4425, 0),
        )

    /** Whether [c] is in the pyramid's map square (the eight rooms on level 0, the four lobbies on levels 2 and 3). */
    fun inside(c: CoordGrid): Boolean = c.x in 1920..1983 && c.z in 4416..4479

    /** The placed locs of the eight rooms by origin tile ([key]); the cache's map square 30,69 (PP4). */
    val SPOTS: Map<Int, PpSpot> by lazy { loadSpots() }

    fun key(c: CoordGrid): Int = (c.x shl 16) or c.z

    private fun loadSpots(): Map<Int, PpSpot> {
        val text = PpData::class.java.getResource("pyramid-plunder-locs.tsv")?.readText() ?: error("pyramid-plunder-locs.tsv missing")
        val out = HashMap<Int, PpSpot>()
        for (line in text.lineSequence().drop(1)) {
            if (line.isBlank()) continue
            val cells = line.split('\t')
            val kind = PpKind.valueOf(cells[1].uppercase())
            out[(cells[3].toInt() shl 16) or cells[4].toInt()] = PpSpot(cells[0].toInt(), kind, cells[2].toInt())
        }
        return out
    }
}
