package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.engine.AccountBridge
import tp.engine.AccountProfile
import tp.engine.ChannelConfig
import tp.engine.ExchangeOffer
import tp.engine.ExchangeRequest
import tp.engine.World
import tp.engine.entity.Player
import tp.engine.stat.PlayerStats
import tp.protocol.api.FriendRow
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.protocol.api.ServerMessage
import tp.protocol.api.SocialAction
import tp.services.ChannelSettings
import tp.services.GeOffer
import tp.services.HubServices
import tp.services.LoginResult
import tp.services.Rights
import tp.services.Sanction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

private val logger = KotlinLogging.logger {}

/**
 * The world's [AccountBridge] to the hub. Hub calls run on virtual threads (never the world thread). Saves for one
 * account are chained in order, so an autosave still in flight always lands before that account's logout save, which
 * the hub writes durably before releasing the session lock (brief §7 dupe prevention).
 */
class HubAccountBridge(private val hub: HubServices, private val worldId: Int, private val world: World) : AccountBridge {
    private val io = Executors.newVirtualThreadPerTaskExecutor()
    private val chains = ConcurrentHashMap<Long, CompletableFuture<Unit>>()

    override fun authorize(request: LoginRequest, world: World) {
        io.execute {
            val grant =
                runCatching { runBlocking { hub.accounts.login(request.username, request.password, worldId, request.address) } }
                    .onFailure { logger.warn(it) { "hub login failed for ${request.username}" } }
                    .getOrNull()
            when (grant?.result) {
                LoginResult.OK -> {
                    world.admitAuthorized(
                        request,
                        AccountProfile(grant.accountId, grant.displayName, staffLevel(grant.rights), grant.save, grant.mutedUntil),
                    )
                }

                LoginResult.INVALID_CREDENTIALS -> {
                    request.reject(LoginRejection.INVALID_CREDENTIALS)
                }

                LoginResult.ALREADY_ONLINE -> {
                    request.reject(LoginRejection.ALREADY_ONLINE)
                }

                LoginResult.BANNED -> {
                    request.reject(LoginRejection.BANNED)
                }

                LoginResult.THROTTLED -> {
                    request.reject(LoginRejection.TOO_MANY_ATTEMPTS)
                }

                LoginResult.SAVE_PENDING -> {
                    request.reject(LoginRejection.HOP_BLOCKED)
                }

                LoginResult.SERVER_ERROR, null -> {
                    request.reject(LoginRejection.SERVER_ERROR)
                }
            }
        }
    }

    override fun saved(player: Player, save: String, final: Boolean) {
        val account = player.accountId
        if (account == 0L) return
        val started = System.nanoTime()
        chains.compute(account) { _, previous ->
            (previous ?: CompletableFuture.completedFuture(Unit)).thenApplyAsync({
                runCatching {
                    runBlocking {
                        if (final) hub.accounts.logout(account, worldId, save) else hub.saves.save(account, worldId, save)
                    }
                }.onFailure { logger.error(it) { "save failed for account $account (final=$final)" } }
                SaveMetrics.record(System.nanoTime() - started)
            }, io)
        }
        if (final) {
            chains[account]?.whenComplete { _, _ -> chains.remove(account) }
            val xp = IntArray(PlayerStats.COUNT) { player.stats.xpTenths(it) }
            val name = player.username
            if (player.staffLevel < ADMIN_LEVEL) io.execute { call("hiscores") { hub.hiscores.submit(account, name, xp) } }
        }
    }

    override fun refused(profile: AccountProfile) {
        io.execute { call("release") { hub.accounts.logout(profile.accountId, worldId, "") } }
    }

    override fun loggedIn(player: Player) {
        val account = player.accountId
        if (account == 0L) return
        io.execute {
            val friends = call("friends") { hub.social.friends(account) } ?: emptyList()
            val ignores = call("ignores") { hub.social.ignores(account) } ?: emptyList()
            world.execute {
                val p = world.playerByAccount(account) ?: return@execute
                if (ignores.isNotEmpty()) p.session.send(ServerMessage.IgnoreList(ignores))
                p.session.send(ServerMessage.FriendList(friends.map { FriendRow(it.name, it.worldId, added = false, rank = it.rank) }))
                p.session.send(ServerMessage.FriendListLoaded)
            }
        }
    }

    override fun social(player: Player, action: SocialAction, name: String, text: String, rank: Int) {
        val account = player.accountId
        if (account == 0L) return
        if (action >= SocialAction.CHANNEL_JOIN) return channel(account, action, name, text, rank)
        io.execute {
            val reply: String? = call("social") { listEdit(account, action, name, text) }
            // Refresh the edited row with the friend's current world (the client already shows the edit locally).
            val friend =
                if (action == SocialAction.FRIEND_ADD && reply == null) {
                    call("friends") { hub.social.friends(account) }?.firstOrNull { it.name.equals(name, ignoreCase = true) }
                } else {
                    null
                }
            world.execute {
                val p = world.playerByAccount(account) ?: return@execute
                if (reply != null) return@execute p.session.send(ServerMessage.GameMessage(reply))
                when (action) {
                    SocialAction.PRIVATE_MESSAGE -> {
                        p.session.send(ServerMessage.PrivateMessageEcho(name, text))
                    }

                    SocialAction.FRIEND_ADD -> {
                        friend?.let {
                            p.session.send(
                                ServerMessage.FriendList(listOf(FriendRow(it.name, it.worldId, true, it.rank))),
                            )
                        }
                    }

                    SocialAction.IGNORE_ADD -> {
                        p.session.send(ServerMessage.IgnoreList(listOf(name)))
                    }

                    SocialAction.IGNORE_DEL -> {
                        p.session.send(ServerMessage.IgnoreList(listOf(name), removed = true))
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    private suspend fun listEdit(account: Long, action: SocialAction, name: String, text: String): String? =
        when (action) {
            SocialAction.FRIEND_ADD -> hub.social.addFriend(account, name)
            SocialAction.FRIEND_DEL -> hub.social.removeFriend(account, name).let { null }
            SocialAction.IGNORE_ADD -> hub.social.addIgnore(account, name)
            SocialAction.IGNORE_DEL -> hub.social.removeIgnore(account, name).let { null }
            SocialAction.PRIVATE_MESSAGE -> hub.social.privateMessage(account, name, text)
            else -> null
        }

    /** Friends chat actions; the hub sends the channel updates through the social stream (WorldLinks). */
    private fun channel(account: Long, action: SocialAction, name: String, text: String, rank: Int) {
        io.execute {
            val reply =
                call("channel") {
                    when (action) {
                        SocialAction.CHANNEL_JOIN -> hub.social.joinChannel(account, name)
                        SocialAction.CHANNEL_LEAVE -> hub.social.leaveChannel(account).let { null }
                        SocialAction.CHANNEL_MESSAGE -> hub.social.channelMessage(account, text)
                        SocialAction.CHANNEL_KICK -> hub.social.channelKick(account, name)
                        SocialAction.CHANNEL_RANK -> hub.social.setChannelRank(account, name, rank)
                        else -> null
                    }
                }
            if (action == SocialAction.CHANNEL_RANK && reply == null) refreshFriend(account, name)
            val messages =
                when {
                    reply != null -> {
                        listOf(reply)
                    }

                    action == SocialAction.CHANNEL_JOIN -> {
                        listOf(
                            "Now talking in chat-channel $name.",
                            "To talk, start each line of chat with the / symbol.",
                        )
                    }

                    action == SocialAction.CHANNEL_LEAVE -> {
                        listOf("You have left the channel.")
                    }

                    else -> {
                        emptyList()
                    }
                }
            if (messages.isEmpty()) return@execute
            world.execute {
                val p = world.playerByAccount(account) ?: return@execute
                messages.forEach { p.session.send(ServerMessage.GameMessage(it)) }
            }
        }
    }

    /** Re-sends one friends-list row (its rank changed). Runs on an io thread. */
    private fun refreshFriend(account: Long, name: String) {
        val friend = call("friends") { hub.social.friends(account) }?.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return
        world.execute {
            world
                .playerByAccount(
                    account,
                )?.session
                ?.send(ServerMessage.FriendList(listOf(FriendRow(friend.name, friend.worldId, false, friend.rank))))
        }
    }

    override fun staff(player: Player, command: String, args: List<String>) {
        val account = player.accountId
        if (account == 0L) return
        io.execute {
            val reply =
                call("staff") {
                    when (command) {
                        "broadcast" -> hub.broadcasts.broadcast(args.joinToString(" ")).let { "Broadcast sent." }
                        "update" -> hub.broadcasts.systemUpdate(args[0].toIntOrNull() ?: 0).let { "System update scheduled." }
                        else -> moderate(account, command, args)
                    }
                } ?: "The hub did not answer."
            world.execute { world.playerByAccount(account)?.session?.send(ServerMessage.GameMessage(reply)) }
        }
    }

    override fun channelConfig(player: Player, update: ChannelConfig?, reply: (Player, ChannelConfig?) -> Unit) {
        val account = player.accountId
        if (account == 0L) return reply(player, null)
        io.execute {
            val settings =
                call("channel settings") {
                    update?.let { hub.social.setChannelSettings(account, ChannelSettings(it.name, it.enterRank, it.talkRank, it.kickRank)) }
                    hub.social.channelSettings(account)
                }
            val config = settings?.let { ChannelConfig(it.name, it.enterRank, it.talkRank, it.kickRank) }
            world.execute { world.playerByAccount(account)?.let { reply(it, config) } }
        }
    }

    override fun exchange(player: Player, request: ExchangeRequest, reply: (Player, List<ExchangeOffer>?) -> Unit) {
        val account = player.accountId
        if (account == 0L) return reply(player, null)
        io.execute {
            val result: List<GeOffer>? =
                call("exchange") {
                    when (request) {
                        ExchangeRequest.Offers -> {
                            hub.exchange.offers(account)
                        }

                        is ExchangeRequest.Place -> {
                            listOf(hub.exchange.place(account, request.slot, request.buy, request.obj, request.quantity, request.price))
                        }

                        is ExchangeRequest.Collect -> {
                            listOfNotNull(hub.exchange.collect(account, request.slot))
                        }

                        is ExchangeRequest.Abort -> {
                            listOfNotNull(hub.exchange.abort(account, request.slot))
                        }
                    }
                }
            val offers =
                result?.map {
                    ExchangeOffer(
                        it.slot,
                        it.buy,
                        it.obj,
                        it.quantity,
                        it.price,
                        it.completed,
                        it.collectCoins,
                        it.collectItems,
                    )
                }
            world.execute { world.playerByAccount(account)?.let { reply(it, offers) } }
        }
    }

    private suspend fun moderate(account: Long, command: String, args: List<String>): String {
        val sanction = SANCTIONS[command] ?: return "Unknown command."
        val minutes = args.getOrNull(1)?.toIntOrNull() ?: 0
        val reason = args.drop(if (args.getOrNull(1)?.toIntOrNull() != null) 2 else 1).joinToString(" ")
        return hub.moderation.apply(account, args[0].replace('_', ' '), sanction, minutes, reason) ?: "Done: $command ${args[0]}."
    }

    /** Runs a hub call on this (virtual) thread; failures are logged and give null. */
    private fun <T> call(what: String, block: suspend () -> T): T? =
        runCatching { runBlocking { block() } }.onFailure { logger.warn(it) { "hub $what call failed" } }.getOrNull()

    private companion object {
        const val ADMIN_LEVEL = 2
        val SANCTIONS =
            mapOf(
                "kick" to Sanction.KICK,
                "mute" to Sanction.MUTE,
                "unmute" to Sanction.UNMUTE,
                "ban" to Sanction.BAN,
                "ipban" to Sanction.IP_BAN,
                "unban" to Sanction.UNBAN,
            )
    }

    private fun staffLevel(rights: Rights): Int =
        when (rights) {
            Rights.PLAYER -> 0
            Rights.MODERATOR -> 1
            Rights.ADMIN -> 2
        }
}

/** Save latency (brief §12: alert on save latency). */
object SaveMetrics {
    @Volatile var lastNanos: Long = 0
        private set

    @Volatile var maxNanos: Long = 0
        private set

    private val windowMax =
        java.util.concurrent.atomic
            .AtomicLong()

    fun record(nanos: Long) {
        lastNanos = nanos
        if (nanos > maxNanos) maxNanos = nanos
        windowMax.accumulateAndGet(nanos, ::maxOf)
    }

    /** Slowest save since the previous call (alert window). */
    fun takeWindowMaxNanos(): Long = windowMax.getAndSet(0)
}
