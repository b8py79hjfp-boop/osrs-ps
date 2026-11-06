package tp.tools.replay

import tp.engine.World
import tp.engine.WorldConfig
import tp.engine.tick.CommandCodec
import tp.plugin.PluginContent
import tp.tools.scenario.HeadlessNetwork
import tp.tools.scenario.HeadlessSession
import tp.tools.scenario.Scenario
import tp.util.CoordGrid
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/**
 * `replay <log>`: rebuilds the recorded world (same id, seed, spawn), feeds each tick's logins, inputs and logouts at
 * the points they happened, and compares the state hash after every tick with the recorded one. Exit code 0 = identical.
 */
fun main(args: Array<String>) {
    val path =
        args.firstOrNull()?.let(Path::of) ?: run {
            System.err.println("usage: replay <log>")
            exitProcess(2)
        }
    val result = replay(Files.readAllLines(path))
    println(result.summary)
    exitProcess(if (result.divergedAt == null) 0 else 1)
}

class ReplayResult(val ticks: Long, val divergedAt: Long?, val summary: String)

@Suppress("CyclomaticComplexMethod", "MagicNumber") // one branch per record type; indices are field positions
fun replay(lines: List<String>): ReplayResult {
    val events = lines.filter { it.isNotBlank() }.map { it.split(' ') }
    val header = events.firstOrNull { it[0] == "W" } ?: error("missing W header")
    val world =
        World(
            WorldConfig(id = header[1].toInt(), spawn = CoordGrid(header[3].toInt()), seed = header[2].toLong()),
            Scenario.data,
            HeadlessNetwork(),
            PluginContent.load(),
        )
    val sessions = HashMap<Int, HeadlessSession>()
    val byTick = events.filter { it[0] != "W" }.groupBy { it[1].toLong() }
    val last = byTick.keys.maxOrNull() ?: 0L
    for (t in 1..last) {
        val tickEvents = byTick[t].orEmpty()
        for (e in tickEvents) {
            when (e[0]) {
                "L" -> {
                    val session = HeadlessSession()
                    sessions[e[2].toInt()] = session
                    world.queueAdmission(world.newPlayer(e[2].toInt(), e[3], session, world.config.spawn))
                }

                "C" -> {
                    sessions.getValue(e[2].toInt()).input(CommandCodec.decode(e.drop(3)))
                }

                "X" -> {
                    sessions.getValue(e[2].toInt()).disconnectAfterInput()
                }
            }
        }
        world.runTick()
        val recorded = tickEvents.firstOrNull { it[0] == "H" }?.get(2)?.toLong() ?: continue
        val actual = world.stateHash()
        if (actual != recorded) {
            return ReplayResult(t, t, "DIVERGED at tick $t: recorded $recorded, replayed $actual")
        }
    }
    return ReplayResult(last, null, "OK: $last ticks replayed, every state hash matches")
}
