package tp.content.raids

import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.util.CoordGrid

/**
 * One raid, declared once with [raid] (docs/mechanics/raids/common.md; brief section 3.3). Immutable: definitions are
 * values built at startup; runs ([RaidRun]) hold the mutable state. Every coordinate here is a **template**
 * coordinate (the raid's real map), resolved through the run's instance.
 */
public class RaidDefinition internal constructor(
    public val id: String,
    public val name: String,
    public val partySize: IntRange,
    public val modes: List<String>,
    /** Whole template map squares copied into each run's instance (square x, z), with the instance's size. */
    public val squares: List<Pair<Int, Int>>,
    public val rooms: Map<String, RoomDef>,
    /** Room slots in order: one id = fixed room, several = the party picks one (TFA's doors). */
    public val slots: List<List<String>>,
    /** Where finished, failed and leaving players go (real map), and where a wipe's graves lie. */
    public val exit: CoordGrid,
    public val graveAt: CoordGrid,
    public val deaths: DeathPolicy,
    public val disconnect: DisconnectPolicy,
    public val raidOnlyItems: Set<Int>,
    /** Inventory that holds a player's rewards until they claim them (persisted with the player). */
    public val claimInv: Int,
    public val requirements: (PlayerRef) -> String?,
    public val scaling: (ScalingContext, NpcRef) -> Unit,
    public val contribution: ContributionRules,
    public val rewards: (RunSnapshot, RaidRng) -> Map<String, RewardResult>,
    public val records: RecordsDef,
    public val driver: RaidDriver,
    /** After a room clears, a single-room next slot starts at once (else content calls [Raids.advance], e.g. a passage). */
    public val autoAdvance: Boolean,
    /** Levels copied from level 0 for a square (default all four); TFA's nexus keeps its other levels as states. */
    public val squareLevels: Map<Pair<Int, Int>, Int>,
) {
    internal val instanceWidth: Int = squares.maxOf { it.first } - squares.minOf { it.first } + 1
    internal val instanceLength: Int = squares.maxOf { it.second } - squares.minOf { it.second } + 1
    internal val originSquareX: Int = squares.minOf { it.first }
    internal val originSquareZ: Int = squares.minOf { it.second }
}

/** What kind of space a room is (encounters count damage and deaths). */
public enum class RoomKind { ENCOUNTER, CHOICE, REWARD }

/** A room: its [entry] tile (template), its [kind], the [contribution] it is worth, and its start script. */
public class RoomDef(
    public val id: String,
    public val kind: RoomKind,
    public val entry: CoordGrid,
    public val contribution: Int = 0,
    /** Where a dead player waits while teammates fight on (template); null = [entry]. */
    public val waitAt: CoordGrid? = null,
    /** Runs when the room starts, with an entity of the run's world (spawns, messages). */
    public val onStart: (RaidRun, tp.plugin.EntityRef) -> Unit = { _, _ -> },
)

/** Death rules (brief 3.9). */
public sealed interface DeathPolicy {
    /** The dead player waits (items kept) until the room ends; when nobody is left alive the [wipe] rule applies. */
    public class WaitUntilRoomEnds(
        public val wipe: WipePolicy,
        /** A different wipe rule for some modes (ToB Entry retries the room). */
        public val wipeByMode: Map<String, WipePolicy> = emptyMap(),
    ) : DeathPolicy

    /** The player respawns at the [checkpoint] room's entry, items kept (CoX: start of the previous floor). */
    public class RespawnAtCheckpoint(public val checkpoint: (RaidRun) -> String) : DeathPolicy
}

public sealed interface WipePolicy {
    /** The party is ejected to the exit and every member's unprotected items go to graves at [RaidDefinition.graveAt]. */
    public object EjectWithGraves : WipePolicy

    /** The run fails (ToB Normal / Hard); unprotected items go to [storeInv] when >= 0 (else the normal grave). */
    public class FailRaid(public val storeInv: Int = -1) : WipePolicy

    /** The room starts again with everyone alive, items kept (ToB Entry, TB16); content restarts it on RoomFailed. */
    public object RetryRoom : WipePolicy
}

/** What a lost session does (brief 3.10). */
public enum class DisconnectPolicy {
    /** Nothing: the member stays a member and rejoins on login while the run lives (TFA). */
    REJOIN,

    /** In an encounter it counts as a death; elsewhere safe (ToB, ToA). */
    DEATH_IN_ENCOUNTER,
}

/** Fixed when the raid starts and never changed afterwards (brief 3.7). */
public class ScalingContext(
    public val partySize: Int,
    public val scaledSize: Int,
    public val mode: String,
    /** Highest of each combat level among the members at start (attack, defence, strength, hitpoints, ranged, magic). */
    public val highestLevels: IntArray,
    public val settings: Map<String, Int>,
)

/**
 * How damage turns into points. [normalisedRooms]: each room's contribution is split by damage share at room end
 * (TFA); otherwise damage x multiplier adds directly (CoX, ToA). [deathPenalty] returns the points a death costs.
 */
public class ContributionRules(
    public val normalisedRooms: Boolean,
    public val multiplier: (npcType: Int) -> Double = { 1.0 },
    public val deathPenalty: (member: RaidMember, run: RaidRun) -> Double = { _, _ -> 0.0 },
)

/** KC varp, messages and PB key per raid (brief 3.13); messages are exact strings from the fact sheets. */
public class RecordsDef(
    public val kcVarp: Int,
    public val kcMessage: (Int) -> String,
    public val timeMessage: (time: String, personalBest: String?, newBest: Boolean) -> String,
    public val pbKey: String,
    /** Saved-value key of the practice-mode completion count; null = practice runs count as normal ones. */
    public val practiceKcKey: String? = null,
    public val practiceMessage: ((Int) -> String)? = null,
    /** Per-mode KC varps (ToB: Entry / Normal / Hard each keep their own count and personal bests); empty = [kcVarp]. */
    public val kcVarpByMode: Map<String, Int> = emptyMap(),
    /** The KC message for a mode; null = [kcMessage]. */
    public val kcMessageByMode: ((mode: String, kc: Int) -> String)? = null,
)

/** Maps run state to a raid's HUD and party varbits (brief 3.15); called at the end of a tick when the run changed. */
public fun interface RaidDriver {
    public fun sync(run: RaidRun)
}

/** The DSL entry point: `raid("tfa") { ... }`. */
public fun raid(id: String, block: RaidBuilder.() -> Unit): RaidDefinition = RaidBuilder(id).apply(block).build()

@DslMarker
internal annotation class RaidDsl

@RaidDsl
public class RaidBuilder internal constructor(private val id: String) {
    public var name: String = id
    public var partySize: IntRange = 1..1
    public var modes: List<String> = listOf("normal")
    public var squares: List<Pair<Int, Int>> = emptyList()
    public var exit: CoordGrid? = null
    public var graveAt: CoordGrid? = null
    public var deaths: DeathPolicy = DeathPolicy.WaitUntilRoomEnds(WipePolicy.EjectWithGraves)
    public var disconnect: DisconnectPolicy = DisconnectPolicy.REJOIN
    public var raidOnlyItems: Set<Int> = emptySet()
    public var claimInv: Int = -1
    public var requirements: (PlayerRef) -> String? = { null }
    public var scaling: (ScalingContext, NpcRef) -> Unit = { _, _ -> }
    public var contribution: ContributionRules = ContributionRules(normalisedRooms = false)
    public var rewards: (RunSnapshot, RaidRng) -> Map<String, RewardResult> = { _, _ -> emptyMap() }
    public var records: RecordsDef? = null
    public var driver: RaidDriver = RaidDriver { }
    public var autoAdvance: Boolean = true
    public var squareLevels: Map<Pair<Int, Int>, Int> = emptyMap()
    private val rooms = LinkedHashMap<String, RoomDef>()
    private val slots = ArrayList<List<String>>()

    public fun room(def: RoomDef) {
        rooms[def.id] = def
    }

    /** The next slot: one room id (fixed) or several (the party chooses). */
    public fun slot(vararg roomIds: String) {
        slots += roomIds.toList()
    }

    internal fun build(): RaidDefinition {
        require(squares.isNotEmpty()) { "raid $id: no template squares" }
        require(slots.isNotEmpty() && slots.flatten().all { it in rooms }) { "raid $id: slots must name declared rooms" }
        require(claimInv >= 0) { "raid $id: claimInv" }
        val exit = requireNotNull(exit) { "raid $id: exit" }
        return RaidDefinition(
            id,
            name,
            partySize,
            modes,
            squares,
            rooms.toMap(),
            slots.toList(),
            exit,
            graveAt ?: exit,
            deaths,
            disconnect,
            raidOnlyItems,
            claimInv,
            requirements,
            scaling,
            contribution,
            rewards,
            requireNotNull(records) { "raid $id: records" },
            driver,
            autoAdvance,
            squareLevels,
        )
    }
}
