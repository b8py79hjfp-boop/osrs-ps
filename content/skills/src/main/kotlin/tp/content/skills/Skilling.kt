package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * Shared skilling helpers (docs/mechanics/skilling.md). A repeating skill action is an op script that keeps its
 * interaction ([tp.plugin.PlayerScript.keepInteraction]) so it runs again every tick while the player stays, the
 * way OSRS re-triggers `p_oploc` each tick (RS Mod `opLoc3`). [SkillClock] carries the per-player action and
 * animation clocks across those runs.
 */
internal object Skilling {
    /** Chat type of filterable game messages ("spam", RS Mod `ChatType.Spam`). */
    const val SPAM = 105

    /** Cache obj param `levelrequire`: the skill level a tool needs (axes, pickaxes, harpoons). */
    const val PARAM_LEVEL_REQUIRE = 23

    /** Worn-inventory slot of the weapon (cache wear position 3). */
    const val WEAPON_SLOT = 3

    private val CLOCK = AttrKey<SkillClock>("skilling.clock")

    /** A [SkillClock.lastRun] that no tick follows. */
    const val NEVER = -2L

    /** Mask of a packed tile in an action key. */
    private const val LOW_INT_MASK = 0xFFFFFFFFL

    /**
     * The skill clock of [player] for action [key] (what is being worked, e.g. the loc's packed tile and id). A run
     * that is not the next tick of the same action (a new click, another target, or a gap) is [SkillClock.fresh] and
     * restarts [SkillClock.stage]; the action and animation clocks are the player's and carry over (RS Mod keeps
     * `actionDelay` / `skillAnimDelay` on the player, so a re-click while one runs skips the arming tick).
     */
    fun clock(player: PlayerRef, key: Long): SkillClock {
        val tick = player.tick
        val clock = player[CLOCK] ?: SkillClock().also { player[CLOCK] = it }
        clock.fresh = clock.key != key || clock.lastRun != tick - 1
        if (clock.fresh) {
            clock.key = key
            clock.stage = 0
        }
        clock.lastRun = tick
        return clock
    }

    /** Ends [player]'s running action (inventory full, level too low, resource gone); the clocks keep running. */
    fun stop(player: PlayerRef) {
        player[CLOCK]?.let { it.lastRun = NEVER }
    }

    /**
     * The best tool [player] can use: the worn weapon or any backpack obj in [tools] (obj -> level needed) whose level
     * [stat] meets, preferring the higher requirement (RS Mod `findAxe`: worn wins a tie).
     */
    fun bestTool(player: PlayerRef, stat: Int, tools: Map<Int, Int>): Int {
        val level = player.level(stat)
        var best = -1
        var bestReq = -1
        val worn = player.inv(Invs.WORN).obj(WEAPON_SLOT)
        tools[worn]?.let { req -> if (req <= level) best = worn.also { bestReq = req } }
        val inv = player.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            val obj = inv.obj(slot)
            val req = tools[obj] ?: continue
            if (req <= level && req > bestReq) {
                best = obj
                bestReq = req
            }
        }
        return best
    }

    /** Packs a loc's tile and id into an action key. */
    fun locKey(id: Int, packedCoord: Int): Long = (id.toLong() shl Int.SIZE_BITS) or (packedCoord.toLong() and LOW_INT_MASK)
}

/** Per-player clocks of the running skill action, in world ticks (RS Mod `actionDelay` / `skillAnimDelay`). */
internal class SkillClock {
    var key: Long = 0
    var lastRun: Long = Skilling.NEVER
    var fresh: Boolean = true
    var actionDelay: Long = -1
    var animDelay: Long = -1

    /** Skill-specific progress of the action (woodcutting: 1 once the first swing played). */
    var stage: Int = 0
}
