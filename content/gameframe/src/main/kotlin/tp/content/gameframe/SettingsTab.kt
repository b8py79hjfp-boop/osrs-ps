package tp.content.gameframe

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits
import tp.symbols.Varps

/** Events the settings side panel needs, also re-sent when it replaces the house options in its side slot. */
internal object SettingsPanel {
    /** Volume and brightness sliders have bobble slots 0..21. */
    private const val SLIDER_LAST_SLOT = 21
    private const val PLAYER_PRIORITY_LAST = 5
    private const val NPC_PRIORITY_LAST = 4

    private val SLIDERS: List<Int> =
        listOf(
            Components.Settings_Side.MASTER_BOBBLE_CONTAINER,
            Components.Settings_Side.MUSIC_BOBBLE_CONTAINER,
            Components.Settings_Side.SOUND_BOBBLE_CONTAINER,
            Components.Settings_Side.AREASOUNDS_BOBBLE_CONTAINER,
            Components.Settings_Side.BRIGHTNESS_BOBBLE_CONTAINER,
        )

    /** Slider and button grids send op 1 per slot (RS Mod `SettingsSideScript.updateIfEvents`). */
    fun enableEvents(player: PlayerRef) {
        val op1 = IfEvents.op(1)
        for (slider in SLIDERS) player.ifEvents(slider, 0..SLIDER_LAST_SLOT, op1)
        player.ifEvents(Components.Settings_Side.ATTACK_PRIORITY_PLAYER_BUTTONS, 1..PLAYER_PRIORITY_LAST, op1)
        player.ifEvents(Components.Settings_Side.ATTACK_PRIORITY_NPC_BUTTONS, 1..NPC_PRIORITY_LAST, op1)
    }
}

/**
 * Settings side panel (docs/mechanics/gameframe-buttons.md S1..S8). Every control only changes the varp/varbit the
 * client reads back; behaviour ported from RS Mod `Control/Audio/DisplaySettingsScript` (ISC, DECISIONS D-027).
 */
public class SettingsTab : ContentPlugin {
    override fun ContentScope.register() {
        onButton(Components.Settings_Side.SETTINGS_TAB) { setVarbit(Varbits.SETTINGS_SIDE_PANEL_TAB, PANEL_CONTROLS) }
        onButton(Components.Settings_Side.AUDIO_TAB) { setVarbit(Varbits.SETTINGS_SIDE_PANEL_TAB, PANEL_AUDIO) }
        onButton(Components.Settings_Side.DISPLAY_TAB) { setVarbit(Varbits.SETTINGS_SIDE_PANEL_TAB, PANEL_DISPLAY) }
        onButton(Components.Settings_Side.SETTINGS_OPEN) { openModal(Components.Toplevel.MAINMODAL, Interfaces.SETTINGS) }
        // Bond pouch: a plain main modal with a fully transparent background (RS Mod `selectBondPouch`).
        onButton(Components.Settings_Side.BONDOPTIONS) { SideInventory.openMain(this, Interfaces.BOND_MAIN, BOND_TRANSPARENCY) }
        // House options replace the settings panel in its side slot until Close (RS Mod opens them in the sidemodal).
        // The house itself does not exist yet, so only opening and closing the panel work (OPEN-S8).
        onButton(Components.Settings_Side.HOUSEOPTIONS) { openOverlay(Components.Toplevel.SIDE11, Interfaces.POH_OPTIONS) }
        onButton(Components.Poh_Options.COM_24) {
            openOverlay(Components.Toplevel.SIDE11, Interfaces.SETTINGS_SIDE)
            SettingsPanel.enableEvents(this)
        }

        // Controls panel.
        onButton(Components.Settings_Side.ACCEPTAID) { flip(Varbits.OPTION_ACCEPTAID) }
        onButton(Components.Settings_Side.SKULL_PREVENTION) { flip(Varbits.SKULL_PREVENT_ENABLED) }
        onButton(
            Components.Settings_Side.ATTACK_PRIORITY_PLAYER_BUTTONS,
        ) { click -> selectPriority(Varps.OPTION_ATTACKPRIORITY, click.slot, PLAYER_PRIORITIES) }
        onButton(
            Components.Settings_Side.ATTACK_PRIORITY_NPC_BUTTONS,
        ) { click -> selectPriority(Varps.OPTION_ATTACKPRIORITY_NPC, click.slot, NPC_PRIORITIES) }

        // Audio panel.
        volume(
            Components.Settings_Side.MASTER_ICON,
            Components.Settings_Side.MASTER_BOBBLE_CONTAINER,
            Varps.OPTION_MASTER_VOLUME,
            Varbits.OPTION_MASTER_VOLUME_SAVED,
        )
        volume(
            Components.Settings_Side.MUSIC_ICON,
            Components.Settings_Side.MUSIC_BOBBLE_CONTAINER,
            Varps.OPTION_MUSIC,
            Varbits.OPTION_MUSIC_SAVED,
        )
        volume(
            Components.Settings_Side.SOUND_ICON,
            Components.Settings_Side.SOUND_BOBBLE_CONTAINER,
            Varps.OPTION_SOUNDS,
            Varbits.OPTION_SOUNDS_SAVED,
        )
        volume(
            Components.Settings_Side.AREASOUND_ICON,
            Components.Settings_Side.AREASOUNDS_BOBBLE_CONTAINER,
            Varps.OPTION_AREASOUNDS,
            Varbits.OPTION_AREASOUNDS_SAVED,
        )

        // Display panel.
        // Op 2 ("Restore Default Zoom") is client-side.
        onButton(Components.Settings_Side.ZOOM_TOGGLE, OP_PRIMARY) { flip(Varbits.CAMERA_ZOOM_MOUSE_DISABLED) }
        // The brightness slider is client-local; RS Mod only resyncs the varbit, which the client already holds (S7).
        onButton(Components.Settings_Side.BRIGHTNESS_BOBBLE_CONTAINER) { }
    }

    /** Registers an icon (mute toggle) and its 0..21 slider (slot * 5 percent) for one volume varp. */
    private fun ContentScope.volume(icon: Int, slider: Int, varp: Int, saved: Int) {
        onButton(icon) {
            val current = varp(varp)
            when {
                current > 0 -> setVolume(varp, saved, 0)
                varbit(saved) == 0 -> setVolume(varp, saved, FULL_VOLUME)
                else -> setVolume(varp, saved, varbit(saved))
            }
        }
        onButton(slider) { click -> setVolume(varp, saved, minOf(FULL_VOLUME, click.slot * SLIDER_STEP)) }
    }

    /** Muting remembers the previous volume in [saved] so the icon can restore it (RS Mod `setMasterVolume`). */
    private fun PlayerRef.setVolume(varp: Int, saved: Int, volume: Int) {
        if (volume == 0) setVarbit(saved, varp(varp))
        setVarp(varp, volume)
    }

    private fun PlayerRef.flip(varbit: Int) {
        setVarbit(varbit, 1 - varbit(varbit))
    }

    /** Priority buttons send sub 1..N, stored as 0..N-1 (RS Mod `PlayerPriority` / `NpcPriority`). */
    private fun PlayerRef.selectPriority(varp: Int, slot: Int, count: Int) {
        if (slot in 1..count) setVarp(varp, slot - 1)
    }

    private companion object {
        const val OP_PRIMARY = 1

        /** Main modal background transparency of the bond pouch (RS Mod passes colour -1, transparency -2). */
        const val BOND_TRANSPARENCY = -2

        /** `settings_side_panel_tab` values (RS Mod `SettingsSideScript.Panel`). */
        const val PANEL_CONTROLS = 0
        const val PANEL_AUDIO = 1
        const val PANEL_DISPLAY = 2

        /** Volume range and slider step: bobble slot n = n * 5 percent, capped at 100 (RS Mod `selectMasterSlider`). */
        const val FULL_VOLUME = 100
        const val SLIDER_STEP = 5

        /** Attack priority options: player 5 (combat level, right-click always, left-click, hidden, clan), NPC 4. */
        const val PLAYER_PRIORITIES = 5
        const val NPC_PRIORITIES = 4
    }
}
