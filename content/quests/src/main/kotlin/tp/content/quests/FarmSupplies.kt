package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpcAs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits

/**
 * The ingredient sources of Cook's Assistant and Sheep Shearer (docs/mechanics/quests.md QU10): milking a dairy cow, picking
 * wheat, the windmill (hopper, hopper controls, flour bin) and shearing sheep. The Wiki gives the rules; the message wording
 * is not published (OPEN-QU4).
 */
public class FarmSupplies : ContentPlugin {
    override fun ContentScope.register() {
        for (cow in DAIRY_COWS) {
            onOpLoc(cow, OP_FIRST) {
                player.stopInteraction()
                milk()
            }
            onUseOnLoc(Objs.BUCKET_EMPTY, cow) {
                player.stopInteraction()
                milk()
            }
        }
        for (wheat in WHEAT) {
            onOpLoc(wheat, OP_SECOND) {
                player.stopInteraction()
                pick()
            }
        }
        for (hopper in HOPPERS) {
            onOpLoc(hopper, OP_FIRST) {
                player.stopInteraction()
                fill()
            }
            onUseOnLoc(Objs.GRAIN, hopper) {
                player.stopInteraction()
                fill()
            }
        }
        for (controls in HOPPER_CONTROLS) {
            onOpLoc(controls, OP_FIRST) {
                player.stopInteraction()
                operate()
            }
        }
        for (bin in FLOUR_BINS) {
            onOpLoc(bin, OP_FIRST) {
                player.stopInteraction()
                collect()
            }
            onUseOnLoc(Objs.POT_EMPTY, bin) {
                player.stopInteraction()
                collect()
            }
        }
        for ((whole, sheared) in UNSHEARED_TO_SHEARED) {
            onOpNpc(whole, OP_FIRST) { sheep ->
                player.stopInteraction()
                shear(sheep, sheared)
            }
            onUseOnNpc(Objs.SHEARS, whole) { sheep ->
                player.stopInteraction()
                shear(sheep, sheared)
            }
        }
        onOpNpc(Npcs.SHEEP_SHEARER_THE_THING, OP_FIRST) {
            player.stopInteraction()
            theThing()
        }
        onUseOnNpc(Objs.SHEARS, Npcs.SHEEP_SHEARER_THE_THING) {
            player.stopInteraction()
            theThing()
        }
    }

    // ---- milking

    private suspend fun PlayerScript.milk() {
        val p = player
        if (!p.has(Objs.BUCKET_EMPTY)) {
            // Wiki "Transcript:Gillie Groats", "Attempting to milk a dairy cow without a bucket".
            val speaker = "Gillie Groats the Milkmaid"
            chatNpcAs(Npcs.GILLIE_THE_MILKMAID, speaker, "Tee hee! You've never milked a cow before, have you?")
            me("Erm... No. How could you tell?")
            chatNpcAs(
                Npcs.GILLIE_THE_MILKMAID,
                speaker,
                "Because you're spilling milk all over the floor. What a waste! You need something to hold the milk.",
            )
            me("Ah yes, I really should have guessed that one, shouldn't I?")
            chatNpcAs(Npcs.GILLIE_THE_MILKMAID, speaker, "You're from the city, aren't you... Try it again with an empty bucket.")
            me("Right, I'll do that.")
            return
        }
        p.anim(Seqs.HUMAN_PICKUPFLOOR)
        delay(1)
        if (p.transaction("milk a dairy cow") { remove(INV, Objs.BUCKET_EMPTY, 1) && add(INV, Objs.BUCKET_MILK, 1) }) {
            p.message("You milk the cow.")
        }
    }

    // ---- wheat and the windmill

    private suspend fun PlayerScript.pick() {
        val p = player
        p.anim(Seqs.HUMAN_PICKUPFLOOR)
        delay(1)
        // OPEN-QU14: the picked wheat's replacement loc and its respawn (Wiki "Wheat": respawn 20) are not modelled.
        if (p.give(
                Objs.GRAIN,
                1,
                "pick wheat",
            )
        ) {
            p.message("You pick some wheat.")
        } else {
            p.message("You don't have enough inventory space.")
        }
    }

    private fun PlayerScript.fill() {
        val p = player
        if (!p.has(Objs.GRAIN)) return p.message("You need some grain to put in the hopper.")
        if (p.flag(HOPPER_KEY)) return p.message("There is already grain in the hopper.")
        if (p.take(Objs.GRAIN, 1, "fill hopper")) {
            p.setFlag(HOPPER_KEY, true)
            p.message("You put the grain in the hopper. You should now pull the lever nearby to operate the hopper.")
        }
    }

    private fun PlayerScript.operate() {
        val p = player
        p.anim(Seqs.HUMAN_LEVERDOWN)
        if (!p.flag(HOPPER_KEY)) return p.message("You operate the hopper. The hopper is empty.")
        val bin = p.varbit(Varbits.MILL_SHOWFLOUR)
        if (bin >= BIN_CAPACITY) return p.message("The flour bin is full.")
        p.setFlag(HOPPER_KEY, false)
        p.setVarbit(Varbits.MILL_SHOWFLOUR, bin + 1)
        p.message("You operate the hopper. The grain slides down the chute.")
    }

    private fun PlayerScript.collect() {
        val p = player
        val bin = p.varbit(Varbits.MILL_SHOWFLOUR)
        if (bin <= 0) return p.message("The flour bin is empty.")
        if (!p.has(Objs.POT_EMPTY)) return p.message("I need an empty pot so I can collect my flour.")
        if (p.transaction("collect flour") { remove(INV, Objs.POT_EMPTY, 1) && add(INV, Objs.POT_FLOUR, 1) }) {
            p.setVarbit(Varbits.MILL_SHOWFLOUR, bin - 1)
            p.message("You fill a pot with flour from the bin.")
        }
    }

    // ---- sheep

    private suspend fun PlayerScript.shear(sheep: NpcRef, sheared: Int) {
        val p = player
        if (!p.has(Objs.SHEARS)) return p.message("You need a set of shears to do this.")
        val whole = sheep.type
        p.anim(Seqs.HUMAN_SHEARING)
        sheep.face(p)
        delay(1)
        // Wiki "Sheep": 100% success since 3 November 2022; the wool grows back after 50 ticks.
        if (!p.give(Objs.WOOL, 1, "shear a sheep")) return p.message("You don't have enough inventory space.")
        p.message("You get some wool.")
        sheep.transform(sheared, restoreStats = false)
        sheep.queueHit(REGROW_TICKS, strong = false) { if (sheep.isInWorld) sheep.transform(whole, restoreStats = false) }
    }

    private fun PlayerScript.theThing() {
        player.setFlag(QuestKeys.SAW_THE_THING, true)
        player.message("The... whatever it is... manages to get away from you!")
    }

    internal companion object {
        private const val OP_FIRST = 1
        private const val OP_SECOND = 2
        private const val INV = tp.symbols.Invs.INV

        /** Dairy cow scenery ids (Wiki "Dairy cow" infobox). */
        private val DAIRY_COWS = intArrayOf(8689, 60788)

        /** Wheat scenery ids with the "Pick" op (cache locs 313, 15506-15508; Lumbridge's field is 15506). */
        private val WHEAT = intArrayOf(Locs.WHEAT, 15506, 15507, 15508)
        private val HOPPERS = intArrayOf(Locs.HOPPER, Locs.HOPPER1, Locs.HOPPER3, Locs.HOPPER4)
        private val HOPPER_CONTROLS = intArrayOf(Locs.HOPPERLEVERS1, Locs.HOPPERLEVERS3, Locs.HOPPERLEVERS4)

        /** The flour bin (loc 1781 is the multi-loc the map places, 1782 its filled form; both can be clicked). */
        private val FLOUR_BINS = intArrayOf(Locs.MILLBASE, Locs.MILLBASE_FLOUR)

        /** The grain waiting in the hopper (one at a time: Wiki "Windmill"). */
        private const val HOPPER_KEY = "quests.mill.hopper"

        /** A flour bin holds 30 pots (Wiki "Windmill"). */
        private const val BIN_CAPACITY = 30

        /** Wool grows back after 50 ticks (Wiki "Sheep"). */
        private const val REGROW_TICKS = 50

        /** Unsheared sheep NPC → the sheared one it turns into (the inverse of [QuestSpawns.SHEARED_TO_UNSHEARED]). */
        private val UNSHEARED_TO_SHEARED: Map<Int, Int> by lazy { QuestSpawns.SHEARED_TO_UNSHEARED.entries.associate { (k, v) -> v to k } }
    }
}

/** Account flags shared between quest plugins. */
internal object QuestKeys {
    /** The player tried to shear The Thing in Fred's pen (Sheep Shearer transcript "After interacting with The Thing"). */
    const val SAW_THE_THING = "quests.sheep.thing"
}
