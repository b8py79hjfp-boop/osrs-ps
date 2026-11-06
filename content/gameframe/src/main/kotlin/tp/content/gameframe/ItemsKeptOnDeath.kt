package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import java.util.Locale

/**
 * Worn items tab "Items kept on death" (docs/mechanics/gameframe-buttons.md Q3): the `deathkeep` modal showing what
 * would be kept and lost with the player's current skull and Protect Item state. The rule is the one content/pvp
 * applies on death (wilderness.md W11, P9): the 3 most valuable single items are kept (0 when skulled, +1 with Protect
 * Item), stackables are never kept (OPEN-D1). Panel layout and client script follow RS Mod `ItemsKeptOnDeathScript`
 * (ISC, D-027).
 */
public class ItemsKeptOnDeath : ContentPlugin {
    /** Bit of Protect Item in `prayer_allactive`, found by name in the prayer enum; -1 if absent. */
    private var protectItemBit: Int = -1

    override fun ContentScope.register() {
        onWorldStart {
            protectItemBit =
                enumInts(PRAYER_ENUM)
                    .values
                    .firstOrNull { objParamString(it, PARAM_NAME) == "Protect Item" }
                    ?.let { objParamInt(it, PARAM_BIT) } ?: -1
        }
        onButton(Components.Wornitems.DEATHKEEP) { open() }
    }

    private class Carried(val obj: Int, val count: Int, val value: Int)

    private fun PlayerRef.open() {
        val skulled = this[SharedAttrs.SKULL_ICON] != null
        val protectItem = protectItemBit >= 0 && (varbit(Varbits.PRAYER_ALLACTIVE) ushr protectItemBit) and 1 == 1
        val keepCount = (if (skulled) 0 else KEPT_BASE) + if (protectItem) 1 else 0

        // Backpack first, then worn, most valuable first (stable, like the death drop).
        val carried = ArrayList<Carried>()
        for (invId in intArrayOf(Invs.INV, Invs.WORN)) {
            val inv = inv(invId)
            for (slot in 0 until inv.size) {
                val obj = inv.obj(slot)
                if (obj >= 0) carried += Carried(obj, inv.count(slot), objValue(obj))
            }
        }
        carried.sortByDescending { it.value }
        val kept = carried.filter { it.count == 1 }.take(keepCount)
        val lost = carried.filter { u -> kept.none { it === u } }

        // Display-only inventories: the client script reads lost items from deathkeep_items and each one's fee tier from
        // the count of a stand-in obj in diango_hols_sack. Nothing server-side changes (no transaction, no journal).
        val lostObjs = IntArray(DISPLAY_SLOTS) { lost.getOrNull(it)?.obj ?: -1 }
        val lostCounts = IntArray(DISPLAY_SLOTS) { lost.getOrNull(it)?.count ?: 0 }
        showInv(Invs.DEATHKEEP_ITEMS, lostObjs, lostCounts)
        showInv(
            Invs.DIANGO_HOLS_SACK,
            IntArray(DISPLAY_SLOTS) { if (lost.getOrNull(it) == null) Objs.BURNTFISH1 else Objs.BURNTFISH4 },
            IntArray(DISPLAY_SLOTS) { lost.getOrNull(it)?.let { u -> fee(u.value) + 1 } ?: 1 },
        )
        SideInventory.openMain(this, Interfaces.DEATHKEEP)
        clientScript(
            CS_DEATHKEEP_INIT,
            if (skulled) 1 else 0,
            if (protectItem) 1 else 0,
            WILDERNESS_LEVEL,
            0,
            "",
            kept.size,
            kept.getOrNull(0)?.obj ?: -1,
            kept.getOrNull(1)?.obj ?: -1,
            kept.getOrNull(2)?.obj ?: -1,
            kept.getOrNull(3)?.obj ?: -1,
        )
        // OPEN-Q3a: the four toggle buttons (Protect Item, skull, PK, Wilderness level) are pause buttons, whose
        // RESUME_PAUSEBUTTON the engine does not hand to content, so the view shows the current state only.
        ifEvents(Components.Deathkeep.RIGHT, 0..3, IfEvents.PAUSE_BUTTON)
        val risk = lost.sumOf { it.value.toLong() * it.count }
        ifText(Components.Deathkeep.VALUE, "Guide risk value:<br><col=ffffff>${String.format(Locale.US, "%,d", risk)}</col>")
        ModalWatch.start(this, Interfaces.DEATHKEEP, onClose = {
            showInv(Invs.DEATHKEEP_ITEMS, IntArray(0), IntArray(0))
            showInv(Invs.DIANGO_HOLS_SACK, IntArray(0), IntArray(0))
        })
    }

    /** RS Mod `calculateFee`: stand-in count tiers by item value. */
    private fun fee(value: Int): Int =
        when {
            value < FEE_TIER_1 -> 0
            value < FEE_TIER_2 -> FEE_1
            value < FEE_TIER_3 -> FEE_2
            else -> FEE_3
        }

    private companion object {
        /** Items kept when not skulled (wilderness.md W11). */
        const val KEPT_BASE = 3

        /** Lost-item slots the panel lists (one per carried slot: 28 backpack + 14 worn). */
        const val DISPLAY_SLOTS = 42

        /** Cache enum of prayer objs and their params, as in QuickPrayers / combat-data.md. */
        const val PRAYER_ENUM = 4956
        const val PARAM_BIT = 630
        const val PARAM_NAME = 1752

        /** Client script `deathkeep_init` (RS Mod `deathKeepInit`): skull, protect item, wilderness level, pk, header, kept. */
        const val CS_DEATHKEEP_INIT = 972

        /** OPEN-Q3a: the panel is always opened outside the Wilderness and not as a PK death. */
        const val WILDERNESS_LEVEL = 0

        /** Fee tiers of the stand-in count (RS Mod `calculateFee`). */
        const val FEE_TIER_1 = 100_000
        const val FEE_TIER_2 = 1_000_000
        const val FEE_TIER_3 = 10_000_000
        const val FEE_1 = 1_000
        const val FEE_2 = 10_000
        const val FEE_3 = 100_000
    }
}
