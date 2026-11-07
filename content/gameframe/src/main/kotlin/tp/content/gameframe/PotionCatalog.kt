package tp.content.gameframe

import tp.plugin.WorldRef

/**
 * The potions the potion store takes, read once at world start from the cache enums the client builds its list from
 * (client script 6080: enum 4826 = finished potions, enum 4829 = unfinished potions; script 6066 maps a row to its family by
 * key, finished first). Each value is a family enum (see [PotionEntry]); rows keep the cache order.
 */
internal class PotionCatalog(world: WorldRef) {
    /** One row of the store: [objs] indexed by dose count (index 0 unused), so `objs.size - 1` is the largest dose count. */
    class Family(val entry: PotionEntry, val objs: IntArray) {
        val maxDoses: Int get() = objs.size - 1
    }

    /** What a backpack obj is worth to the store: [doses] doses of family [family] (-1: an empty vial, no doses). */
    class Deposit(val family: Int, val doses: Int)

    val families: List<Family>
    val deposits: Map<Int, Deposit>

    /** The empty vial obj (key 0 of every family enum) given out by vial withdrawals. */
    val vialObj: Int

    init {
        val potionIds = world.enumInts(ENUM_POTIONS).toSortedMap().values
        val unfinishedIds = world.enumInts(ENUM_UNFINISHED).toSortedMap().values
        val potionTable = PotionTable.POTIONS.associateBy { it.enumId }
        val unfinishedTable = PotionTable.UNFINISHED.associateBy { it.enumId }
        val built = ArrayList<Family>()
        val found = HashMap<Int, Deposit>()
        var vial = -1
        var vialNoted = -1
        for ((table, ids) in listOf(potionTable to potionIds, unfinishedTable to unfinishedIds)) {
            for (id in ids) {
                val entry = table[id] ?: error("potion store: family enum $id has no varp in PotionTable")
                val ints = world.enumInts(id)
                val max = ints.keys.max()
                val objs = IntArray(max + 1) { n -> if (n == 0) -1 else ints[n] ?: error("potion store: enum $id lacks key $n") }
                for (n in 1..max) {
                    found[objs[n]] = Deposit(built.size, n)
                    ints[-(NOTED_BASE + n)]?.let { found[it] = Deposit(built.size, n) }
                }
                if (vial < 0) vial = ints[KEY_VIAL] ?: -1
                if (vialNoted < 0) vialNoted = ints[KEY_VIAL_NOTED] ?: -1
                built += Family(entry, objs)
            }
        }
        check(vial >= 0) { "potion store: no empty vial in the family enums" }
        found[vial] = Deposit(VIAL_FAMILY, 0)
        if (vialNoted >= 0) found[vialNoted] = Deposit(VIAL_FAMILY, 0)
        families = built
        deposits = found
        vialObj = vial
    }

    companion object {
        /** Cache enum 4826: row -> family enum of a finished potion (client script 6080). */
        const val ENUM_POTIONS = 4826

        /** Cache enum 4829: row -> family enum of an unfinished potion (client script 6080). */
        const val ENUM_UNFINISHED = 4829

        /** Family enum key of the empty vial; the noted vial is key -10 and the noted n-dose potion key -(10 + n) (enum 729). */
        const val KEY_VIAL = 0
        const val NOTED_BASE = 10
        const val KEY_VIAL_NOTED = -NOTED_BASE

        /** [Deposit.family] of an empty vial. */
        const val VIAL_FAMILY = -1
    }
}
