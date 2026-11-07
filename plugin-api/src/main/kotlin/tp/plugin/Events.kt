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
