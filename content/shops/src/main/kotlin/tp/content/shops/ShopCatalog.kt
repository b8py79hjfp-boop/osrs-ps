package tp.content.shops

/** One item a shop stocks by default. */
internal class ShopItem(
    val obj: Int,
    /** Default (base) stock; [INFINITE] for items that never run out. */
    val stock: Int,
    /** Ticks between two restock (or destock) steps; [UNKNOWN_RESTOCK] when the Wiki gives none. */
    val restock: Int,
    /** Fixed price the shop sells at (Wiki StoreLine `sell`), -1 = by multiplier. */
    val sellPrice: Int,
    /** Fixed price the shop buys at (Wiki StoreLine `buy`), -1 = by multiplier. */
    val buyPrice: Int,
) {
    companion object {
        /** `shop-items.tsv` marks the Wiki's `stock=inf` with -1. */
        const val INFINITE = -1

        /** `shop-items.tsv` marks a missing or `-1` Wiki restock time with -1 (Module:StoreLine: "unknown"). */
        const val UNKNOWN_RESTOCK = -1
    }
}

/** A shop from `shops.tsv` (tools/wiki-import/shops.py, docs/mechanics/shops.md). */
internal class ShopDef(
    val id: Int,
    val name: String,
    /** Cache obj the shop trades in (coins, Tokkul, ...). */
    val currency: Int,
    /** General store: buys any tradeable item. */
    val general: Boolean,
    /** Wiki `sellmultiplier`: price the shop sells at, per mille of the item's value. */
    val sellPermille: Int,
    /** Wiki `buymultiplier`: price the shop buys at, per mille of the item's value. */
    val buyPermille: Int,
    /** Wiki `delta`: price change per stock unit, per mille of the item's value. */
    val deltaPermille: Int,
    /** Cache inv the client binds the shop interface to (its size is the slot count). */
    val inv: Int,
    val slots: Int,
    val items: List<ShopItem>,
)

/** All shops and the NPC types that open them. Immutable, parsed once from the resources. */
internal class ShopCatalog private constructor(val shops: List<ShopDef>, private val byNpc: Map<Int, ShopDef>) {
    fun forNpc(type: Int): ShopDef? = byNpc[type]

    companion object {
        private const val SHOP_COLUMNS = 11
        private const val ITEM_COLUMNS = 6

        /** `shop-items.tsv` column of the fixed buy price, and `shops.tsv` column of the owner NPC ids. */
        private const val BUY_PRICE = 5
        private const val OWNERS = 10

        fun load(): ShopCatalog {
            val items = HashMap<Int, MutableMap<Int, ShopItem>>()
            for (line in resource("shop-items.tsv").lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < ITEM_COLUMNS) continue
                val shop = c[0].toInt()
                // A repeated row keeps the first one.
                items
                    .getOrPut(shop) {
                        LinkedHashMap()
                    }.putIfAbsent(c[1].toInt(), ShopItem(c[1].toInt(), c[2].toInt(), c[3].toInt(), c[4].toInt(), c[BUY_PRICE].toInt()))
            }
            val shops = ArrayList<ShopDef>()
            val byNpc = HashMap<Int, ShopDef>()
            for (line in resource("shops.tsv").lineSequence().drop(1)) {
                val c = line.split('\t')
                if (c.size < SHOP_COLUMNS - 1) continue
                val slots = c[8].toInt()
                val stock = items[c[0].toInt()]?.values?.take(slots).orEmpty()
                val def =
                    ShopDef(
                        c[0].toInt(),
                        c[1],
                        c[2].toInt(),
                        c[3] == "1",
                        c[4].toInt(),
                        c[5].toInt(),
                        c[6].toInt(),
                        c[7].toInt(),
                        slots,
                        stock,
                    )
                shops += def
                // An NPC type opens the first shop that lists it.
                if (c.size >= SHOP_COLUMNS) c[OWNERS].split(',').mapNotNull { it.toIntOrNull() }.forEach { byNpc.putIfAbsent(it, def) }
            }
            return ShopCatalog(shops, byNpc)
        }

        private fun resource(name: String): String =
            ShopCatalog::class.java.getResource(name)?.readText() ?: error("$name missing")
    }
}
