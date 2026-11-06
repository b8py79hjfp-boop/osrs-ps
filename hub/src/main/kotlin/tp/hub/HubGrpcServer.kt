package tp.hub

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import tp.services.Broadcast
import tp.services.HubServices
import tp.services.LoginResult
import tp.services.Sanction
import tp.services.WorldInfo
import tp.services.grpc.AccountRef
import tp.services.grpc.AccountsGrpcKt
import tp.services.grpc.BroadcastEvent
import tp.services.grpc.BroadcastRequest
import tp.services.grpc.BroadcastsGrpcKt
import tp.services.grpc.ErrorReply
import tp.services.grpc.ExchangeGrpcKt
import tp.services.grpc.FriendList
import tp.services.grpc.GeOffers
import tp.services.grpc.Heartbeat
import tp.services.grpc.HiscoreQuery
import tp.services.grpc.HiscoreSubmit
import tp.services.grpc.HiscoreTable
import tp.services.grpc.HiscoresGrpcKt
import tp.services.grpc.Kick
import tp.services.grpc.LoginReply
import tp.services.grpc.LogoutRequest
import tp.services.grpc.MaybeOffer
import tp.services.grpc.ModerationGrpcKt
import tp.services.grpc.NameList
import tp.services.grpc.NameRequest
import tp.services.grpc.PlaceOfferRequest
import tp.services.grpc.PrivateMessageRequest
import tp.services.grpc.RegisterRequest
import tp.services.grpc.SanctionRequest
import tp.services.grpc.SaveRequest
import tp.services.grpc.SlotRequest
import tp.services.grpc.SocialGrpcKt
import tp.services.grpc.SystemUpdateRequest
import tp.services.grpc.WorldList
import tp.services.grpc.WorldRef
import tp.services.grpc.WorldsGrpcKt
import tp.services.grpc.Empty as PbEmpty
import tp.services.grpc.GeOffer as PbOffer
import tp.services.grpc.LoginRequest as PbLogin
import tp.services.grpc.SocialEvent as PbSocialEvent
import tp.services.grpc.World as PbWorld

private val EMPTY: PbEmpty = PbEmpty.getDefaultInstance()

private fun error(message: String?): ErrorReply = ErrorReply.newBuilder().setError(message.orEmpty()).build()

internal fun tp.services.GeOffer.toPb(): PbOffer =
    PbOffer
        .newBuilder()
        .setId(id)
        .setAccountId(accountId)
        .setSlot(slot)
        .setBuy(buy)
        .setObj(obj)
        .setQuantity(quantity)
        .setPrice(price)
        .setCompleted(completed)
        .setCollectCoins(collectCoins)
        .setCollectItems(collectItems)
        .build()

private fun maybe(o: tp.services.GeOffer?): MaybeOffer =
    if (o ==
        null
    ) {
        MaybeOffer.getDefaultInstance()
    } else {
        MaybeOffer
            .newBuilder()
            .setPresent(true)
            .setOffer(o.toPb())
            .build()
    }

/** Serves [hub] over gRPC (hub.proto). */
class HubGrpcServer(private val hub: HubServices, port: Int) : AutoCloseable {
    private val server: Server =
        NettyServerBuilder
            .forPort(port)
            .addService(Accounts())
            .addService(Worlds())
            .addService(Social())
            .addService(Exchange())
            .addService(Hiscores())
            .addService(Moderation())
            .addService(Broadcasts())
            .build()

    fun start(): HubGrpcServer = apply { server.start() }

    val port: Int get() = server.port

    override fun close() {
        server.shutdown()
    }

    private inner class Accounts : AccountsGrpcKt.AccountsCoroutineImplBase() {
        override suspend fun login(request: PbLogin): LoginReply {
            val g = hub.accounts.login(request.username, request.password, request.worldId, request.ip)
            return LoginReply
                .newBuilder()
                .setResult(g.result.name)
                .setAccountId(g.accountId)
                .setDisplayName(g.displayName)
                .setRights(g.rights.name)
                .setSave(g.save.orEmpty())
                .setHasSave(g.save != null)
                .setMutedUntil(g.mutedUntil)
                .build()
        }

        override suspend fun logout(request: LogoutRequest): PbEmpty {
            hub.accounts.logout(request.accountId, request.worldId, request.save)
            return EMPTY
        }

        override suspend fun register(request: RegisterRequest): ErrorReply =
            error(
                hub.accounts.register(request.username, request.password, request.ip),
            )

        override suspend fun save(request: SaveRequest): PbEmpty {
            hub.saves.save(request.accountId, request.worldId, request.save)
            return EMPTY
        }
    }

    private inner class Worlds : WorldsGrpcKt.WorldsCoroutineImplBase() {
        override suspend fun register(request: PbWorld): PbEmpty {
            hub.worlds.register(WorldInfo(request.id, request.host, request.port, request.activity, request.flags, request.population))
            return EMPTY
        }

        override suspend fun beat(request: Heartbeat): PbEmpty {
            hub.worlds.heartbeat(request.worldId, request.population)
            return EMPTY
        }

        override suspend fun list(request: PbEmpty): WorldList =
            WorldList
                .newBuilder()
                .addAllWorlds(
                    hub.worlds.list().map {
                        PbWorld
                            .newBuilder()
                            .setId(
                                it.id,
                            ).setHost(it.host)
                            .setPort(it.port)
                            .setActivity(it.activity)
                            .setFlags(it.flags)
                            .setPopulation(it.population)
                            .build()
                    },
                ).build()
    }

    private inner class Social : SocialGrpcKt.SocialCoroutineImplBase() {
        override suspend fun friends(request: AccountRef): FriendList =
            FriendList
                .newBuilder()
                .addAllFriends(
                    hub.social.friends(request.accountId).map {
                        tp.services.grpc.Friend
                            .newBuilder()
                            .setName(it.name)
                            .setWorldId(it.worldId)
                            .build()
                    },
                ).build()

        override suspend fun ignores(request: AccountRef): NameList =
            NameList
                .newBuilder()
                .addAllNames(
                    hub.social.ignores(request.accountId),
                ).build()

        override suspend fun addFriend(request: NameRequest): ErrorReply = error(hub.social.addFriend(request.accountId, request.name))

        override suspend fun removeFriend(request: NameRequest): PbEmpty {
            hub.social.removeFriend(request.accountId, request.name)
            return EMPTY
        }

        override suspend fun addIgnore(request: NameRequest): ErrorReply = error(hub.social.addIgnore(request.accountId, request.name))

        override suspend fun removeIgnore(request: NameRequest): PbEmpty {
            hub.social.removeIgnore(request.accountId, request.name)
            return EMPTY
        }

        override suspend fun sendPrivateMessage(request: PrivateMessageRequest): ErrorReply =
            error(hub.social.privateMessage(request.fromAccount, request.toName, request.text))

        override fun subscribe(request: WorldRef): Flow<PbSocialEvent> =
            callbackFlow {
                val handle =
                    hub.social.subscribe(request.worldId) { e ->
                        val b = PbSocialEvent.newBuilder().setToAccount(e.toAccount)
                        when (e) {
                            is tp.services.SocialEvent.PrivateMessage -> {
                                b.setPrivateMessage(
                                    tp.services.grpc.PrivateMessage
                                        .newBuilder()
                                        .setFrom(
                                            e.from,
                                        ).setFromRights(e.fromRights.name)
                                        .setText(e.text),
                                )
                            }

                            is tp.services.SocialEvent.FriendStatus -> {
                                b.setFriendStatus(
                                    tp.services.grpc.FriendStatus
                                        .newBuilder()
                                        .setName(e.name)
                                        .setWorldId(e.worldId),
                                )
                            }
                        }
                        trySend(b.build())
                    }
                awaitClose { handle.close() }
            }
    }

    private inner class Exchange : ExchangeGrpcKt.ExchangeCoroutineImplBase() {
        override suspend fun place(request: PlaceOfferRequest): PbOffer =
            hub.exchange.place(request.accountId, request.slot, request.buy, request.obj, request.quantity, request.price).toPb()

        override suspend fun offers(request: AccountRef): GeOffers =
            GeOffers
                .newBuilder()
                .addAllOffers(
                    hub.exchange
                        .offers(
                            request.accountId,
                        ).map {
                            it.toPb()
                        },
                ).build()

        override suspend fun collect(request: SlotRequest): MaybeOffer = maybe(hub.exchange.collect(request.accountId, request.slot))

        override suspend fun abort(request: SlotRequest): MaybeOffer = maybe(hub.exchange.abort(request.accountId, request.slot))
    }

    private inner class Hiscores : HiscoresGrpcKt.HiscoresCoroutineImplBase() {
        override suspend fun submit(request: HiscoreSubmit): PbEmpty {
            hub.hiscores.submit(request.accountId, request.name, request.xpTenthsList.toIntArray())
            return EMPTY
        }

        override suspend fun top(request: HiscoreQuery): HiscoreTable =
            HiscoreTable
                .newBuilder()
                .addAllEntries(
                    hub.hiscores.top(request.stat, request.count).map {
                        tp.services.grpc.HiscoreEntry
                            .newBuilder()
                            .setName(it.name)
                            .setLevel(it.level)
                            .setXp(it.xp)
                            .build()
                    },
                ).build()
    }

    private inner class Moderation : ModerationGrpcKt.ModerationCoroutineImplBase() {
        override suspend fun apply(request: SanctionRequest): ErrorReply =
            error(
                hub.moderation.apply(
                    request.staffAccount,
                    request.target,
                    Sanction.valueOf(request.sanction),
                    request.minutes,
                    request.reason,
                ),
            )

        override fun subscribeKicks(request: WorldRef): Flow<Kick> =
            callbackFlow {
                val handle = hub.moderation.subscribeKicks(request.worldId) { trySend(Kick.newBuilder().setAccountId(it).build()) }
                awaitClose { handle.close() }
            }
    }

    private inner class Broadcasts : BroadcastsGrpcKt.BroadcastsCoroutineImplBase() {
        override suspend fun broadcast(request: BroadcastRequest): PbEmpty {
            hub.broadcasts.broadcast(request.text)
            return EMPTY
        }

        override suspend fun systemUpdate(request: SystemUpdateRequest): PbEmpty {
            hub.broadcasts.systemUpdate(request.seconds)
            return EMPTY
        }

        override fun subscribe(request: PbEmpty): Flow<BroadcastEvent> =
            callbackFlow {
                val handle =
                    hub.broadcasts.subscribe { b ->
                        val e =
                            when (b) {
                                is Broadcast.Message -> BroadcastEvent.newBuilder().setMessage(b.text)
                                is Broadcast.SystemUpdate -> BroadcastEvent.newBuilder().setSystemUpdateSeconds(b.seconds)
                            }
                        trySend(e.build())
                    }
                awaitClose { handle.close() }
            }
    }

    @Suppress("unused") // keeps LoginResult referenced for readers of the wire format
    private val resultNames = LoginResult.entries.map { it.name }
}
