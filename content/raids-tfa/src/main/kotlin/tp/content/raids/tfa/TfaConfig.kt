package tp.content.raids.tfa

import tp.plugin.PlayerRef

/**
 * Named switches for TFA's OPEN rules (BRIEF section 0.4), read once from system properties `tp.tfa.<name>`. Values are
 * immutable for the JVM's life.
 */
internal object TfaConfig {
    /**
     * TFA1 needs While Guthix Sleeps, which this server does not implement yet, so nobody could pass the check. Off by
     * default; `-Dtp.tfa.requireWgs=true` enforces it through the quest's completion varp once the quest exists.
     */
    val requireWgs: Boolean = System.getProperty("tp.tfa.requireWgs") == "true"

    /** OPEN-NX7: ticks from entering the vault until the braziers are lit and the chests appear (3 Nov). */
    val brazierTicks: Int = System.getProperty("tp.tfa.brazierTicks")?.toIntOrNull() ?: DEFAULT_BRAZIER_TICKS

    private const val DEFAULT_BRAZIER_TICKS = 5

    /** The entry requirement problem for [player], or null. */
    fun requirement(player: PlayerRef): String? =
        if (requireWgs && !TfaQuest.wgsComplete(player)) "You need to complete While Guthix Sleeps to enter the Archive." else null
}

/** While Guthix Sleeps completion (TFA1); the quest is not on this server, so only the flag uses this. */
internal object TfaQuest {
    /** OPEN-TFA15: the WGS completion value of varp `WGS_PRIMARY` (4092) is not known without the quest. */
    fun wgsComplete(player: PlayerRef): Boolean = player.varp(tp.symbols.Varps.WGS_PRIMARY) > 0
}
