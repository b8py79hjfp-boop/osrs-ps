package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * Ranged ammunition use and recovery (combat-data.md CD-A1..A4; Wiki "Ava's device", "Ava's assembler"): each shot,
 * 20% of ammunition breaks; an Ava's device (attractor 60%, accumulator 72%, assembler 80%, their max-cape forms) in
 * the cape slot keeps its share unless a metal body interferes; the rest drops under the target, for the shooter.
 */
internal object AmmoRecovery {
    private const val PERMILLE = 1000

    /** 80% of shots don't break (20% "break on impact"). */
    private const val NOT_BROKEN = 800
    private const val ATTRACTOR = 600
    private const val ACCUMULATOR = 720
    private const val ASSEMBLER = 800

    private val interfering: Set<String> by lazy {
        val text = AmmoRecovery::class.java.getResource("ava-interference.tsv")?.readText() ?: error("ava-interference.tsv missing")
        text
            .lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .drop(1)
            .map { it.trim() }
            .toSet()
    }

    /** CD-A2: the device's recovery permille, or 0 without one or with an interfering body. */
    fun recovery(player: PlayerRef): Int {
        val cape = player.objName(player.worn(Wear.CAPE)).lowercase()
        val rate =
            when {
                "assembler" in cape -> ASSEMBLER
                "accumulator" in cape -> ACCUMULATOR
                "attractor" in cape -> ATTRACTOR
                else -> 0
            }
        if (rate == 0 || player.objName(player.worn(Wear.BODY)) in interfering) return 0
        return rate
    }

    /**
     * CD-A1: spends one of [obj] from worn [slot] for a shot at [target]: recovered (nothing removed), dropped at the
     * target's tile (private to the shooter first), or broken. False when the slot no longer holds it.
     */
    fun spend(player: PlayerRef, slot: Int, obj: Int, target: EntityRef): Boolean {
        val roll = player.random(PERMILLE)
        if (roll < recovery(player)) return true
        val at = target.coord
        return player.transaction("ammo") {
            removeAt(Invs.WORN, slot, obj, 1) && (roll >= NOT_BROKEN || createOnGround(obj, 1, at, player.username))
        }
    }
}
