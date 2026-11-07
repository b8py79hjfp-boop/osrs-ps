package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.ItemTx
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Reclaiming lost items (docs/mechanics/death.md DE6-DE9): looting the grave with its tiered fee, Death's Office
 * storage with its 5% fee, and Death's coffer that pays both.
 */
internal object DeathsOffice {
    /** DE8 (Wiki "Death (NPC)", "Portal (Death's Office)"): Death, the portal and the arrival tile. */
    const val DEATH_NPC = 9855
    const val PORTAL = 39549
    const val COFFER = 39550
    val ARRIVAL = CoordGrid(3174, 5726, 0)

    /** DE8 (Wiki "Death's Domain (scenery)"): the coffins next to the respawn points. */
    val ENTRANCES = intArrayOf(38426, 39535, 39546, 39547, 39548, 39637, 42344, 42345, 52570)

    private const val COFFER_KEY = "death.coffer"
    private val RETURN_TO = AttrKey<CoordGrid>("death_office_return")
    private val LOOTING = AttrKey<Boolean>("death_looting_grave")

    /** DE5: the grave timer pauses while the grave interface is open. */
    fun lootingGrave(player: PlayerRef): Boolean = player[LOOTING] == true

    fun setLooting(player: PlayerRef, open: Boolean) {
        player[LOOTING] = if (open) true else null
    }

    /** DE7: moves [count] of [obj] from [slot] of [fromInv] into Death's Office storage; deleted if it is full. */
    fun store(tx: ItemTx, player: PlayerRef, fromInv: Int, slot: Int, obj: Int, count: Int): Boolean {
        val storage = player.inv(Invs.DEATH_PERMANENT, stackAll = true)
        val fits = storage.total(obj) > 0 || storage.freeSlots > 0
        return if (fits) tx.move(fromInv, slot, obj, count, Invs.DEATH_PERMANENT) else tx.removeAt(fromInv, slot, obj, count)
    }

    /** DE6: the grave fee of one item by value. */
    fun graveFee(value: Long): Long =
        when {
            value < TIER_1 -> 0
            value < TIER_2 -> FEE_1
            value < TIER_3 -> FEE_2
            else -> FEE_3
        }

    /** DE8: Death's Office fee of one item: 5% of the value when it is worth 100,000 or more. */
    fun officeFee(value: Long): Long = if (value < TIER_1) 0 else value * OFFICE_PERCENT / PERCENT

    /**
     * Takes every item of [fromInv] (the grave or Death's Office storage) into the backpack, paying [fee] per item from
     * the coffer, then the bank. Items that don't fit stay. True if anything was taken.
     */
    fun reclaim(player: PlayerRef, fromInv: Int, fee: (Long) -> Long, cap: Long = Long.MAX_VALUE): Boolean {
        val from = player.inv(fromInv, stackAll = true)
        val backpack = player.inv(Invs.INV)
        val taking = ArrayList<Int>()
        var free = backpack.freeSlots
        var total = 0L
        for (slot in 0 until from.size) {
            val obj = from.obj(slot)
            if (obj < 0) continue
            val stack = player.objStackable(obj) || from.count(slot) == 1
            val slotsNeeded = if (player.objStackable(obj)) (if (backpack.total(obj) > 0) 0 else 1) else from.count(slot)
            if (!stack && slotsNeeded > free) continue
            if (slotsNeeded > free) continue
            free -= slotsNeeded
            taking += slot
            total += fee(player.objValue(obj).toLong()) * if (player.objStackable(obj)) 1 else from.count(slot)
        }
        if (taking.isEmpty()) {
            player.message("You don't have enough inventory space.")
            return false
        }
        total = minOf(total, cap)
        if (!canPay(player, total)) {
            player.message("You don't have enough money to pay the reclaim fee.")
            return false
        }
        val ok =
            player.transaction("death reclaim") {
                taking.all { slot -> move(fromInv, slot, from.obj(slot), from.count(slot), Invs.INV) } && pay(this, player, total)
            }
        if (ok && total > 0) player.message("You pay $total coins to reclaim your items.")
        return ok
    }

    private fun canPay(player: PlayerRef, amount: Long): Boolean =
        amount <= coffer(player) + player.inv(Invs.BANK, stackAll = true).total(Objs.COINS)

    /** DE6 / DE8: the coffer pays first, the bank the rest. */
    private fun pay(tx: ItemTx, player: PlayerRef, amount: Long): Boolean {
        if (amount <= 0) return true
        val fromCoffer = minOf(amount, coffer(player))
        player.setSaved(COFFER_KEY, coffer(player) - fromCoffer)
        val rest = amount - fromCoffer
        return rest <= 0 || tx.remove(Invs.BANK, Objs.COINS, rest.toInt())
    }

    fun coffer(player: PlayerRef): Long = player.saved(COFFER_KEY) ?: 0L

    /** DE9: sacrifices the item in backpack [slot] for 105% of its Grand Exchange price. */
    fun sacrifice(player: PlayerRef, slot: Int, obj: Int) {
        val backpack = player.inv(Invs.INV)
        if (slot !in 0 until backpack.size || backpack.obj(slot) != obj) return
        val value = player.objValue(obj).toLong()
        if (!player.objTradeable(obj) || value < SACRIFICE_MIN) return player.message("You cannot trade that item to Death.")
        val count = backpack.count(slot)
        val worth = minOf(value * SACRIFICE_PERCENT / PERCENT, ITEM_CAP) * count
        if (coffer(player) + worth > COFFER_CAP) return player.message("The coffer is too full to accommodate that.")
        if (!player.transaction("death coffer") { removeAt(Invs.INV, slot, obj, count) }) return
        player.setSaved(COFFER_KEY, coffer(player) + worth)
        player.message("Your coffer now holds ${coffer(player)} coins.")
    }

    /** DE8: the coffins take the player to the office, remembering where they came from. */
    fun enter(player: PlayerRef) {
        player[RETURN_TO] = player.coord
        player.teleport(ARRIVAL)
    }

    /** DE8: the portal returns to the entrance, else the respawn point. */
    fun leave(player: PlayerRef, respawn: CoordGrid) {
        player.teleport(player[RETURN_TO] ?: respawn)
        player[RETURN_TO] = null
    }

    /** DE8: Death's "Collect": the stored items, then a live grave early, at the office fee. */
    fun collect(player: PlayerRef, death: NpcRef) {
        player.face(death)
        val stored = player.inv(Invs.DEATH_PERMANENT, stackAll = true)
        if (stored.freeSlots < stored.size) {
            reclaim(player, Invs.DEATH_PERMANENT, ::officeFee)
            return
        }
        val grave = player.inv(Invs.GRAVESTONE, stackAll = true)
        if (grave.freeSlots < grave.size) {
            if (reclaim(player, Invs.GRAVESTONE, ::officeFee)) Graves.changed(player)
            return
        }
        player.message("Death has nothing of yours to give back.")
    }

    private const val PERCENT = 100L
    private const val OFFICE_PERCENT = 5L

    /** Wiki "Death/Item Recovery Fees": tiers 100k / 1m / 10m, fees 1k / 10k / 100k, at most 500k per grave. */
    private const val TIER_1 = 100_000L
    private const val TIER_2 = 1_000_000L
    private const val TIER_3 = 10_000_000L
    private const val FEE_1 = 1_000L
    private const val FEE_2 = 10_000L
    private const val FEE_3 = 100_000L
    const val GRAVE_FEE_CAP = 500_000L

    /** Wiki "Death's Coffer": items worth 10,000+, 105%, 2,100,000,000 per item, 2,147,483,647 in all. */
    private const val SACRIFICE_MIN = 10_000L
    private const val SACRIFICE_PERCENT = 105L
    private const val ITEM_CAP = 2_100_000_000L
    private const val COFFER_CAP = 2_147_483_647L
}
