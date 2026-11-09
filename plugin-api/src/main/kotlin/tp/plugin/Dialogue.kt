package tp.plugin

import tp.engine.PauseInput
import tp.protocol.api.ServerMessage
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Seqs
import tp.symbols.Varbits

/**
 * Chatbox dialogues as suspending script steps (docs/mechanics/dialogue.md DL1–DL6), ported from RS Mod's
 * `ProtectedAccess` dialogue functions and `PlayerInterfaceExtensions` (ISC): an NPC or player chat head, a message
 * box, an item box and the option menu. Each step opens its chatbox interface and waits for "Click here to continue" or
 * a pick; long text is split into pages of up to four lines on the dialogue font's real glyph widths.
 */
public enum class Expression(internal val oneLine: Int) {
    NEUTRAL(Seqs.CHATNEU1),
    HAPPY(Seqs.CHATHAP1),
    CONFUSED(Seqs.CHATCON1),
    SHOCKED(Seqs.CHATSHOCK1),
    SAD(Seqs.CHATSKULL1),
    ANGRY(Seqs.CHATBORED1),
    SCARED(Seqs.CHATSCARED1),
    SHIFTY(Seqs.CHATSHIFTY1),
    LAUGHING(Seqs.CHATLAUGH1),
    QUIZ(Seqs.CHATQUIZ1),
    DRUNK(Seqs.CHATDRUNK1),
    ;

    /** The variant for [lines] lines (`chatneu1..4` etc. are consecutive seq ids). */
    internal fun forLines(lines: Int): Int = oneLine + (lines.coerceIn(1, MAX_LINES) - 1)
}

/** An NPC speaks: its chat head and name on the left (`chat_left`). */
public suspend fun PlayerScript.chatNpc(npc: NpcRef, text: String, expression: Expression = Expression.NEUTRAL) {
    npc.face(player)
    player.face(npc)
    // A multi-NPC (spawned by its parent id, config name "null") speaks as the type this player sees (I11).
    val shown = player.p.world.seenNpcType(player.p, npc.type)
    if (shown >= 0 && shown != npc.type) {
        val name =
            player.p.world.data.configs.npcs[shown]
                ?.name ?: npc.displayName
        return chatNpcAs(shown, name, text, expression)
    }
    chatNpcAs(npc.type, npc.displayName, text, expression)
}

/**
 * Speech of a speaker that is not the NPC clicked (a second character in a cutscene conversation): the chat head of NPC type
 * [npcType] under [name]. No NPC turns to face the player.
 */
public suspend fun PlayerScript.chatNpcAs(npcType: Int, name: String, text: String, expression: Expression = Expression.NEUTRAL) {
    for (page in Dialogues.pages(player, text, CHAT_WIDTH)) {
        val p = player.p
        p.session.send(ServerMessage.GameMessage("$name|${page.text}", DIALOGUE_CHAT_TYPE))
        Dialogues.openChat(player, Interfaces.CHAT_LEFT)
        p.session.send(ServerMessage.IfNpcHead(Components.Chat_Left.HEAD, npcType))
        p.session.send(ServerMessage.IfAnim(Components.Chat_Left.HEAD, expression.forLines(page.lines)))
        player.ifText(Components.Chat_Left.NAME, name)
        Dialogues.text(player, Components.Chat_Left.TEXT, page, Dialogues::chatLineHeight)
        Dialogues.pauseButton(player, Components.Chat_Left.CONTINUE)
        if (!awaitContinue()) return
    }
}

/** The player speaks: their chat head and name on the right (`chat_right`). */
public suspend fun PlayerScript.chatPlayer(text: String, expression: Expression = Expression.NEUTRAL) {
    for (page in Dialogues.pages(player, text, CHAT_WIDTH)) {
        val p = player.p
        p.session.send(ServerMessage.GameMessage("${player.username}|${page.text}", DIALOGUE_CHAT_TYPE))
        Dialogues.openChat(player, Interfaces.CHAT_RIGHT)
        p.session.send(ServerMessage.IfPlayerHead(Components.Chat_Right.HEAD))
        p.session.send(ServerMessage.IfAnim(Components.Chat_Right.HEAD, expression.forLines(page.lines)))
        player.ifText(Components.Chat_Right.NAME, player.username)
        Dialogues.text(player, Components.Chat_Right.TEXT, page, Dialogues::chatLineHeight)
        Dialogues.pauseButton(player, Components.Chat_Right.CONTINUE)
        if (!awaitContinue()) return
    }
}

/** A plain message box (`messagebox`). */
public suspend fun PlayerScript.mesbox(text: String) {
    for (page in Dialogues.pages(player, text, MESBOX_WIDTH)) {
        player.p.world.interfaces
            .openModal(player.p, Components.Chatbox.CHATMODAL, Interfaces.MESSAGEBOX)
        Dialogues.text(player, Components.Messagebox.TEXT, page, Dialogues::mesLineHeight)
        Dialogues.pauseButton(player, Components.Messagebox.CONTINUE)
        player.clientScript(CS_MESBOX_INIT, "0")
        if (!awaitContinue()) return
    }
}

/** An item and a message (`objectbox`); [zoom] is the model zoom (RS Mod default 400). */
public suspend fun PlayerScript.objbox(obj: Int, text: String, zoom: Int = DEFAULT_ZOOM) {
    Dialogues.openChat(player, Interfaces.OBJECTBOX)
    player.clientScript(CS_OBJBOX_BUTTONS, CONTINUE_TEXT)
    player.ifEvents(Components.Objectbox.UNIVERSE, 0..1, IfEvents.PAUSE_BUTTON)
    player.ifObject(Components.Objectbox.ITEM, obj, zoom)
    player.ifText(Components.Objectbox.TEXT, text)
    awaitContinue()
}

/**
 * The option menu (`chatmenu`, client script 58): [title] over 2..5 [options]; returns the 1-based pick, or 0 if the
 * menu was closed instead.
 */
public suspend fun PlayerScript.choice(title: String = "Select an Option", vararg options: String): Int {
    require(options.size in 2..MAX_OPTIONS) { "choice menus have 2..$MAX_OPTIONS options" }
    Dialogues.openChat(player, Interfaces.CHATMENU)
    player.clientScript(CS_CHATBOX_MULTI_INIT, title, options.joinToString("|"))
    player.ifEvents(Components.Chatmenu.OPTIONS, 1..options.size, IfEvents.PAUSE_BUTTON)
    val input = awaitInput() as? PauseInput ?: return 0
    if (input.component != Components.Chatmenu.OPTIONS || input.sub !in 1..options.size) return 0
    player.closeModal()
    return input.sub
}

/**
 * The destroy confirmation (`confirmdestroy` 584, client script 814: obj, count, question, [message]): true only when
 * "Yes" is clicked (docs/mechanics/gameframe-buttons.md DS1).
 */
public suspend fun PlayerScript.confirmDestroy(obj: Int, count: Int, message: String): Boolean {
    Dialogues.openChat(player, Interfaces.CONFIRMDESTROY)
    player.clientScript(CS_CONFIRM_DESTROY, obj, count, DESTROY_QUESTION, message)
    player.ifEvents(Components.Confirmdestroy.YES, -1..-1, IfEvents.PAUSE_BUTTON)
    player.ifEvents(Components.Confirmdestroy.NO, -1..-1, IfEvents.PAUSE_BUTTON)
    val input = awaitInput() as? PauseInput ?: return false
    player.closeModal()
    return input.component == Components.Confirmdestroy.YES
}

/** Waits for a click on a pause button (IfEvents.PAUSE_BUTTON): its component, or null if something else answered. */
public suspend fun PlayerScript.awaitPauseButton(): Int? = (awaitInput() as? PauseInput)?.component

/** Waits for a count answer (also what client-built keypads send, e.g. the bank PIN): null if something else answered. */
public suspend fun PlayerScript.awaitCount(): Int? = awaitInput() as? Int

/** Waits for the continue click; false if something else answered (the script should stop). */
private suspend fun PlayerScript.awaitContinue(): Boolean {
    val input = awaitInput()
    if (input !is PauseInput) return false
    player.closeModal()
    return true
}

internal object Dialogues {
    class Page(val text: String, val lines: Int)

    /** RS Mod `ifOpenChat`: unclamped chat modal (`modal_infinitewidthandheight`), chatbox background reset (2379). */
    fun openChat(player: PlayerRef, iface: Int) {
        player.setVarbit(Varbits.CHATMODAL_UNCLAMP, 1)
        player.clientScript(CS_CHATBOX_RESET_BACKGROUND)
        player.p.world.interfaces
            .openModal(player.p, Components.Chatbox.CHATMODAL, iface)
    }

    fun text(player: PlayerRef, component: Int, page: Page, lineHeight: (Int) -> Int) {
        player.ifText(component, page.text)
        // RS Mod `ifSetTextAlign`: client script 600 (alignH 1, alignV 1, line height for the line count).
        player.clientScript(CS_TEXT_ALIGN, 1, 1, lineHeight(page.lines), component)
    }

    fun pauseButton(player: PlayerRef, component: Int) {
        player.ifEvents(component, -1..-1, IfEvents.PAUSE_BUTTON)
        player.ifText(component, CONTINUE_TEXT)
    }

    /** RS Mod `TextAlignment.chatLineHeight` / `mesLineHeight`. */
    fun chatLineHeight(lines: Int): Int =
        when (lines) {
            2 -> CHAT_TWO_LINES
            3 -> CHAT_THREE_LINES
            else -> CHAT_DEFAULT
        }

    fun mesLineHeight(lines: Int): Int =
        when (lines) {
            2 -> MES_TWO_LINES
            3 -> MES_THREE_LINES
            MAX_LINES -> MES_FOUR_LINES
            else -> 0
        }

    /**
     * RS Mod `TextAlignment.generatePageList`: words wrap at [width] pixels of the dialogue font (tags take no width;
     * `<br>` forces a break), four lines a page.
     */
    fun pages(player: PlayerRef, text: String, width: Int): List<Page> {
        val font = player.p.world.data.dialogueFont
        val lines = ArrayList<String>()
        for (paragraph in text.split("<br>")) {
            var line = StringBuilder()
            var lineWidth = 0
            for (word in paragraph.split(' ')) {
                val w = width(font, word)
                val space = if (line.isEmpty()) 0 else width(font, " ")
                if (line.isNotEmpty() && lineWidth + space + w > width) {
                    lines += line.toString()
                    line = StringBuilder(word)
                    lineWidth = w
                } else {
                    if (line.isNotEmpty()) line.append(' ')
                    line.append(word)
                    lineWidth += space + w
                }
            }
            lines += line.toString()
        }
        return lines.chunked(MAX_LINES).map { Page(it.joinToString("<br>"), it.size) }
    }

    private fun width(font: IntArray, s: String): Int {
        if (font.isEmpty()) return s.length * FALLBACK_GLYPH
        var w = 0
        var inTag = false
        for (c in s) {
            when {
                c == '<' -> inTag = true
                c == '>' -> inTag = false
                !inTag -> w += font[c.code and BYTE_MASK]
            }
        }
        return w
    }

    private const val FALLBACK_GLYPH = 7
    private const val BYTE_MASK = 0xFF
    private const val CHAT_DEFAULT = 16
    private const val CHAT_TWO_LINES = 28
    private const val CHAT_THREE_LINES = 20
    private const val MES_TWO_LINES = 31
    private const val MES_THREE_LINES = 24
    private const val MES_FOUR_LINES = 17
}

/** Chat history type of dialogue lines (RuneLite `ChatMessageType.DIALOG`; RS Mod `ChatType.Dialogue`): "name|text". */
private const val DIALOGUE_CHAT_TYPE = 11

/** RS Mod `Constants.cm_pausebutton`. */
private const val CONTINUE_TEXT = "Click here to continue"

/** RS Mod `TextAlignment`: CHAT_MAX_LINE_PIXEL_WIDTH / MESBOX_MAX_LINE_PIXEL_WIDTH. */
private const val CHAT_WIDTH = 380
private const val MESBOX_WIDTH = 470
private const val MAX_LINES = 4
private const val MAX_OPTIONS = 5
private const val DEFAULT_ZOOM = 400
private const val CS_TEXT_ALIGN = 600
private const val CS_CHATBOX_RESET_BACKGROUND = 2379
private const val CS_CHATBOX_MULTI_INIT = 58
private const val CS_CONFIRM_DESTROY = 814

/** OPEN-DS1: the question line of the destroy confirmation (the client script takes it as an argument). */
private const val DESTROY_QUESTION = "Are you sure you want to destroy this object?"

private const val CS_OBJBOX_BUTTONS = 2868
private const val CS_MESBOX_INIT = 1508
