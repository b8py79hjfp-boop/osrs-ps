package tp.util

/**
 * Chat icon frames in sprite group 423 `mod_icons` (D-095). 0 and 1 are the official player-moderator and J-Mod crowns
 * (RuneLite `IconID`); 60..68 are ours, appended by content/gameframe's `pack/sprites/423` (base 60 frames), drawn by
 * tools/icons/chat_icons.py. In text the client draws one as `<img=N>`.
 */
object ChatIcons {
    const val PLAYER_MODERATOR = 0
    const val JAGEX_MODERATOR = 1
    const val ADMINISTRATOR = 60
    const val OWNER = 61
    const val DEVELOPER = 62

    /** Regular .. Uber are 63..68, in tier order. */
    private const val FIRST_DONATOR = 63

    /** A staff rank's icon, or null for players. */
    fun forRank(rank: Rank): Int? =
        when (rank) {
            Rank.PLAYER -> null
            Rank.MODERATOR -> PLAYER_MODERATOR
            Rank.ADMINISTRATOR -> ADMINISTRATOR
            Rank.OWNER -> OWNER
            Rank.DEVELOPER -> DEVELOPER
        }

    fun forDonator(tier: DonatorTier): Int? = if (tier == DonatorTier.NONE) null else FIRST_DONATOR + tier.ordinal - 1

    /** The icon shown before a name: the staff icon wins over the donator one. */
    fun of(rank: Rank, tier: DonatorTier): Int? = forRank(rank) ?: forDonator(tier)

    /** Donator name colours (hex for `<col=>`), matching the gems: Regular .. Uber. */
    fun nameColour(tier: DonatorTier): String? =
        when (tier) {
            DonatorTier.NONE -> null
            DonatorTier.REGULAR -> "3cbe50"
            DonatorTier.SUPER -> "3c82ff"
            DonatorTier.EXTREME -> "e63c3c"
            DonatorTier.LEGENDARY -> "ffb000"
            DonatorTier.SUPREME -> "b450ff"
            DonatorTier.UBER -> "ff46c8"
        }
}
