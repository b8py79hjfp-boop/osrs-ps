package tp.plugin

/** Combat style of a hit, as seen by other modules (vengeance, recoil, protection). */
public enum class HitStyle { MELEE, RANGED, MAGIC, TYPELESS }

/**
 * Asked before an attack starts (PvP rules: wilderness levels, combat brackets, single/multi). Any subscriber may
 * [deny] with a message; the attack doesn't happen. Player-vs-player attacks are denied unless a subscriber [allow]s;
 * NPC attacks are allowed unless denied.
 */
public class AttackCheck(
    public val attacker: EntityRef,
    public val target: EntityRef,
    /** The attack's style when known (duel rules); null for checks before a style is chosen. */
    public val style: HitStyle? = null,
) : Event {
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
 * Asks the combat module to apply [damage] to [target] as one regular hit from [source] (a failed pickpocket's strike,
 * a rooftop fall): hitsplat, hitpoints, death. The combat module sets [handled] when it did; content that publishes
 * this applies its own non-lethal fallback otherwise.
 */
public class DamageRequest(
    public val target: EntityRef,
    public val source: EntityRef?,
    public val damage: Int,
    public val style: HitStyle = HitStyle.TYPELESS,
) : Event {
    public var handled: Boolean = false
}

/**
 * Asks the combat module to roll a spell's accuracy: [caster]'s magic attack roll against [target]'s magic defence roll
 * (combat-formulas.md F14-F16). [hit] stays true if no combat module answers.
 */
public class SpellAccuracyRoll(public val caster: PlayerRef, public val target: EntityRef) : Event {
    public var hit: Boolean = true
}

/**
 * An NPC was just brought to 0 hitpoints by [source] (npc-combat.md N12). A subscriber that sets [surviveWith] > 0 keeps
 * it alive at that many hitpoints (a quest NPC that only dies to its special weapon, e.g. Count Draynor without a
 * stake); otherwise it dies as usual. Published before [DeathEvent].
 */
public class LethalHitEvent(public val npc: NpcRef, public val source: EntityRef?) : Event {
    public var surviveWith: Int = 0
}

/** [entity] died; published when the death sequence completes, before respawn (items kept on death, Retribution). */
public class DeathEvent(
    public val entity: EntityRef,
    public val killer: EntityRef?,
    /** For an NPC: the player who dealt it the most damage this life (who gets the drop), or null. */
    public val lootFor: PlayerRef? = null,
) : Event

/**
 * Duel Arena rules (docs/mechanics/duel-arena.md DA2): bit numbers in varp `duel_options` (the client's option
 * buttons, script 6169); worn slot `n` is disabled by bit [WORN_SHIFT] + n. Set on both duellers as
 * [SharedAttrs.DUEL_RULES] while the fight runs so every module can enforce its part.
 */
public object DuelRules {
    public const val NO_FORFEIT: Int = 0
    public const val NO_MOVEMENT: Int = 1
    public const val NO_WEAPON_SWITCH: Int = 2
    public const val SHOW_INVENTORIES: Int = 3
    public const val NO_RANGED: Int = 4
    public const val NO_MELEE: Int = 5
    public const val NO_MAGIC: Int = 6
    public const val NO_DRINKS: Int = 7
    public const val NO_FOOD: Int = 8
    public const val NO_PRAYER: Int = 9
    public const val FUN_WEAPONS: Int = 12
    public const val NO_SPECIAL_ATTACKS: Int = 13
    public const val WORN_SHIFT: Int = 14

    /** Whether [player] is in a running duel with rule [bit] on. */
    public fun has(player: PlayerRef, bit: Int): Boolean = (player[SharedAttrs.DUEL_RULES] ?: 0) and (1 shl bit) != 0

    /** Whether worn [slot] is disabled for [player]'s running duel. */
    public fun slotDisabled(player: PlayerRef, slot: Int): Boolean = has(player, WORN_SHIFT + slot)
}

/** Attributes shared between content modules. */
public object SharedAttrs {
    /**
     * World attr: whether a tile is in a multi-combat area. Set by the module that owns the multi-combat map (pvp,
     * RS Mod `multiway.json`); read by combat for multi-target attacks (barrage, burst, chinchompas). Absent = singles.
     */
    public val MULTI_AREA: AttrKey<(tp.util.CoordGrid) -> Boolean> = AttrKey("multi_area")

    /**
     * NPC attr: the party size a raid scales this NPC for (set by raids-core when it spawns a run's NPC; TFA scales a
     * solo raid as two). Boss scripts read it for their HP and mechanic tables. Absent = not a raid NPC.
     */
    public val RAID_SCALE: AttrKey<Int> = AttrKey("raid_scale")

    /** NPC attr: the raid run an NPC belongs to (raids-core's run id); boss minions may inherit it from their owner. */
    public val RAID_RUN: AttrKey<Long> = AttrKey("raid_run")

    /**
     * NPC attr: the raid's scaling for its run (set by raids-core on a run's NPC); boss minions that inherit the run are
     * scaled through it too (CoX scales every raid NPC by party size and the party's levels).
     */
    public val RAID_SCALER: AttrKey<(NpcRef) -> Unit> = AttrKey("raid_scaler")

    /** NPC attr: the raid run's mode (raids-core's mode name, e.g. CoX "challenge"). */
    public val RAID_MODE: AttrKey<String> = AttrKey("raid_mode")

    /** NPC attr: a raid NPC's meter shared between the fight and the room (CoX lux grubs: ticks before they burst). */
    public val RAID_METER: AttrKey<Int> = AttrKey("raid_meter")

    /** NPC attr: the raider a raid NPC turns on (CoX tightrope: the one crossing the rope). */
    public val RAID_FOCUS: AttrKey<String> = AttrKey("raid_focus")

    /** NPC attr: the tick a raid NPC's temporary state ends (CoX jewelled crabs: their colour). */
    public val RAID_UNTIL: AttrKey<Long> = AttrKey("raid_until")

    /** NPC attr: the raid room an NPC was spawned for (CoX cells: template positions per rotation). */
    public val RAID_ROOM: AttrKey<RaidRoomFrame> = AttrKey("raid_room")

    /** NPC attr: the raid party's member names in orb order (leader first), for tank rules. Set with [RAID_SCALE]. */
    public val RAID_ORDER: AttrKey<List<String>> = AttrKey("raid_order")

    /** Rules of the running duel (bits of [DuelRules]); set from "FIGHT!" until the duel ends. */
    public val DUEL_RULES: AttrKey<Int> = AttrKey("duel_rules")

    /** The opponent while a duel is set up or running (other modules let the duel plugin decide PvP checks). */
    public val DUEL_OPPONENT: AttrKey<PlayerRef> = AttrKey("duel_opponent")

    /** When set, a death drops nothing and the player respawns here (Duel Arena: a safe death). */
    public val SAFE_DEATH_AT: AttrKey<tp.util.CoordGrid> = AttrKey("safe_death_at")

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

    /** Tick of the player's last attack on or from another player (PvP-only rules: POH pools, Death Charge). */
    public val LAST_PVP_TICK: AttrKey<Long> = AttrKey("last_pvp_tick")

    /** Set on NPCs immune to Ring of recoil and similar reflection (Vengeance): the Wilderness bosses. */
    public val REFLECT_IMMUNE: AttrKey<Boolean> = AttrKey("reflect_immune")

    /** Tick until which the player can't teleport for a short while (boss lairs: 3 ticks after entering or leaving). */
    public val TELEPORT_DELAY_UNTIL: AttrKey<Long> = AttrKey("teleport_delay_until")

    /** Tick until which teleports are blocked (Tele Block). */
    public val TELEBLOCK_UNTIL: AttrKey<Long> = AttrKey("teleblock_until")

    /** Tick at which the player's Charge runs out (magic sets it; combat reads it for the god spells). */
    public val MAGIC_CHARGED_UNTIL: AttrKey<Long> = AttrKey("magic_charged_until")

    /** Tick at which the player's Shadow Veil runs out (magic sets it; thieving reads it). */
    public val SHADOW_VEIL_UNTIL: AttrKey<Long> = AttrKey("shadow_veil_until")
}

/** Asks the Slayer module whether [npc] is [player]'s current Slayer task; [onTask] stays false if nobody answers. */
public class SlayerTaskCheck(public val player: PlayerRef, public val npc: NpcRef) : Event {
    public var onTask: Boolean = false
}

/** [caster] paid for a combat spell cast from [book] (magic content ends a Spellbook Swap on it). */
public class CombatSpellCast(public val caster: PlayerRef, public val book: Int) : Event

/** A player's look changed outside the equipment module (skull); the appearance owner re-sends it. */
public class AppearanceChanged(public val player: PlayerRef) : Event

/**
 * Asked when [player] is about to respawn after dying (raids: docs/mechanics/raids/common.md RC6). A handler that owns
 * the death (the player is in its raid) sets [handled] and decides: [keepItems] keeps everything (a raid's "wait until
 * the room ends" or a safe death), [respawnAt] moves the player there instead of the normal respawn, and [graveAt]
 * puts a dangerous death's grave there (a raid wipe: gravestones outside the entrance). Unhandled, the normal rules
 * apply; nothing changes for deaths outside such content.
 */
public class DeathPolicyRequest(public val player: PlayerRef, public val killer: EntityRef?) : Event {
    public var handled: Boolean = false
    public var keepItems: Boolean = false
    public var respawnAt: tp.util.CoordGrid? = null
    public var graveAt: tp.util.CoordGrid? = null

    /** When >= 0, the items lost go into this (persisted) inventory instead of a grave (ToB's reclaim chest). */
    public var storeInv: Int = -1
}

/**
 * Applies the normal dangerous-death item rules to [player] now, with the grave at [graveAt] (a raid wipe also costs the
 * players who died earlier in the room). Combat handles it; [done] says it ran.
 */
public class DeathItemsRequest(
    public val player: PlayerRef,
    public val graveAt: tp.util.CoordGrid,
    /** As [DeathPolicyRequest.storeInv]. */
    public val storeInv: Int = -1,
) : Event {
    public var done: Boolean = false
}

/** Turns all of [player]'s prayers off (content rules such as a raid room ending). Combat handles it. */
public class PrayersOffRequest(public val player: PlayerRef) : Event

/**
 * An inventory item used on another player that no owner of the inventory-on-player handler claims: published so
 * several modules can react to their own items (ToB escape crystal) through the one registered handler.
 */
public class ItemOnPlayerEvent(
    public val player: PlayerRef,
    public val target: PlayerRef,
    public val obj: Int,
    public val slot: Int,
) : Event
