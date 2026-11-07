package tp.content.combat

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Toxic / blazing blowpipe charges (docs/mechanics/gear-effects.md GE60-GE64, Wiki "Toxic blowpipe"): Zulrah's scales
 * and one kind of unpoisoned dart, up to 16,383 each, kept with the player (an item has no state of its own here;
 * the blowpipe only trades when empty). Each shot uses a scale two times in three and a dart like any ammunition
 * (Ava's devices keep theirs, the rest drops or breaks). The loaded darts' ranged strength adds to the blowpipe's.
 */
public class Blowpipe : ContentPlugin {
    override fun ContentScope.register() {
        for ((empty, loaded) in PAIRS) {
            for (obj in intArrayOf(empty, loaded)) {
                onUseOnItem(Objs.SNAKEBOSS_SCALE, obj) { use -> addScales(this, use.toSlot, use.toObj) }
                for (dart in DARTS) onUseOnItem(dart, obj) { addDarts(this, dart) }
            }
            onItemOp(loaded, "Check") { check(this) }
            onItemOp(loaded, "Unload") { unload(this) }
            onItemOp(loaded, "Uncharge") { click -> uncharge(this, click.slot, loaded, empty) }
        }
        // The worn weapon slot's first worn op is "Check" (op 2 on wornitems slot 3).
        onButton(Components.Wornitems.SLOT3, OP_WORN_CHECK) { if (isLoaded(worn(Wear.WEAPON))) check(this) }
    }

    internal companion object {
        /** (empty, loaded) objs: toxic blowpipe and its blazing ornament form. */
        val PAIRS =
            listOf(Objs.TOXIC_BLOWPIPE to Objs.TOXIC_BLOWPIPE_LOADED, Objs.TOXIC_BLOWPIPE_ORNAMENT to Objs.TOXIC_BLOWPIPE_LOADED_ORNAMENT)

        /** Wiki "Toxic blowpipe": "It uses unpoisoned darts as ammunition". */
        val DARTS =
            intArrayOf(
                Objs.BRONZE_DART,
                Objs.IRON_DART,
                Objs.STEEL_DART,
                Objs.BLACK_DART,
                Objs.MITHRIL_DART,
                Objs.ADAMANT_DART,
                Objs.RUNE_DART,
                Objs.AMETHYST_DART,
                Objs.DRAGON_DART,
            )

        /** Wiki: "can hold up to 16,383 scales and 16,383 darts". */
        const val CAPACITY = 16_383L

        /** Wiki: "a 1/3 chance to not use scales when firing". */
        private const val SCALE_SAVE = 3

        private const val OP_WORN_CHECK = 2
        private const val SCALES = "combat.blowpipe_scales"
        private const val DART_OBJ = "combat.blowpipe_dart"
        private const val DART_COUNT = "combat.blowpipe_darts"

        fun isLoaded(obj: Int): Boolean = PAIRS.any { it.second == obj }

        fun isBlowpipe(obj: Int): Boolean = PAIRS.any { it.first == obj || it.second == obj }

        fun scales(p: PlayerRef): Long = p.saved(SCALES) ?: 0L

        fun darts(p: PlayerRef): Long = p.saved(DART_COUNT) ?: 0L

        fun dart(p: PlayerRef): Int = (p.saved(DART_OBJ) ?: -1L).toInt()

        /** The loaded darts' ranged strength while a loaded blowpipe is wielded (else 0). */
        fun dartStrength(p: PlayerRef): Int {
            if (!isLoaded(p.worn(Wear.WEAPON)) || darts(p) <= 0) return 0
            return dart(p).takeIf { it >= 0 }?.let { p.objParam(it, Param.RANGED_STRENGTH, 0) } ?: 0
        }

        /** Spends one shot: false (with the message) when out of scales or darts. */
        fun fire(p: PlayerRef, target: EntityRef): Boolean {
            if (scales(p) <= 0) {
                p.message("Your blowpipe needs to be charged with Zulrah's scales.")
                return false
            }
            if (darts(p) <= 0 || dart(p) < 0) {
                p.message("Your blowpipe has run out of darts.")
                return false
            }
            if (p.random(SCALE_SAVE) != 0) p.setSaved(SCALES, scales(p) - 1)
            val roll = p.random(PERMILLE)
            if (roll < AmmoRecovery.recovery(p)) return true
            val dart = dart(p)
            setDarts(p, dart, darts(p) - 1)
            if (roll < NOT_BROKEN) p.transaction("blowpipe dart") { createOnGround(dart, 1, target.coord, p.username) }
            return true
        }

        private const val PERMILLE = 1000
        private const val NOT_BROKEN = 800

        private fun setDarts(p: PlayerRef, dart: Int, count: Long) {
            p.setSaved(DART_COUNT, count.takeIf { it > 0 })
            p.setSaved(DART_OBJ, if (count > 0) dart.toLong() else null)
        }

        private fun addScales(p: PlayerRef, slot: Int, obj: Int) {
            val room = CAPACITY - scales(p)
            val adding = minOf(room, p.inv(Invs.INV).total(Objs.SNAKEBOSS_SCALE).toLong()).toInt()
            if (adding <= 0) return p.message("Your blowpipe can't hold any more scales.")
            val loaded = PAIRS.firstOrNull { it.first == obj }?.second ?: obj
            val ok =
                p.transaction("charge blowpipe") {
                    remove(Invs.INV, Objs.SNAKEBOSS_SCALE, adding) && (loaded == obj || replaceAt(Invs.INV, slot, obj, loaded))
                }
            if (!ok) return
            p.setSaved(SCALES, scales(p) + adding)
            check(p)
        }

        private fun addDarts(p: PlayerRef, dart: Int) {
            val current = dart(p)
            if (current >= 0 && current != dart && darts(p) > 0) {
                return p.message("Unload the ${p.objName(current).lowercase()}s first.")
            }
            val adding = minOf(CAPACITY - darts(p), p.inv(Invs.INV).total(dart).toLong()).toInt()
            if (adding <= 0) return p.message("Your blowpipe can't hold any more darts.")
            if (!p.transaction("load blowpipe") { remove(Invs.INV, dart, adding) }) return
            setDarts(p, dart, darts(p) + adding)
            check(p)
        }

        /** Wiki "Check": the darts and scales held. */
        fun check(p: PlayerRef) {
            val dart = dart(p)
            val darts = if (dart >= 0 && darts(p) > 0) "${p.objName(dart)} x ${commas(darts(p))}" else "None"
            val percent = scales(p) * PERCENT_TENTHS / CAPACITY
            p.message("Darts: $darts. Scales: ${commas(scales(p))} (${percent / TENTHS}.${percent % TENTHS}%).")
        }

        private fun unload(p: PlayerRef) {
            val dart = dart(p)
            val count = darts(p)
            if (dart < 0 || count <= 0) return p.message("Your blowpipe has no darts in it.")
            if (!p.transaction(
                    "unload blowpipe",
                ) { add(Invs.INV, dart, count.toInt()) }
            ) {
                return p.message("You don't have enough inventory space.")
            }
            setDarts(p, dart, 0)
            check(p)
        }

        private fun uncharge(p: PlayerRef, slot: Int, loaded: Int, empty: Int) {
            val dart = dart(p)
            val darts = darts(p).toInt()
            val scales = scales(p).toInt()
            val ok =
                p.transaction("uncharge blowpipe") {
                    replaceAt(Invs.INV, slot, loaded, empty) &&
                        (scales <= 0 || add(Invs.INV, Objs.SNAKEBOSS_SCALE, scales)) &&
                        (darts <= 0 || dart < 0 || add(Invs.INV, dart, darts))
                }
            if (!ok) return p.message("You don't have enough inventory space.")
            p.setSaved(SCALES, null)
            setDarts(p, dart, 0)
            p.message("You uncharge your blowpipe.")
        }

        /** A PvP death that loses a loaded blowpipe: it drops empty, its scales and darts beside it (Wiki). */
        fun dropOnDeath(p: PlayerRef, invId: Int, slot: Int, at: CoordGrid, owner: String): Boolean {
            val loaded = p.inv(invId).obj(slot)
            val empty = PAIRS.firstOrNull { it.second == loaded }?.first ?: return false
            val dart = dart(p)
            val darts = darts(p).toInt()
            val scales = scales(p).toInt()
            val ok =
                p.transaction("blowpipe death drop") {
                    replaceAt(invId, slot, loaded, empty) &&
                        dropAt(invId, slot, empty, 1, at, owner) &&
                        (scales <= 0 || createOnGround(Objs.SNAKEBOSS_SCALE, scales, at, owner)) &&
                        (darts <= 0 || dart < 0 || createOnGround(dart, darts, at, owner))
                }
            if (ok) {
                p.setSaved(SCALES, null)
                setDarts(p, dart, 0)
            }
            return ok
        }

        private const val PERCENT_TENTHS = 1000L
        private const val TENTHS = 10L

        private fun commas(n: Long): String = "%,d".format(n)
    }
}
