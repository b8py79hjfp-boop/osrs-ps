/*
 * Adapted from OpenRune-Server `content/other/spawn` SpawnMenuScript (ISC licence, Copyright (c) 2025 RS Mod; see
 * DECISIONS D-069 and content/dev/src/main/resources/pack/NOTICE.md): the search index, ranking, labels, quantity /
 * note / bank / nulls buttons and the spawn rules. The interface and client scripts are OpenRune's pack, unchanged.
 */
package tp.content.dev

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Clientscripts
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * `::spawn` (administrators): a searchable grid of every item. Typing filters live (IF_SCRIPTTRIGGER from the search
 * box); a card click spawns the selected quantity, noted or into the bank as toggled. The results are only drawn on the
 * client: a click is resolved against the server's own result list by card index, never by the obj the client names.
 */
public class SpawnMenu : ContentPlugin {
    override fun ContentScope.register() {
        onCommand("spawn") { if (rank.atLeast(ADMIN)) open() else message("Only administrators can use ::spawn.") }
        onModalClosed(Interfaces.SPAWN_MENU) {
            this[STATE] = null
            showInv(Invs.SPAWN_RESULTS, IntArray(0), IntArray(0))
            clientScript(CS_CHAT_RESTORE_INPUT)
        }
        onScriptTrigger(Components.Spawn_Menu.SEARCHTEXT, "iis") { t -> if (admin()) search(t.args[2] as String) }
        // Focusing the search box is client-side (its onop script); OpenRune enables op 1 there, so the click arrives.
        onButton(Components.Spawn_Menu.SEARCHTEXT) { }
        for (i in 0 until QTY_BUTTON_COUNT) {
            onButton(QTY_BUTTONS[i]) { if (admin()) selectQuantity(i) }
        }
        onButton(Components.Spawn_Menu.NOTEBTN) { if (admin()) state().let { it.note = !it.note }.also { renderButtons() } }
        onButton(Components.Spawn_Menu.BANKBTN) { if (admin()) state().let { it.bank = !it.bank }.also { renderButtons() } }
        onButton(Components.Spawn_Menu.NULLBTN) { if (admin()) toggleNulls() }
        onButton(Components.Spawn_Menu.GRID) { click -> if (admin()) spawn(click) }
    }

    private class State {
        var quantity = 1
        var note = false
        var bank = false
        var query = ""
        var results: List<Int> = emptyList()
    }

    private fun PlayerRef.state(): State = this[STATE] ?: State().also { this[STATE] = it }

    /** OpenRune `isAdmin`: a non-admin with the menu open (rights removed meanwhile) has it closed. */
    private fun PlayerRef.admin(): Boolean {
        if (rank.atLeast(ADMIN) && this[STATE] != null) return true
        closeModal()
        return false
    }

    private fun PlayerRef.open() {
        this[STATE] = State()
        showInv(Invs.SPAWN_RESULTS, IntArray(0), IntArray(0))
        openModal(Components.Toplevel.MAINMODAL, Interfaces.SPAWN_MENU)
        ifEvents(Components.Spawn_Menu.GRID, 0 until MAX_RESULTS * COMPONENTS_PER_CARD, IfEvents.op(1))
        ifEvents(Components.Spawn_Menu.SEARCHTEXT, -1..-1, IfEvents.op(1) or IfEvents.SCRIPT_TRIGGER)
        renderButtons()
    }

    private fun PlayerRef.search(query: String) {
        val s = state()
        s.query = query.take(MAX_QUERY)
        val trimmed = s.query.trim().lowercase()
        s.results = if (trimmed.isEmpty()) emptyList() else index().search(trimmed, MAX_RESULTS, varbit(Varbits.SPAWN_INCLUDE_NULLS) == 1)
        val labels =
            buildString {
                for (obj in s.results) append(objGameval(obj).ifEmpty { "obj_$obj" }.take(LABEL_WIDTH).padEnd(LABEL_WIDTH))
            }
        clientScript(
            Clientscripts.SPAWN_MENU_LABELS,
            Components.Spawn_Menu.GRID,
            Components.Spawn_Menu.SCROLLBAR,
            Components.Spawn_Menu.STATUS,
            labels,
        )
        showInv(Invs.SPAWN_RESULTS, s.results.toIntArray(), IntArray(s.results.size) { 1 })
    }

    private fun PlayerRef.selectQuantity(index: Int) {
        val s = state()
        if (index != QTY_CUSTOM_INDEX) {
            s.quantity = QTY_PRESETS[index]
            return renderButtons()
        }
        clientScript(Clientscripts.SPAWN_MENU_SEARCH_STOP)
        countDialog("Enter spawn quantity:") { n ->
            val st = this[STATE] ?: return@countDialog
            st.quantity = n.coerceAtLeast(1)
            renderButtons()
        }
    }

    private fun PlayerRef.toggleNulls() {
        setVarbit(Varbits.SPAWN_INCLUDE_NULLS, 1 - varbit(Varbits.SPAWN_INCLUDE_NULLS))
        renderButtons()
        search(state().query)
    }

    /** OpenRune `spawn`: notes on request (or when a non-stackable stack won't fit the backpack), adds what fits. */
    private fun PlayerRef.spawn(click: ButtonClick) {
        // A card click arrives twice: once from the card's own op and once from its onop script, which triggers op 1 on
        // the card's icon. Only the icon carries an obj (OpenRune spawns on `it.obj`), so only the icon sub counts.
        if (click.slot % COMPONENTS_PER_CARD != ICON_SUB) return
        val s = state()
        val item = s.results.getOrNull(click.slot / COMPONENTS_PER_CARD) ?: return
        var obj = item
        if (s.note) {
            obj = noted(item).takeIf { it != -1 } ?: return message("${objName(item)} cannot be noted.")
        }
        if (!s.bank && !objStackable(obj) && s.quantity > inv(Invs.INV).freeSlots) {
            noted(obj).takeIf { it != -1 }?.let { obj = it }
        }
        val destination = if (s.bank) "bank" else "inventory"
        val count =
            when {
                s.bank || objStackable(obj) -> s.quantity
                else -> minOf(s.quantity, inv(Invs.INV).freeSlots)
            }
        val added = count > 0 && transaction("::spawn") { add(if (s.bank) Invs.BANK else Invs.INV, obj, count) }
        if (!added) return message("You don't have enough $destination space for ${objName(obj)}.")
        if (s.bank) transmitInv(Invs.BANK)
        message("Spawned ${objName(obj)} x $count to your $destination.")
    }

    private fun PlayerRef.renderButtons() {
        val s = state()
        val custom = s.quantity !in QTY_PRESETS
        clientScript(
            Clientscripts.SPAWN_MENU_BUTTONS,
            if (custom) QTY_CUSTOM_INDEX else QTY_PRESETS.indexOf(s.quantity),
            if (custom) s.quantity.toString() else "X",
            if (s.note) 1 else 0,
            if (s.bank) 1 else 0,
            varbit(Varbits.SPAWN_INCLUDE_NULLS),
        )
    }

    /** OpenRune `SpawnSearchIndex`: every real item (no notes / placeholders / stack-size variants), shortest label first. */
    private class Index(private val entries: List<Entry>) {
        class Entry(val name: String, val gameval: String, val obj: Int) {
            val label: String get() = name.ifEmpty { gameval }
        }

        /** Exact name or gameval, then prefix, then substring matches; unnamed items only with nulls on. */
        fun search(query: String, limit: Int, includeNulls: Boolean): List<Int> {
            val exact = ArrayList<Int>()
            val prefix = ArrayList<Int>()
            val partial = ArrayList<Int>()
            for (e in entries) {
                if (!includeNulls && e.name.isEmpty()) continue
                when {
                    e.name == query || e.gameval == query -> exact
                    e.name.startsWith(query) || e.gameval.startsWith(query) -> prefix
                    e.name.contains(query) || e.gameval.contains(query) -> partial
                    else -> null
                }?.add(e.obj)
            }
            return (exact + prefix + partial).take(limit)
        }
    }

    private fun PlayerRef.index(): Index = worldAttr(INDEX) ?: buildIndex().also { setWorldAttr(INDEX, it) }

    private fun PlayerRef.buildIndex(): Index {
        val variants = HashSet<Int>()
        for (obj in 0 until objTypeCount) objCountVariants(obj).forEach { variants += it }
        val entries =
            (0 until objTypeCount).mapNotNull { obj ->
                if (obj in variants || objIsTemplateCopy(obj)) return@mapNotNull null
                val raw = objName(obj)
                val name = if (raw.isBlank() || raw.equals("null", ignoreCase = true)) "" else raw.lowercase()
                val gameval = objGameval(obj).lowercase()
                if (name.isEmpty() && gameval.isEmpty()) null else Index.Entry(name, gameval, obj)
            }
        return Index(entries.sortedWith(compareBy({ it.label.length }, { it.label }, { it.obj })))
    }

    private companion object {
        val STATE = AttrKey<State>("spawn_menu")
        val INDEX = AttrKey<Index>("spawn_menu_index")

        /** Administrators and above may use the menu. */
        val ADMIN = tp.util.Rank.ADMINISTRATOR

        /** OpenRune constants: the result inv size and the components each card creates (frame, fill, icon, name, label). */
        const val MAX_RESULTS = 60
        const val COMPONENTS_PER_CARD = 5

        /** The card's icon graphic (`spawn_menu_draw`: frame, fill, icon, name, label). */
        const val ICON_SUB = 2

        /** Fixed-width gameval labels the client slices (`spawn_menu_draw`: 32 chars per card). */
        const val LABEL_WIDTH = 32

        /** Longest query kept (the client sends at most a short search string; anything longer is hostile). */
        const val MAX_QUERY = 64

        const val QTY_BUTTON_COUNT = 4
        const val QTY_CUSTOM_INDEX = 3
        val QTY_PRESETS = intArrayOf(1, 100, 1000)
        val QTY_BUTTONS =
            intArrayOf(
                Components.Spawn_Menu.QTYBTN0,
                Components.Spawn_Menu.QTYBTN1,
                Components.Spawn_Menu.QTYBTN2,
                Components.Spawn_Menu.QTYBTN3,
            )

        /** Client script 2158 `chatdefault_restoreinput` (OpenRune `ClientScripts.chatDefaultRestoreInput`). */
        const val CS_CHAT_RESTORE_INPUT = 2158
    }
}
