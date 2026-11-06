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

/** Attributes shared between content modules. */
public object SharedAttrs {
    /** Skull icon shown over a player (-1/null = none); PvP content sets it, combat's appearance shows it. */
    public val SKULL_ICON: AttrKey<Int> = AttrKey("skull_icon")

    /** Tick until which teleports are blocked (Tele Block). */
    public val TELEBLOCK_UNTIL: AttrKey<Long> = AttrKey("teleblock_until")
}

/** A player's look changed outside the equipment module (skull); the appearance owner re-sends it. */
public class AppearanceChanged(public val player: PlayerRef) : Event
