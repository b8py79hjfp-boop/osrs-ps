package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.StatAdvanceEvent
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Jingles
import tp.symbols.SpotAnims
import tp.symbols.Varbits

/**
 * Level-ups (docs/mechanics/level-up.md): the chat message, the `levelup_display` chatbox popup with the skill's icon
 * layer, the skill's jingle and the fireworks. Several level-ups at once queue their popups (LU4).
 */
public class LevelUp : ContentPlugin {
    override fun ContentScope.register() {
        events.on<StatAdvanceEvent> { e -> e.player.advanced(e.stat, e.to) }
        onButton(Components.Levelup_Display.CONTINUE) { next() }
    }

    private fun PlayerRef.advanced(stat: Int, level: Int) {
        val name = Stats.name(stat)
        // LU1: the chat message (RuneLite ScreenshotPlugin LEVEL_UP_MESSAGE_PATTERN).
        message(
            if (level >= Stats.MAX_LEVEL) {
                "Congratulations, you've reached the highest possible $name level of ${Stats.MAX_LEVEL}."
            } else {
                "Congratulations, you've just advanced your $name level. You are now level $level."
            },
        )
        // LU3: fireworks; the level-99 burst at 99.
        spotAnim(if (level >= Stats.MAX_LEVEL) SpotAnims.LEVELUP_99_ANIM else SpotAnims.LEVELUP_ANIM)
        JINGLES.getOrNull(stat)?.let { jingle(it) }
        // LU2: the popup unless the "Level-up interface" setting is off.
        if (varbit(Varbits.OPTION_LEVEL_UP_MESSAGE_DISABLED) == 1) return
        val queue = this[PENDING] ?: ArrayDeque<Int>().also { this[PENDING] = it }
        // A popup closed some other way (walking off, another interface) leaves nothing waiting behind it.
        if (!isOpen(Interfaces.LEVELUP_DISPLAY)) queue.clear()
        queue.addLast(stat shl LEVEL_BITS or level)
        if (queue.size == 1) show(stat, level)
    }

    /** Continue on the popup: the next queued level-up, or close. */
    private fun PlayerRef.next() {
        val queue = this[PENDING] ?: return closeModal()
        queue.removeFirstOrNull()
        val packed = queue.firstOrNull() ?: return closeModal()
        show(packed ushr LEVEL_BITS, packed and LEVEL_MASK)
    }

    private fun PlayerRef.show(stat: Int, level: Int) {
        val name = Stats.name(stat)
        // RS Mod `ifOpenChat` (as Dialogues.openChat): unclamped chat modal, chatbox background reset.
        setVarbit(Varbits.CHATMODAL_UNCLAMP, 1)
        clientScript(CS_CHATBOX_RESET_BACKGROUND)
        openModal(Components.Chatbox.CHATMODAL, Interfaces.LEVELUP_DISPLAY)
        ICONS.forEachIndexed { i, component -> ifHide(component, i != stat) }
        ifHide(Components.Levelup_Display.COMBAT, true)
        val article = if (name.first() in VOWELS) "an" else "a"
        // LU2: RuneLite ScreenshotPlugin(Test): TEXT1 the congratulation in dark blue, TEXT2 "Your X level is now N."
        ifText(Components.Levelup_Display.TEXT1, "<col=000080>Congratulations, you've just advanced $article $name level.</col>")
        ifText(Components.Levelup_Display.TEXT2, "Your $name level is now $level.")
        ifText(Components.Levelup_Display.CONTINUE, CONTINUE_TEXT)
        ifEvents(Components.Levelup_Display.CONTINUE, -1..-1, IfEvents.PAUSE_BUTTON)
    }

    private companion object {
        val PENDING = AttrKey<ArrayDeque<Int>>("levelup.pending")

        /** A queued level-up is `stat << LEVEL_BITS | level`. */
        const val LEVEL_BITS = 8
        const val LEVEL_MASK = (1 shl LEVEL_BITS) - 1

        /** Client script `chatbox_reset_background` (RS Mod `ifOpenChat`). */
        const val CS_CHATBOX_RESET_BACKGROUND = 2379
        const val CONTINUE_TEXT = "Click here to continue"
        val VOWELS = setOf('A', 'E', 'I', 'O', 'U')

        /** `levelup_display` icon layer of each stat, in [Stats] order. */
        val ICONS =
            intArrayOf(
                Components.Levelup_Display.ATTACK,
                Components.Levelup_Display.DEFENCE,
                Components.Levelup_Display.STRENGTH,
                Components.Levelup_Display.HITPOINTS,
                Components.Levelup_Display.RANGED,
                Components.Levelup_Display.PRAYER,
                Components.Levelup_Display.MAGIC,
                Components.Levelup_Display.COOKING,
                Components.Levelup_Display.WOODCUTTING,
                Components.Levelup_Display.FLETCHING,
                Components.Levelup_Display.FISHING,
                Components.Levelup_Display.FIREMAKING,
                Components.Levelup_Display.CRAFTING,
                Components.Levelup_Display.SMITHING,
                Components.Levelup_Display.MINING,
                Components.Levelup_Display.HERBLORE,
                Components.Levelup_Display.AGILITY,
                Components.Levelup_Display.THIEVING,
                Components.Levelup_Display.SLAYER,
                Components.Levelup_Display.FARMING,
                Components.Levelup_Display.RUNECRAFT,
                Components.Levelup_Display.HUNTER,
                Components.Levelup_Display.CONSTRUCTION,
                Components.Levelup_Display.SAILING,
            )

        /** Each stat's level-up jingle (cache jingle names `advance_<skill>`; OPEN-LU1 for the `2` variants). */
        val JINGLES =
            intArrayOf(
                Jingles.ADVANCE_ATTACK,
                Jingles.ADVANCE_DEFENSE,
                Jingles.ADVANCE_STRENGTH,
                Jingles.ADVANCE_HITPOINTS,
                Jingles.ADVANCE_RANGED,
                Jingles.ADVANCE_PRAYER,
                Jingles.ADVANCE_MAGIC,
                Jingles.ADVANCE_COOKING,
                Jingles.ADVANCE_WOODCUTTING,
                Jingles.ADVANCE_FLETCHING,
                Jingles.ADVANCE_FISHING,
                Jingles.ADVANCE_FIREMARKING,
                Jingles.ADVANCE_CRAFTING,
                Jingles.ADVANCE_SMITHING,
                Jingles.ADVANCE_MINING,
                Jingles.ADVANCE_HERBLAW,
                Jingles.ADVANCE_AGILITY,
                Jingles.ADVANCE_THIEVING,
                Jingles.ADVANCE_SLAYER,
                Jingles.FARMING_LEVELUP,
                Jingles.ADVANCE_RUNECRAFT,
                Jingles.ADVANCE_HUNTING,
                Jingles.ADVANCE_CARPENTRY,
                Jingles.ADVANCE_SAILING,
            ).asList()
    }
}
