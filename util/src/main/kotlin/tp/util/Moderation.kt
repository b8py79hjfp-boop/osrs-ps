package tp.util

/** A moderation action (commands brief §6, §7). Lifting actions close the matching record type. */
enum class Sanction(
    /** The record type this action creates, or null for one that only lifts. */
    val record: RecordType?,
    /** The record type this action lifts, or null. */
    val lifts: RecordType? = null,
) {
    KICK(RecordType.KICK),
    MUTE(RecordType.MUTE),
    UNMUTE(null, RecordType.MUTE),
    YELL_MUTE(RecordType.YELL_MUTE),
    YELL_UNMUTE(null, RecordType.YELL_MUTE),
    JAIL(RecordType.JAIL),
    UNJAIL(null, RecordType.JAIL),
    WARN(RecordType.WARN),
    NOTE(RecordType.NOTE),
    BAN(RecordType.BAN),
    UNBAN(null, RecordType.BAN),
    IP_BAN(RecordType.IP_BAN),
    UN_IP_BAN(null, RecordType.IP_BAN),
    DEVICE_BAN(RecordType.DEVICE_BAN),
    UN_DEVICE_BAN(null, RecordType.DEVICE_BAN),
    ;

    /** The lowest rank allowed to use it (commands brief §6: bans are administrator commands). */
    val minRank: Rank
        get() =
            when (this) {
                BAN, UNBAN, IP_BAN, UN_IP_BAN, DEVICE_BAN, UN_DEVICE_BAN -> Rank.ADMINISTRATOR
                else -> Rank.MODERATOR
            }
}

/** What a `moderation_records` row is (D-088). */
enum class RecordType {
    KICK,
    MUTE,
    YELL_MUTE,
    JAIL,
    WARN,
    NOTE,
    BAN,
    IP_BAN,
    DEVICE_BAN,
    ;

    /** Whether the record stays in force until it expires or is lifted (a kick, warning or note is a moment). */
    val lasting: Boolean get() = this == MUTE || this == YELL_MUTE || this == JAIL || this == BAN || this == IP_BAN || this == DEVICE_BAN
}

/**
 * One `moderation_records` row (commands brief §7). Times are epoch ms; [expires] is [PERMANENT] or 0 for a moment.
 * [scope] is the IP or device id a ban covers, else empty. [liftedBy] is empty while the record is in force.
 */
class ModRecord(
    val id: Long,
    val accountId: Long,
    val type: RecordType,
    val actor: String,
    val reason: String,
    val start: Long,
    val expires: Long,
    val scope: String = "",
    val liftedBy: String = "",
    val liftedAt: Long = 0,
) {
    /** In force at [now]: lasting, not lifted and not expired. */
    fun active(now: Long): Boolean = type.lasting && liftedBy.isEmpty() && (expires == PERMANENT || expires > now)

    companion object {
        const val PERMANENT: Long = -1
    }
}

/**
 * The sanctions in force on one account, as each world needs them (commands brief §7: what the player sees shows
 * the reason and the expiry). An `until` of 0 means none, [ModRecord.PERMANENT] means no expiry.
 */
data class Sanctions(
    val mutedUntil: Long = 0,
    val muteReason: String = "",
    val yellMutedUntil: Long = 0,
    val yellMuteReason: String = "",
    val jailedUntil: Long = 0,
    val jailReason: String = "",
) {
    fun muted(now: Long): Boolean = inForce(mutedUntil, now)

    fun yellMuted(now: Long): Boolean = inForce(yellMutedUntil, now)

    fun jailed(now: Long): Boolean = inForce(jailedUntil, now)

    companion object {
        val NONE: Sanctions = Sanctions()

        fun inForce(until: Long, now: Long): Boolean = until == ModRecord.PERMANENT || until > now

        /** The newest active record of each type wins. */
        fun of(records: List<ModRecord>, now: Long): Sanctions {
            fun latest(type: RecordType) = records.filter { it.type == type && it.active(now) }.maxByOrNull { it.start }
            val mute = latest(RecordType.MUTE)
            val yell = latest(RecordType.YELL_MUTE)
            val jail = latest(RecordType.JAIL)
            return Sanctions(
                mute?.expires ?: 0,
                mute?.reason.orEmpty(),
                yell?.expires ?: 0,
                yell?.reason.orEmpty(),
                jail?.expires ?: 0,
                jail?.reason.orEmpty(),
            )
        }

        /** "permanently" or "until 2026-11-08 19:40 UTC". */
        fun describeUntil(until: Long): String =
            if (until == ModRecord.PERMANENT) {
                "permanently"
            } else {
                "until " +
                    java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm 'UTC'")
                        .withZone(java.time.ZoneOffset.UTC)
                        .format(java.time.Instant.ofEpochMilli(until))
            }
    }
}

/** `::lookup` (commands brief §6): the player card. IP and device fields are filled for administrators only (§8). */
class PlayerCard(
    val accountId: Long,
    val name: String,
    val rank: Rank,
    /** World id, or 0 when offline. */
    val world: Int,
    val sanctions: Sanctions,
    val bannedUntil: Long,
    val records: Int,
    val lastIp: String = "",
)
