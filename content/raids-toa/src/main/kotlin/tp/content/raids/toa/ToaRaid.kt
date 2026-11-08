package tp.content.raids.toa

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
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/** The four paths (toa.md TA20; client-contract 2.1 `TOA_CLIENT_CURRENT_PATH` values 1-4, 6.2 doors). */
internal enum class ToaPath(
    val god: String,
    val puzzle: String,
    val boss: String,
    val bossName: String,
    /** `TOA_CLIENT_CURRENT_PATH` while on this path. */
    val hudPath: Int,
    val levelVarbit: Int,
) {
    SCABARAS("Scabaras", "scabaras", "kephri", "Kephri", 1, Varbits.TOA_CLIENT_SCABARAS_LEVEL),
    HET("Het", "het", "akkha", "Akkha", 2, Varbits.TOA_CLIENT_HET_LEVEL),
    APMEKEN("Apmeken", "apmeken", "baba", "Ba-Ba", 3, Varbits.TOA_CLIENT_APMEKEN_LEVEL),
    CRONDIS("Crondis", "crondis", "zebak", "Zebak", 4, Varbits.TOA_CLIENT_CRONDIS_LEVEL),
    ;

    val pathName: String get() = "Path of $god"

    companion object {
        fun ofRoom(room: String): ToaPath? = entries.firstOrNull { it.puzzle == room || it.boss == room }
    }
}

/**
 * The Tombs of Amascut as a raid definition (docs/mechanics/raids/toa.md; plan docs/plans/raids-toa.md). The instance
 * holds the 12 raid squares as unrotated whole-square copies (client-contract 6.1: RuneLite reads the template region
 * ids and fixed scene coordinates); the pyramid and the lobby (13354, 13454) are the real map. Coordinates here are
 * template tiles. Rooms: `nexus` (start), the four puzzles and bosses, `wardens`, `tomb` (rewards); [ToaFlow] drives the
 * graph (any path order, TA20).
 */
internal object ToaRaid {
    const val ID = "toa"
    const val ENTRY = "entry"
    const val NORMAL = "normal"
    const val EXPERT = "expert"
    const val NEXUS = "nexus"
    const val WARDENS = "wardens"
    const val TOMB = "tomb"

    /** TA1: 1-8 players. */
    const val MAX_PARTY = 8

    /** TA18: Entry below 150, Normal 150-299, Expert from 300 (client-contract 4.2). */
    const val NORMAL_FROM = 150
    const val EXPERT_FROM = 300

    fun modeOf(raidLevel: Int): String =
        when {
            raidLevel >= EXPERT_FROM -> EXPERT
            raidLevel >= NORMAL_FROM -> NORMAL
            else -> ENTRY
        }

    // ---- the real map

    /** The lobby, inside the raid door 46089 (5x1 at (3357, 9112), r2) (free tile north of it, collision). */
    val LOBBY = CoordGrid(3358, 9114)

    // ---- template arrival tiles (free tiles beside each room's way in; collision, client-contract 6.2)

    /** By the Nexus exit 45128 (3549, 5163). */
    val NEXUS_ENTRY = CoordGrid(3550, 5160)
    val SCABARAS_ENTRY = CoordGrid(3523, 5278)
    val HET_ENTRY = CoordGrid(3695, 5278)
    val APMEKEN_ENTRY = CoordGrid(3793, 5279)
    val CRONDIS_ENTRY = CoordGrid(3953, 5279)
    val KEPHRI_ENTRY = CoordGrid(3535, 5407)
    val AKKHA_ENTRY = CoordGrid(3697, 5406, 1)
    val BABA_ENTRY = CoordGrid(3791, 5407)
    val ZEBAK_ENTRY = CoordGrid(3959, 5407, 1)

    /** By the Wardens exit 45129 (3806, 5178, L1). */
    val WARDENS_ENTRY = CoordGrid(3807, 5176, 1)

    /** OPEN-TA33: the burial chamber's arrival tile, north of the sarcophagus 46220 (3679, 5140). */
    val TOMB_ENTRY = CoordGrid(3680, 5155)

    /** The squares of the raid (client-contract 6.1): Nexus, tomb, Wardens x2, four puzzles, four bosses. */
    private val SQUARES =
        listOf(
            55 to 80,
            57 to 80,
            59 to 80,
            61 to 80,
            55 to 82,
            57 to 82,
            59 to 82,
            61 to 82,
            55 to 84,
            57 to 84,
            59 to 84,
            61 to 84,
        )

    private val ENTRIES =
        mapOf(
            "scabaras" to SCABARAS_ENTRY,
            "het" to HET_ENTRY,
            "apmeken" to APMEKEN_ENTRY,
            "crondis" to CRONDIS_ENTRY,
            "kephri" to KEPHRI_ENTRY,
            "akkha" to AKKHA_ENTRY,
            "baba" to BABA_ENTRY,
            "zebak" to ZEBAK_ENTRY,
        )

    val DEFINITION: RaidDefinition =
        raid(ID) {
            name = "Tombs of Amascut"
            partySize = 1..MAX_PARTY
            modes = listOf(NORMAL, ENTRY, EXPERT)
            squares = SQUARES
            exit = LOBBY
            graveAt = LOBBY
            // TA42-TA44: wipes retry the room, unless an attempts invocation runs out (then the raid fails, the
            // unprotected items go to the Item Retrieval chest).
            deaths = DeathPolicy.WaitUntilRoomEnds(WipePolicy.ByRun { run -> ToaFlow.wipeRule(run) })
            // TA47: logging out in an encounter counts as a death.
            disconnect = DisconnectPolicy.DEATH_IN_ENCOUNTER
            // TA48 (26 Mar 2025): a logout holding an escape crystal is safe.
            safeLogout = { p -> p.inv(Invs.INV).let { inv -> (0 until inv.size).any { inv.obj(it) == ESCAPE_CRYSTAL } } }
            claimInv = Invs.TOA_CHESTS
            raidOnlyItems = ToaPuzzles.RAID_ONLY + ToaSupplies.RAID_ONLY
            scaling = { ctx, npc -> ToaScaling.apply(ctx, npc) }
            // TA35-TA39: raids-core's own damage ledger is off (multiplier 0): ToaPoints keeps the room points apart,
            // joins them at room completion and leaves RaidMember.points as the Total; the death penalty is 20%, min 1,000.
            contribution =
                ContributionRules(normalisedRooms = false, multiplier = { 0.0 }, deathPenalty = { m, _ -> ToaPoints.deathPenalty(m) })
            // TR1-TR28 (lobby-rewards.md): one unique per raid, commons, tertiaries and the challenge rewards.
            rewards = { snap, rng -> ToaLoot.roll(snap, rng) }
            records =
                RecordsDef(
                    kcVarp = Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT,
                    kcMessage = { "Your completed Tombs of Amascut count is: <col=ff0000>$it</col>." },
                    // TA62 (the challenge-time line; the room split in front of it is ToaFlow's).
                    timeMessage = { t, pb, newBest ->
                        if (newBest || pb == null) {
                            "Tombs of Amascut total completion time: <col=ef1020>$t</col> (new personal best)"
                        } else {
                            "Tombs of Amascut total completion time: <col=ef1020>$t</col>. Personal best: $pb"
                        }
                    },
                    pbKey = "raids.toa.pb",
                    // TA62: `m:ss`, two decimals with the precise-timing option.
                    formatTime = { p, ticks -> ToaRecords.formatTime(p, ticks) },
                    kcVarpByMode =
                        mapOf(
                            NORMAL to Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT,
                            ENTRY to Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY,
                            EXPERT to Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT,
                        ),
                    // TA61 (RL chat-commands fixtures).
                    kcMessageByMode = { mode, kc ->
                        when (mode) {
                            EXPERT -> "Your completed Tombs of Amascut: Expert Mode count is: <col=ff0000>$kc</col>."
                            ENTRY -> "Your completed Tombs of Amascut: Entry Mode count is: <col=ff0000>$kc</col>."
                            else -> "Your completed Tombs of Amascut count is: <col=ff0000>$kc</col>."
                        }
                    },
                )
            driver = RaidDriver { run -> ToaHud.sync(run) }
            autoAdvance = false
            room(RoomDef(NEXUS, RoomKind.CHOICE, NEXUS_ENTRY, onStart = { run, any -> ToaFlow.nexusStarted(run, any) }))
            for ((id, entry) in ENTRIES) {
                room(RoomDef(id, RoomKind.ENCOUNTER, entry, waitAt = entry, onStart = { run, any -> ToaFlow.roomStarted(run, any, id) }))
            }
            room(
                RoomDef(WARDENS, RoomKind.ENCOUNTER, WARDENS_ENTRY, waitAt = WARDENS_ENTRY, onStart = { run, any ->
                    ToaFlow.roomStarted(run, any, WARDENS)
                }),
            )
            room(RoomDef(TOMB, RoomKind.REWARD, TOMB_ENTRY, onStart = { run, any -> ToaFlow.tombStarted(run, any) }))
            slot(NEXUS)
            slot(*ENTRIES.keys.toTypedArray(), WARDENS)
            slot(TOMB)
        }

    /** Escape crystal (cache obj 22517, toa.md TA48). */
    const val ESCAPE_CRYSTAL = 22517
}
