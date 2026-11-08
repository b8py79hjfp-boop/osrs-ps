package tp.content.raids

import tp.plugin.AttrKey
import tp.plugin.InstanceRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.util.CoordGrid
import java.util.SplittableRandom

@JvmInline
public value class RaidRunId(public val value: Long)

/** Run states (brief 3.5). */
public sealed interface RunState {
    /** The instance is being built (attaches next tick). */
    public object Building : RunState

    public class InRoom(public val room: RoomDef, public val startedTick: Long) : RunState

    /** Between rooms: the next slot is [slot]; with several choices the party picks one ([RaidRun.advance]). */
    public class BetweenRooms(public val slot: Int) : RunState

    public object Completed : RunState

    public class Failed(public val reason: String) : RunState

    public object Abandoned : RunState

    public object Closed : RunState
}

/** One member of a run, kept until the run closes (rejoins, offline rewards, the leaver rule). */
public class RaidMember internal constructor(public val name: String) {
    public var online: Boolean = true
        internal set

    /** Inside the instance (entered, not left, not ejected). */
    public var inside: Boolean = false
        internal set

    /** Alive in the current room (false while waiting after a death). */
    public var alive: Boolean = true
        internal set
    public var deaths: Int = 0
        internal set
    public var points: Double = 0.0
        internal set

    /** Damage dealt in the current room (normalised contribution). */
    internal var roomDamage: Long = 0
    public var reward: RewardResult? = null
        internal set

    /** Reward already put in the claim inventory (or forfeited): never paid twice. */
    public var paid: Boolean = false
        internal set
}

/** A run's seeded RNG (brief 3.16): every roll is logged for the tick inspector and replay. */
public class RaidRng internal constructor(seed: Long) {
    private val random = SplittableRandom(seed)
    private val log = ArrayList<String>()
    public val rolls: List<String> get() = log

    /** 0 until [bound]. */
    public fun next(bound: Int, label: String): Int = random.nextInt(bound).also { log += "$label $it/$bound" }

    /** [0, 1). */
    public fun nextDouble(label: String): Double = random.nextDouble().also { log += "$label $it" }
}

/** One player's reward (brief 3.12): items, and whether it includes a unique (chest visuals, broadcasts). */
public class RewardResult(
    public val items: List<Pair<Int, Int>>,
    public val unique: Boolean = false,
    public val tags: Set<String> = emptySet(),
)

/** What a reward function sees: pure data (brief section 9: rewards are `(RunSnapshot, Rng) -> results`). */
public class RunSnapshot(
    public val raid: String,
    public val mode: String,
    public val partySize: Int,
    public val scaledSize: Int,
    /** Name -> (points, deaths, still a member at the end). */
    public val members: Map<String, Triple<Double, Int, Boolean>>,
    public val teamDeaths: Int,
    public val settings: Map<String, Int>,
)

/**
 * A raid in progress (brief 3.5). World-thread owned; content drives it: [enterRoom] when a room starts, [roomCleared]
 * when its condition is met, [advance] when the party picks the next room, [complete] at the end.
 */
public class RaidRun internal constructor(
    public val id: RaidRunId,
    public val raid: RaidDefinition,
    public val party: Party,
    public val scaling: ScalingContext,
    public val rng: RaidRng,
    public val startedTick: Long,
) {
    public var instance: InstanceRef? = null
        internal set
    public var state: RunState = RunState.Building
        internal set
    public val members: Map<String, RaidMember> = party.members.associateWith { RaidMember(it) }

    /** Rooms taken so far, in order (TFA: which doors were chosen). */
    public val roomsTaken: MutableList<String> = ArrayList()
    internal var slotIndex = 0
    internal var emptySinceTick = -1L
    internal var dirty = true
    public val spectators: MutableSet<String> = LinkedHashSet()
    public var teamDeaths: Int = 0
        internal set
    public var endedTick: Long = -1
        internal set

    public fun member(name: String): RaidMember? = members.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** The instance tile of template tile [template]. */
    public fun at(template: CoordGrid, sizeX: Int = 1, sizeZ: Int = 1): CoordGrid =
        requireNotNull(instance?.fromTemplate(template, sizeX, sizeZ)) { "run ${id.value}: $template is not in ${raid.id}" }

    /** Members inside the instance and online, as handles. */
    public fun presentPlayers(any: tp.plugin.EntityRef): List<PlayerRef> =
        members.values.filter { it.inside && it.online }.mapNotNull { any.onlinePlayer(it.name) }

    public fun markDirty() {
        dirty = true
    }

    internal companion object {
        /** NPCs spawned for a run carry its id, so only their damage counts (brief 3.16). */
        val NPC_RUN = AttrKey<Long>("raid_run")
        val PLAYER_RUN = AttrKey<Long>("raid_run_player")

        fun npcRun(npc: NpcRef): Long? = npc[NPC_RUN]
    }
}
