package tp.util

/**
 * Staff ranks, lowest to highest (docs/plans/commands-brief.md §1). Each rank can use every command of the ranks below
 * it. [stored] is the value kept in the accounts database and on the wire: ADMINISTRATOR keeps the pre-existing
 * `ADMIN`, so no account row changes (D-087).
 */
enum class Rank(val stored: String) {
    PLAYER("PLAYER"),
    MODERATOR("MODERATOR"),
    ADMINISTRATOR("ADMIN"),
    OWNER("OWNER"),
    DEVELOPER("DEVELOPER"),
    ;

    fun atLeast(other: Rank): Boolean = ordinal >= other.ordinal

    val isStaff: Boolean get() = atLeast(MODERATOR)

    /**
     * §1 targeting rule: a staff command reaches only a strictly lower rank; developers reach everyone except other
     * developers. Self-targeting is always allowed (the caller checks identity).
     */
    fun canTarget(target: Rank): Boolean = if (this == DEVELOPER) target != DEVELOPER else ordinal > target.ordinal

    /**
     * §1 setting ranks: owners set up to ADMINISTRATOR, developers set any rank. Nobody changes someone at or above
     * their own rank, except a developer acting on a non-developer.
     */
    fun canSetRank(current: Rank, wanted: Rank): Boolean =
        when (this) {
            DEVELOPER -> current != DEVELOPER
            OWNER -> current.ordinal < ordinal && wanted.ordinal <= ADMINISTRATOR.ordinal
            else -> false
        }

    /** Chat crown index for public, private, friends and clan chat: 0 none, 1 player-moderator, 2 J-Mod (§1). */
    val crown: Int
        get() =
            when (this) {
                PLAYER -> 0
                MODERATOR -> 1
                else -> 2
            }

    /** Login response `staffModLevel` (§1: administrator and above get the J-Mod level). */
    val staffModLevel: Int get() = if (atLeast(ADMINISTRATOR)) JMOD_LEVEL else 0

    /** Login response `playerMod` (§1: moderators and above get the player-moderator flag). */
    val playerMod: Boolean get() = atLeast(MODERATOR)

    companion object {
        /** The J-Mod staff level the client checks (OPEN-CM1 until the cs2 reads are confirmed: docs/plans/commands.md). */
        const val JMOD_LEVEL: Int = 2

        /** The rank stored as [value] (database, wire, config); also accepts the enum names. */
        fun parse(value: String): Rank? =
            entries.firstOrNull { it.stored.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true) }

        /** [parse], or PLAYER for an unknown or empty value (old rows, defaults). */
        fun parseOrPlayer(value: String?): Rank = value?.let(::parse) ?: PLAYER
    }
}
