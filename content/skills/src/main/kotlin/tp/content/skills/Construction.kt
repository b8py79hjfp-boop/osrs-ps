package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Construction, the part that needs no player-owned house (docs/mechanics/construction.md): the sawmill operator's
 * "Buy-plank" option turns unnoted logs and coins into planks. The house itself (instanced map, build mode, hotspots, rooms,
 * furniture) needs dynamic regions the engine does not have yet (construction.md "Engine gap"); its data tables
 * (`construction-furniture.tsv`, `construction-rooms.tsv`) are checked here at start so a bad import fails early.
 */
public class Construction : ContentPlugin {
    override fun ContentScope.register() {
        check(ConstructionData.furniture().isNotEmpty() && ConstructionData.rooms().isNotEmpty()) { "construction tables are empty" }
        onOpNpc(Npcs.POH_SAWMILL_OPP, OP_BUY_PLANK) { buyPlanks(player) }
    }

    /**
     * Asks which plank and how many (OPEN-CO1: the real "Buy-plank" interface is not named in the cache; this uses the chatbox
     * option menu and number prompt instead).
     */
    private suspend fun PlayerScript.buyPlanks(p: PlayerRef) {
        val planks = ConstructionData.PLANKS
        val first = planks.take(FIRST_PAGE)
        var pick = choice(TITLE, *first.map(::label).toTypedArray(), MORE)
        var page = first
        if (pick == first.size + 1) {
            page = planks.drop(FIRST_PAGE)
            pick = choice(TITLE, *page.map(::label).toTypedArray())
        }
        val type = page.getOrNull(pick - 1) ?: return
        p.stopInteraction()
        p.countDialog("Enter amount:") { amount -> make(this, type, amount) }
    }

    private fun label(t: PlankType): String = "${t.name} (${t.cost} gp)"

    /**
     * Makes up to [amount] planks: as many as the player holds unnoted logs and can pay for (Wiki "Sawmill operator":
     * unnoted logs only, a fee per plank). Logs, coins and planks move in one transaction.
     */
    private fun make(p: PlayerRef, type: PlankType, amount: Int) {
        val inv = p.inv(Invs.INV)
        val logs = inv.total(type.log)
        if (amount <= 0) return
        if (logs == 0L) return p.message("You need ${p.objName(type.log).lowercase()} to make ${p.objName(type.plank).lowercase()}s.")
        val affordable = inv.total(Objs.COINS) / type.cost
        if (affordable == 0L) return p.message("You need ${type.cost} coins to make a ${p.objName(type.plank).lowercase()}.")
        val count = minOf(amount.toLong(), logs, affordable).toInt()
        val done =
            p.transaction("sawmill") {
                remove(Invs.INV, Objs.COINS, count * type.cost) && remove(Invs.INV, type.log, count) && add(Invs.INV, type.plank, count)
            }
        if (done) p.message("The sawmill operator makes you $count ${p.objName(type.plank).lowercase()}${if (count == 1) "" else "s"}.")
    }

    private companion object {
        /** Op 3 of the sawmill operator npc (cache 2757: op1 "Talk-to", op3 "Buy-plank", op4 "Trade"). */
        const val OP_BUY_PLANK = 3

        /** The option menu holds five entries: four planks and "More...". */
        const val FIRST_PAGE = 4
        const val TITLE = "What kind of planks do you want?"
        const val MORE = "More..."
    }
}
