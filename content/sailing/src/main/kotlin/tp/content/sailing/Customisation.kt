package tp.content.sailing

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Varbits
import tp.symbols.Varps

/**
 * The boat customisation interface 939 (`SAILING_CUSTOMISATION`, client scripts 8809-8845; docs/mechanics/sailing-building.md
 * UI1-UI5). The client builds the whole screen itself from the cache (db 189 tabs, db 166 option lists, db 175 hotspot options)
 * and from five server values: varp `SAILING_BOAT_CUSTOMISATION_BOAT` 5190 (the `Sailing_Boat` row), varbits
 * `SAILING_BOAT_CUSTOMISATION_TYPE` (0 core parts, 1 facilities), `_HOTSPOT_ID` (the hotspot, facilities only) and `_BOAT_SLOT`
 * (boat slot + 1), and the varbits `SAILING_BOAT_n_*` of that slot, which say what is "currently built". Each option's
 * "Build" button is a child of `FACILITY_CLICK_LAYER` whose sub id is the db row of the part or facility, so a click arrives
 * as an IF_BUTTON op 1 with the row in [ButtonClick.slot]. The row is client input: it is checked against the boat's option
 * lists before anything is built.
 */
internal class Customisation(private val state: SailingState, private val builder: BoatBuilder) {
    fun ContentScope.register() {
        onButton(Components.Sailing_Customisation.FACILITY_CLICK_LAYER, 1) { click -> clicked(this, click) }
    }

    /** Opens the interface on the core parts ([hotspot] null) or on the facilities of hotspot [hotspot]. */
    fun open(p: PlayerRef, s: ShipyardSession, hotspot: Int?) {
        sync(p, s)
        p.setVarp(Varps.SAILING_BOAT_CUSTOMISATION_BOAT, s.boat.boat.row)
        p.setVarbit(Varbits.SAILING_BOAT_CUSTOMISATION_TYPE, if (hotspot == null) CORE_MODE else FACILITY_MODE)
        p.setVarbit(Varbits.SAILING_BOAT_CUSTOMISATION_HOTSPOT_ID, hotspot ?: 0)
        p.setVarbit(Varbits.SAILING_BOAT_CUSTOMISATION_BOAT_SLOT, s.slot + 1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.SAILING_CUSTOMISATION)
    }

    /** Closes the interface if it is open. */
    fun close(p: PlayerRef) {
        if (p.isOpen(Interfaces.SAILING_CUSTOMISATION)) p.closeModal()
    }

    /**
     * Writes the saved boat of slot [s] into the `SAILING_BOAT_n_*` varbits the interface reads (UI3): parts as option-list
     * indices (flag and brazier + 1, hotspots option index + 1, as the client scripts 9067 and 8805 subtract), the sail pattern
     * in its own varbit.
     */
    fun sync(p: PlayerRef, s: ShipyardSession) {
        val owned = BoatSave.get(p, s.slot) ?: return
        val n = s.slot

        fun set(first: Int, value: Int) = p.setVarbit(first + n * BOAT_STRIDE, value)
        set(Varbits.SAILING_BOAT_1_OWNED, 1)
        set(Varbits.SAILING_BOAT_1_TYPE, owned.type)
        set(Varbits.SAILING_BOAT_1_KEEL, owned.keel)
        set(Varbits.SAILING_BOAT_1_HULL, owned.hull)
        set(Varbits.SAILING_BOAT_1_SAIL, owned.sail)
        set(Varbits.SAILING_BOAT_1_STEERING, owned.helm)
        set(Varbits.SAILING_BOAT_1_FLAG, owned.flag)
        set(Varbits.SAILING_BOAT_1_BRAZIER, owned.brazier)
        set(Varbits.SAILING_BOAT_1_TRIM, owned.trim)
        p.setVarbit(Varbits.SAILING_BOAT_1_SAIL_PATTERN + n, owned.pattern)
        for (k in 0 until FIRST_EXTRA_HOTSPOT) set(Varbits.SAILING_BOAT_1_HOTSPOT_0 + k, owned.hotspotAt(k))
        for (k in FIRST_EXTRA_HOTSPOT until BoatSave.MAX_HOTSPOTS) {
            p.setVarbit(Varbits.SAILING_BOAT_1_HOTSPOT_11 + n * EXTRA_STRIDE + (k - FIRST_EXTRA_HOTSPOT), owned.hotspotAt(k))
        }
    }

    private fun clicked(p: PlayerRef, click: ButtonClick) {
        val s = state.shipyard.session(p) ?: return
        val row = click.slot
        p.startScript {
            with(builder) {
                if (player.varbit(Varbits.SAILING_BOAT_CUSTOMISATION_TYPE) == FACILITY_MODE) {
                    val facility = state.boats.build.facilityByRow[row] ?: return@startScript
                    if (state.shipyard.aboard(
                            player,
                            s,
                        )
                    ) {
                        buildFacility(s, player.varbit(Varbits.SAILING_BOAT_CUSTOMISATION_HOTSPOT_ID), facility)
                    }
                } else {
                    val part = state.data.part(row) ?: return@startScript
                    buildPart(s, part)
                }
            }
        }
    }

    private companion object {
        /** Values of `SAILING_BOAT_CUSTOMISATION_TYPE`: the first tab group of db 189 (core parts) or the second (facilities). */
        const val CORE_MODE = 0
        const val FACILITY_MODE = 1

        /** Distance between the `SAILING_BOAT_n_*` varbits of consecutive boat slots (19266 -> 19304). */
        const val BOAT_STRIDE = 38

        /** Hotspots 11 and 12 have their own varbits (20207, 20208), four apart per boat slot (20211 for boat 2). */
        const val FIRST_EXTRA_HOTSPOT = 11
        const val EXTRA_STRIDE = 4
    }
}
