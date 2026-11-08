package tp.content.raids.tob

import tp.content.raids.ContributionRules
import tp.content.raids.DeathPolicy
import tp.content.raids.DisconnectPolicy
import tp.content.raids.RaidDefinition
import tp.content.raids.RaidDriver
import tp.content.raids.RecordsDef
import tp.content.raids.RoomDef
import tp.content.raids.RoomKind
import tp.content.raids.WipePolicy
import tp.content.raids.raid
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * The Theatre of Blood as a raid definition (docs/mechanics/raids/tob.md TB1-TB35; map from the cache,
 * tob/client-contract.md section 5). Coordinates are template (real map) tiles; Ver Sinhaza is the real, shared map.
 * The hallways between rooms are not in the cache (client-contract 8.8): a room's exit box moves the party to the next
 * room's entry (OPEN-TB7, DECISIONS D-081).
 */
internal object TobRaid {
    const val ID = "tob"
    const val ENTRY = "entry"
    const val NORMAL = "normal"
    const val HARD = "hard"

    // ---- Ver Sinhaza (square 57, 50)

    /** Just inside the theatre entrance 32653 (3678, 3216) (free tile, collision). */
    val LOBBY = CoordGrid(3675, 3219)

    /** The notice board 32655 (3662, 3218, L1). */
    val NOTICE_BOARD = CoordGrid(3662, 3218, 1)

    // ---- rooms: entry (antechamber) and the purgatory tile outside the fight

    val MAIDEN_ENTRY = CoordGrid(3176, 4424)
    val MAIDEN_WAIT = CoordGrid(3190, 4447)
    val BLOAT_ENTRY = CoordGrid(3272, 4447)
    val BLOAT_WAIT = CoordGrid(3286, 4447)
    val NYLOCAS_ENTRY = CoordGrid(3295, 4270)
    val NYLOCAS_WAIT = CoordGrid(3295, 4260)
    val SOTETSEG_ENTRY = CoordGrid(3279, 4300)
    val SOTETSEG_WAIT = CoordGrid(3279, 4306)
    val XARPUS_ENTRY = CoordGrid(3170, 4397, 1)
    val XARPUS_WAIT = CoordGrid(3170, 4396, 1)
    val VERZIK_ENTRY = CoordGrid(3168, 4304)

    /** OPEN-TB7: the Verzik justiciar hall (z 4296..4301, blocked in the static map) as her room's purgatory. */
    val VERZIK_WAIT = CoordGrid(3168, 4299)
    val TREASURE_ENTRY = CoordGrid(3237, 4309)

    /** The rooms in order (TB7). */
    val ORDER = listOf("maiden", "bloat", "nylocas", "sotetseg", "xarpus", "verzik")

    /** TB18: Bandages and the Dawnbringer exist only inside the theatre. */
    private val RAID_ONLY = setOf(Objs.TOB_BANDAGES, Objs.VERZIK_SPECIAL_WEAPON)

    val DEFINITION: RaidDefinition =
        raid(ID) {
            name = "Theatre of Blood"
            partySize = 1..MAX_PARTY
            modes = listOf(NORMAL, ENTRY, HARD)
            squares = listOf(49 to 67, 49 to 68, 49 to 69, 50 to 67, 50 to 69, 51 to 66, 51 to 67, 51 to 69, 52 to 67)
            exit = LOBBY
            deaths = DeathPolicy.WaitUntilRoomEnds(WipePolicy.FailRaid)
            disconnect = DisconnectPolicy.DEATH_IN_ENCOUNTER
            raidOnlyItems = RAID_ONLY
            claimInv = Invs.TOB_CHESTS
            requirements = { p -> TobConfig.requirement(p) }
            scaling = { ctx, npc -> TobScaling.apply(ctx, npc) }
            // LR16: points are rooms, MVP and deaths (TobLoot); room damage is kept for the MVP awards.
            contribution = ContributionRules(normalisedRooms = true)
            rewards = { snap, rng -> TobLoot.roll(snap, rng) }
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_THEATREOFBLOOD,
                    kcMessage = { "Your completed Theatre of Blood count is: <col=ff0000>$it</col>." },
                    timeMessage = { t, pb, newBest ->
                        if (newBest || pb == null) {
                            "Theatre of Blood total completion time: <col=ff0000>$t</col> (new personal best)"
                        } else {
                            "Theatre of Blood total completion time: <col=ff0000>$t</col>. Personal best: $pb"
                        }
                    },
                    pbKey = "raids.tob.pb",
                    kcVarpByMode =
                        mapOf(
                            NORMAL to Varps.TOTAL_COMPLETED_THEATREOFBLOOD,
                            HARD to Varps.TOTAL_COMPLETED_THEATREOFBLOOD_HARD,
                            ENTRY to Varps.TOTAL_COMPLETED_THEATREOFBLOOD_STORY,
                        ),
                    // OPEN-TB17: the Hard / Entry KC lines are not captured; the mode name is assumed to follow.
                    kcMessageByMode = { mode, kc ->
                        when (mode) {
                            HARD -> "Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>$kc</col>."
                            ENTRY -> "Your completed Theatre of Blood: Entry Mode count is: <col=ff0000>$kc</col>."
                            else -> "Your completed Theatre of Blood count is: <col=ff0000>$kc</col>."
                        }
                    },
                )
            driver = RaidDriver { run -> TobHud.sync(run) }
            autoAdvance = false
            room(
                RoomDef("maiden", RoomKind.ENCOUNTER, MAIDEN_ENTRY, waitAt = MAIDEN_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "maiden")
                }),
            )
            room(
                RoomDef("bloat", RoomKind.ENCOUNTER, BLOAT_ENTRY, waitAt = BLOAT_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "bloat")
                }),
            )
            room(
                RoomDef("nylocas", RoomKind.ENCOUNTER, NYLOCAS_ENTRY, waitAt = NYLOCAS_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "nylocas")
                }),
            )
            room(
                RoomDef("sotetseg", RoomKind.ENCOUNTER, SOTETSEG_ENTRY, waitAt = SOTETSEG_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "sotetseg")
                }),
            )
            room(
                RoomDef("xarpus", RoomKind.ENCOUNTER, XARPUS_ENTRY, waitAt = XARPUS_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "xarpus")
                }),
            )
            room(
                RoomDef("verzik", RoomKind.ENCOUNTER, VERZIK_ENTRY, waitAt = VERZIK_WAIT, onStart = {
                    run,
                    any,
                    ->
                    TobFlow.roomStarted(run, any, "verzik")
                }),
            )
            room(RoomDef("treasure", RoomKind.REWARD, TREASURE_ENTRY, onStart = { run, any -> TobFlow.treasure(run, any) }))
            for (r in ORDER) slot(r)
            slot("treasure")
        }

    /** TB1: 1-5 players. */
    const val MAX_PARTY = 5
}
