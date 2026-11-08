package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * The Great Olm's room (olm.md; the fight is content/combat `CoxOlm`): the first raider through the mystical barrier wakes
 * Olm (head and claws spawn in the Olm square, tagged with its frame); its death shatters the exit crystal, completes the
 * raid (the time ends there, cox.md CX50 / CX53) and opens the reward chamber. Challenge Mode inside the target time adds
 * the CX32 bonus.
 */
internal object CoxOlmRoom {
    fun register(scope: ContentScope) {
        with(scope) {
            onOpNpc(Npcs.OLM_FIREWALL_NPC, OP1) { fire -> douse(player, fire) }
        }
    }

    /** The barrier was passed into the arena: the fight starts once. */
    fun entered(run: RaidRun, any: EntityRef) {
        val st = CoxFlow.state(run) ?: return
        if (st.olmStarted || (run.state as? RunState.InRoom)?.room?.id != CoxRaid.OLM) return
        st.olmStarted = true
        val frame = RaidRoomFrame(CoxMap.olm(run, SQUARE_SW), 0, SQUARE, SQUARE_SW)
        for ((type, at) in listOf(
            Npcs.OLM_HEAD_SPAWNING to HEAD,
            Npcs.OLM_HAND_LEFT_SPAWNING to LEFT,
            Npcs.OLM_HAND_RIGHT_SPAWNING to RIGHT,
        )) {
            val npc = Raids.spawnAt(any, run, type, CoxMap.olm(run, at))
            npc[SharedAttrs.RAID_ROOM] = frame
        }
    }

    /** The head died: the raid is complete. */
    fun olmDied(run: RaidRun, head: NpcRef) {
        val st = CoxFlow.state(run) ?: return
        if (st.olmDeadTick >= 0) return
        st.olmDeadTick = head.tick
        // Strat: the head collapses and the exit crystal shatters.
        head.deleteLoc(CoxMap.olm(run, EXIT_CRYSTAL), CENTREPIECE)
        val ticks = CoxHud.elapsed(run, st, head.tick).toLong()
        if (run.scaling.mode == CoxRaid.CHALLENGE && ticks <= targetTicks(run.scaling.partySize)) {
            // CX32 / CX50: inside the target time, 5,000 points per player to the team; the kit and dust rolls follow it.
            CoxPoints.challengeBonus(run)
            run.extraSettings[IN_TIME] = 1
        }
        run.extraSettings[TEAM_POINTS] = CoxPoints.team(run).toInt()
        if ((run.state as? RunState.InRoom)?.room?.id == CoxRaid.OLM) Raids.roomCleared(head, run)
        Raids.advance(head, run, CoxRaid.REWARDS)
        Raids.complete(head, run, endedAt = head.tick)
    }

    /** CX50: the Challenge Mode target time by party size (1 1:10, 2 1:05, 3 0:50, 4 0:45, 5-10 0:42, 11-15 0:45, 16-23 1:00, 24+ 1:20). */
    fun targetTicks(size: Int): Long {
        val minutes =
            when {
                size <= 1 -> SOLO_MINUTES
                size == 2 -> DUO_MINUTES
                size == 3 -> TRIO_MINUTES
                size == 4 -> FOUR_MINUTES
                size <= 10 -> SMALL_MINUTES
                size <= 15 -> MID_MINUTES
                size <= 23 -> LARGE_MINUTES
                else -> HUGE_MINUTES
            }
        return minutes * TICKS_PER_MINUTE
    }

    /**
     * OL11: a fire-wall segment is doused by a raider who can cast a water or ice spell or carries a filled gourd vial or
     * watering can (OPEN-OL12: the spell check is by runes carried).
     */
    private fun douse(player: PlayerRef, fire: NpcRef) {
        val inv = player.inv(Invs.INV)
        val can =
            (0 until inv.size).any {
                val n = player.objName(inv.obj(it)).lowercase()
                n.startsWith("water-filled") || n.startsWith("watering can(") || n == "water rune" || n == "mist rune" || n == "mud rune" ||
                    n == "steam rune"
            }
        if (!can) {
            player.message(NEED_WATER)
            return
        }
        fire.despawn()
    }

    /** Template tiles of the Olm square (client-contract 6.6). */
    private val SQUARE_SW = CoordGrid(3200, 5696)
    private val HEAD = CoordGrid(3220, 5738)
    private val LEFT = CoordGrid(3220, 5743)
    private val RIGHT = CoordGrid(3220, 5733)
    private val EXIT_CRYSTAL = CoordGrid(3232, 5749)
    private const val SQUARE = 64
    private const val CENTREPIECE = 10
    private const val OP1 = 1
    private const val TICKS_PER_MINUTE = 100L
    private const val SOLO_MINUTES = 70L
    private const val DUO_MINUTES = 65L
    private const val TRIO_MINUTES = 50L
    private const val FOUR_MINUTES = 45L
    private const val SMALL_MINUTES = 42L
    private const val MID_MINUTES = 45L
    private const val LARGE_MINUTES = 60L
    private const val HUGE_MINUTES = 80L

    /** Reward snapshot settings (the loot model reads them). */
    const val IN_TIME = "cox_cm_in_time"
    const val TEAM_POINTS = "cox_team_points"
    private const val NEED_WATER = "You need a way to make water to douse the fire."
}
