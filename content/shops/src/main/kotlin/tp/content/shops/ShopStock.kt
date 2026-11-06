package tp.content.shops

/**
 * One shop's stock in one world (docs/mechanics/shops.md). Slots `0 until def.items.size` hold the default items in
 * Wiki order and keep their slot at count 0; items sold to a general store take the next free slot and vanish when
 * they reach 0 (RS Mod `ShopRestockProcess`, ISC, DECISIONS D-032). Counts walk back to their base lazily: [sync]
 * applies the restock steps that fell due since an item last changed, so no world tick hook is needed.
 */
internal class ShopStock(val def: ShopDef, now: Long) {
    /** Obj per slot, -1 = empty. Sent to the client as the shop inv. */
    val objs = IntArray(def.slots) { -1 }
    val counts = IntArray(def.slots)
    private val base = IntArray(def.slots)
    private val rate = IntArray(def.slots) { EXTRA_RESTOCK_TICKS }
    private val last = LongArray(def.slots) { now }

    /** Bumped on every change of [objs] / [counts]; viewers compare it with the version they last received. */
    var version = 0
        private set

    init {
        for ((slot, item) in def.items.withIndex()) {
            objs[slot] = item.obj
            base[slot] = if (item.stock == ShopItem.INFINITE) INFINITE_STOCK else item.stock
            counts[slot] = base[slot]
            rate[slot] = if (item.restock > 0) item.restock else EXTRA_RESTOCK_TICKS
        }
    }

    fun slotOf(obj: Int): Int = objs.indexOf(obj)

    fun baseOf(slot: Int): Int = base[slot]

    fun item(slot: Int): ShopItem? = def.items.getOrNull(slot)

    fun isInfinite(slot: Int): Boolean = item(slot)?.stock == ShopItem.INFINITE

    /** First empty slot past the default items, or -1 when the shop is full. */
    fun freeSlot(): Int = (def.items.size until objs.size).firstOrNull { objs[it] < 0 } ?: -1

    /** Applies the restock / destock steps due by tick [now]: one unit per `rate` ticks toward the base stock. */
    fun sync(now: Long) {
        for (slot in objs.indices) {
            if (objs[slot] < 0 || counts[slot] == base[slot]) {
                // Not off its base: the clock starts when it first leaves it (the next change syncs first).
                last[slot] = now
                continue
            }
            val steps = now / rate[slot] - last[slot] / rate[slot]
            if (steps <= 0) continue
            last[slot] = now
            counts[slot] =
                if (counts[slot] > base[slot]) {
                    maxOf(base[slot].toLong(), counts[slot] - steps).toInt()
                } else {
                    minOf(base[slot].toLong(), counts[slot] + steps).toInt()
                }
            if (counts[slot] == 0 && slot >= def.items.size) objs[slot] = -1
            version++
        }
    }

    /** Takes [n] units out of [slot] (a purchase). Items with infinite stock never run out. */
    fun take(slot: Int, n: Int) {
        if (!isInfinite(slot)) counts[slot] -= n
        version++
    }

    /** Puts [n] units of [obj] into the shop (a sale), in its slot or the next free one. False when there is no room. */
    fun put(obj: Int, n: Int): Boolean {
        val slot = slotOf(obj).let { if (it >= 0) it else freeSlot() }
        if (slot < 0 || counts[slot].toLong() + n > Int.MAX_VALUE) return false
        objs[slot] = obj
        counts[slot] += n
        version++
        return true
    }

    private companion object {
        /**
         * OSRS Wiki "Shop" (Stock): "Items that aren't part of the shop's regular stock ... will slowly be removed"; "General
         * store": "destock at a rate of one per minute" = 100 ticks. Also the rate of default items without a Wiki restock
         * time (OPEN-SH3).
         */
        const val EXTRA_RESTOCK_TICKS = 100

        /**
         * OSRS Wiki "Shop" (Stock): shops with infinite stock allow "the purchase of up to 10 items at a time"; the count
         * shown and available is 10 (OPEN-SH4).
         */
        const val INFINITE_STOCK = 10
    }
}

/**
 * Shop price formulas. OSRS Wiki "Shop" (Pricing), "General store" (player buy / sell prices) and Module:Shop calculator;
 * the same linear model as RS Mod `StandardGpCostCalculations` (ISC, DECISIONS D-032). Prices are in the shop's currency.
 */
internal object ShopPricing {
    private const val PERMILLE = 1000L

    /** Wiki "General store": the buying price stops falling at 30% of the value (Module:Shop calculator `lowerBound`). */
    private const val BUY_FLOOR_PERMILLE = 300L

    /** Wiki "Shop": "all shops will buy the player's items for a minimum of 10% of the item's value". */
    private const val SELL_FLOOR_PERMILLE = 100L

    /** Module:Shop calculator `upperBound` when buying: the sell multiplier plus 500% of the value. */
    private const val BUY_CEILING_EXTRA_PERMILLE = 5000L

    /**
     * What a player pays for one unit while the shop holds [stock] of an item whose default stock is [base]: the Wiki
     * calculator's `value * (sellMultiplier + delta * (base - stock))`, at least 30% of the value and 1; [fixed] >= 0 is
     * the StoreLine's own price.
     */
    fun buyPrice(def: ShopDef, value: Int, fixed: Int, base: Int, stock: Int): Int {
        if (fixed >= 0) return fixed
        val v = value.toLong()
        val raw = Math.floorDiv(v * def.sellPermille + (base.toLong() - stock) * v * def.deltaPermille, PERMILLE)
        val floor = maxOf(1L, v * BUY_FLOOR_PERMILLE / PERMILLE)
        val ceiling = maxOf(floor, v * (def.sellPermille + BUY_CEILING_EXTRA_PERMILLE) / PERMILLE)
        return raw.coerceIn(floor, ceiling).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * What the shop pays for one unit while it holds [stock] (default stock [base]): `value * (buyMultiplier - delta *
     * (stock - base))`, at least 10% of the value, at most value + the base price (Module:Shop calculator `upperBound`).
     */
    fun sellPrice(def: ShopDef, value: Int, fixed: Int, base: Int, stock: Int): Int {
        if (fixed >= 0) return fixed
        val v = value.toLong()
        val raw = Math.floorDiv(v * def.buyPermille + (base.toLong() - stock) * v * def.deltaPermille, PERMILLE)
        val floor = v * SELL_FLOOR_PERMILLE / PERMILLE
        val ceiling = maxOf(floor, v * (PERMILLE + def.buyPermille) / PERMILLE)
        return raw.coerceIn(floor, ceiling).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
