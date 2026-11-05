package tp.content.dev

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.util.CoordGrid

/** Developer `::commands` (Phase 3 stand-ins; an admin rights check arrives with accounts in Phase 5). */
public class DevCommands : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { message("Dev commands: ::pos ::run ::tele x z [level] ::item id [count] ::empty ::xp stat amount") }
        onCommand("pos") { message("$coord tick $tick pid $pid") }
        onCommand("run") {
            runMode = !runMode
            message("Run: $runMode")
        }
        onCommand("tele") { args -> tele(args) }
        onCommand("item") { args -> item(args) }
        onCommand("empty") {
            val inv = inv(Invs.INV)
            transaction("dev ::empty") {
                (0 until inv.size).all { slot -> inv.obj(slot) < 0 || removeAt(Invs.INV, slot, inv.obj(slot), inv.count(slot)) }
            }
        }
        onCommand("xp") { args ->
            val stat = args.getOrNull(0)?.toIntOrNull()
            val xp = args.getOrNull(1)?.toDoubleOrNull()
            if (stat == null || xp == null || stat !in 0 until STAT_COUNT) {
                message("Usage: ::xp stat amount")
            } else {
                message("Levels gained: ${addXp(stat, xp)}")
            }
        }
    }

    private fun PlayerRef.tele(args: List<String>) {
        val x = args.getOrNull(0)?.toIntOrNull()
        val z = args.getOrNull(1)?.toIntOrNull()
        val level = args.getOrNull(2)?.toIntOrNull() ?: coord.level
        if (x == null || z == null) {
            message("Usage: ::tele x z [level]")
        } else {
            teleport(CoordGrid(x, z, level))
        }
    }

    private fun PlayerRef.item(args: List<String>) {
        val id = args.getOrNull(0)?.toIntOrNull()
        val count = args.getOrNull(1)?.toIntOrNull() ?: 1
        if (id == null) {
            message("Usage: ::item id [count]")
        } else if (!transaction("dev ::item") { add(Invs.INV, id, count) }) {
            message("Couldn't add $count x $id (unknown obj or no space).")
        }
    }

    private companion object {
        const val STAT_COUNT = 24
    }
}
