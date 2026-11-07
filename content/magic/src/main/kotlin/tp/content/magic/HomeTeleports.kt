package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.TimerId
import tp.symbols.Components
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/** A home teleport of one spellbook (OSRS Wiki "Home teleport"): free, 24 ticks, once per 30 minutes. */
private class HomeSpell(val component: Int, val book: Int, val destination: CoordGrid)

/** A running home cast: where it ends and the tick it began. */
private class HomeCast(val destination: CoordGrid, val start: Long)

/**
 * Lumbridge, Edgeville and Lunar Home Teleport (docs/mechanics/teleports.md H1-H4): a 24-tick, multi-stage cast that
 * any movement, interaction or combat interrupts, with a cooldown of 30 minutes shared by all three.
 */
public class HomeTeleports : ContentPlugin {
    override fun ContentScope.register() {
        for (spell in HOMES) onButton(spell.component, SpellTeleports.OP_CAST) { start(spell) }
    }

    private fun PlayerRef.start(spell: HomeSpell) {
        if (isDelayed || Teleports.isBusy(this) || this[CAST] != null) return
        if (varbit(Varbits.SPELLBOOK) != spell.book) return
        val now = tick
        if (now - (this[SharedAttrs.LAST_COMBAT_TICK] ?: Long.MIN_VALUE / 2) < COMBAT_LOCK) {
            return message("You can't cast this spell while in combat.")
        }
        // TP9: the last cast is varp `aide_tele_timer` in minutes since the epoch (RuneLite `LAST_HOME_TELEPORT`), so the
        // cooldown is real time, saved with the account and shown by the client.
        val left = varp(Varps.AIDE_TELE_TIMER) + COOLDOWN_MINUTES - epochMinutes
        if (varp(Varps.AIDE_TELE_TIMER) > 0 && left > 0) {
            return message("You need to wait another $left minutes to cast this spell.")
        }
        val denial = Teleports.denial(this, Teleports.WILDERNESS_LIMIT)
        if (denial != null) return message(denial)
        interrupt()
        this[CAST] = HomeCast(spell.destination, now)
        play(STAGES[0])
        timer(WATCH, 1, soft = true) { step() }
    }

    /** One tick of a running cast: interruption (H4), the next stage (H2) or the move (H3). */
    private fun PlayerRef.step() {
        val cast = this[CAST] ?: return stopTimer(WATCH)
        val elapsed = (tick - cast.start).toInt()
        val interrupted =
            isMoving || isInteracting ||
                (this[SharedAttrs.LAST_COMBAT_TICK] ?: Long.MIN_VALUE) >= cast.start ||
                Teleports.denial(this, Teleports.WILDERNESS_LIMIT) != null
        if (interrupted) return cancel(this)
        if (elapsed >= CAST_TICKS) {
            this[CAST] = null
            stopTimer(WATCH)
            setVarp(Varps.AIDE_TELE_TIMER, epochMinutes.toInt())
            teleport(cast.destination)
            anim(RESET_ANIM)
            return
        }
        STAGES.firstOrNull { it.at == elapsed }?.let { play(it) }
    }

    private fun PlayerRef.play(stage: CastStage) {
        anim(stage.seq)
        spotAnim(stage.spot)
    }

    internal companion object {
        private val CAST = AttrKey<HomeCast>("home_teleport_cast")

        private val WATCH = TimerId("home_teleport")

        /** Stops a running home cast, if any (another teleport or an interruption). */
        fun cancel(player: PlayerRef) {
            if (player[CAST] == null) return
            player[CAST] = null
            player.stopTimer(WATCH)
            player.anim(RESET_ANIM)
        }

        /** Wiki "Home Teleport": "24 ticks (default)". */
        private const val CAST_TICKS = 24

        /** Wiki "Lumbridge Home Teleport": a 30-minute cooldown. */
        private const val COOLDOWN_MINUTES = 30

        /** Wiki "Home teleport": cast only "10 seconds or more after combat has ended (similar to logging out)": wilderness.md W7. */
        private const val COMBAT_LOCK = 16

        /** Animation id -1 stops the current animation (protocol `Animate`). */
        private const val RESET_ANIM = -1

        /**
         * The default animation's five stages (teleports.md H2). Seqs/spotanims are the cache's `aide_*` ids; the start
         * ticks are each earlier seq's length without its trailing hold frame, in ticks rounded up (seq 4847: 149
         * cycles, 4850: 43, 4853: 58, 4855: 150), so the last stage (seq 4857, 260 cycles) runs until tick 24. OPEN-TP10.
         */
        @Suppress("MagicNumber")
        private val STAGES: List<CastStage> =
            listOf(
                CastStage(0, Seqs.AIDE_DRAWING_CHALK_CIRCLE, SpotAnims.AIDE_CHALK_CIRCLE),
                CastStage(5, Seqs.AIDE_SITTING_DOWN_CROSSLEGGED, SpotAnims.AIDE_CHALK_CIRCLE_STATIC),
                CastStage(7, Seqs.AIDE_PLAYER_GETTING_BOOK, SpotAnims.AIDE_PLAYER_BOOK_GET),
                CastStage(9, Seqs.AIDE_RECITING_INCANTATION, SpotAnims.AIDE_PLAYER_BOOK_PORTAL),
                CastStage(14, Seqs.AIDE_PLAYER_TELEPORTING, SpotAnims.AIDE_PLAYER_TELEPORT),
            )

        /**
         * Wiki: Lumbridge (3221, 3218), Edgeville (centre of the (3087..3090, 3501..3504) polygon), Lunar Isle (2094, 3913),
         * Arceuus: the Dark Altar (1700, 3882).
         */
        @Suppress("MagicNumber")
        private val HOMES: List<HomeSpell> =
            listOf(
                HomeSpell(Components.Magic_Spellbook.TELEPORT_HOME_STANDARD, Book.STANDARD, CoordGrid(3221, 3218, 0)),
                HomeSpell(Components.Magic_Spellbook.TELEPORT_HOME_ZAROS, Book.ANCIENT, CoordGrid(3088, 3502, 0)),
                HomeSpell(Components.Magic_Spellbook.TELEPORT_HOME_LUNAR, Book.LUNAR, CoordGrid(2094, 3913, 0)),
                HomeSpell(Components.Magic_Spellbook.TELEPORT_HOME_ARCEUUS, Book.ARCEUUS, CoordGrid(1700, 3882, 0)),
            )
    }
}
