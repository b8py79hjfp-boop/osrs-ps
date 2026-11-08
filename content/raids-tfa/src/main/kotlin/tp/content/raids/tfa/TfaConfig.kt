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

    /**
     * OPEN-TFA7: supply credits a fair share of the first three rooms earns (TFA16: "your performance up until then does
     * play a role"; no source gives the number). Invented placeholder: about a quarter of the 83 credits the whole
     * stock costs. Capped by the varbit `TFA_SUPPLY_POINTS` (7 bits).
     */
    val supplyCreditsBase: Int =
        System.getProperty("tp.tfa.supplyCreditsBase")?.toIntOrNull()?.coerceIn(0, MAX_SUPPLY_CREDITS) ?: DEFAULT_SUPPLY_CREDITS

    /** Largest value of the 7-bit varbit `TFA_SUPPLY_POINTS` (client-contract 1.2: 0-127). */
    const val MAX_SUPPLY_CREDITS: Int = 127

    private const val DEFAULT_SUPPLY_CREDITS = 24

    /**
     * OPEN-TFA7: a player whose points are below this share of an equal split of the first three rooms is offered the
     * failure shop (row 17151, with the Leftovers). TFA16 only says "poor contribution".
     */
    val supplyPoorShare: Double = System.getProperty("tp.tfa.supplyPoorShare")?.toDoubleOrNull() ?: DEFAULT_POOR_SHARE

    private const val DEFAULT_POOR_SHARE = 0.5

    /**
     * OPEN-TFA7: whether every player has their own Alliance Supplies stock. Off: one stock per run, shared by the party
     * (tfa.md TFA16 gives one stock count per item).
     */
    val supplyStockPerPlayer: Boolean = System.getProperty("tp.tfa.supplyStockPerPlayer") == "true"

    /** The entry requirement problem for [player], or null. */
    fun requirement(player: PlayerRef): String? =
        if (requireWgs && !TfaQuest.wgsComplete(player)) "You need to complete While Guthix Sleeps to enter the Archive." else null
}

/** While Guthix Sleeps completion (TFA1); the quest is not on this server, so only the flag uses this. */
internal object TfaQuest {
    /** OPEN-TFA15: the WGS completion value of varp `WGS_PRIMARY` (4092) is not known without the quest. */
    fun wgsComplete(player: PlayerRef): Boolean = player.varp(tp.symbols.Varps.WGS_PRIMARY) > 0
}
