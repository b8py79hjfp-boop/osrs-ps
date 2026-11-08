package tp.plugin

/** Marker for typed cross-plugin events (brief §6). */
public interface Event

/** Synchronous typed event bus: handlers run on the world thread in registration order. No reflection at publish. */
public class EventBus internal constructor() {
    private val handlers = HashMap<Class<*>, MutableList<(Event) -> Unit>>()

    public fun <E : Event> subscribe(type: Class<E>, handler: (E) -> Unit) {
        @Suppress("UNCHECKED_CAST") // keyed by the exact class
        handlers.getOrPut(type) { ArrayList() } += handler as (Event) -> Unit
    }

    public inline fun <reified E : Event> on(noinline handler: (E) -> Unit) {
        subscribe(E::class.java, handler)
    }

    public fun publish(event: Event) {
        handlers[event.javaClass]?.forEach { it(event) }
    }
}

/** Published after the login sequence's engine part (vars sent, player in world). */
public class LoginEvent(public val player: PlayerRef) : Event

/** Published before the player leaves the world. */
public class LogoutEvent(public val player: PlayerRef) : Event

/**
 * Opens a shop for [player] from a dialogue answer ("Yes please!"): the shop of NPC type [npcType] as its "Trade" op
 * would, or, for shops no NPC trades from, the one named [shopName] (its Wiki name). The shops plugin handles it.
 */
public class OpenShopRequest(public val player: PlayerRef, public val npcType: Int = -1, public val shopName: String? = null) : Event

/** Open [player]'s bank for a banker-like NPC that has its own conditions (Eniola charges 20 runes); the bank plugin handles it. */
public class OpenBankRequest(public val player: PlayerRef) : Event

/**
 * Asks [player] for the bank PIN when one is set and not yet entered this session (docs/mechanics/bank-pin.md BP2); [then]
 * runs once it is, at once without a PIN, and never on a wrong PIN or "Exit". The gameframe bank plugin handles it.
 */
public class BankPinRequest(public val player: PlayerRef, public val then: PlayerRef.() -> Unit) : Event

/** Open [player]'s Grand Exchange collection box (bankers' "I'd like to collect items."); the GE plugin handles it. */
public class OpenCollectionBox(public val player: PlayerRef) : Event

/** Open [player]'s collection log (the account summary's "Collection Log" row, gameframe J4); the collection log plugin handles it. */
public class OpenCollectionLog(public val player: PlayerRef) : Event

/**
 * [player] started a loc op (option [option], 1-based) or its approach script: published as the content script starts.
 * For bookkeeping (achievement diary tasks, quests): it cannot change what the op does.
 */
public class LocInteraction(public val player: PlayerRef, public val loc: LocRef, public val option: Int) : Event

/** As [LocInteraction], for an NPC op ([PlayerRef.seenType] gives the type the player sees). */
public class NpcInteraction(public val player: PlayerRef, public val npc: NpcRef, public val option: Int) : Event

/**
 * Items [player] got that did not exist before (crafted, caught, mined, bought from a shop, given by an NPC):
 * the net gain of each obj over everything the transaction [reason] touched, so bank / ground / trade moves don't
 * count. Published after the transaction commits.
 */
public class ItemsCreated(public val player: PlayerRef, public val reason: String, public val objs: Map<Int, Long>) : Event

/** [player] gained [xp] in [stat] (published by [PlayerRef.addXp]). */
public class XpGained(public val player: PlayerRef, public val stat: Int, public val xp: Double) : Event

/** [player] arrived at [to] from [from] by a teleport spell, tablet or jewellery (content/magic `Teleports`). */
public class Teleported(public val player: PlayerRef, public val from: tp.util.CoordGrid, public val to: tp.util.CoordGrid) : Event

/** [player] wielded / wore [obj] into worn slot [slot]. */
public class Equipped(public val player: PlayerRef, public val obj: Int, public val slot: Int) : Event

/**
 * Asks the equipment module to wield / wear / equip [obj] from [slot] of inventory [invId] without closing the interface
 * the request came from (the bank's backpack column, where a modal must stay open). The handler checks that [slot]
 * holds [obj], does what the backpack "Wield" op would, but skips the interrupt that closes the modal, and sets [handled].
 */
public class EquipRequest(public val player: PlayerRef, public val invId: Int, public val slot: Int, public val obj: Int) : Event {
    /** Set by the handler; false after publishing means nobody handles equip requests. */
    public var handled: Boolean = false
}

/**
 * As [EquipRequest] for eating or drinking [obj] in [slot] of inventory [invId] (the food / potion module; the backpack
 * "Eat" / "Drink" op without the modal-closing interrupt).
 */
public class ConsumeRequest(public val player: PlayerRef, public val invId: Int, public val slot: Int, public val obj: Int) : Event {
    /** Set by the handler; false after publishing means nobody handles consume requests. */
    public var handled: Boolean = false
}

/** [player] used the emote named [name] in the emote tab (e.g. "Cheer"); locked emotes are refused after this. */
public class EmotePerformed(public val player: PlayerRef, public val name: String) : Event

/**
 * Asks the combat module to cure [player]'s poison and give [immunityTicks] of immunity (content potions such as the
 * Chambers of Xeric antipoisons; the poison state belongs to combat).
 */
public class CurePoisonRequest(public val player: PlayerRef, public val immunityTicks: Int, public val curesVenom: Boolean = false) : Event

/**
 * Asked before a special attack's energy is checked or spent (content/combat): listeners may change [tenths] (energy in
 * tenths of a percent, the varp's unit); ToA's Overly Draining and liquid adrenaline (toa.md TA15).
 */
public class SpecialCostQuery(public val player: PlayerRef, public val special: String, public var tenths: Int) : Event

/**
 * Asked before [player] eats ([food]) or drinks [obj] (content/combat): a listener that sets [refusal] stops it with that
 * message; [restoresHealth] marks Saradomin brews and Guthix rest (ToA's On a Diet / Dehydration, toa.md TA15).
 */
public class ConsumeCheck(
    public val player: PlayerRef,
    public val obj: Int,
    public val food: Boolean,
    public val restoresHealth: Boolean,
    public var refusal: String? = null,
) : Event

/** Delays [player]'s next attack by [ticks] (content/combat; ToA nectar's "small attack delay"). */
public class AttackDelayRequest(public val player: PlayerRef, public val ticks: Int) : Event

/** [player] ate ([drink] false) or drank a dose of [obj] (content/combat food and potions; e.g. Wintertodt warmth). */
public class Consumed(public val player: PlayerRef, public val obj: Int, public val drink: Boolean) : Event

/**
 * Offer up to [max] of the player's bones ([ashes] false) or demonic ashes from the backpack for [multiplier] times
 * their Prayer xp (Sinister / Demonic Offering). The Prayer plugin removes them, grants the xp and sets [offered];
 * [refusal] is set instead when nothing could be offered.
 */
public class OfferRemains(
    public val player: PlayerRef,
    public val ashes: Boolean,
    public val max: Int,
    public val multiplier: Double,
) : Event {
    public var offered: Int = 0
    public var refusal: String? = null
}

/**
 * Teleport [player] to [to] with the standard spell-cast visuals and checks (Tele Block, boss-lair delay, Wilderness
 * level above [wildernessLimit], one teleport at a time): content/magic `Teleports` handles it (teleports.md T7) for
 * other modules' devices (house portals, jewellery boxes). [denied] holds the refusal message when it was refused.
 */
public class TeleportRequest(public val player: PlayerRef, public val to: tp.util.CoordGrid, public val wildernessLimit: Int = 20) : Event {
    public var handled: Boolean = false
    public var denied: String? = null
}

/** Poison ([venom] false, starting at [severity]) or envenom [target] from content outside combat (snake bites, traps). */
public class PoisonRequest(public val target: EntityRef, public val severity: Int, public val venom: Boolean = false) : Event

/** One IF_SCRIPTTRIGGER: [component] / [sub] / [obj] and the decoded [args] (Int, String, IntArray or Array<String>). */
public class ScriptTrigger(public val component: Int, public val sub: Int, public val obj: Int, public val args: List<Any>)

/**
 * The bank's backpack-column extra op (script 2576: "Fill" / "Empty") on a storage item at [slot] ([obj]): essence
 * pouches, the coal bag, the rune pouch. The plugin that owns the item sets [handled] and moves its contents with
 * [BankTransfer] (docs/mechanics/bank.md B30).
 */
public class BankItemOp(public val player: PlayerRef, public val slot: Int, public val obj: Int, public val text: String) : Event {
    public var handled: Boolean = false
}

/**
 * Moves up to [requested] of [obj] between the open bank and some storage that is not an inventory (pouch varbits):
 * [deposit] adds to the bank, else takes from it, through the bank's own tabs and placeholders. The bank plugin sets
 * [moved] to the amount and then calls [onMoved] with it, so the storage changes by exactly that much. A deposit from an
 * inventory ([source] >= 0, e.g. the looting bag) is taken from it in the bank's own transaction instead.
 */
public class BankTransfer(
    public val player: PlayerRef,
    public val obj: Int,
    public val requested: Int,
    public val deposit: Boolean,
    public val source: Int = -1,
    public val onMoved: (Int) -> Unit = {},
) : Event {
    public var moved: Int = 0
}

/** [player] picked [count] of [obj] up off the ground into the backpack (open bags then store it: gem bag, herb sack, seed box). */
public class ObjPickedUp(public val player: PlayerRef, public val obj: Int, public val count: Int) : Event

/**
 * [player] died a PvP death; storage whose contents are lost to the killer (herb sack: Wiki "Herb sack") drops them at
 * [at] for [killer] and clears itself. Published by content/combat before the items are split.
 */
public class PvpDeathStorage(public val player: PlayerRef, public val killer: String, public val at: tp.util.CoordGrid) : Event

/**
 * [player] was found in instance space with no live instance (restart, torn-down raid) and was moved to the exit saved
 * with [PlayerRef.setInstanceExit] ([hadExit]) or the world spawn (instances.md IN8): content applies its leave rules.
 */
public class InstanceLost(public val player: PlayerRef, public val hadExit: Boolean) : Event
