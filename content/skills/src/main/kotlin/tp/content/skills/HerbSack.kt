package tp.content.skills

import tp.plugin.BankItemOp
import tp.plugin.BankTransfer
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.ItemsCreated
import tp.plugin.ObjPickedUp
import tp.plugin.PlayerRef
import tp.plugin.PvpDeathStorage
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * The herb sack and silklined herb sack (docs/mechanics/storage-bags.md HS1-HS7; Wiki "Herb sack", "Silklined herb
 * sack"): 30 (silklined 100) of each of the 15 grimy normal herbs, kept in player-saved keys. Fill / Empty / Check /
 * Open / Close; an open sack takes grimy herbs picked up or harvested; "Empty" in the bank banks them.
 */
public class HerbSack : ContentPlugin {
    override fun ContentScope.register() {
        for (sack in ALL) {
            onItemOp(sack, "Fill") { click -> if (holds(this, click)) fill(this, HERBS.toList()) }
            onItemOp(sack, "Empty") { click -> if (holds(this, click)) empty(this) }
            onItemOp(sack, "Check") { click -> if (holds(this, click)) check(this) }
            onUseOnItem(sack, -1) { use -> useOn(this, use.toObj) }
        }
        for ((closed, open) in listOf(Objs.SLAYER_HERB_SACK to Objs.SLAYER_HERB_SACK_OPEN, SILK to SILK_OPEN)) {
            onItemOp(closed, "Open") { click -> swap(this, click, open) }
            onItemOp(open, "Close") { click -> swap(this, click, closed) }
        }
        onUseOnItem(Objs.SLAYER_SPIDER_SILK, Objs.SLAYER_HERB_SACK) { upgrade(this, Objs.SLAYER_HERB_SACK, SILK) }
        onUseOnItem(Objs.SLAYER_SPIDER_SILK, Objs.SLAYER_HERB_SACK_OPEN) { upgrade(this, Objs.SLAYER_HERB_SACK_OPEN, SILK_OPEN) }
        events.on<ObjPickedUp> { e -> if (e.obj in HERBS) autoStore(e.player, e.obj) }
        events.on<ItemsCreated> { e ->
            if (e.reason == HARVEST) {
                e.objs.keys
                    .filter { it in HERBS }
                    .forEach { autoStore(e.player, it) }
            }
        }
        events.on<BankItemOp> { op -> if (op.obj in ALL) bankEmpty(op) }
        events.on<PvpDeathStorage> { d -> pvpDeath(d) }
    }

    internal companion object {
        private val SILK = Objs.SLAYER_HERB_SACK_SILK
        private val SILK_OPEN = Objs.SLAYER_HERB_SACK_SILK_OPEN
        private val REGULAR = setOf(Objs.SLAYER_HERB_SACK, Objs.SLAYER_HERB_SACK_OPEN)
        private val SILKLINED = setOf(SILK, SILK_OPEN)
        val ALL: Set<Int> = REGULAR + SILKLINED
        private val OPEN = setOf(Objs.SLAYER_HERB_SACK_OPEN, SILK_OPEN)

        /** Wiki "Herb sack": the 15 grimy normal herbs, lowest level first (the order "Empty" uses). */
        val HERBS =
            intArrayOf(
                Objs.UNIDENTIFIED_GUAM,
                Objs.UNIDENTIFIED_MARENTILL,
                Objs.UNIDENTIFIED_TARROMIN,
                Objs.UNIDENTIFIED_HARRALANDER,
                Objs.UNIDENTIFIED_RANARR,
                Objs.UNIDENTIFIED_TOADFLAX,
                Objs.UNIDENTIFIED_IRIT,
                Objs.UNIDENTIFIED_AVANTOE,
                Objs.UNIDENTIFIED_KWUARM,
                Objs.UNIDENTIFIED_HUASCA,
                Objs.UNIDENTIFIED_SNAPDRAGON,
                Objs.UNIDENTIFIED_CADANTINE,
                Objs.UNIDENTIFIED_LANTADYME,
                Objs.UNIDENTIFIED_DWARF_WEED,
                Objs.UNIDENTIFIED_TORSTOL,
            )

        /** Wiki: "up to 30 of each type"; silklined "up to 100 of each". */
        private const val CAPACITY = 30
        private const val SILK_CAPACITY = 100

        /** Farming's harvest transaction reason (an open sack takes harvested herbs). */
        private const val HARVEST = "farming-harvest"

        private fun key(herb: Int) = "bags.herb.$herb"

        fun stored(p: PlayerRef, herb: Int): Int = (p.saved(key(herb)) ?: 0L).toInt()

        private fun setStored(p: PlayerRef, herb: Int, n: Int) = p.setSaved(key(herb), n.toLong().takeIf { it > 0 })

        /** The capacity of the sack in the backpack (the silklined one wins), 0 without one. */
        private fun capacity(p: PlayerRef): Int {
            val inv = p.inv(Invs.INV)
            return when {
                SILKLINED.any { inv.total(it) > 0 } -> SILK_CAPACITY
                REGULAR.any { inv.total(it) > 0 } -> CAPACITY
                else -> 0
            }
        }

        private fun name(p: PlayerRef): String = if (SILKLINED.any { p.inv(Invs.INV).total(it) > 0 }) "silklined herb sack" else "herb sack"

        private fun holds(p: PlayerRef, click: ButtonClick): Boolean =
            p.inv(Invs.INV).let { click.slot in 0 until it.size && it.obj(click.slot) == click.obj }

        /** HS2: stores [herbs] from the backpack up to the room per herb. **OPEN-HS1:** the fill messages. */
        private fun fill(p: PlayerRef, herbs: List<Int>) {
            val cap = capacity(p)
            var moved = 0
            var full = false
            for (herb in herbs) {
                val held = p.inv(Invs.INV).total(herb).toInt()
                if (held == 0) continue
                val n = minOf(held, cap - stored(p, herb))
                if (n <= 0) {
                    full = true
                    continue
                }
                if (!p.transaction("herb sack fill") { remove(Invs.INV, herb, n) }) continue
                setStored(p, herb, stored(p, herb) + n)
                moved += n
            }
            when {
                moved > 0 -> p.message("You put the herbs into your ${name(p)}.")
                full -> p.message("Your ${name(p)} cannot hold any more of those herbs.")
                else -> p.message("You don't have any grimy herbs to put in your ${name(p)}.")
            }
        }

        /** HS3: lowest level first, as far as the backpack has room. */
        private fun empty(p: PlayerRef) {
            if (HERBS.none { stored(p, it) > 0 }) return p.message("The herb sack is empty.")
            for (herb in HERBS) {
                val n = minOf(stored(p, herb), p.inv(Invs.INV).freeSlots)
                if (n <= 0 || !p.transaction("herb sack empty") { add(Invs.INV, herb, n) }) continue
                setStored(p, herb, stored(p, herb) - n)
            }
        }

        /** HS4: Wiki "You look in your herb sack and see:" then the herbs (OPEN-HS1: the line format). */
        private fun check(p: PlayerRef) {
            if (HERBS.none { stored(p, it) > 0 }) return p.message("The herb sack is empty.")
            p.message("You look in your ${name(p)} and see:")
            for (herb in HERBS) stored(p, herb).takeIf { it > 0 }?.let { p.message("$it x ${p.objName(herb)}") }
        }

        private fun useOn(p: PlayerRef, obj: Int) {
            when {
                obj in ALL || obj == Objs.SLAYER_SPIDER_SILK -> Unit
                obj in HERBS -> fill(p, listOf(obj))
                else -> p.message("You can't possibly put that in there, it would spoil your sack.")
            }
        }

        private fun swap(p: PlayerRef, click: ButtonClick, to: Int) {
            if (holds(p, click)) p.transaction("herb sack toggle") { replaceAt(Invs.INV, click.slot, click.obj, to) }
        }

        /** HS6 Wiki "Silklined herb sack": pristine spider silk used on the herb sack (the herbs stay). */
        private fun upgrade(p: PlayerRef, from: Int, to: Int) {
            val ok =
                p.transaction("silklined herb sack") {
                    remove(Invs.INV, Objs.SLAYER_SPIDER_SILK, 1) && remove(Invs.INV, from, 1) &&
                        add(Invs.INV, to, 1)
                }
            if (ok) p.message("You line your herb sack with the pristine spider silk.")
        }

        /** HS5: an open sack takes a grimy herb that just came into the backpack while there is room. */
        private fun autoStore(p: PlayerRef, herb: Int) {
            if (OPEN.none { p.inv(Invs.INV).total(it) > 0 }) return
            val n = minOf(p.inv(Invs.INV).total(herb).toInt(), capacity(p) - stored(p, herb))
            if (n <= 0 || !p.transaction("herb sack auto") { remove(Invs.INV, herb, n) }) return
            setStored(p, herb, stored(p, herb) + n)
        }

        /** HS3 Wiki: "with the bank open it will deposit all of the contents into the bank". */
        private fun bankEmpty(op: BankItemOp) {
            op.handled = true
            val p = op.player
            if (HERBS.none { stored(p, it) > 0 }) return p.message("The herb sack is empty.")
            for (herb in HERBS) {
                val n = stored(p, herb)
                if (n >
                    0
                ) {
                    p.events.publish(
                        BankTransfer(p, herb, n, deposit = true, onMoved = { m ->
                            setStored(p, herb, stored(p, herb) - m)
                        }),
                    )
                }
            }
        }

        /** HS7 Wiki: "the herbs inside the herb sack are NOT kept on death when killed by a player": they drop for the killer. */
        private fun pvpDeath(d: PvpDeathStorage) {
            val p = d.player
            if (capacity(p) == 0) return
            val held = HERBS.filter { stored(p, it) > 0 }.map { it to stored(p, it) }
            if (held.isEmpty()) return
            if (p.transaction("herb sack death drop") {
                    held.all { (herb, n) ->
                        (1..n).all { createOnGround(herb, 1, d.at, d.killer) }
                    }
                }
            ) {
                for ((herb, _) in held) setStored(p, herb, 0)
            }
        }
    }
}
