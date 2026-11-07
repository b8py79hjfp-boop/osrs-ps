package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Components.Farming_Tools as FT
import tp.symbols.Components.Farming_Tools_Side as FTS
import tp.symbols.Varbits as VB

/**
 * The tool leprechaun's store (docs/mechanics/farming.md FT1-FT4; Wiki "Tool Leprechaun"): the "Exchange" option opens the cache's
 * farming tools interface (125, with side interface 126 over the backpack), "Deposit-all" empties the backpack's farming tools into
 * the store, and farming produce used on the leprechaun is noted.
 *
 * The counts are saved per tool and mirrored into the tool varbits the interface's client script reads. A count above a varbit's
 * width continues in the "extra" varbit the cache added later (rakes: `FARMING_TOOLS_RAKE` 1 bit, then `FARMING_TOOLS_EXTRARAKES`
 * 6 bits), the extra bits being the high bits of the count (OPEN-FA13: the client script itself is not decoded here).
 */
internal class FarmingTools(private val crops: CropTable, private val chains: SaplingTable) {
    /** One stored tool: the main and side interface components, the item, the limit and the varbits holding the count (low part first). */
    private class Tool(
        val name: String,
        val main: Int,
        val side: Int,
        val item: Int,
        val limit: Int,
        val parts: List<Part>,
    )

    /** A varbit of [bits] bits holding part of a count. */
    private class Part(val varbit: Int, val bits: Int)

    private val tools: List<Tool> =
        listOf(
            tool("rake", FT.RAKE, FTS.RAKE, Objs.RAKE, TOOL_LIMIT, VB.FARMING_TOOLS_RAKE to 1, VB.FARMING_TOOLS_EXTRARAKES to 6),
            tool(
                "dibber",
                FT.DIBBER,
                FTS.DIBBER,
                Objs.DIBBER,
                TOOL_LIMIT,
                VB.FARMING_TOOLS_DIBBER to 1,
                VB.FARMING_TOOLS_EXTRADIBBERS to 6,
            ),
            tool("spade", FT.SPADE, FTS.SPADE, Objs.SPADE, TOOL_LIMIT, VB.FARMING_TOOLS_SPADE to 1, VB.FARMING_TOOLS_EXTRASPADES to 6),
            tool(
                "secateurs",
                FT.SECATEURS,
                FTS.SECATEURS,
                Objs.SECATEURS,
                TOOL_LIMIT,
                VB.FARMING_TOOLS_SECATEURS to 1,
                VB.FARMING_TOOLS_EXTRASECATEURS to 6,
            ),
            tool("watering can", FT.WATERINGCAN, FTS.WATERINGCAN, Objs.WATERING_CAN_0, 1, VB.FARMING_TOOLS_WATERINGCAN to 4),
            tool(
                "gardening trowel",
                FT.TROWEL,
                FTS.TROWEL,
                Objs.GARDENING_TROWEL,
                TOOL_LIMIT,
                VB.FARMING_TOOLS_TROWEL to 1,
                VB.FARMING_TOOLS_EXTRATROWELS to 6,
            ),
            tool("plant cure", FT.PLANTCURE, FTS.PLANTCURE, Objs.PLANT_CURE, SUPPLY_LIMIT, VB.FARMING_TOOLS_PLANTCURE to 10),
            tool(
                "bucket",
                FT.BUCKET,
                FTS.BUCKET,
                Objs.BUCKET_EMPTY,
                SUPPLY_LIMIT,
                VB.FARMING_TOOLS_BUCKETS to 5,
                VB.FARMING_TOOLS_EXTRABUCKETS to 3,
                VB.FARMING_TOOLS_EXTRA2BUCKETS to 2,
            ),
            tool(
                "compost",
                FT.COMPOST,
                FTS.COMPOST,
                Objs.BUCKET_COMPOST,
                SUPPLY_LIMIT,
                VB.FARMING_TOOLS_COMPOST to 8,
                VB.FARMING_TOOLS_EXTRACOMPOST to 2,
            ),
            tool(
                "supercompost",
                FT.SUPERCOMPOST,
                FTS.SUPERCOMPOST,
                Objs.BUCKET_SUPERCOMPOST,
                SUPPLY_LIMIT,
                VB.FARMING_TOOLS_SUPERCOMPOST to 8,
                VB.FARMING_TOOLS_EXTRASUPERCOMPOST to 2,
            ),
            tool(
                "ultracompost",
                FT.ULTRACOMPOST,
                FTS.ULTRACOMPOST,
                Objs.BUCKET_ULTRACOMPOST,
                SUPPLY_LIMIT,
                VB.FARMING_TOOLS_ULTRACOMPOST to 10,
            ),
        )

    fun ContentScope.registerTools() {
        for (npc in FarmingSites.LEPRECHAUNS) {
            onOpNpc(npc, OP_EXCHANGE) { open(player) }
            onOpNpc(npc, OP_DEPOSIT_ALL) { depositAll(player) }
        }
        onModalClosed(Interfaces.FARMING_TOOLS) { restoreBackpack(this) }
        for (tool in tools) {
            onButton(tool.main) { withdraw(this, tool, it) }
            onButton(tool.side) { deposit(this, tool, it) }
        }
        onButton(Components.Farming_Tools.QUANTITY_1) { setVarbit(Varbits.FARMING_TOOLS_SELECTEDQUANTITY, QUANTITY_ONE) }
        onButton(Components.Farming_Tools.QUANTITY_5) { setVarbit(Varbits.FARMING_TOOLS_SELECTEDQUANTITY, QUANTITY_FIVE) }
        onButton(Components.Farming_Tools.QUANTITY_X) { setVarbit(Varbits.FARMING_TOOLS_SELECTEDQUANTITY, QUANTITY_X) }
        onButton(Components.Farming_Tools.QUANTITY_ALL) { setVarbit(Varbits.FARMING_TOOLS_SELECTEDQUANTITY, QUANTITY_ALL) }
        onButton(Components.Farming_Tools.DEPOSIT_ALL) { depositAll(this) }
        onButton(Components.Farming_Tools.DEPOSITINV) { depositAll(this) }
    }

    /** Wiki "Tool Leprechaun": produce, herbs and saplings can be noted; not logs, baskets, sacks, spirit or crystal saplings. */
    fun isNotable(obj: Int): Boolean =
        crops.crops.any { crop ->
            val sapling = crop.isSapling && crop.seed == obj && crop.key != SPIRIT && crop.key != CRYSTAL
            sapling || (crop.type.harvest != Harvest.CHOP && (crop.produce == obj || crop.clean == obj))
        }

    // ---- the interface

    private fun open(p: PlayerRef) {
        for (tool in tools) writeVarbits(p, tool)
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.FARMING_TOOLS)
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.FARMING_TOOLS_SIDE)
        for (tool in tools) {
            p.ifEvents(tool.main, 0..0, MAIN_EVENTS)
            p.ifEvents(tool.side, 0..0, SIDE_EVENTS)
        }
    }

    /** Puts the backpack back in its side slot once the store closes (what gameframe's `SideInventory.restore` does). */
    private fun restoreBackpack(p: PlayerRef) {
        p.openOverlay(Components.Toplevel.SIDE3, Interfaces.INVENTORY)
        p.ifEvents(Components.Inventory.ITEMS, 0..LAST_BACKPACK_SLOT, ITEM_EVENTS)
    }

    private fun selectedQuantity(p: PlayerRef, tool: Tool, available: Int, click: ButtonClick): Int =
        when (click.op) {
            OP_ONE -> {
                1
            }

            OP_FIVE -> {
                FIVE
            }

            OP_TEN -> {
                TEN
            }

            OP_ALL -> {
                available
            }

            else -> {
                when (p.varbit(Varbits.FARMING_TOOLS_SELECTEDQUANTITY)) {
                    QUANTITY_ONE -> 1
                    QUANTITY_FIVE -> FIVE
                    QUANTITY_ALL -> available
                    else -> maxOf(1, minOf(available, tool.limit))
                }
            }
        }

    // ---- store

    private fun count(p: PlayerRef, tool: Tool): Int = (p.saved(KEY + tool.name) ?: 0L).toInt()

    private fun setCount(p: PlayerRef, tool: Tool, value: Int) {
        p.setSaved(KEY + tool.name, value.toLong())
        writeVarbits(p, tool)
    }

    /** A watering can is stored as its dose count plus one (0 = none), in the can's 4-bit varbit. */
    private fun writeVarbits(p: PlayerRef, tool: Tool) {
        var rest = count(p, tool)
        for (part in tool.parts) {
            p.setVarbit(part.varbit, rest and ((1 shl part.bits) - 1))
            rest = rest shr part.bits
        }
    }

    private fun withdraw(p: PlayerRef, tool: Tool, click: ButtonClick) {
        val stored = count(p, tool)
        if (stored == 0) return p.message("You don't have any ${tool.name} stored.")
        val inv = p.inv(Invs.INV)
        if (tool.item == Objs.WATERING_CAN_0) return withdrawCan(p, tool, stored)
        val want = minOf(selectedQuantity(p, tool, stored, click), stored)
        val stackable = p.objStackable(tool.item)
        val room = if (stackable) want else minOf(want, inv.freeSlots)
        if (room <= 0) return p.message("You don't have enough inventory space.")
        if (p.transaction("farming-tools-withdraw") { add(Invs.INV, tool.item, room) }) setCount(p, tool, stored - room)
    }

    private fun withdrawCan(p: PlayerRef, tool: Tool, stored: Int) {
        val can = FarmingItems.CAN_WITH.getOrElse(stored - 1) { Objs.WATERING_CAN_8 }
        if (p.transaction("farming-tools-withdraw") { add(Invs.INV, can, 1) }) setCount(p, tool, 0)
    }

    private fun deposit(p: PlayerRef, tool: Tool, click: ButtonClick) {
        val inv = p.inv(Invs.INV)
        if (tool.item == Objs.WATERING_CAN_0) return depositCan(p, tool)
        val plain = inv.total(tool.item).toInt()
        val noted = inv.total(p.noted(tool.item)).toInt()
        val have = plain + noted
        if (have == 0) return p.message("You don't have any ${tool.name} to store.")
        val room = tool.limit - count(p, tool)
        if (room <= 0) return p.message("The leprechaun can't hold any more ${tool.name}.")
        val want = minOf(selectedQuantity(p, tool, have, click), have, room)
        val fromPlain = minOf(want, plain)
        val ok =
            p.transaction("farming-tools-deposit") {
                (fromPlain == 0 || remove(Invs.INV, tool.item, fromPlain)) &&
                    (want - fromPlain == 0 || remove(Invs.INV, p.noted(tool.item), want - fromPlain))
            }
        if (ok) setCount(p, tool, count(p, tool) + want)
    }

    private fun depositCan(p: PlayerRef, tool: Tool) {
        if (count(p, tool) > 0) return p.message("The leprechaun already has a watering can.")
        val inv = p.inv(Invs.INV)
        val can =
            (FarmingItems.CANS.keys + Objs.WATERING_CAN_0).firstOrNull { inv.total(it) > 0 }
                ?: return p.message("You don't have a watering can to store.")
        val doses = FarmingItems.CANS[can] ?: 0
        if (p.transaction("farming-tools-deposit") { remove(Invs.INV, can, 1) }) setCount(p, tool, doses + 1)
    }

    /** "Deposit-all": every stored kind of tool and supply in the backpack goes into the store. */
    private fun depositAll(p: PlayerRef) {
        for (tool in tools) {
            if (tool.item == Objs.WATERING_CAN_0) {
                depositCan(p, tool)
                continue
            }
            val inv = p.inv(Invs.INV)
            val plain = inv.total(tool.item).toInt()
            val noted = inv.total(p.noted(tool.item)).toInt()
            val room = tool.limit - count(p, tool)
            val want = minOf(plain + noted, room)
            if (want <= 0) continue
            val fromPlain = minOf(want, plain)
            val ok =
                p.transaction("farming-tools-deposit") {
                    (fromPlain == 0 || remove(Invs.INV, tool.item, fromPlain)) &&
                        (want - fromPlain == 0 || remove(Invs.INV, p.noted(tool.item), want - fromPlain))
                }
            if (ok) setCount(p, tool, count(p, tool) + want)
        }
        p.message("The leprechaun takes your farming tools.")
    }

    private companion object {
        /** Wiki "Tool Leprechaun": 100 of each tool, 1,000 of each supply. */
        const val TOOL_LIMIT = 100
        const val SUPPLY_LIMIT = 1000

        /** Cache npc ops of a tool leprechaun: "Exchange" and "Deposit-all". */
        const val OP_EXCHANGE = 3
        const val OP_DEPOSIT_ALL = 4

        /** The interface's item ops (OPEN-FA13): 1 = the selected quantity, 2 / 3 / 4 = one / five / ten, 9 = all. */
        const val OP_ONE = 2
        const val OP_FIVE = 3
        const val OP_TEN = 4
        const val OP_ALL = 9
        const val FIVE = 5
        const val TEN = 10

        /** `FARMING_TOOLS_SELECTEDQUANTITY` (2 bits): the quantity buttons 1, 5, X and All. */
        const val QUANTITY_ONE = 0
        const val QUANTITY_FIVE = 1
        const val QUANTITY_X = 2
        const val QUANTITY_ALL = 3

        const val SPIRIT = "SPIRIT_TREE"
        const val CRYSTAL = "CRYSTAL_TREE"
        const val KEY = "farming.tools."

        /** Client script `toplevel_mainmodal_open` (gameframe `SideInventory`): sets the main modal background; -1 = default. */
        const val CS_MAINMODAL_BACKGROUND = 2524
        const val LAST_BACKPACK_SLOT = 27

        /** Ops 1 to 4, 9 and 10 of the store's tool slots (cache component actions 0..3, 8, 9). */
        val MAIN_EVENTS: Long =
            IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(9) or IfEvents.op(10)

        /** Ops 1 to 5 and 10 of the side interface's slots. */
        val SIDE_EVENTS: Long =
            IfEvents.op(1) or IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(5) or IfEvents.op(10)

        /** The backpack's item targets and ops (gameframe `SideInventory.ITEM_EVENTS`). */
        val ITEM_EVENTS: Long =
            IfEvents.op(2) or IfEvents.op(3) or IfEvents.op(4) or IfEvents.op(6) or IfEvents.op(7) or IfEvents.op(10) or
                IfEvents.TGT_OBJ or IfEvents.TGT_NPC or IfEvents.TGT_LOC or IfEvents.TGT_PLAYER or IfEvents.TGT_INV or
                IfEvents.TGT_COM or IfEvents.DEPTH1 or IfEvents.DRAG_TARGET or IfEvents.TARGET
    }

    private fun tool(name: String, main: Int, side: Int, item: Int, limit: Int, vararg parts: Pair<Int, Int>): Tool =
        Tool(name, main, side, item, limit, parts.map { Part(it.first, it.second) })
}
