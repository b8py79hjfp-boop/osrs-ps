package tp.tools.scenario

import tp.cache.js5.Js5Cache
import tp.engine.ContentHandlers
import tp.engine.World
import tp.engine.WorldConfig
import tp.engine.WorldData
import tp.engine.entity.Player
import tp.plugin.PluginContent
import tp.protocol.api.ClientCommand
import tp.util.CoordGrid
import tp.util.Revision
import java.nio.file.Path

/**
 * A headless world driven tick by tick (brief §9 test 3 runs on this): fixed [seed], scripted input, optional forced
 * PID order, and assertions between ticks. Uses the real engine and, by default, the real content plugins.
 *
 * ```
 * val s = Scenario()
 * val a = s.player("alice", CoordGrid(3222, 3218))
 * s.tick()                                  // alice admitted in this tick's post-tick
 * a.input(ClientCommand.MoveClick(3225, 3218, ctrl = false, minimap = false))
 * s.tick(3)
 * check(a.coord == CoordGrid(3225, 3218))
 * ```
 */
class Scenario(
    seed: Long = 1L,
    spawn: CoordGrid = CoordGrid(DEFAULT_X, DEFAULT_Z),
    content: ContentHandlers = PluginContent.load(),
    worldId: Int = 1,
) {
    val world: World = World(WorldConfig(id = worldId, spawn = spawn, seed = seed), data, HeadlessNetwork(), content)

    private var nextSlot = 1

    /** Queues a player for admission in the next tick's post-tick (as a network login would be). */
    fun player(username: String, at: CoordGrid = world.config.spawn): ScenarioPlayer {
        val session = HeadlessSession()
        val player = world.newPlayer(nextSlot++, username, session, at)
        world.queueAdmission(player)
        return ScenarioPlayer(player, session)
    }

    /** Fixes the processing order (first = lowest PID) and stops PID re-randomisation. */
    fun forcePid(vararg players: ScenarioPlayer) {
        world.forcePidOrder(players.map { it.player.slot })
    }

    fun tick(n: Int = 1) {
        repeat(n) { world.runTick() }
    }

    val currentTick: Long get() = world.tick

    companion object {
        private const val DEFAULT_X = 3222
        private const val DEFAULT_Z = 3218

        /** Cache-derived data, decoded once per JVM. Cache: `-Dtp.cacheDir`, else `.data/cache/<rev>/disk`. */
        val data: WorldData by lazy {
            val root = System.getProperty("tp.repoRoot") ?: "."
            val dir =
                System.getProperty("tp.cacheDir")?.let(Path::of) ?: Path.of(root, ".data", "cache", Revision.NUMBER.toString(), "disk")
            Js5Cache.open(dir).use { WorldData.load(it, dir) }
        }
    }
}

class ScenarioPlayer internal constructor(val player: Player, val session: HeadlessSession) {
    val coord: CoordGrid get() = player.coord
    val messages: List<String> get() = session.messages

    fun input(command: ClientCommand) {
        session.input(command)
    }
}
