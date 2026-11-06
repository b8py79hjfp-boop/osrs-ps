package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.Examined
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Invs

/**
 * Examine (docs/mechanics/examine.md EX1–EX3): texts from the OSRS Wiki infoboxes (`examines.tsv`,
 * tools/wiki-import/examines.py), sent as the client's examine chat types; "a stack of 100,000 or more items ... will
 * instead state the exact number of items in the stack, e.g. 111,237 x Coins."
 */
public class Examines : ContentPlugin {
    override fun ContentScope.register() {
        onExamine { e ->
            when (e.kind) {
                Examined.Kind.LOC -> message(text(LOCS, e.id) ?: fallback(locName(e.id)), TYPE_LOC)
                Examined.Kind.NPC -> message(text(NPCS, e.id) ?: fallback(npcName(e.id)), TYPE_NPC)
                Examined.Kind.OBJ -> item(this, e.id, 1)
            }
        }
        onButton(Components.Inventory.ITEMS, OP_EXAMINE) { click ->
            val inv = inv(Invs.INV)
            if (click.slot in 0 until inv.size && inv.obj(click.slot) == click.obj) item(this, click.obj, inv.count(click.slot))
        }
        WORN.forEachIndexed { i, component ->
            onButton(component, OP_EXAMINE) {
                val obj = inv(Invs.WORN).obj(WORN_SLOTS[i])
                if (obj >= 0) item(this, obj, inv(Invs.WORN).count(WORN_SLOTS[i]))
            }
        }
    }

    internal companion object {
        private const val OP_EXAMINE = 10

        /** Client chat message types (RuneLite `ChatMessageType` ITEM_EXAMINE / NPC_EXAMINE / OBJECT_EXAMINE). */
        private const val TYPE_ITEM = 27
        private const val TYPE_NPC = 28
        private const val TYPE_LOC = 29

        /** EX2: stacks of this size or more show their count instead. */
        private const val BIG_STACK = 100_000

        private val ITEMS = HashMap<Int, String>()
        private val NPCS = HashMap<Int, String>()
        private val LOCS = HashMap<Int, String>()

        init {
            val text =
                Examines::class.java
                    .getResourceAsStream("examines.tsv")
                    ?.bufferedReader()
                    ?.readLines()
                    .orEmpty()
            for (line in text.drop(1)) {
                val f = line.split('\t', limit = 3)
                if (f.size < 3) continue
                val map =
                    when (f[0]) {
                        "obj" -> ITEMS
                        "npc" -> NPCS
                        else -> LOCS
                    }
                map[f[1].toInt()] = f[2]
            }
        }

        private fun text(map: Map<Int, String>, id: Int): String? = map[id]

        /** OPEN-EX1: no Wiki text — the cache name, so the player still gets an answer. */
        private fun fallback(
            name: String,
        ): String = "It's ${if (name.first().lowercaseChar() in "aeiou") "an" else "a"} ${name.lowercase()}."

        /** EX1/EX2: item examine; noted items use their unnoted text. */
        fun item(player: PlayerRef, obj: Int, count: Int) {
            val base = player.unnoted(obj)
            val text =
                if (count >= BIG_STACK) {
                    "%,d x %s.".format(count, player.objName(base))
                } else {
                    ITEMS[base] ?: ITEMS[obj] ?: fallback(player.objName(base))
                }
            player.message(text, TYPE_ITEM)
        }

        /** Worn tab slot components and their worn slots (gameframe equipment layout). */
        private val WORN =
            listOf(
                Components.Wornitems.SLOT0,
                Components.Wornitems.SLOT1,
                Components.Wornitems.SLOT2,
                Components.Wornitems.SLOT3,
                Components.Wornitems.SLOT4,
                Components.Wornitems.SLOT5,
                Components.Wornitems.SLOT7,
                Components.Wornitems.SLOT9,
                Components.Wornitems.SLOT10,
                Components.Wornitems.SLOT12,
                Components.Wornitems.SLOT13,
            )

        @Suppress("MagicNumber") // worn slot numbers
        private val WORN_SLOTS = listOf(0, 1, 2, 3, 4, 5, 7, 9, 10, 12, 13)
    }
}
