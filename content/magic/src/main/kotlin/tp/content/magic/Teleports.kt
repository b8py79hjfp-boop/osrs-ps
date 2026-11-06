package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/** One animation + graphic step of a cast, [at] ticks after the click ([spot] -1 = none). */
internal class CastStage(val at: Int, val seq: Int, val spot: Int)

/**
 * How a teleport looks and how long it stalls the player: [stages] (the first one plays on the click tick) and [ticks]
 * until the move (docs/mechanics/teleports.md T2, T3). All ids are cache symbols; their use as teleport visuals is
 * OPEN-TP3 (the Wiki has no animation ids).
 */
internal enum class CastStyle(val stages: List<CastStage>, val ticks: Int) {
    /** Standard spellbook and jewellery: seq 714 (`human_castteleport`) and spotanim 111 (`teleport_casting`). */
    STANDARD(listOf(CastStage(0, Seqs.HUMAN_CASTTELEPORT, SpotAnims.TELEPORT_CASTING)), STANDARD_TICKS),

    /** Ancient Magicks: seq 1979 (`zaros_vertical_casting`) and spotanim 392 (`zaros_teleport`); Wiki speed 5 ticks. */
    ANCIENT(listOf(CastStage(0, Seqs.ZAROS_VERTICAL_CASTING, SpotAnims.ZAROS_TELEPORT)), ANCIENT_TICKS),

    /** Lunar spellbook: seq 1816 and spotanim 747 (`lunar_teleport_spotanim`); Wiki speed 4 ticks. */
    LUNAR(listOf(CastStage(0, Seqs.HUMAN_TELEPORT_OTHER_IMPACT, SpotAnims.LUNAR_TELEPORT_SPOTANIM)), STANDARD_TICKS),
}

/**
 * Magic tablet "Break": seq 4069 (`poh_smash_magic_tablet`, 44 client cycles), then seq 4071
 * (`poh_absorb_tablet_teleport`, 41 cycles) with spotanim 678 (`poh_absorb_tablet_magic`) once the first is done
 * (44 cycles = 1.5 ticks, rounded up to tick 2). The move comes after the same ticks as the spell it stores (OPEN-TP3).
 */
internal val TABLET_STAGES: List<CastStage> =
    listOf(
        CastStage(0, Seqs.POH_SMASH_MAGIC_TABLET, -1),
        CastStage(TABLET_ABSORB_AT, Seqs.POH_ABSORB_TABLET_TELEPORT, SpotAnims.POH_ABSORB_TABLET_MAGIC),
    )

/**
 * Ticks the player is stalled by a Wiki "4 ticks" (standard, lunar) cast, see OPEN-S7 in scripts.md: osrs-docs says a
 * Falador teleport "cause[s] a three tick delay"; Henke says four. We stall speed - 1 (teleports.md T2).
 */
private const val STANDARD_TICKS = 3

/** Ancient Magicks teleports have Wiki speed 5 ticks: speed - 1 (teleports.md T2). */
private const val ANCIENT_TICKS = 4

/** Tick of the tablet's second stage (teleports.md T3). */
private const val TABLET_ABSORB_AT = 2

/** Shared teleport rules and the delayed action (docs/mechanics/teleports.md). */
internal object Teleports {
    /** Wilderness level above which spells, tablets and most jewellery fail (Wiki "Wilderness#Teleport restrictions"). */
    const val WILDERNESS_LIMIT = 20

    /** Highest level for the dragonstone jewellery and Ring of wealth (Wiki "Wilderness#Member teleportation up to level 30"). */
    const val DRAGONSTONE_WILDERNESS_LIMIT = 30

    /**
     * OPEN-TP1: the Wiki says a Tele Blocked player is told how long remains (2 March 2017 change) but quotes no text;
     * this is the classic message.
     */
    private const val BLOCKED = "A magical force stops you from teleporting."

    /** Set from the click until the move; stops a second cast in the same input phase (teleports.md T2). */
    private val BUSY = AttrKey<Boolean>("teleport_busy")

    fun isBusy(player: PlayerRef): Boolean = player[BUSY] == true

    /**
     * Why [player] can't teleport now, or null (teleports.md T4): Tele Block first, then the Wilderness level against
     * [wildernessLimit]. Both are read at the click only (OPEN-TP2).
     */
    fun denial(player: PlayerRef, wildernessLimit: Int): String? =
        when {
            (player[SharedAttrs.TELEBLOCK_UNTIL] ?: 0L) > player.tick -> {
                BLOCKED
            }

            // Boss lairs (boss-lairs.md BL5): "unable to teleport for 3 ticks" after entering or leaving. OPEN-BL4: no
            // message is published; the game's usual "You can't do that right now" stand-in.
            (player[SharedAttrs.TELEPORT_DELAY_UNTIL] ?: 0L) > player.tick -> {
                "You can't teleport right now."
            }

            (player[SharedAttrs.WILDERNESS_LEVEL] ?: 0) > wildernessLimit -> {
                "You can't teleport above level $wildernessLimit in the Wilderness."
            }

            else -> {
                null
            }
        }

    /**
     * Starts the stalled cast (teleports.md T2): interrupts the player, plays the first stage now, then a strong script
     * plays the later [stages], delays [ticks] in total and moves the player to [destination].
     */
    fun start(player: PlayerRef, stages: List<CastStage>, ticks: Int, destination: CoordGrid) {
        player.interrupt()
        HomeTeleports.cancel(player) // any other teleport ends a running home cast (teleports.md H4)
        player[BUSY] = true
        play(player, stages[0])
        player.startScript {
            val self = this.player
            try {
                var elapsed = 0
                for (stage in stages.drop(1)) {
                    delay(stage.at - elapsed)
                    elapsed = stage.at
                    play(self, stage)
                }
                delay(ticks - elapsed)
                self.teleport(destination)
                self.anim(RESET_ANIM)
            } finally {
                self[BUSY] = null
            }
        }
    }

    fun start(player: PlayerRef, style: CastStyle, destination: CoordGrid) {
        start(player, style.stages, style.ticks, destination)
    }

    private fun play(player: PlayerRef, stage: CastStage) {
        player.anim(stage.seq)
        if (stage.spot >= 0) player.spotAnim(stage.spot)
    }

    /** Animation id -1 stops the current animation (protocol `Animate`). */
    private const val RESET_ANIM = -1
}
