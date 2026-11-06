package tp.plugin

/** Combat style of a hit, as seen by other modules (vengeance, recoil, protection). */
public enum class HitStyle { MELEE, RANGED, MAGIC, TYPELESS }

/**
 * Asked before an attack starts (PvP rules: wilderness levels, combat brackets, single/multi). Any subscriber may
 * [deny] with a message; the attack doesn't happen. Player-vs-player attacks are denied unless a subscriber [allow]s.
 */
public class AttackCheck(public val attacker: EntityRef, public val target: EntityRef) : Event {
    public var denial: String? = null
        private set
    public var allowed: Boolean = target !is PlayerRef
        private set

    public fun deny(message: String) {
        denial = message
    }

    public fun allow() {
        allowed = true
    }
}

/** An attack was performed this tick (skull / PJ timers). */
public class AttackEvent(public val attacker: EntityRef, public val target: EntityRef, public val style: HitStyle) : Event

/** A hit was applied to [target] (after HP changed): recoil, vengeance, Smite, Redemption. */
public class HitEvent(
    public val target: EntityRef,
    public val source: EntityRef?,
    public val damage: Int,
    public val style: HitStyle,
) : Event

/** [entity] died; published when the death sequence completes, before respawn (items kept on death, Retribution). */
public class DeathEvent(public val entity: EntityRef, public val killer: EntityRef?) : Event
