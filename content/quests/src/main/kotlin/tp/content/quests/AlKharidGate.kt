package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * The Al Kharid toll gate (docs/mechanics/quest-prince-ali-rescue.md PA7). Sources: OSRS Wiki "Gate (Al Kharid)", "Border Guard",
 * "Transcript:Border Guard"; cache locs 44598 / 44599 (the gate halves, multi-locs on varp 273: from value 100 they show the
 * free gate 44050 / 44051 without "Pay-toll", so the Prince escaping is the "friend of Al-Kharid" bound) and NPCs 4287 / 4288.
 * The gate has no open model in `doors.tsv`, so the player is moved through it (OPEN-QUA6).
 */
public class AlKharidGate : ContentPlugin {
    override fun ContentScope.register() {
        for (gate in intArrayOf(Locs.KHARIDMETALGATECLOSEDL, Locs.KHARIDMETALGATECLOSEDR)) {
            onOpLoc(gate, OP_OPEN) { loc -> open(loc, null) }
            onOpLoc(gate, OP_PAY) { loc -> pay(loc) }
        }
        for (guard in intArrayOf(Npcs.BORDERGUARD1, Npcs.BORDERGUARD2)) {
            onOpNpc(guard, OP_TALK) { who ->
                player.stopInteraction()
                open(null, who)
            }
        }
    }

    private val free: (tp.plugin.PlayerRef) -> Boolean = { QuestsB.PRINCE_ALI_RESCUE.stage(it) >= FRIEND_STAGE }

    /** "Open" on the gate (or talking to a guard): the Wiki's before / after dialogue. */
    private suspend fun PlayerScript.open(clicked: LocRef?, guard: NpcRef?) {
        val p = player
        val gate = clicked ?: nearestGate()
        say(guard, "Can I come through this gate?", asPlayer = true)
        if (free(p)) {
            say(guard, "You may pass for free, you are a friend of Al-Kharid.")
            gate?.let { p.stepAcross(it) }
            return
        }
        say(guard, "You must pay a toll of 10 gold coins to pass.")
        when (choice("Select an Option", "No thank you, I'll walk around.", "Who does my money go to?", "Yes, ok.")) {
            1 -> {
                me("No, thank you. I'll walk around.")
                say(guard, "Ok suit yourself.")
            }

            2 -> {
                me("Who does my money go to?")
                say(guard, "The money goes to the city of Al-Kharid.")
            }

            3 -> {
                me("Yes, ok.")
                payToll(gate)
            }

            else -> {
                Unit
            }
        }
    }

    /** "Pay-toll(10gp)": the "Yes, ok." branch without the talk (the Wiki gives no separate text, OPEN-QUC3). */
    private suspend fun PlayerScript.pay(gate: LocRef) {
        if (free(player)) {
            player.stepAcross(gate)
        } else {
            payToll(gate)
        }
    }

    /** The gate half nearest to the player, for talking to a guard (the Wiki: the guard lets the player through the gate). */
    private fun PlayerScript.nearestGate(): LocRef? =
        GATE_TILES.sortedBy { player.coord.chebyshev(it) }.firstNotNullOfOrNull { player.locAt(it, WALL_SHAPE) }

    private suspend fun PlayerScript.payToll(gate: LocRef?) {
        val p = player
        if (!p.has(Objs.COINS, TOLL)) {
            me("Oh dear, I don't actually seem to have enough money.")
            return
        }
        if (p.take(Objs.COINS, TOLL, "al kharid toll")) {
            box("You pay the guard.")
            gate?.let { p.stepAcross(it) }
        }
    }

    /** The guard (the clicked one, else the Wiki's "Guard" under the guard's head) or the player speaks one line. */
    private suspend fun PlayerScript.say(guard: NpcRef?, line: String, asPlayer: Boolean = false) {
        when {
            asPlayer -> me(line)
            guard != null -> npc(guard, line)
            else -> npcAs(Npcs.BORDERGUARD1, "Border Guard", line)
        }
    }

    private companion object {
        const val OP_OPEN = 1
        const val OP_PAY = 4
        const val OP_TALK = 1

        /** Loc shape of a straight wall (the gate halves are walls). */
        const val WALL_SHAPE = 0

        /** The gate halves' tiles (cache map 51 50: locs 44598 at 3268,3227 and 44599 at 3268,3228). */
        val GATE_TILES = listOf(CoordGrid(3268, 3227, 0), CoordGrid(3268, 3228, 0))

        /** Wiki "Border Guard": the toll is 10 coins. */
        const val TOLL = 10

        /** Varp 273 value from which the cache shows the free gate (multi-loc 44598's last entry): the Prince is free. */
        const val FRIEND_STAGE = 100
    }
}
