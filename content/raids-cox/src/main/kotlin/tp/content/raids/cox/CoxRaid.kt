package tp.content.raids.cox

import tp.content.raids.ContributionRules
import tp.content.raids.DeathPolicy
import tp.content.raids.DisconnectPolicy
import tp.content.raids.RaidDefinition
import tp.content.raids.RaidDriver
import tp.content.raids.RecordsDef
import tp.content.raids.RoomDef
import tp.content.raids.RoomKind
import tp.content.raids.raid
import tp.symbols.Invs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The Chambers of Xeric as a raid definition (docs/mechanics/raids/cox.md CX1-CX59; docs/plans/raids-cox.md). Each run
 * builds its own instance from a generated layout ([CoxLayout]): framework room `lobby` (the raid's start room, before
 * the leader starts), `chambers` (both generated floors, an encounter; the rooms inside are [CoxCell]s driven by
 * [CoxFlow]), `olm` and `rewards`.
 */
internal object CoxRaid {
    const val ID = "cox"
    const val NORMAL = "normal"
    const val CHALLENGE = "challenge"
    const val LOBBY = "lobby"
    const val CHAMBERS = "chambers"
    const val OLM = "olm"
    const val REWARDS = "rewards"

    /** CX1: 1-100 players. */
    const val MAX_PARTY = 100

    /** Mount Quidamortem, just south of the entrance steps 29777 (1232, 3573, 4 x 1) (free tile, collision). */
    val OUTSIDE = CoordGrid(1233, 3571)

    /** The entrance steps (client-contract 7 loc table). */
    val ENTRANCE = CoordGrid(1232, 3573)

    /** Olm square template tiles (client-contract 6.6): the antechamber by the rope 29996 (3232, 5718). */
    val OLM_ENTRY = CoordGrid(3233, 5721)

    /** By the reward crystal 30027 (3233, 5751) north of the arena. */
    val REWARD_ENTRY = CoordGrid(3233, 5753)

    /** CX54 / OPEN-CX30 (the brief's buckets): Solo, 2-10, 11-15, 16-23, 24+. */
    fun pbBucket(size: Int): String =
        when {
            size <= 1 -> "solo"
            size <= BUCKET_SMALL -> "2-10"
            size <= BUCKET_MID -> "11-15"
            size <= BUCKET_LARGE -> "16-23"
            else -> "24+"
        }

    /** CX53: the team size as the completion line writes it (RL's pattern: `Solo`, `N players`, `A-B players`, `N+ players`). */
    fun teamSize(size: Int): String =
        when {
            size <= 1 -> "Solo"
            size <= BUCKET_SMALL -> "$size players"
            size <= BUCKET_MID -> "11-15 players"
            size <= BUCKET_LARGE -> "16-23 players"
            else -> "24+ players"
        }

    /** CX53 (exact string, stray `</col>` tags included; OPEN-CX29). */
    fun completion(team: String, time: String, pb: String?, newBest: Boolean): String {
        val tail = if (newBest || pb == null) " (new personal best)</col>" else " Personal best: </col><col=ff0000>$pb</col>"
        return "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>$team</col> Duration:</col> " +
            "<col=ff0000>$time</col>$tail"
    }

    /** Raid times as `m:ss` (`h:mm:ss` past an hour), `m:ss.cc` with the precise-timing option 11866 (CX53). */
    fun formatTime(p: tp.plugin.PlayerRef, ticks: Long): String {
        val precise = p.varbit(tp.symbols.Varbits.OPTION_PRECISE_TIMING) == 1
        val centis = ticks * CENTIS_PER_TICK
        val totalSeconds = centis / CENTIS
        val h = totalSeconds / SECONDS_PER_HOUR
        val m = (totalSeconds / SECONDS_PER_MINUTE) % SECONDS_PER_MINUTE
        val sec = totalSeconds % SECONDS_PER_MINUTE
        val base = if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
        return if (precise) base + ".%02d".format(centis % CENTIS) else base
    }

    private const val BUCKET_SMALL = 10
    private const val BUCKET_MID = 15
    private const val BUCKET_LARGE = 23
    private const val CENTIS_PER_TICK = 60L
    private const val CENTIS = 100L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3600L

    /** Room ids are not template tiles here: every entry comes from [RoomDef.entryAt]. */
    private val NOWHERE = CoordGrid(3296, 5184)

    val DEFINITION: RaidDefinition =
        raid(ID) {
            name = "Chambers of Xeric"
            partySize = 1..MAX_PARTY
            modes = listOf(NORMAL, CHALLENGE)
            layout = { run -> CoxFlow.build(run) }
            exit = OUTSIDE
            // CX33 / CX34: every death is safe; the player respawns on the previous floor (OPEN-CX17).
            deaths = DeathPolicy.RespawnAt { run, p -> CoxFlow.respawn(run, p) }
            // CX36: a logout is not a death (OPEN-CX19: the Olm-room logout penalty).
            disconnect = DisconnectPolicy.REJOIN
            claimInv = Invs.RAIDS_REWARDS
            // CX35 / OPEN-CX18: raid-made items never leave the Chambers.
            raidOnlyItems = CoxItems.RAID_ONLY
            // CX17-CX19 / CX17a: party size, the highest combat and Hitpoints levels, Challenge Mode.
            scaling = { ctx, npc -> CoxScaling.apply(ctx, npc) }
            // CX21-CX22: damage adds points directly; CX30: a death costs 40%.
            contribution =
                ContributionRules(
                    normalisedRooms = false,
                    multiplier = { type -> CoxPoints.multiplier(type) },
                    deathPenalty = { m, run -> CoxPoints.deathPenalty(m, run) },
                )
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_XERICCHAMBERS,
                    kcMessage = { "Your completed Chambers of Xeric count is: <col=ff0000>$it</col>." },
                    timeMessage = { t, pb, newBest -> completion("Solo", t, pb, newBest) },
                    pbKey = "raids.cox.pb",
                    kcVarpByMode =
                        mapOf(
                            NORMAL to Varps.TOTAL_COMPLETED_XERICCHAMBERS,
                            CHALLENGE to Varps.TOTAL_COMPLETED_XERICCHAMBERS_CHALLENGE,
                        ),
                    // CX53: the CM line is assumed to name Challenge Mode.
                    kcMessageByMode = { mode, kc ->
                        if (mode == CHALLENGE) {
                            "Your completed Chambers of Xeric Challenge Mode count is: <col=ff0000>$kc</col>."
                        } else {
                            "Your completed Chambers of Xeric count is: <col=ff0000>$kc</col>."
                        }
                    },
                    pbBucket = { size -> pbBucket(size) },
                    teamTimeMessage = { size, t, pb, newBest -> completion(teamSize(size), t, pb, newBest) },
                    formatTime = { p, ticks -> formatTime(p, ticks) },
                )
            driver = RaidDriver { run -> CoxFlow.sync(run) }
            autoAdvance = false
            room(
                RoomDef(LOBBY, RoomKind.CHOICE, NOWHERE, entryAt = {
                    run,
                    any,
                    ->
                    CoxFlow.lobbyEntry(run, any)
                }, onStart = { run, any -> CoxFlow.lobbyStarted(run, any) }),
            )
            room(RoomDef(CHAMBERS, RoomKind.ENCOUNTER, NOWHERE, entryAt = { run, any -> CoxFlow.firstRoomEntry(run, any) }, gather = false))
            room(RoomDef(OLM, RoomKind.ENCOUNTER, NOWHERE, entryAt = { run, any -> CoxFlow.olmEntry(run, any) }, gather = false))
            room(
                RoomDef(REWARDS, RoomKind.REWARD, NOWHERE, entryAt = {
                    run,
                    any,
                    ->
                    CoxFlow.olmTile(run, any, REWARD_ENTRY)
                }, gather = false),
            )
            slot(LOBBY)
            slot(CHAMBERS)
            slot(OLM)
            slot(REWARDS)
        }
}
