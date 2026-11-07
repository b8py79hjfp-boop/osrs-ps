package tp.content.sailing

import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Objs

/**
 * Rewards of a completed port task and the opening of coin bags (docs/mechanics/sailing-port-tasks.md PT7). A courier task
 * pays a port coin bag 4 times in 5 and a port reward bag of its destination 1 time in 5, the bag size set by the task's
 * base XP; a bounty task pays coins equal to its base XP; either has a 1 in 36 chance of one shark paint.
 */
internal object PortRewards {
    /** Base XP at which the bag size rises to small, medium, large and huge (Wiki Courier tasks: 400 / 1,000 / 2,500 / 6,000). */
    private val BAG_TIER_XP = intArrayOf(400, 1000, 2500, 6000)

    /** A courier reward is a reward bag 1 time in 5 and a coin bag otherwise (Wiki Courier tasks). */
    private const val REWARD_BAG_ODDS = 5

    /** Shark paint comes with 1 in 36 completed port tasks (Wiki Shark paint, news of 3 December 2025). */
    private const val SHARK_PAINT_ODDS = 36

    /** Colour of the shark paint message (Wiki Shark paint: purple). */
    private const val PAINT_MESSAGE = "<col=800080>You've received some paint!</col>"

    /** Obj param of the coin bags: the average coins in the bag (cache `PORT_TASK_LOOTSACK_T*_COINS`, 1,000 .. 8,000). */
    private const val PARAM_COINS = 2647

    /** Coin bags hold their average +/- 20% (Wiki Port coin bag: tiny 800-1,200 around the cache's 1,000, and so on). */
    private const val SPREAD_PERCENT = 20
    private const val PERCENT = 100

    /** Obj id of coins. */
    private const val COINS = 995

    /** Ids of an obj tier apart: the placeholder obj sits between two bag tiers. */
    private const val TIER_STEP = 2

    /** Size tier 0 (tiny) .. 4 (huge) of the bags for a task of base XP [xp]. */
    fun tier(xp: Int): Int = BAG_TIER_XP.count { xp >= it }

    fun coinBag(tier: Int): Int = Objs.PORT_TASK_LOOTSACK_T0_COINS + tier * TIER_STEP

    /** Every coin bag obj, tiny to huge. */
    val COIN_BAGS: List<Int> = (0 until BAG_TIER_XP.size + 1).map { coinBag(it) }

    /** Pays the rewards of completed [task] (base XP [xp]) to [p]; what does not fit the backpack lands at [p]'s feet. */
    fun grant(p: PlayerRef, task: PortTask, xp: Int, sites: PortSites) {
        p.transaction("port task reward") {
            if (task.isBounty) {
                xp <= 0 || addOrDrop(Invs.INV, COINS, xp)
            } else {
                val bag = sites.rewardBag(task.endingDock)?.takeIf { p.random(REWARD_BAG_ODDS) == 0 }?.plus(tier(xp) * TIER_STEP)
                addOrDrop(Invs.INV, bag ?: coinBag(tier(xp)), 1)
            }
        }
        if (p.random(SHARK_PAINT_ODDS) == 0 && p.transaction("port task paint") { addOrDrop(Invs.INV, Objs.SAILING_PAINT_SHARK, 1) }) {
            p.message(PAINT_MESSAGE)
        }
    }

    /** The coin bags' "Open" option: the bag becomes its coins, +/- [SPREAD_PERCENT] around the cache average. */
    fun ContentScope.registerBags() {
        for (bag in COIN_BAGS) {
            onItemOp(bag, "Open") { click ->
                val average = objParam(bag, PARAM_COINS, 0)
                val low = average * (PERCENT - SPREAD_PERCENT) / PERCENT
                val high = average * (PERCENT + SPREAD_PERCENT) / PERCENT
                val coins = low + random(high - low + 1)
                if (inv(Invs.INV).obj(click.slot) == bag && coins > 0) {
                    transaction("port coin bag") { removeAt(Invs.INV, click.slot, bag, 1) && addOrDrop(Invs.INV, COINS, coins) }
                }
            }
        }
    }
}
