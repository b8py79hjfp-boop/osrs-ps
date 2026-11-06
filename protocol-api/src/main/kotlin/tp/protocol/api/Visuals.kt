package tp.protocol.api

/**
 * Identifies an avatar for visuals that point at another entity (hitsplat source, face target, projectile ends).
 * Players and NPCs live in separate index spaces.
 */
sealed interface AvatarRef {
    data class OfPlayer(val index: Int) : AvatarRef

    data class OfNpc(val index: Int) : AvatarRef
}

/** One visual event on an avatar this tick (player or NPC extended info). Delays are client cycles (20 ms). */
sealed interface Visual {
    /** Plays [seq] (-1 stops the current one). */
    data class Animate(val seq: Int, val delay: Int = 0) : Visual

    data class SpotAnim(val slot: Int, val id: Int, val height: Int, val delay: Int = 0) : Visual

    /**
     * A hitsplat. [selfType] is shown to the avatar's own player, [sourceType] to the player who dealt it, [otherType]
     * to everyone else (-1 = nobody else); hitmark config ids.
     */
    data class Hit(
        val source: AvatarRef?,
        val selfType: Int,
        val sourceType: Int,
        val otherType: Int,
        val value: Int,
        val delay: Int = 0,
    ) : Visual

    /** A head bar ([bar] = headbar config id) with fill 0..segments. */
    data class HeadBar(val source: AvatarRef?, val bar: Int, val fill: Int) : Visual

    /** Turn towards [target] (null = stop facing). */
    data class Face(val target: AvatarRef?) : Visual

    /** Overhead text. */
    data class Say(val text: String) : Visual
}

/** What other players see of a player's look: worn objs by wear position (-1 = empty), icons, combat level. */
class Appearance(
    val worn: IntArray,
    /**
     * Per wear position, the two extra positions its obj hides (obj `wearpos2/3`, e.g. a full helm hides hair and
     * beard), as pairs: `[pos0a, pos0b, pos1a, ...]`, -1 = none.
     */
    val hidden: IntArray,
    val combatLevel: Int,
    /** Skull icon id, -1 = none. */
    val skullIcon: Int,
    /** Overhead prayer icon id, -1 = none. */
    val prayerIcon: Int,
)
