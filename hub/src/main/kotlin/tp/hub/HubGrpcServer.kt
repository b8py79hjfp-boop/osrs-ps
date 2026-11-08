package tp.hub

import io.grpc.Server
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import tp.services.Broadcast
import tp.services.CollectPart
import tp.services.HubServices
import tp.services.LoginResult
import tp.services.Sanction
import tp.services.TpPb
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
import tp.services.grpc.RankRequest
import tp.services.grpc.RegisterRequest
import tp.services.grpc.SanctionRequest
import tp.services.grpc.SaveRequest
import tp.services.grpc.SlotRequest
import tp.services.grpc.SocialGrpcKt
import tp.services.grpc.SystemUpdateRequest
import tp.services.grpc.WorldList
import tp.services.grpc.WorldRef
import tp.services.grpc.WorldsGrpcKt
import tp.util.Rank
import tp.services.grpc.Empty as PbEmpty
import tp.services.grpc.GeOffer as PbOffer
import tp.services.grpc.LoginRequest as PbLogin
import tp.services.grpc.SocialEvent as PbSocialEvent
import tp.services.grpc.World as PbWorld

private val EMPTY: PbEmpty = PbEmpty.getDefaultInstance()

private fun tp.services.ChannelMember.toPb(): tp.services.grpc.ChannelMember =
    tp.services.grpc.ChannelMember
        .newBuilder()
        .setName(name)
        .setWorldId(worldId)
        .setRank(rank)
        .build()

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
        .setGold(gold)
        .setAborted(aborted)
        .setTaxRate(taxRate)
        .setTax(tax)
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
            .addService(TradingPostRpc())
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
                .setRights(g.rights.stored)
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

        override suspend fun changePassword(request: tp.services.grpc.ChangePasswordRequest): ErrorReply =
            error(hub.accounts.changePassword(request.accountId, request.current, request.newPassword))

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
                            .setRank(it.rank)
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

        override suspend fun clanFound(request: NameRequest): ErrorReply = error(hub.social.clanFound(request.accountId, request.name))

        override suspend fun clanInvite(request: NameRequest): ErrorReply = error(hub.social.clanInvite(request.accountId, request.name))

        override suspend fun clanAccept(request: AccountRef): ErrorReply = error(hub.social.clanAccept(request.accountId))

        override suspend fun clanLeave(request: AccountRef): ErrorReply = error(hub.social.clanLeave(request.accountId))

        override suspend fun clanSetRank(request: RankRequest): ErrorReply =
            error(hub.social.clanSetRank(request.accountId, request.name, request.rank))

        override suspend fun clanKick(request: tp.services.grpc.ClanKickRequest): ErrorReply =
            error(hub.social.clanKick(request.accountId, request.name, request.ban))

        override suspend fun clanSetRules(request: tp.services.grpc.ClanRulesRequest): ErrorReply =
            error(hub.social.clanSetRules(request.accountId, tp.services.ClanPb.rules(request.rules)))

        override suspend fun clanJoinGuest(request: NameRequest): ErrorReply =
            error(
                hub.social.clanJoinGuest(request.accountId, request.name),
            )

        override suspend fun clanLeaveGuest(request: AccountRef): PbEmpty {
            hub.social.clanLeaveGuest(request.accountId)
            return EMPTY
        }

        override suspend fun clanMessage(request: tp.services.grpc.ClanMessageRequest): ErrorReply =
            error(hub.social.clanMessage(request.accountId, request.guest, request.text))

        override suspend fun joinChannel(request: NameRequest): ErrorReply = error(hub.social.joinChannel(request.accountId, request.name))

        override suspend fun leaveChannel(request: AccountRef): PbEmpty {
            hub.social.leaveChannel(request.accountId)
            return EMPTY
        }

        override suspend fun channelMessage(request: NameRequest): ErrorReply =
            error(
                hub.social.channelMessage(request.accountId, request.name),
            )

        override suspend fun channelKick(request: NameRequest): ErrorReply = error(hub.social.channelKick(request.accountId, request.name))

        override suspend fun getChannelSettings(request: AccountRef): tp.services.grpc.ChannelSettings {
            val s = hub.social.channelSettings(request.accountId)
            return tp.services.grpc.ChannelSettings
                .newBuilder()
                .setName(s.name.orEmpty())
                .setEnterRank(s.enterRank)
                .setTalkRank(s.talkRank)
                .setKickRank(s.kickRank)
                .build()
        }

        override suspend fun setChannelSettings(request: tp.services.grpc.ChannelSettingsRequest): PbEmpty {
            val s = request.settings
            hub.social.setChannelSettings(
                request.accountId,
                tp.services.ChannelSettings(s.name.ifEmpty { null }, s.enterRank, s.talkRank, s.kickRank),
            )
            return EMPTY
        }

        override suspend fun setChannelRank(request: RankRequest): ErrorReply =
            error(hub.social.setChannelRank(request.accountId, request.name, request.rank))

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
                                        ).setFromRights(e.fromRights.stored)
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

                            is tp.services.SocialEvent.ChannelJoined -> {
                                b.setChannelJoined(
                                    tp.services.grpc.ChannelJoined
                                        .newBuilder()
                                        .setOwner(e.owner)
                                        .setChannelName(e.channelName)
                                        .setKickRank(e.kickRank)
                                        .addAllMembers(e.members.map { it.toPb() }),
                                )
                            }

                            is tp.services.SocialEvent.ChannelLeft -> {
                                b.setChannelLeft(
                                    tp.services.grpc.ChannelLeft
                                        .newBuilder()
                                        .setKicked(e.kicked),
                                )
                            }

                            is tp.services.SocialEvent.ChannelMemberUpdate -> {
                                b.setChannelMember(
                                    tp.services.grpc.ChannelMemberUpdate
                                        .newBuilder()
                                        .setMember(e.member.toPb())
                                        .setRemoved(e.removed),
                                )
                            }

                            is tp.services.SocialEvent.ChannelMessage -> {
                                b.setChannelMessage(
                                    tp.services.grpc.ChannelMessage
                                        .newBuilder()
                                        .setFrom(e.from)
                                        .setFromRights(e.fromRights.stored)
                                        .setChannelName(e.channelName)
                                        .setWorldId(e.worldId)
                                        .setText(e.text),
                                )
                            }

                            is tp.services.SocialEvent.ClanState -> {
                                b.setClanState(tp.services.ClanPb.state(e))
                            }

                            is tp.services.SocialEvent.ClanMessage -> {
                                b.setClanMessage(tp.services.ClanPb.message(e))
                            }

                            is tp.services.SocialEvent.ClanInvite -> {
                                b.setClanInvite(tp.services.ClanPb.invite(e))
                            }

                            is tp.services.SocialEvent.Muted -> {
                                b.setMuted(
                                    tp.services.grpc.Muted
                                        .newBuilder()
                                        .setUntil(e.until),
                                )
                            }
                        }
                        trySend(b.build())
                    }
                awaitClose { handle.close() }
            }
    }

    private inner class TradingPostRpc : tp.services.grpc.TradingPostGrpcKt.TradingPostCoroutineImplBase() {
        private val tpost get() = hub.tradingPost

        override suspend fun list(request: tp.services.grpc.TpListRequest) =
            with(request) { TpPb.maybe(tpost.list(accountId, seller, obj, quantity, price, maxListings)) }

        override suspend fun cancel(request: tp.services.grpc.TpIdRequest) = TpPb.maybe(tpost.cancel(request.accountId, request.id))

        override suspend fun search(request: tp.services.grpc.TpSearchRequest) =
            with(request) {
                TpPb.listings(
                    tpost.search(if (byObj) objsList.toSet() else null, seller.ifEmpty { null }, limit.coerceIn(1, MAX_TP_RESULTS)),
                )
            }

        override suspend fun mine(request: AccountRef) = TpPb.listings(tpost.mine(request.accountId))

        override suspend fun buy(request: tp.services.grpc.TpBuyRequest) =
            with(request) { TpPb.buy(tpost.buy(accountId, buyer, id, quantity, price)) }

        override suspend fun collect(request: AccountRef) = TpPb.collect(tpost.collect(request.accountId))

        override suspend fun pending(request: AccountRef) = TpPb.collect(tpost.pending(request.accountId))

        override suspend fun history(request: AccountRef) = TpPb.trades(tpost.history(request.accountId))
    }

    private inner class Exchange : ExchangeGrpcKt.ExchangeCoroutineImplBase() {
        override suspend fun guidePrices(request: PbEmpty): tp.services.grpc.GuidePriceList =
            tp.services.grpc.GuidePriceList
                .newBuilder()
                .addAllPrices(
                    hub.exchange.guidePrices().map { (obj, price) ->
                        tp.services.grpc.GuidePrice
                            .newBuilder()
                            .setObj(obj)
                            .setPrice(price)
                            .build()
                    },
                ).build()

        override suspend fun place(request: PlaceOfferRequest): PbOffer =
            hub.exchange
                .place(request.accountId, request.slot, request.buy, request.obj, request.quantity, request.price, request.taxRate)
                .toPb()

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

        override suspend fun collect(request: SlotRequest): MaybeOffer =
            maybe(
                hub.exchange.collect(request.accountId, request.slot, CollectPart.entries.getOrElse(request.part) { CollectPart.ALL }),
            )

        override suspend fun abort(request: SlotRequest): MaybeOffer = maybe(hub.exchange.abort(request.accountId, request.slot))

        override suspend fun history(request: AccountRef): GeOffers =
            GeOffers.newBuilder().addAllOffers(hub.exchange.history(request.accountId).map { it.toPb() }).build()
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

        override suspend fun audit(request: tp.services.grpc.AuditRequest): tp.services.grpc.Empty {
            hub.moderation.audit(request.actor, request.action, request.target, request.detail)
            return tp.services.grpc.Empty
                .getDefaultInstance()
        }

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

        override suspend fun yell(request: tp.services.grpc.YellRequest): PbEmpty {
            hub.broadcasts.yell(request.from, Rank.parseOrPlayer(request.rank), request.worldId, request.text)
            return EMPTY
        }

        override suspend fun setInvisible(request: tp.services.grpc.InvisibleRequest): PbEmpty {
            hub.broadcasts.setInvisible(request.accountId, request.invisible)
            return EMPTY
        }

        override fun subscribe(request: PbEmpty): Flow<BroadcastEvent> =
            callbackFlow {
                val handle =
                    hub.broadcasts.subscribe { b ->
                        val e =
                            when (b) {
                                is Broadcast.Message -> {
                                    BroadcastEvent.newBuilder().setMessage(b.text)
                                }

                                is Broadcast.SystemUpdate -> {
                                    BroadcastEvent.newBuilder().setSystemUpdateSeconds(b.seconds)
                                }

                                is Broadcast.Presence -> {
                                    val u =
                                        tp.services.grpc.PresenceUpdate
                                            .newBuilder()
                                            .setSnapshot(b.snapshot)
                                    for (p in b.online) {
                                        u.addOnline(
                                            tp.services.grpc.PresencePb
                                                .newBuilder()
                                                .setAccountId(p.accountId)
                                                .setName(p.name)
                                                .setWorldId(p.worldId)
                                                .setRank(p.rank.stored)
                                                .setInvisible(p.invisible),
                                        )
                                    }
                                    u.addAllOffline(b.offline)
                                    BroadcastEvent.newBuilder().setPresence(u)
                                }

                                is Broadcast.Yell -> {
                                    BroadcastEvent.newBuilder().setYell(
                                        tp.services.grpc.YellRequest
                                            .newBuilder()
                                            .setFrom(b.from)
                                            .setRank(b.rank.stored)
                                            .setWorldId(b.worldId)
                                            .setText(b.text),
                                    )
                                }
                            }
                        trySend(e.build())
                    }
                awaitClose { handle.close() }
            }
    }

    @Suppress("unused") // keeps LoginResult referenced for readers of the wire format
    private val resultNames = LoginResult.entries.map { it.name }
}

/** Upper bound on one trading post search reply (a client asking for more gets this many). */
private const val MAX_TP_RESULTS = 200
