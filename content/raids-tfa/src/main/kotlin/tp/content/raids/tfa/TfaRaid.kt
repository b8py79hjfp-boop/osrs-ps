package tp.content.raids.tfa

import tp.content.raids.ContributionRules
import tp.content.raids.DeathPolicy
import tp.content.raids.RaidDefinition
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
 * The Fractured Archive as a raid definition (docs/mechanics/raids/tfa.md; map from the cache, client-contract.md
 * section 5). Coordinates are template (real map) tiles; the lobby is the real, shared map and is not instanced.
 */
internal object TfaRaid {
    const val ID = "tfa"

    // ---- lobby (real map, square 64, 86)

    /** In front of the Archive Door (4123, 5562): where leavers and wiped parties arrive (free tile, collision). */
    val LOBBY_DOOR = CoordGrid(4122, 5559)

    /** Inside the lobby by its exit Opening (4126, 5505), for players coming in from outside (free tile, collision). */
    val LOBBY_FROM_OUTSIDE = CoordGrid(4127, 5508)

    /** Next to the outside entrance loc 62542 (4050, 4567) in the Guthixian temple cave (free tile, collision). */
    val OUTSIDE = CoordGrid(4053, 4568)

    // ---- nexus and the Archive Guardian (square 64, 87)

    /** The nexus square: copied from level 0 only; levels 1-3 are its later states (OPEN-NX4). */
    val NEXUS_SQUARE = 64 to 87

    /** South side of the nexus, in front of the Guardian (free tile, collision). */
    val GUARDIAN_ENTRY = CoordGrid(4127, 5585)

    /** Where players come back to the nexus after a side room (free tile, collision; OPEN-NX2). */
    val NEXUS_RETURN = CoordGrid(4127, 5592)

    /** South-west tile of the size-5 Inert Guardian: the guardian corpse loc 62800 lies at (4125, 5597) on L2/L3. */
    val INERT_GUARDIAN = CoordGrid(4125, 5597)

    /** The Quartermaster, by the supply wheelbarrow 62787 (4120, 5578) (free tile; OPEN-NX2). */
    val QUARTERMASTER = CoordGrid(4122, 5581)

    // ---- side rooms (entry tiles inside each room's passageway, OPEN-NX2)

    val EZEKIEL_ENTRY = CoordGrid(4083, 5536)
    val DAGONHAI_ENTRY = CoordGrid(4086, 5600)
    val MOIA_ENTRY = CoordGrid(4084, 5664)
    val XARTH_ENTRY = CoordGrid(4171, 5536)
    val BORK_ENTRY = CoordGrid(4166, 5600)
    val DREADNOTH_ENTRY = CoordGrid(4171, 5664)

    // ---- Ocellus (square 64, 88, arena on level 1) and the Void vault (square 64, 90)

    /** Inside the arena entrance 62950 (4126, 5660, 1) (free tile, collision). */
    val OCELLUS_ENTRY = CoordGrid(4127, 5664, 1)

    /** South-west tile of the size-3 Blade NPC 16683 at the Blade loc 62706 (4127, 5673, 1) (OPEN-NX2). */
    val BLADE = CoordGrid(4126, 5676, 1)

    /** In front of the vault entrance portal 62990 (4127, 5772, 2) (free tile, collision). */
    val VAULT_ENTRY = CoordGrid(4127, 5776, 2)

    /** The vault exit Rift 16690 (size 3) over the entrance portal (OPEN-NX6). */
    val VAULT_RIFT = CoordGrid(4126, 5772, 2)

    /** The eight chests in a ring on the vault platform, chest 1 first (OPEN-NX6: no source gives the layout). */
    val VAULT_CHESTS =
        listOf(
            CoordGrid(4121, 5798, 2),
            CoordGrid(4123, 5802, 2),
            CoordGrid(4127, 5803, 2),
            CoordGrid(4131, 5802, 2),
            CoordGrid(4133, 5798, 2),
            CoordGrid(4131, 5794, 2),
            CoordGrid(4127, 5793, 2),
            CoordGrid(4123, 5794, 2),
        )

    /** The northern alcove of the platform for the size-3 unique chest (TFA24 "northern platform"; OPEN-NX6). */
    val UNIQUE_CHEST = CoordGrid(4126, 5805, 2)

    /** TFA6: the nexus door pairs, south row first (section 1-3 doors; OPEN-NX1 maps section n to slot n + 1). */
    val SECTIONS =
        listOf(
            "ezekiel" to "bork",
            "moia" to "dreadnoth",
            "dagonhai" to "xarth",
        )

    /** TFA16 (cache row names): the shop's note and leftovers exist only inside the raid. */
    private val RAID_ONLY = setOf(Objs.TFA_MIDRAID_SUPPLIES_NOTE, Objs.TFA_MIDRAID_SUPPLIES_LEFTOVERS)

    val DEFINITION: RaidDefinition =
        raid(ID) {
            name = "The Fractured Archive"
            partySize = 1..MAX_PARTY
            modes = listOf("normal", "practice")
            squares = listOf(63 to 86, 65 to 86, 63 to 87, NEXUS_SQUARE, 65 to 87, 63 to 88, 64 to 88, 65 to 88, 64 to 90)
            squareLevels = mapOf(NEXUS_SQUARE to 1)
            exit = LOBBY_DOOR
            // TFA14: on a wipe "unprotected items are in gravestones outside the entrance" (the lobby side of the door).
            graveAt = LOBBY_DOOR
            deaths = DeathPolicy.WaitUntilRoomEnds(WipePolicy.EjectWithGraves)
            raidOnlyItems = RAID_ONLY
            claimInv = Invs.TFA_CHESTS
            requirements = { p -> TfaConfig.requirement(p) }
            contribution =
                ContributionRules(normalisedRooms = true, deathPenalty = { _, run -> TfaRewards.deathPenalty(run.scaling.partySize) })
            rewards = { snap, rng -> TfaLoot.roll(snap, rng) }
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_THEFRACTUREDARCHIVE,
                    kcMessage = { "Your completed Fractured Archive count is: <col=ff0000>$it</col>." },
                    timeMessage = { t, pb, newBest ->
                        if (newBest || pb == null) {
                            "The Fractured Archive: Total completion time: <col=ef1020>$t</col> (new personal best)"
                        } else {
                            "The Fractured Archive: Total completion time: <col=ef1020>$t</col>. Personal best: $pb"
                        }
                    },
                    pbKey = "raids.tfa.pb",
                    practiceKcKey = "raids.tfa.practiceKc",
                    // OPEN-TFA32: the practice completion message is not captured.
                    practiceMessage = { "Your completed Fractured Archive practice count is: <col=ff0000>$it</col>." },
                )
            autoAdvance = false
            val c = TfaRewards.ROOM_CONTRIBUTION
            room(
                RoomDef("guardian", RoomKind.ENCOUNTER, GUARDIAN_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaFlow.guardianRoom(run, any)
                }),
            )
            room(
                RoomDef("ezekiel", RoomKind.ENCOUNTER, EZEKIEL_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "ezekiel")
                }),
            )
            room(
                RoomDef("bork", RoomKind.ENCOUNTER, BORK_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "bork")
                }),
            )
            room(
                RoomDef("moia", RoomKind.ENCOUNTER, MOIA_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "moia")
                }),
            )
            room(
                RoomDef("dreadnoth", RoomKind.ENCOUNTER, DREADNOTH_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "dreadnoth")
                }),
            )
            room(
                RoomDef("dagonhai", RoomKind.ENCOUNTER, DAGONHAI_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "dagonhai")
                }),
            )
            room(
                RoomDef("xarth", RoomKind.ENCOUNTER, XARTH_ENTRY, contribution = c, onStart = {
                    run,
                    any,
                    ->
                    TfaBosses.start(run, any, "xarth")
                }),
            )
            room(
                RoomDef("ocellus", RoomKind.ENCOUNTER, OCELLUS_ENTRY, contribution = TfaRewards.OCELLUS_CONTRIBUTION) { run, any ->
                    TfaBosses.start(run, any, "ocellus")
                },
            )
            room(RoomDef("vault", RoomKind.REWARD, VAULT_ENTRY, onStart = { run, any -> TfaVault.start(run, any) }))
            slot("guardian")
            for ((west, east) in SECTIONS) slot(west, east)
            slot("ocellus")
            slot("vault")
        }

    /** TFA4: 1-8 players. */
    const val MAX_PARTY = 8

    /** TFA9: a solo raid uses two-player scaling. */
    const val MIN_SCALE = 2
}
