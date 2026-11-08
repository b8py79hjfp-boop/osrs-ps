package tp.content.raids.cox

import tp.content.raids.RaidEvent
import tp.content.raids.Raids
import tp.plugin.AttrKey
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps

/** One scoreboard line: a Challenge Mode time in ticks and the team that set it. */
internal class CoxScoreEntry(val ticks: Long, val names: String)

/** This world's best Challenge Mode times, per team-size bracket, fastest first (CX51; OPEN-CR1: no persistence). */
internal class CoxScoreWorld {
    val brackets: Array<MutableList<CoxScoreEntry>> = Array(CoxScoreboard.BRACKETS.size) { ArrayList() }

    companion object {
        private val KEY = AttrKey<CoxScoreWorld>("cox_scores_world")

        fun of(any: EntityRef): CoxScoreWorld = any.worldAttr(KEY) ?: CoxScoreWorld().also { any.setWorldAttr(KEY, it) }

        fun of(world: WorldRef): CoxScoreWorld = world.worldAttr(KEY) ?: CoxScoreWorld().also { world.setWorldAttr(KEY, it) }
    }
}

/**
 * The Challenge Mode scoreboard outside the entrance (cox.md CX51; client-contract 2.8) and Captain Rimor's capes. The
 * times are kept per world in world-scoped state, so they do not survive a restart (no persistence layer change; OPEN-CR1).
 */
internal object CoxScoreboard {
    /**
     * The team-size upper bounds of the 13 brackets (client-contract 2.8: enum 1915; labels in enum 1914: Solo, 2 players ...
     * 10 players, 11-15, 16-23, 24+).
     */
    val BRACKETS: IntArray = intArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 23, 100)

    /** The bracket index of a team of [size]. */
    fun bracketOf(size: Int): Int = BRACKETS.indexOfFirst { size <= it }.let { if (it < 0) BRACKETS.size - 1 else it }

    /** A Challenge Mode raid is complete: its time goes on the board (the whole team that finished it shares the line). */
    fun completed(world: WorldRef, e: RaidEvent.Completed) {
        val run = e.run
        if (run.scaling.mode != CoxRaid.CHALLENGE) return
        val st = CoxFlow.state(run) ?: return
        if (!st.begun || st.olmDeadTick < 0) return
        val ticks = (st.olmDeadTick - st.begunTick).coerceAtLeast(0)
        val names =
            run.members.values
                .filter { it.inside }
                .joinToString(", ") { it.name }
        // CX50 / OPEN-CX28: the bracket is the party size at the start.
        val list = CoxScoreWorld.of(world).brackets[bracketOf(run.scaling.partySize)]
        list += CoxScoreEntry(ticks, names)
        list.sortBy { it.ticks }
        while (list.size > LINES) list.removeLast()
    }

    /** "Read" on the score board 32544: the bracket of the viewer's party size, filled by the server script 2292. */
    fun open(p: PlayerRef) {
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.RAIDS_CHALLENGE)
        val size =
            Raids
                .of(p)
                .partyOf(p.username)
                ?.members
                ?.size ?: 1
        val index = bracketOf(size)
        val list = CoxScoreWorld.of(p).brackets[index]
        val args = ArrayList<Any>(SCRIPT_ARGS)
        // OPEN-CXUI9: global best = this world's best (no hub service); the unit is ticks (script 2401 is not read to the end).
        args += (list.firstOrNull()?.ticks ?: 0L).toInt()
        args += CoxOlmRoom.targetTicks(BRACKETS[index]).toInt()
        for (line in 0 until LINES) args += (list.getOrNull(line)?.ticks ?: 0L).toInt()
        args += (0 until LINES).joinToString("|") { list.getOrNull(it)?.names.orEmpty() }
        p.clientScript(SCRIPT_SCORES, *args.toTypedArray())
    }

    // ---- Captain Rimor's capes

    /**
     * "Claim-cape" (op3 of NPC 7595): gives the highest cape the player's Challenge Mode completions earn, if not already
     * owned. Returns the line Rimor says.
     */
    fun claim(p: PlayerRef): String {
        val kc = p.varp(Varps.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE)
        val tier = THRESHOLDS.indexOfLast { kc >= it }
        if (tier < 0) return NOT_ENOUGH
        val cape = CAPES[tier]
        if (owns(p, cape)) return ALREADY_HAVE
        // OPEN-CXUI10: the Rimor page gives no price for the claim, so it is free.
        return if (p.transaction("cox claim cape") { add(Invs.INV, cape) }) HERE_YOU_GO else NO_SPACE
    }

    private fun owns(p: PlayerRef, cape: Int): Boolean =
        p.inv(Invs.INV).total(cape) > 0 || p.inv(Invs.WORN).total(cape) > 0 || p.inv(Invs.BANK).total(cape) > 0

    // ---- constants

    /** Lines of one bracket (client-contract 2.8: five time lines, 21:16). */
    private const val LINES = 5

    /** Script 2292's arguments: global, target, five times, the teams string (client-contract 2.8). */
    private const val SCRIPT_ARGS = 8
    private const val SCRIPT_SCORES = 2292

    /** CX51: Challenge Mode completions for Xeric's guard, warrior, sentinel, general and champion. */
    private val THRESHOLDS = intArrayOf(100, 500, 1000, 1500, 2000)

    /** The capes in tier order (`COX_CHALLENGE_CAPE_T1..T5`, objs 22388 .. 22396 in steps of 2: the odd ids are placeholders). */
    private val CAPES =
        intArrayOf(
            Objs.COX_CHALLENGE_CAPE_T1,
            Objs.COX_CHALLENGE_CAPE_T2,
            Objs.COX_CHALLENGE_CAPE_T3,
            Objs.COX_CHALLENGE_CAPE_T4,
            Objs.COX_CHALLENGE_CAPE_T5,
        )

    // OPEN-CXUI1: the dialogue is not captured.
    private const val NOT_ENOUGH = "You have not completed enough Challenge Mode raids to earn a cape yet."
    private const val ALREADY_HAVE = "You already have the cape you have earned."
    private const val HERE_YOU_GO = "Here is the cape you have earned. Wear it with pride."
    private const val NO_SPACE = "You have no room to carry it. Come back with a free space."
}

/** Captain Rimor's "Claim-cape" dialogue. */
internal suspend fun PlayerScript.claimCape(rimor: NpcRef) {
    chatNpc(rimor, CoxScoreboard.claim(player))
}
