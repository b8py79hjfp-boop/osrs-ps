package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs

/** One bone of `bonemeal.tsv` (tools/wiki-import/ectofuntus_bonemeal.py): the pot of [meal] it grinds to and the [worshipXp] it gives. */
internal class Bonemeal(val bone: Int, val meal: Int, val worshipXp: Double)

/**
 * The Ectofuntus (docs/mechanics/prayer-training.md PT4-PT6): a bone used on the Loader of the bone grinder becomes a pot of
 * bonemeal for each empty pot held, a bucket used on the Pool of Slime is filled, "Worship" on the Ectofuntus turns one bonemeal
 * and one bucket of slime into the bone's worship xp (4 times the burying xp) and 5 unclaimed ecto-tokens, and a ghost disciple
 * pays the tokens out to a player who can speak with ghosts. The grinder's wheel and bin and the slime pool's fill pace are
 * collapsed into timed loops (OPEN-PT7).
 */
public class Ectofuntus : ContentPlugin {
    override fun ContentScope.register() {
        val table = loadTable()
        val byMeal = table.associateBy { it.meal }
        for (bone in table) onUseOnLoc(bone.bone, LOADER) { grind(bone) }
        for (pool in SLIME_POOLS) onUseOnLoc(Objs.BUCKET_EMPTY, pool) { fillBuckets() }
        onOpLoc(ECTOFUNTUS, OP_WORSHIP) { worship(byMeal) }
        onOpNpc(GHOST_DISCIPLE, OP_TALK) { npc -> claim(npc) }
    }

    private fun loadTable(): List<Bonemeal> {
        val text = Ectofuntus::class.java.getResource("bonemeal.tsv")?.readText() ?: error("bonemeal.tsv missing")
        return text
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val cols = line.split('\t')
                require(cols.size == COLUMNS) { "bonemeal.tsv row has ${cols.size} columns: $cols" }
                Bonemeal(cols[0].toInt(), cols[1].toInt(), cols[4].toDouble())
            }.toList()
    }

    /** PT4: grinds the bones of one kind, one per [GRIND_TICKS], while the player holds an empty pot for each. */
    private suspend fun PlayerScript.grind(bone: Bonemeal) {
        val p = player
        p.stopInteraction()
        var first = true
        while (true) {
            val ground =
                p.transaction("ectofuntus grinder") {
                    remove(Invs.INV, bone.bone, 1) && remove(Invs.INV, POT, 1) && add(Invs.INV, bone.meal, 1)
                }
            if (!ground) {
                if (first) p.message("You need an empty pot to collect the bonemeal.")
                return
            }
            first = false
            delay(GRIND_TICKS)
        }
    }

    /** PT4: fills the buckets one per [FILL_TICKS] (Wiki "Ectofuntus": it "will automatically fill all the buckets in your inventory"). */
    private suspend fun PlayerScript.fillBuckets() {
        val p = player
        p.stopInteraction()
        while (p.transaction("ectofuntus slime") { remove(Invs.INV, Objs.BUCKET_EMPTY, 1) && add(Invs.INV, BUCKET_OF_SLIME, 1) }) {
            delay(FILL_TICKS)
        }
    }

    /** PT5: worships with every bonemeal and bucket of slime the player holds, one set per [WORSHIP_TICKS]. */
    private suspend fun PlayerScript.worship(byMeal: Map<Int, Bonemeal>) {
        val p = player
        p.stopInteraction()
        while (true) {
            val meal = firstMeal(p, byMeal)
            if (meal == null || p.inv(Invs.INV).total(BUCKET_OF_SLIME) == 0L) {
                p.message("You need a pot of bonemeal and a bucket of slime to worship the Ectofuntus.")
                return
            }
            val owed = p.saved(TOKENS_KEY) ?: 0L
            if (owed + TOKENS_PER_WORSHIP > TOKEN_CAP) {
                p.message("There isn't room to put any more in.")
                return
            }
            val used =
                p.transaction("ectofuntus worship") {
                    remove(Invs.INV, meal.meal, 1) && remove(Invs.INV, BUCKET_OF_SLIME, 1) &&
                        add(Invs.INV, POT, 1) && add(Invs.INV, Objs.BUCKET_EMPTY, 1)
                }
            if (!used) return
            p.setSaved(TOKENS_KEY, owed + TOKENS_PER_WORSHIP)
            p.addXp(Stats.PRAYER, meal.worshipXp)
            delay(WORSHIP_TICKS)
        }
    }

    private fun firstMeal(p: PlayerRef, byMeal: Map<Int, Bonemeal>): Bonemeal? {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) byMeal[inv.obj(slot)]?.let { return it }
        return null
    }

    /** PT6: a ghost disciple pays out the owed ecto-tokens to a player wearing a ghostspeak amulet or Morytania legs 2 or better. */
    private fun PlayerScript.claim(npc: NpcRef) {
        val p = player
        p.stopInteraction()
        p.face(npc)
        val worn = p.inv(Invs.WORN)
        val hears = worn.obj(AMULET_SLOT) in GHOSTSPEAK_AMULETS || worn.obj(LEGS_SLOT) in MORYTANIA_LEGS
        if (!hears) return p.message("You cannot understand what the ghost is saying.")
        val owed = p.saved(TOKENS_KEY) ?: 0L
        if (owed <= 0L) return p.message("The ghost disciple has no ecto-tokens for you.")
        if (!p.transaction(
                "ecto-tokens",
            ) { add(Invs.INV, ECTO_TOKEN, owed.toInt()) }
        ) {
            return p.message("You don't have enough inventory space.")
        }
        p.setSaved(TOKENS_KEY, null)
    }

    private companion object {
        const val COLUMNS = 5

        /** Cache locs: the Ectofuntus (op 1 "Worship"), the bone grinder's Loader, the four Pool of Slime locs. */
        const val ECTOFUNTUS = 16648
        const val LOADER = 16654
        val SLIME_POOLS = intArrayOf(17116, 17117, 17118, 17119)
        const val OP_WORSHIP = 1

        /** Cache npc "Ghost disciple" (op 1 "Talk-to"). */
        const val GHOST_DISCIPLE = 2988
        const val OP_TALK = 1

        /** Cache objs: "Pot" 1931, "Bucket of slime" 4286, "Ecto-token" 4278. */
        const val POT = 1931
        const val BUCKET_OF_SLIME = 4286
        const val ECTO_TOKEN = 4278

        /** Wiki "Ectofuntus": 5 ecto-tokens per worship; at 1000 unclaimed "There isn't room to put any more in.". */
        const val TOKENS_PER_WORSHIP = 5L
        const val TOKEN_CAP = 1000L
        const val TOKENS_KEY = "ectofuntus.tokens"

        /** Ghostspeak amulet (cache objs 552 and 4250) and "Morytania legs 2" to "4" (13113..13115), Wiki "Ectofuntus". */
        val GHOSTSPEAK_AMULETS = setOf(552, 4250)
        val MORYTANIA_LEGS = setOf(13113, 13114, 13115)
        const val AMULET_SLOT = 2
        const val LEGS_SLOT = 7

        /** OPEN-PT7: the Wiki gives no pace for the grinder, the pool or the worship, so each step takes this many ticks. */
        const val GRIND_TICKS = 3
        const val FILL_TICKS = 2
        const val WORSHIP_TICKS = 3
    }
}
