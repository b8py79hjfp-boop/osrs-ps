package tp.content.raids.tob

import tp.content.raids.Party

/** The party's chosen mode (party setting `mode`: 0 Normal, 1 Entry, 2 Hard; enum 3852 order Normal / Entry / Hard). */
internal object TobParties {
    const val MODE_SETTING = "mode"

    fun modeOf(party: Party): String =
        when (party.settings[MODE_SETTING] ?: 0) {
            MODE_ENTRY -> TobRaid.ENTRY
            MODE_HARD -> TobRaid.HARD
            else -> TobRaid.NORMAL
        }

    const val MODE_NORMAL = 0
    const val MODE_ENTRY = 1
    const val MODE_HARD = 2
}
