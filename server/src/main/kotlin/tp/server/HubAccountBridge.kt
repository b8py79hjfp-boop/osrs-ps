package tp.server

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import tp.engine.AccountBridge
import tp.engine.AccountProfile
import tp.engine.ChannelConfig
import tp.engine.ClanRequest
import tp.engine.ExchangeOffer
import tp.engine.ExchangeRequest
import tp.engine.TpListingRow
import tp.engine.TpTradeRow
import tp.engine.TradingPostReply
import tp.engine.TradingPostRequest
import tp.engine.World
import tp.engine.entity.Player
import tp.engine.stat.PlayerStats
import tp.protocol.api.FriendRow
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.protocol.api.ServerMessage
import tp.protocol.api.SocialAction
import tp.services.ChannelSettings
import tp.services.CollectPart
import tp.services.GeOffer
import tp.services.HubServices
import tp.services.LoginResult
import tp.services.Sanction
import tp.util.Rank
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
                        AccountProfile(grant.accountId, grant.displayName, grant.rights, grant.save, grant.mutedUntil),
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
            if (!player.rank.atLeast(Rank.ADMINISTRATOR)) io.execute { call("hiscores") { hub.hiscores.submit(account, name, xp) } }
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

    override fun audit(actor: String, action: String, target: String, detail: String) {
        io.execute { call("audit") { hub.moderation.audit(actor, action, target, detail) } }
    }

    override fun yell(player: Player, text: String) {
        val from = player.username
        val rank = player.rank
        io.execute { call("yell") { hub.broadcasts.yell(from, rank, worldId, text) } }
    }

    override fun setInvisible(player: Player, invisible: Boolean) {
        val account = player.accountId
        if (account == 0L) return
        io.execute { call("invisible") { hub.broadcasts.setInvisible(account, invisible) } }
    }

    override fun changePassword(player: Player, current: String, new: String, reply: (String?) -> Unit) {
        val account = player.accountId
        if (account == 0L) return reply("Unknown account.")
        io.execute {
            // A one-element list keeps "no answer" (null) apart from "done" (a null reason).
            val answer = call("password") { listOf(hub.accounts.changePassword(account, current, new)) }
            val result = if (answer == null) "The hub did not answer." else answer[0]
            // Back on the world thread through the pre-tick inbox (commands brief §4).
            world.execute { if (world.playerByAccount(account) != null) reply(result) }
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

    /**
     * G7: a staff member's report with "mute" mutes the reported player for 48 hours through the hub's audit-logged
     * moderation (OSRS "Mute for 48 hours"); other reports stay in the world log for review (DECISIONS D-068).
     */
    override fun reported(player: Player, name: String, rule: Int, mute: Boolean) {
        val account = player.accountId
        if (!mute || account == 0L || !player.rank.isStaff) return
        io.execute {
            val error =
                call("report mute") {
                    hub.moderation.apply(account, name, Sanction.MUTE, REPORT_MUTE_MINUTES, "report rule $rule")
                        ?: ""
                }
            val reply =
                when (error) {
                    null -> "The hub did not answer."
                    "" -> "$name has been muted for 48 hours."
                    else -> error
                }
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

    override fun clan(player: Player, request: ClanRequest, reply: (Player, String?) -> Unit) {
        val account = player.accountId
        if (account == 0L) return reply(player, AccountBridge.NO_HUB)
        io.execute {
            // call() answers null when the hub failed; a successful action answers Optional.empty().
            val answer = call("clan") { java.util.Optional.ofNullable(hub.social.clan(account, request)) }
            val err = if (answer == null) AccountBridge.NO_HUB else answer.orElse(null)
            world.execute { world.playerByAccount(account)?.let { reply(it, err) } }
        }
    }

    override fun tradingPost(player: Player, request: TradingPostRequest, reply: (Player, TradingPostReply?) -> Unit) {
        val account = player.accountId
        if (account == 0L) return reply(player, null)
        val name = player.username
        io.execute {
            val result = call("trading post") { tradingPostCall(account, name, request) }
            world.execute { world.playerByAccount(account)?.let { reply(it, result) } }
        }
    }

    private suspend fun tradingPostCall(account: Long, name: String, request: TradingPostRequest): TradingPostReply {
        val tpost = hub.tradingPost

        fun rows(ls: List<tp.services.TpListing>) =
            ls.map { TpListingRow(it.id, it.accountId, it.seller, it.obj, it.quantity, it.sold, it.price, it.listedAtMs) }
        return when (request) {
            is TradingPostRequest.Listing -> {
                val l = tpost.list(account, name, request.obj, request.quantity, request.price, request.maxListings)
                if (l == null) TradingPostReply(error = "You can't list any more items.") else TradingPostReply(listings = rows(listOf(l)))
            }

            is TradingPostRequest.Cancel -> {
                TradingPostReply(listings = rows(listOfNotNull(tpost.cancel(account, request.id))))
            }

            is TradingPostRequest.Search -> {
                TradingPostReply(listings = rows(tpost.search(request.objs, request.seller, request.limit)))
            }

            TradingPostRequest.Mine -> {
                TradingPostReply(listings = rows(tpost.mine(account)))
            }

            is TradingPostRequest.Buy -> {
                val r = tpost.buy(account, name, request.id, request.quantity, request.price)
                TradingPostReply(listings = rows(listOfNotNull(r.listing)), bought = r.bought, error = r.error)
            }

            TradingPostRequest.Collect -> {
                tpost.collect(account).let { TradingPostReply(coins = it.coins, items = it.items) }
            }

            TradingPostRequest.Pending -> {
                tpost.pending(account).let { TradingPostReply(coins = it.coins, items = it.items) }
            }

            TradingPostRequest.History -> {
                TradingPostReply(
                    trades = tpost.history(account).map { TpTradeRow(it.obj, it.quantity, it.price, it.bought, it.other, it.atMs) },
                )
            }
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
                            listOf(
                                hub.exchange.place(
                                    account,
                                    request.slot,
                                    request.buy,
                                    request.obj,
                                    request.quantity,
                                    request.price,
                                    request.taxRate,
                                ),
                            )
                        }

                        is ExchangeRequest.Collect -> {
                            val part =
                                when {
                                    request.items && request.coins -> CollectPart.ALL
                                    request.items -> CollectPart.ITEMS
                                    else -> CollectPart.COINS
                                }
                            listOfNotNull(hub.exchange.collect(account, request.slot, part))
                        }

                        is ExchangeRequest.Abort -> {
                            listOfNotNull(hub.exchange.abort(account, request.slot))
                        }

                        ExchangeRequest.History -> {
                            hub.exchange.history(account)
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
                        it.gold,
                        it.aborted,
                        it.tax,
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
        /** OSRS report window: "Mute for 48 hours" (staff option). */
        const val REPORT_MUTE_MINUTES = 48 * 60

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
