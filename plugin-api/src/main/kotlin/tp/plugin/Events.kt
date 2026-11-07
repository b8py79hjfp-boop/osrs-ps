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

/** [player] used the emote named [name] in the emote tab (e.g. "Cheer"); locked emotes are refused after this. */
public class EmotePerformed(public val player: PlayerRef, public val name: String) : Event
