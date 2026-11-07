package tp.content.sailing

import tp.plugin.PlayerRef

/**
 * The "Board" boat choice of a gangplank (db 149 `Boat_Selection_Type` 3). Interface 934 (`SAILING_BOAT_SELECTION`, scripts
 * 8621-8654) is not driven yet (OPEN-SEL1: its client scripts read the boat varbits `SAILING_BOAT_n_*` whose encoding is
 * undecoded), so the boats moored at the dock are offered as a chatbox menu instead. Type 3 allows only boats at the current
 * port ([BoatSelectionType.onlyBoatsAtCurrentPort]).
 */
internal object BoatSelection {
    /** Offers the player's boats moored at [dockId]; a single boat boards at once. [pick] receives the chosen boat slot. */
    fun board(p: PlayerRef, state: SailingState, dockId: Int, pick: (Int) -> Unit) {
        val here = BoatSave.owned(p).filter { BoatSave.get(p, it)?.dock == dockId }
        when (here.size) {
            0 -> {
                p.message("You don't have a boat moored here.")
            }

            1 -> {
                pick(here.first())
            }

            else -> {
                val names = here.map { label(state, p, it) }
                p.choiceDialog("Which boat?", names) { choice -> here.getOrNull(choice - 1)?.let(pick) }
            }
        }
    }

    private fun label(state: SailingState, p: PlayerRef, slot: Int): String {
        val type = BoatSave.get(p, slot)?.type ?: return "Boat ${slot + 1}"
        return "${state.data.boat(type)?.name ?: "Boat"} (${slot + 1})"
    }
}
