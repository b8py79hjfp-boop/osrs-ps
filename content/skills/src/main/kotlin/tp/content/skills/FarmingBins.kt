package tp.content.skills

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Compost bins (docs/mechanics/farming.md FB1-FB6; Wiki "Compost bin"): a bin takes un-noted farming produce one item at a time
 * until it holds 15 (30 in the Farming Guild's giant bin), is closed, rots for 35 to 70 minutes into compost, supercompost
 * (every item supercompostable) or rotten tomatoes (every item a tomato), is opened and emptied one empty bucket at a time. An
 * opened bin of compost takes a compost potion dose to become supercompost, an opened bin of supercompost volcanic ash to become
 * ultracompost.
 *
 * A bin is a saved Long ([Bin.stateKey]): the number of items (or buckets left), the mode, the kind of compost and two flags that
 * record whether every item so far was supercompostable / a tomato. The minute the bin was closed is saved beside it.
 */
internal class FarmingBins(private val patches: FarmingPatches) {
    private val crops get() = patches.crops

    /** Compostable items by kind (1 compost, 2 supercompost), from the crops' produce and herbs (Wiki "Compost bin#Mechanics"). */
    private val kinds: Map<Int, Int> by lazy { compostable() }

    fun ContentScope.registerBins() {
        for (site in FarmingSites.SITES) {
            for (bin in site.bins) {
                onOpLoc(bin.loc, OP_PRIMARY) { primary(player, bin) }
                onOpLoc(bin.loc, OP_DUMP) { dump(player, bin) }
                onUseOnLoc(ANY_ITEM, bin.loc) { useOn(player, bin, used?.obj ?: ANY_ITEM) }
            }
        }
    }

    /** Draws every bin of [site] (the bins share the patches' redraw schedule). */
    fun draw(p: PlayerRef, site: Site) {
        for (bin in site.bins) p.setVarbit(bin.slot, value(p, bin, load(p, bin)))
    }

    // ---- state

    /** The state of [bin] with its rot brought up to date. */
    private fun load(p: PlayerRef, bin: Bin): Long {
        val saved = p.saved(bin.stateKey) ?: return EMPTY_STATE
        if (mode(saved) == ROTTING && p.epochMinutes >= readyAt(p.saved(bin.timeKey) ?: p.epochMinutes)) {
            val ready = saved withMode READY
            p.setSaved(bin.stateKey, ready)
            return ready
        }
        return saved
    }

    private fun store(p: PlayerRef, bin: Bin, state: Long) {
        p.setSaved(bin.stateKey, state)
        val site = patches.sync(p).site
        if (site >= 0 && FarmingSites.SITES[site].bins.any { it === bin }) p.setVarbit(bin.slot, value(p, bin, state))
    }

    // ---- clicks

    /** Op 1: close a full bin, open a rotted one, take from an open one. */
    private fun primary(p: PlayerRef, bin: Bin) {
        val state = load(p, bin)
        when (mode(state)) {
            FILLING -> if (count(state) >= capacity(bin)) close(p, bin, state) else p.message("The bin isn't full yet.")
            ROTTING -> p.message("The compost is still rotting.")
            READY -> store(p, bin, state withMode OPEN)
            OPEN -> take(p, bin)
            else -> p.message(NOTHING)
        }
    }

    /** F-B3: closing starts the rot clock; the product is fixed by what was put in. */
    private fun close(p: PlayerRef, bin: Bin, state: Long) {
        val kind =
            if (allTomato(state)) {
                TOMATO
            } else if (allSuper(state)) {
                SUPER
            } else {
                COMPOST
            }
        p.setSaved(bin.timeKey, p.epochMinutes)
        store(p, bin, pack(count(state), ROTTING, kind, allSuper = false, allTomato = false))
        p.message("You close the compost bin.")
    }

    /** Op 5: dump the contents. */
    private fun dump(p: PlayerRef, bin: Bin) {
        val state = load(p, bin)
        if (mode(state) == EMPTY || mode(state) == ROTTING) return p.message(if (mode(state) == ROTTING) "The bin is closed." else NOTHING)
        store(p, bin, EMPTY_STATE)
        p.message("You empty the compost bin.")
    }

    /** F-B2 / F-B5: an item used on the bin fills it, or upgrades an opened bin. */
    private fun useOn(p: PlayerRef, bin: Bin, obj: Int) {
        val state = load(p, bin)
        val mode = mode(state)
        when {
            obj == ANY_ITEM -> {
                p.message(NOTHING)
            }

            mode == OPEN && obj in FarmingItems.COMPOST_POTIONS -> {
                upgradeWithPotion(p, bin, state, obj)
            }

            mode == OPEN && obj == Objs.FOSSIL_VOLCANIC_ASH -> {
                upgradeWithAsh(p, bin, state)
            }

            mode == ROTTING || mode == READY || mode == OPEN -> {
                p.message("You can't add that to the bin right now.")
            }

            else -> {
                fill(p, bin, obj)
            }
        }
    }

    /** F-B2: puts every un-noted compostable item of the kind of [obj] from the backpack into the bin, one every [FILL_TICKS] ticks. */
    private fun fill(p: PlayerRef, bin: Bin, obj: Int) {
        val kind = kinds[obj] ?: if (obj == Objs.WEEDS) COMPOST else 0
        if (kind == 0 || p.unnoted(obj) != obj) return p.message(NOTHING)
        Production.start(
            p,
            Batch(Int.MAX_VALUE, -1, FILL_TICKS) { fillOnce(this, bin, obj, kind) },
            FILL_TICKS,
        )
    }

    private fun fillOnce(p: PlayerRef, bin: Bin, obj: Int, kind: Int): Boolean {
        val state = load(p, bin)
        if (mode(state) > FILLING) return false
        val count = count(state)
        if (count >= capacity(bin)) {
            p.message("The compost bin is full.")
            return false
        }
        if (p.inv(Invs.INV).total(obj) == 0L) return false
        if (!p.transaction("farming-bin-fill") { remove(Invs.INV, obj, 1) }) return false
        val superOnly = (count == 0 || allSuper(state)) && kind == SUPER
        val tomatoOnly = (count == 0 || allTomato(state)) && obj == TOMATO_OBJ
        store(p, bin, pack(count + 1, FILLING, COMPOST, superOnly, tomatoOnly))
        return count + 1 < capacity(bin)
    }

    /** F-B6: takes the product into empty buckets (rotten tomatoes need none); the bin is empty again when the last one is out. */
    private fun take(p: PlayerRef, bin: Bin) {
        val state = load(p, bin)
        val kind = kind(state)
        if (kind != TOMATO && p.inv(Invs.INV).total(Objs.BUCKET_EMPTY) == 0L) return p.message("You need an empty bucket to take that.")
        Production.start(p, Batch(Int.MAX_VALUE, -1, FILL_TICKS) { takeOnce(this, bin) }, FILL_TICKS)
    }

    private fun takeOnce(p: PlayerRef, bin: Bin): Boolean {
        val state = load(p, bin)
        if (mode(state) != OPEN) return false
        val kind = kind(state)
        val done =
            if (kind == TOMATO) {
                p.transaction("farming-bin-take") { add(Invs.INV, Objs.ROTTEN_TOMATO, 1) }
            } else {
                p.transaction("farming-bin-take") {
                    remove(Invs.INV, Objs.BUCKET_EMPTY, 1) &&
                        add(Invs.INV, FarmingItems.BUCKET_OF[if (kind == ULTRA) ULTRA else kind + 1], 1)
                }
            }
        if (!done) return false
        val left = count(state) - 1
        store(p, bin, if (left == 0) EMPTY_STATE else pack(left, OPEN, kind, allSuper = false, allTomato = false))
        return left > 0
    }

    /** Wiki "Compost bin#Upgrading": one compost potion dose turns the opened bin of compost into supercompost. */
    private fun upgradeWithPotion(p: PlayerRef, bin: Bin, state: Long, potion: Int) {
        if (kind(state) != COMPOST) return p.message(NOTHING)
        val doses = FarmingItems.COMPOST_POTIONS.getValue(potion)
        val next = if (doses == 1) Objs.VIAL_EMPTY else potion + POTION_STEP
        if (!p.transaction("farming-bin-potion") { remove(Invs.INV, potion, 1) && add(Invs.INV, next, 1) }) return
        store(p, bin, pack(count(state), OPEN, SUPER, allSuper = false, allTomato = false))
        p.message("You pour the potion over the compost.")
    }

    /** Wiki "Compost bin#Upgrading": 25 volcanic ash (50 for the giant bin) turn an opened bin of supercompost into ultracompost. */
    private fun upgradeWithAsh(p: PlayerRef, bin: Bin, state: Long) {
        if (kind(state) != SUPER) return p.message(NOTHING)
        val need = if (bin.big) ASH_BIG else ASH_REGULAR
        if (p.inv(Invs.INV).total(Objs.FOSSIL_VOLCANIC_ASH) < need) return p.message("You need $need volcanic ash to do that.")
        if (!p.transaction("farming-bin-ash") { remove(Invs.INV, Objs.FOSSIL_VOLCANIC_ASH, need) }) return
        store(p, bin, pack(count(state), OPEN, ULTRA, allSuper = false, allTomato = false))
        p.message("You sprinkle the ash over the compost.")
    }

    // ---- packing

    private infix fun Long.withMode(mode: Int): Long = (this and MODE_CLEAR) or (mode.toLong() shl MODE_SHIFT)

    private fun mode(state: Long): Int = ((state shr MODE_SHIFT) and MODE_MASK).toInt()

    private fun count(state: Long): Int = (state and COUNT_MASK).toInt()

    private fun kind(state: Long): Int = ((state shr KIND_SHIFT) and KIND_MASK).toInt()

    private fun allSuper(state: Long): Boolean = (state shr SUPER_SHIFT) and 1L == 1L

    private fun allTomato(state: Long): Boolean = (state shr TOMATO_SHIFT) and 1L == 1L

    private fun pack(count: Int, mode: Int, kind: Int, allSuper: Boolean, allTomato: Boolean): Long =
        count.toLong() or
            (mode.toLong() shl MODE_SHIFT) or
            (kind.toLong() shl KIND_SHIFT) or
            ((if (allSuper) 1L else 0L) shl SUPER_SHIFT) or
            ((if (allTomato) 1L else 0L) shl TOMATO_SHIFT)

    private fun capacity(bin: Bin): Int = if (bin.big) BIG_CAPACITY else CAPACITY

    /** The minute a bin closed at [closed] can be opened: the second 35-minute tick after (Wiki: "35 to 70 minutes"). */
    private fun readyAt(closed: Long): Long = (closed / ROT_TICK_MIN + 2) * ROT_TICK_MIN

    // ---- drawing

    /**
     * The varbit value that draws [state] on [bin] (RuneLite `PatchImplementation.COMPOST` / `BIG_COMPOST`, BSD-2, D-044). A rotting
     * bin shows its first stage until the first 35-minute tick has passed.
     */
    private fun value(p: PlayerRef, bin: Bin, state: Long): Int {
        val n = count(state)
        val big = bin.big
        return when (mode(state)) {
            EMPTY -> {
                0
            }

            FILLING -> {
                // the kind is not fixed yet: the look follows the contents (tomatoes, supercompostables, anything else)
                val kind =
                    if (allTomato(state)) {
                        TOMATO
                    } else if (allSuper(state)) {
                        SUPER
                    } else {
                        COMPOST
                    }
                filling(kind, n, big)
            }

            ROTTING, READY -> {
                rotting(bin, kind(state), mode(state) == READY, p.saved(bin.timeKey) ?: p.epochMinutes, p.epochMinutes)
            }

            else -> {
                taking(kind(state), n, big)
            }
        }
    }

    private fun filling(kind: Int, n: Int, big: Boolean): Int =
        when (kind) {
            COMPOST -> if (n <= REGULAR_RANGE || !big) n else n + COMPOST_FILL_BIG
            SUPER -> if (n <= REGULAR_RANGE || !big) n + SUPER_FILL else n + SUPER_FILL_BIG
            else -> if (n <= REGULAR_RANGE || !big) n + TOMATO_FILL else n + TOMATO_FILL_BIG
        }

    private fun taking(kind: Int, n: Int, big: Boolean): Int =
        when (kind) {
            COMPOST -> if (n <= REGULAR_RANGE || !big) n + COMPOST_TAKE else n + COMPOST_TAKE_BIG
            SUPER -> if (n <= REGULAR_RANGE || !big) n + SUPER_TAKE else n + SUPER_TAKE_BIG
            TOMATO -> if (n <= REGULAR_RANGE || !big) n + TOMATO_TAKE else n + TOMATO_TAKE_BIG
            else -> n + ULTRA_TAKE
        }

    private fun rotting(bin: Bin, kind: Int, ready: Boolean, closed: Long, now: Long): Int {
        val big = bin.big
        if (ready) {
            return when (kind) {
                COMPOST -> if (big) READY_COMPOST_BIG else READY_COMPOST
                SUPER -> if (big) READY_SUPER_BIG else READY_SUPER
                else -> if (big) READY_TOMATO_BIG else READY_TOMATO
            }
        }
        val second = if (now >= (closed / ROT_TICK_MIN + 1) * ROT_TICK_MIN) 1 else 0
        return second +
            when (kind) {
                COMPOST -> if (big) ROT_COMPOST_BIG else ROT_COMPOST
                SUPER -> if (big) ROT_SUPER_BIG else ROT_SUPER
                else -> ROT_TOMATO
            }
    }

    // ---- what can be composted

    /** F-B2: obj -> 1 (compost) or 2 (supercompost) for the produce and herbs of the crops (Wiki "Compost bin#Mechanics"). */
    private fun compostable(): Map<Int, Int> {
        val map = HashMap<Int, Int>()
        for (crop in crops.crops) {
            val type = crop.type
            val never =
                type.harvest == Harvest.CHOP || crop.key == CACTUS_KEY || type == PatchType.BELLADONNA || type == PatchType.CRYSTAL_TREE ||
                    (crop.produce < 0 && crop.clean < 0)
            if (never) continue
            val isSuper = (crop.level >= SUPER_LEVEL && crop.key !in NORMAL_ABOVE_47) || crop.key in SUPER_BELOW_47
            val kind = if (isSuper) SUPER else COMPOST
            if (crop.produce >= 0) map[crop.produce] = kind
            if (crop.clean >= 0) map[crop.clean] = kind
        }
        return map
    }

    private companion object {
        const val OP_PRIMARY = 1
        const val OP_DUMP = 5
        const val ANY_ITEM = -1
        const val NOTHING = "Nothing interesting happens."

        /** Wiki "Compost bin": 15 items fill a bin, 30 the Farming Guild's giant bin. */
        const val CAPACITY = 15
        const val BIG_CAPACITY = 30

        /** Wiki "Compost bin#Upgrading": 25 volcanic ash for a regular bin, 50 for the giant bin. */
        const val ASH_REGULAR = 25
        const val ASH_BIG = 50

        /** Wiki "Compost bin#Filling": rotting ends 35 to 70 minutes after closing: two 35-minute ticks (OPEN-FA12: RuneLite has 40). */
        const val ROT_TICK_MIN = 35L

        /** OPEN-FA12: ticks between two items going into (or buckets out of) a bin; not published. */
        const val FILL_TICKS = 2

        /** Wiki "Compost bin#Mechanics": a product is supercompostable if growing it needs Farming 47 or more ... */
        const val SUPER_LEVEL = 47

        /** ... except potato cactus and cotton boll (normal compost) ... */
        val NORMAL_ABOVE_47: Set<String> = setOf("POTATO_CACTUS", "COTTON")

        /** ... and toadflax and giant seaweed, which are supercompostable below 47. */
        val SUPER_BELOW_47: Set<String> = setOf("TOADFLAX", "SEAWEED")

        const val CACTUS_KEY = "CACTUS"

        /** The tomato item (crop TOMATO's produce); a bin of only tomatoes makes rotten tomatoes. */
        const val TOMATO_OBJ = 1982

        /** Compost potion(n) is followed by potion(n-1) one id up in the cache (6470 (4) .. 6476 (1)): subtracting a dose is +2. */
        const val POTION_STEP = 2

        const val EMPTY_STATE = 0L

        // modes
        const val EMPTY = 0
        const val FILLING = 1
        const val ROTTING = 2
        const val READY = 3
        const val OPEN = 4

        // kinds (the kind of product; 3 = ultracompost only after an upgrade)
        const val COMPOST = 0
        const val SUPER = 1
        const val TOMATO = 2
        const val ULTRA = 3

        const val COUNT_MASK = 0x1FL
        const val MODE_SHIFT = 5
        const val MODE_MASK = 0x7L
        const val MODE_CLEAR = (0x7L shl MODE_SHIFT).inv()
        const val KIND_SHIFT = 8
        const val KIND_MASK = 0x3L
        const val SUPER_SHIFT = 10
        const val TOMATO_SHIFT = 11

        // varbit values (RuneLite `PatchImplementation.COMPOST` / `BIG_COMPOST`: value = base + item count)
        const val REGULAR_RANGE = 15
        const val SUPER_FILL = 32
        const val TOMATO_FILL = 128
        const val COMPOST_FILL_BIG = 47
        const val SUPER_FILL_BIG = 145
        const val TOMATO_FILL_BIG = 207
        const val COMPOST_TAKE = 15
        const val SUPER_TAKE = 47
        const val TOMATO_TAKE = 143
        const val ULTRA_TAKE = 175
        const val COMPOST_TAKE_BIG = 62
        const val SUPER_TAKE_BIG = 84
        const val TOMATO_TAKE_BIG = 191
        const val ROT_COMPOST = 31
        const val ROT_SUPER = 95
        const val ROT_TOMATO = 159
        const val ROT_COMPOST_BIG = 127
        const val ROT_SUPER_BIG = 97
        const val READY_COMPOST = 94
        const val READY_SUPER = 126
        const val READY_TOMATO = 160
        const val READY_COMPOST_BIG = 93
        const val READY_SUPER_BIG = 99
        const val READY_TOMATO_BIG = 222
    }
}
