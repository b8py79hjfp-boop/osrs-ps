package tp.plugin

/** Combat style of a hit, as seen by other modules (vengeance, recoil, protection). */
public enum class HitStyle { MELEE, RANGED, MAGIC, TYPELESS }

/**
 * Asked before an attack starts (PvP rules: wilderness levels, combat brackets, single/multi). Any subscriber may
 * [deny] with a message; the attack doesn't happen. Player-vs-player attacks are denied unless a subscriber [allow]s;
 * NPC attacks are allowed unless denied.
 */
public class AttackCheck(public val attacker: EntityRef, public val target: EntityRef) : Event {
    public var denial: String? = null
        private set
    public var allowed: Boolean = target !is PlayerRef || attacker !is PlayerRef
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

/**
 * Asks the combat module to roll a spell's accuracy: [caster]'s magic attack roll against [target]'s magic defence roll
 * (combat-formulas.md F14-F16). [hit] stays true if no combat module answers.
 */
public class SpellAccuracyRoll(public val caster: PlayerRef, public val target: EntityRef) : Event {
    public var hit: Boolean = true
}

/** [entity] died; published when the death sequence completes, before respawn (items kept on death, Retribution). */
public class DeathEvent(public val entity: EntityRef, public val killer: EntityRef?) : Event

/** Attributes shared between content modules. */
public object SharedAttrs {
    /** Skull icon shown over a player (-1/null = none); PvP content sets it, combat's appearance shows it. */
    public val SKULL_ICON: AttrKey<Int> = AttrKey("skull_icon")

    /** Whether the player stands in the Wilderness (PvP content keeps it current; aggression reads it). */
    public val IN_WILDERNESS: AttrKey<Boolean> = AttrKey("in_wilderness_shared")

    /**
     * The player's Wilderness level (0 outside; includes the PvP-world +15, wilderness.md W3). PvP content keeps it
     * current once per tick (Wilderness.updateArea); teleport rules read it (teleports.md T4).
     */
    public val WILDERNESS_LEVEL: AttrKey<Int> = AttrKey("wilderness_level_shared")

    /** Whether the player stands in a multi-combat area (PvP content keeps it current). */
    public val IN_MULTI: AttrKey<Boolean> = AttrKey("in_multi_shared")

    /** The NPC that last started attacking this player (single-way combat: no second NPC may join). */
    public val ENGAGED_BY: AttrKey<NpcRef> = AttrKey("engaged_by_npc")

    /** Tick of the player's last combat (attacked, hit or attacking); aggression skips players in combat (AG2). */
    public val LAST_COMBAT_TICK: AttrKey<Long> = AttrKey("last_combat_tick")

    /** Set on NPCs immune to Ring of recoil and similar reflection (Vengeance): the Wilderness bosses. */
    public val REFLECT_IMMUNE: AttrKey<Boolean> = AttrKey("reflect_immune")

    /** Tick until which the player can't teleport for a short while (boss lairs: 3 ticks after entering or leaving). */
    public val TELEPORT_DELAY_UNTIL: AttrKey<Long> = AttrKey("teleport_delay_until")

    /** Tick until which teleports are blocked (Tele Block). */
    public val TELEBLOCK_UNTIL: AttrKey<Long> = AttrKey("teleblock_until")
}

/** A player's look changed outside the equipment module (skull); the appearance owner re-sends it. */
public class AppearanceChanged(public val player: PlayerRef) : Event
