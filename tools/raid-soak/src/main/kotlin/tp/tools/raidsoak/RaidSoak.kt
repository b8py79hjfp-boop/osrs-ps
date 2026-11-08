package tp.tools.raidsoak

import tp.content.raids.Party
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.engine.World
import tp.plugin.PlayerRef
import tp.tools.scenario.Scenario
import tp.tools.scenario.ScenarioPlayer
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * Raids brief section 7, in process on the headless engine (no sockets, so the info pass encodes nothing):
 *
 * - `soak [cycles]`: create, run and destroy instances round-robin over the four raids. Every [REPORT_EVERY] cycles it
 *   prints the world's NPC / loc / obj / instance counts against the starting baseline, the heap after a full GC, the
 *   process RSS and the build / attach percentiles.
 * - `memory [perRaid]`: [perRaid] live instances of one raid at a time; heap after GC per instance.
 * - `load [perRaid] [ticks]`: [perRaid] solo runs of every raid live at once (75 x 4 = 300 by default); each run
 *   enters its next room every [ROOM_EVERY] ticks; prints the logic-phase tick percentiles.
 *
 * `./gradlew :tools:raid-soak:run --args="soak 10000"` (devSpawns on, world spawns off, ZGC, 4 GB heap).
 */
fun main(args: Array<String>) {
    val n = args.getOrNull(1)?.toIntOrNull()
    when (args.getOrNull(0)) {
        "soak" -> {
            soak(n ?: DEFAULT_CYCLES)
        }

        "memory" -> {
            memory(n ?: DEFAULT_PER_RAID_MEMORY)
        }

        "load" -> {
            load(n ?: DEFAULT_PER_RAID_LOAD, args.getOrNull(2)?.toIntOrNull() ?: DEFAULT_LOAD_TICKS)
        }

        else -> {
            System.err.println("usage: raid-soak soak [cycles] | memory [perRaid] | load [perRaid] [ticks]")
            exitProcess(2)
        }
    }
    exitProcess(0)
}

private val RAIDS = listOf("tob", "cox", "toa", "tfa")

/** One leader and their party, reused for every run of its raid. */
private class Leader(val sp: ScenarioPlayer, val raid: String) {
    val ref: PlayerRef get() = PlayerRef.of(sp.player)

    fun party(): Party {
        val raids = Raids.of(ref)
        return raids.partyOf(ref.username) ?: requireNotNull(raids.createParty(requireNotNull(raids.definition(raid)), ref.username))
    }

    fun run(): RaidRun? = Raids.of(ref).runOf(ref.username)

    fun start() {
        Raids.start(ref, party())?.let { error("$raid: $it") }
    }

    /** Drops what the headless session keeps (messages, visuals): a real session sends and forgets. */
    fun forget() {
        sp.session.sent.clear()
        sp.session.visuals.clear()
    }
}

private fun Scenario.leaders(prefix: String, perRaid: Int): List<Leader> {
    val out = RAIDS.flatMap { raid -> (0 until perRaid).map { Leader(player("$prefix${raid}_$it"), raid) } }
    tick(2)
    return out
}

private fun Scenario.keepAlive(leaders: List<Leader>) {
    for (l in leaders) {
        l.ref.hitpoints = KEEP_ALIVE
        l.ref.frozenUntil = 0
    }
}

private fun Scenario.tickAlive(leaders: List<Leader>, n: Int = 1) {
    repeat(n) {
        keepAlive(leaders)
        tick()
    }
    network.npcVisuals.clear()
}

/** Ticks until every leader's run has its instance attached (build off-thread, attach next pre-tick). */
private fun Scenario.awaitAttached(leaders: List<Leader>) {
    var t = 0
    while (leaders.any { it.run()?.instance == null } && t < ATTACH_WAIT) {
        tickAlive(leaders)
        t++
    }
    check(t < ATTACH_WAIT) { "instances not attached after $t ticks: ${leaders.filter { it.run()?.instance == null }.map { it.raid }}" }
}

private class Counts(val world: World, any: PlayerRef) {
    val npcs = world.npcCount
    val respawns = world.pendingRespawns
    val instances = world.instances.live

    // Instance space only: the open world has its own timed loc changes (the Ascension rocks rotate), bounded and not ours.
    private val inInstances = world.locs.describe().filter { instanceX(it) >= INSTANCE_SPACE_X }
    val locChanges = inInstances.count { it.startsWith("change") }
    val locBases = inInstances.count { it.startsWith("base") }
    val locZones = world.locs.zoneCount
    val objs = world.ground.size
    val objZones = world.ground.zoneCount
    val runs = Raids.of(any).activeRuns.size

    override fun toString(): String =
        "npcs=$npcs respawns=$respawns instances=$instances locChanges=$locChanges locBases=$locBases locZones=$locZones " +
            "objs=$objs objZones=$objZones runs=$runs"

    fun leaksAgainst(base: Counts): List<String> =
        listOfNotNull(
            "npcs".takeIf { npcs > base.npcs },
            "respawns".takeIf { respawns > base.respawns },
            "instances".takeIf { instances > base.instances },
            "locChanges".takeIf { locChanges > base.locChanges },
            "locBases".takeIf { locBases > base.locBases },
            "locZones".takeIf { locZones > base.locZones + OPEN_WORLD_ZONE_SLACK },
            "objs".takeIf { objs > base.objs },
            "objZones".takeIf { objZones > base.objZones },
            "runs".takeIf { runs > base.runs },
        )
}

/** The x of a [tp.engine.map.DynamicLocs.describe] line ("change level x z id" / "base CoordGrid(x, z, level) id"). */
private fun instanceX(line: String): Int =
    if (line.startsWith("change")) {
        line.split(' ')[2].toInt()
    } else {
        line
            .substringAfter('(')
            .substringBefore(',')
            .trim()
            .toInt()
    }

private fun heapAfterGcMb(): Long {
    repeat(GC_PASSES) { System.gc() }
    val mem = ManagementFactory.getMemoryMXBean().heapMemoryUsage
    return mem.used / MB
}

/** Process resident set (Linux /proc), MB; -1 elsewhere. */
private fun rssMb(): Long =
    runCatching {
        Files
            .readAllLines(Path.of("/proc/self/status"))
            .first { it.startsWith("VmRSS:") }
            .split(Regex("\\s+"))[1]
            .toLong() / KB
    }.getOrDefault(-1L)

private fun World.instanceTimes(): String {
    val b = metrics.instanceBuild
    val a = metrics.instanceAttach
    return "build p50=${b.percentileNanos(P50) / US}us p99=${b.percentileNanos(P99) / US}us max=${b.maxNanos / US}us " +
        "attach p50=${a.percentileNanos(P50) / US}us p99=${a.percentileNanos(P99) / US}us max=${a.maxNanos / US}us (n=${a.count})"
}

private fun soak(cycles: Int) {
    val s = Scenario(seed = 1L)
    val leaders = s.leaders("soak_", 1)
    for (l in leaders) l.party()
    // One warm-up cycle per raid: the first build fills the template zone cache, which is meant to stay.
    for (l in leaders) cycle(s, l, leaders)
    val base = Counts(s.world, leaders[0].ref)
    val baseLocs = s.world.locs.describe()
    val baseHeap = heapAfterGcMb()
    val baseRss = rssMb()
    println("baseline: $base heap=${baseHeap}MB rss=${baseRss}MB")
    var worstLeaks = emptyList<String>()
    for (c in 0 until cycles) {
        cycle(s, leaders[c % leaders.size], leaders)
        if ((c + 1) % REPORT_EVERY == 0 || c == cycles - 1) {
            val now = Counts(s.world, leaders[0].ref)
            val leaks = now.leaksAgainst(base)
            if (leaks.isNotEmpty()) worstLeaks = leaks
            println(
                "cycle ${c + 1}: $now heap=${heapAfterGcMb()}MB (base $baseHeap) rss=${rssMb()}MB (base $baseRss) " +
                    "${s.world.instanceTimes()}${if (leaks.isEmpty()) "" else " LEAK $leaks"}",
            )
        }
    }
    if (worstLeaks.isNotEmpty()) {
        val leftover = s.world.locs.describe() - baseLocs.toSet()
        println("leftover locs: $leftover")
    }
    println(if (worstLeaks.isEmpty()) "soak: no leaked NPCs, locs, objs, instances or runs" else "soak: LEAKS $worstLeaks")
}

/** One run of [l]'s raid: start, attach, a few ticks with its first room running, then torn down (Raids.reset). */
private fun cycle(s: Scenario, l: Leader, all: List<Leader>) {
    l.start()
    s.awaitAttached(listOf(l))
    if (System.getenv("SOAK_VERBOSE") != null) {
        println("${l.raid}: build ${s.world.metrics.instanceBuildNanos / US}us attach ${s.world.metrics.instanceAttachNanos / US}us")
    }
    s.tickAlive(all, TICKS_INSIDE)
    val run = requireNotNull(l.run()) { "${l.raid}: the run ended early" }
    Raids.reset(l.ref, run)
    s.tickAlive(all, TICKS_AFTER)
    l.forget()
}

private fun memory(perRaid: Int) {
    val s = Scenario(seed = 1L)
    for (raid in RAIDS) {
        val leaders = RAIDS.indexOf(raid).let { s.leaders("mem${it}_", perRaid).filter { l -> l.raid == raid } }
        // Warm the template cache with one run first, so the per-instance figure is the instance's own memory.
        leaders.first().start()
        s.awaitAttached(leaders.take(1))
        Raids.reset(leaders.first().ref, requireNotNull(leaders.first().run()))
        s.tickAlive(leaders, TICKS_AFTER)
        val before = heapAfterGcMb()
        for (l in leaders) l.start()
        s.awaitAttached(leaders)
        s.tickAlive(leaders, TICKS_INSIDE)
        val after = heapAfterGcMb()
        val perInstanceKb = (after - before) * KB / perRaid
        println("$raid: $perRaid instances, heap ${before}MB -> ${after}MB, about ${perInstanceKb}KB per instance")
        for (l in leaders) l.run()?.let { Raids.reset(l.ref, it) }
        s.tickAlive(leaders, TICKS_AFTER)
        for (l in leaders) l.forget()
    }
}

private fun load(perRaid: Int, ticks: Int) {
    val s = Scenario(seed = 1L)
    val leaders = s.leaders("load_", perRaid)
    for (l in leaders) l.start()
    s.awaitAttached(leaders)
    println("load: ${leaders.size} live instances (${s.world.instances.live}), ${s.world.npcCount} NPCs; ${s.world.instanceTimes()}")
    s.world.metrics.logic
        .reset()
    for (t in 0 until ticks) {
        if (t > 0 && t % ROOM_EVERY == 0) nextRooms(leaders, t / ROOM_EVERY)
        s.tickAlive(leaders)
        if (t % FORGET_EVERY == 0) for (l in leaders) l.forget()
    }
    val h = s.world.metrics.logic
    println(
        "load: $ticks ticks, logic p50=${h.percentileMs(P50)}ms p99=${h.percentileMs(P99)}ms max=${h.maxNanos / MS}ms; " +
            "${s.world.npcCount} NPCs; live runs ${leaders.count { it.run() != null }}",
    )
}

/** Each run enters the [step]-th room of its raid's order (a fixed room, or the first choice of a choice slot). */
private fun nextRooms(leaders: List<Leader>, step: Int) {
    for (l in leaders) {
        val run = l.run() ?: continue
        val rooms = run.raid.slots.map { it.first() }
        Raids.enterRoom(l.ref, run, rooms[step % rooms.size])
    }
}

private const val DEFAULT_CYCLES = 10_000

/** Instance space starts at map square 100 (engine Instances.FIRST_SQUARE_X). */
private const val INSTANCE_SPACE_X = 6400

/** The open world's timed loc changes touch a few zones of their own (the Ascension rocks: 4 zones). */
private const val OPEN_WORLD_ZONE_SLACK = 8
private const val DEFAULT_PER_RAID_MEMORY = 25
private const val DEFAULT_PER_RAID_LOAD = 75
private const val DEFAULT_LOAD_TICKS = 500
private const val REPORT_EVERY = 1_000
private const val ATTACH_WAIT = 20
private const val TICKS_INSIDE = 5
private const val TICKS_AFTER = 2
private const val ROOM_EVERY = 50
private const val FORGET_EVERY = 10
private const val KEEP_ALIVE = 99
private const val GC_PASSES = 3
private const val MB = 1L shl 20
private const val KB = 1024L
private const val US = 1_000L
private const val MS = 1_000_000L
private const val P50 = 0.5
private const val P99 = 0.99
