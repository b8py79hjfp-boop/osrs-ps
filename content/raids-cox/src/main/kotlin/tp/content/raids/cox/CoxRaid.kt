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
            contribution = ContributionRules(normalisedRooms = false)
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_XERICCHAMBERS,
                    kcMessage = { "Your completed Chambers of Xeric count is: <col=ff0000>$it</col>." },
                    // CX53 (OPEN-CX29): the team size line is added by the records driver (milestone 2).
                    timeMessage = { t, pb, newBest ->
                        val tail =
                            if (newBest ||
                                pb == null
                            ) {
                                " (new personal best)</col>"
                            } else {
                                " Personal best: </col><col=ff0000>$pb</col>"
                            }
                        "<col=ef20ff>Congratulations - your raid is complete!</col><br>Duration:</col> <col=ff0000>$t</col>$tail"
                    },
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
