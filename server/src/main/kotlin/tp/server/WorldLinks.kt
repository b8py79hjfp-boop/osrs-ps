package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.engine.World
import tp.engine.WorldAddress
import tp.protocol.api.FriendRow
import tp.protocol.api.ServerMessage
import tp.services.Broadcast
import tp.services.HubServices
import tp.services.SocialEvent
import tp.services.WorldInfo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * One world's live links to the hub: registration and population heartbeats (world directory), kicks (moderation),
 * broadcasts and system updates. Hub events are handed to the world thread with [World.execute].
 */
class WorldLinks(private val world: World, private val hub: HubServices, private val host: String, private val port: Int) : AutoCloseable {
    private val scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())
    private val subscriptions = ArrayList<AutoCloseable>()

    fun start(): WorldLinks {
        val id = world.config.id
        scheduler.execute {
            runCatching { runBlocking { hub.worlds.register(WorldInfo(id, host, port, "tickperfect", MEMBERS, 0)) } }
                .onFailure { logger.warn(it) { "world $id: hub registration failed" } }
        }
        scheduler.scheduleAtFixedRate({
            runCatching {
                runBlocking {
                    hub.worlds.heartbeat(id, world.playerCount)
                    world.worldDirectory = hub.worlds.list().associate { it.id to WorldAddress(it.host, it.flags) }
                }
            }
        }, 1, HEARTBEAT_SECONDS, TimeUnit.SECONDS)
        subscriptions +=
            hub.moderation.subscribeKicks(id) { account -> world.execute { world.playerByAccount(account)?.let(world::requestLogout) } }
        subscriptions += hub.social.subscribe(id) { e -> world.execute { onSocial(e) } }
        subscriptions +=
            hub.broadcasts.subscribe { b ->
                world.execute {
                    when (b) {
                        is Broadcast.Message -> world.forEachPlayer { it.session.send(ServerMessage.GameMessage(b.text)) }
                        is Broadcast.SystemUpdate -> world.systemUpdate(b.seconds)
                    }
                }
            }
        return this
    }

    /** World thread: delivers a hub social event to its player. */
    private fun onSocial(e: SocialEvent) {
        val player = world.playerByAccount(e.toAccount) ?: return
        when (e) {
            is SocialEvent.PrivateMessage -> {
                val crown = e.fromRights.ordinal // 0 none, 1 moderator crown, 2 administrator crown
                player.session.send(ServerMessage.PrivateMessage(e.from, world.config.id, ++pmCounter, crown, e.text))
            }

            is SocialEvent.FriendStatus -> {
                player.session.send(ServerMessage.FriendList(listOf(FriendRow(e.name, e.worldId, added = false))))
            }

            is SocialEvent.Muted -> {
                player.mutedUntil = e.until
                player.session.send(ServerMessage.GameMessage(if (e.until == 0L) "You have been unmuted." else "You have been muted."))
            }
        }
    }

    /** Per-world private-message counter (the client drops a repeated world + counter pair). World thread. */
    private var pmCounter = 0

    override fun close() {
        subscriptions.forEach { it.close() }
        scheduler.shutdownNow()
    }

    private companion object {
        const val MEMBERS = 1
        const val HEARTBEAT_SECONDS = 5L
    }
}
