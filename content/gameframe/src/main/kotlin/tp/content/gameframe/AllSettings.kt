/*
 * Adapted from OpenRune-Server `content/interfaces/settings` AllSettingsScript, Settings, SettingUtils and the
 * `settings_configs` / `account_default_vars` tables (ISC licence, Copyright (c) 2025 RS Mod; DECISIONS D-078): which
 * player variable each setting id writes, its default / range / prompt, and the new-account defaults.
 */
package tp.content.gameframe

import tp.plugin.AttrKey
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Varbits

/**
 * The "All settings" window (interface 134; docs/mechanics/settings.md ST1-ST7). The client draws every setting from
 * cache structs; a click names the setting by its row, and the server writes the setting's player variable (a varbit or
 * varp, from `settings-configs.tsv`), which the player save keeps. Checkboxes flip, dropdowns pick a value, number
 * settings ask for an amount, volume sliders send a script trigger.
 */
public class AllSettings : ContentPlugin {
    override fun ContentScope.register() {
        onLogin { applyDefaults(this) }
        onButton(Components.Settings.CLOSE) { closeModal() }
        onButton(Components.Settings.SEARCHBAR_IMAGE) {
            this[CATEGORY] = -1
            setVarbit(Varbits.FLOATER_IS_SEARCHING, 1)
            setVarbit(Varbits.FLOATER_SEARCH_LISTEN_FOR_KEYBOARD, 1)
        }
        onButton(Components.Settings.CATEGORIES_CLICKZONE) { click -> selectCategory(this, click.slot) }
        onButton(Components.Settings.TOOLTIP_INSIDE_IF_CLICKZONE) { flipVarbit(this, Varbits.SETTINGS_DISABLE_TOOLTIP_IN_INTERFACE) }
        onButton(Components.Settings.DECLUTTER_BUTTON_CLICKZONE) { flipVarbit(this, Varbits.OPTION_SETTINGS_DECLUTTER) }
        onButton(Components.Settings.DROPDOWN_BUTTONS) { click -> dropdown(this, click) }
        onButton(Components.Settings.SETTINGS_CLICKZONE) { click -> settingClick(this, click) }
        // ST6: a volume slider sets its varp client-side and reports (setting id, volume) on release (CS 9266 / 9272 / 9278).
        onScriptTrigger(Components.Settings.SETTINGS_CLICKZONE, "ii") { t ->
            SettingsTab.setVolumeBySetting(this, t.args[0] as Int, t.args[1] as Int)
        }
    }

    internal companion object {
        /** The category whose rows the client shows (-1 = search results over all settings). */
        private val CATEGORY = AttrKey<Int>("settings_category")

        /** Setting kinds (param `settings_setting_type`, OpenRune `SettingType` order). */
        private const val CHECKBOX = 0
        private const val DROPDOWN = 2
        private const val KEYBIND = 3
        private const val INPUT = 4

        /** Cache enum 422 `settings_search_categories`: category order -> category struct. */
        private const val CATEGORIES_ENUM = 422
        private const val PARAM_CATEGORY_ID = 743
        private const val PARAM_CATEGORY_SETTINGS = 745
        private const val PARAM_SETTING_ID = 1077
        private const val PARAM_SETTING_TYPE = 1078
        private const val PARAM_DROPDOWN_ENTRIES = 1091

        /** Collection log "new item" chat / popup: two bits of one varbit (OpenRune setting ids 85 / 171). */
        private const val COLLECTION_CHAT = 85
        private const val COLLECTION_POPUP = 171

        /** The level-up popup dropdown writes two varbits instead of a table row (OpenRune `SettingUtils`). */
        private const val LEVEL_UP_POPUPS = 315

        private const val DEFAULTS_KEY = "settings.defaults"
        private val CATEGORY_SLOTS = 0..9
        private val ROW_SLOTS = 0..512

        /** Dropdown buttons come in threes per entry (OpenRune: `comsub / 3`). */
        private const val DROPDOWN_STRIDE = 3

        private class Row(
            val id: Int,
            val varbit: Boolean,
            val variable: Int,
            val default: Int?,
            val min: Int?,
            val max: Int?,
            val prompt: String?,
            val toggle: Int?,
        )

        private val ROWS: Map<Int, Row> by lazy {
            tsv("settings/settings-configs.tsv").associate { c ->
                val row =
                    Row(
                        c[0].toInt(),
                        c[1] == "varbit",
                        c[2].toInt(),
                        c[3].toIntOrNull(),
                        c[4].toIntOrNull(),
                        c[5].toIntOrNull(),
                        c[6].ifEmpty { null },
                        c.getOrNull(7)?.toIntOrNull(),
                    )
                row.id to row
            }
        }

        /** (varbit?, variable, value) new-account defaults (OpenRune `account_default_vars`). */
        private val ACCOUNT_DEFAULTS: List<Triple<Boolean, Int, Int>> by lazy {
            tsv("settings/account-defaults.tsv").map { c -> Triple(c[0] == "varbit", c[1].toInt(), c[2].toInt()) }
        }

        private fun tsv(path: String): List<List<String>> =
            (AllSettings::class.java.classLoader.getResourceAsStream(path) ?: error("missing $path"))
                .bufferedReader()
                .readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .map { it.split('\t') }

        private fun get(p: PlayerRef, row: Row): Int = if (row.varbit) p.varbit(row.variable) else p.varp(row.variable)

        private fun set(p: PlayerRef, row: Row, value: Int) =
            if (row.varbit) {
                p.setVarbit(
                    row.variable,
                    value,
                )
            } else {
                p.setVarp(row.variable, value)
            }

        private fun flipVarbit(p: PlayerRef, varbit: Int) = p.setVarbit(varbit, 1 - p.varbit(varbit))

        /** Settings struct ids of one category, in the client's row order. */
        private fun categorySettings(p: PlayerRef, categoryStruct: Int): List<Int> {
            val listEnum = p.structInt(categoryStruct, PARAM_CATEGORY_SETTINGS) ?: return emptyList()
            return p
                .enumInts(listEnum)
                .toSortedMap()
                .values
                .toList()
        }

        private fun categories(p: PlayerRef): List<Int> =
            p
                .enumInts(CATEGORIES_ENUM)
                .toSortedMap()
                .values
                .toList()

        /** The struct of the row the client shows at [slot] (the selected category, or all settings while searching). */
        private fun settingAt(p: PlayerRef, slot: Int): Int? {
            val category = p[CATEGORY] ?: 0
            val rows =
                if (category < 0) {
                    categories(p).flatMap { categorySettings(p, it) }
                } else {
                    categories(p).firstOrNull { p.structInt(it, PARAM_CATEGORY_ID) == category }?.let { categorySettings(p, it) }
                }
            return rows?.getOrNull(slot)
        }

        /** ST1: called when the window opens (from the settings tab's "All settings" button). */
        fun opened(p: PlayerRef) {
            p.ifEvents(Components.Settings.CATEGORIES_CLICKZONE, CATEGORY_SLOTS, IfEvents.op(1))
            p.ifEvents(Components.Settings.DROPDOWN_BUTTONS, ROW_SLOTS, IfEvents.op(1))
            p.ifEvents(Components.Settings.SETTINGS_CLICKZONE, ROW_SLOTS, IfEvents.op(1) or IfEvents.SCRIPT_TRIGGER)
            p.ifEvents(Components.Settings.SETTINGS_CLICKZONE, -1..-1, IfEvents.SCRIPT_TRIGGER)
            p.setVarbit(Varbits.SETTINGS_COLOUR_MODAL_OPENED, 0)
            // The client opens on category 0 itself; writing the category / search varbits here hides the window.
            p[CATEGORY] = 0
        }

        private fun selectCategory(p: PlayerRef, category: Int) {
            p[CATEGORY] = category
            p.setVarbit(Varbits.SETTINGS_CATEGORY, category)
            p.setVarbit(Varbits.FLOATER_IS_SEARCHING, 0)
            p.setVarbit(Varbits.FLOATER_SEARCH_LISTEN_FOR_KEYBOARD, 0)
        }

        /** ST2-ST4: a row click selects the setting; checkboxes flip, number settings ask, dropdowns wait for their list. */
        private fun settingClick(p: PlayerRef, click: ButtonClick) {
            val struct = settingAt(p, click.slot) ?: return
            val id = p.structInt(struct, PARAM_SETTING_ID) ?: return
            p.setVarbit(Varbits.SETTINGS_SELECTED_SETTING, id)
            val row = ROWS[id]
            when (p.structInt(struct, PARAM_SETTING_TYPE)) {
                CHECKBOX -> {
                    when {
                        row == null -> Unit
                        id == COLLECTION_CHAT -> set(p, row, get(p, row) xor 1)
                        id == COLLECTION_POPUP -> set(p, row, get(p, row) xor 2)
                        else -> set(p, row, if (get(p, row) == 0) 1 else 0)
                    }
                }

                INPUT -> {
                    if (row != null) {
                        p.setVarbit(Varbits.FLOATER_CHATBOX_OPENED, 1)
                        p.countDialog(row.prompt ?: "Enter amount:") { n -> setNumber(this, row, n) }
                    }
                }
            }
        }

        private fun setNumber(p: PlayerRef, row: Row, value: Int) {
            val n = value.coerceIn(row.min ?: 0, row.max ?: Int.MAX_VALUE)
            set(p, row, n)
            row.toggle?.let { ROWS[it] }?.let { set(p, it, if (n > 0) 1 else 0) }
        }

        /** ST3: a dropdown entry for the selected setting (keybinds: a key taken elsewhere is cleared there). */
        private fun dropdown(p: PlayerRef, click: ButtonClick) {
            val id = p.varbit(Varbits.SETTINGS_SELECTED_SETTING)
            p.setVarbit(Varbits.SETTINGS_SELECTED_SETTING, 0)
            val option = click.slot / DROPDOWN_STRIDE
            val struct = categories(p).flatMap { categorySettings(p, it) }.firstOrNull { p.structInt(it, PARAM_SETTING_ID) == id } ?: return
            val entries = p.structInt(struct, PARAM_DROPDOWN_ENTRIES)?.let { p.enumStrings(it).keys }.orEmpty()
            if (option !in entries) return
            if (id == LEVEL_UP_POPUPS) return levelUpPopups(p, option)
            val row = ROWS[id] ?: return
            if (p.structInt(struct, PARAM_SETTING_TYPE) == KEYBIND) {
                val keybinds =
                    categories(p).flatMap { categorySettings(p, it) }.filter { p.structInt(it, PARAM_SETTING_TYPE) == KEYBIND }
                for (other in keybinds) {
                    val otherRow = p.structInt(other, PARAM_SETTING_ID)?.let { ROWS[it] } ?: continue
                    if (otherRow !== row && get(p, otherRow) == option) set(p, otherRow, 0)
                }
            }
            set(p, row, option)
        }

        private fun levelUpPopups(p: PlayerRef, option: Int) {
            p.setVarbit(Varbits.OPTION_LEVEL_UP_POPUP_LIST, if (option == 2) 1 else 0)
            p.setVarbit(Varbits.OPTION_LEVEL_UP_MESSAGE_DISABLED, if (option == 0) 1 else 0)
        }

        /**
         * ST7: once per account, settings and account defaults are written where the variable is still 0, so a player's
         * own choices (saved with their variables) are never overwritten.
         */
        private fun applyDefaults(p: PlayerRef) {
            if (p.saved(DEFAULTS_KEY) != null) return
            for (row in ROWS.values) {
                val d = row.default ?: continue
                if (get(p, row) == 0) set(p, row, d)
            }
            for ((varbit, variable, value) in ACCOUNT_DEFAULTS) {
                if (varbit && p.varbit(variable) == 0) p.setVarbit(variable, value)
                if (!varbit && p.varp(variable) == 0) p.setVarp(variable, value)
            }
            p.setSaved(DEFAULTS_KEY, 1L)
        }
    }
}
