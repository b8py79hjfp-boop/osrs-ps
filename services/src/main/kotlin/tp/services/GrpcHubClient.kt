package tp.services

import io.grpc.ManagedChannel
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import tp.services.grpc.AccountRef
import tp.services.grpc.AccountsGrpcKt
import tp.services.grpc.BroadcastEvent
import tp.services.grpc.BroadcastRequest
import tp.services.grpc.BroadcastsGrpcKt
import tp.services.grpc.ChannelSettingsRequest
import tp.services.grpc.ClanKickRequest
import tp.services.grpc.ClanMessageRequest
import tp.services.grpc.ClanRulesRequest
import tp.services.grpc.ExchangeGrpcKt
import tp.services.grpc.Heartbeat
import tp.services.grpc.HiscoreQuery
import tp.services.grpc.HiscoreSubmit
import tp.services.grpc.HiscoresGrpcKt
import tp.services.grpc.LogoutRequest
import tp.services.grpc.MaybeOffer
import tp.services.grpc.ModerationGrpcKt
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
import tp.services.grpc.WorldRef
import tp.services.grpc.WorldsGrpcKt
import java.util.concurrent.TimeUnit
import tp.services.grpc.ChannelMember as PbMember
import tp.services.grpc.ChannelSettings as PbSettings
import tp.services.grpc.Empty as PbEmpty
import tp.services.grpc.GeOffer as PbOffer
import tp.services.grpc.LoginRequest as PbLogin
import tp.services.grpc.World as PbWorld

/**
 * [HubServices] over gRPC (hub.proto): what a world-node uses to talk to a remote hub. Streams (social events, kicks,
 * broadcasts) are collected on a background scope and re-subscribed after a short pause if the hub restarts.
 */
class GrpcHubClient(host: String, port: Int) : HubServices, AutoCloseable {
    private val channel: ManagedChannel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val accountsStub = AccountsGrpcKt.AccountsCoroutineStub(channel)
    private val worldsStub = WorldsGrpcKt.WorldsCoroutineStub(channel)
    private val socialStub = SocialGrpcKt.SocialCoroutineStub(channel)
    private val exchangeStub = ExchangeGrpcKt.ExchangeCoroutineStub(channel)
    private val hiscoresStub = HiscoresGrpcKt.HiscoresCoroutineStub(channel)
    private val moderationStub = ModerationGrpcKt.ModerationCoroutineStub(channel)
    private val broadcastsStub = BroadcastsGrpcKt.BroadcastsCoroutineStub(channel)

    private fun err(s: String): String? = s.ifEmpty { null }

    private fun <T> stream(open: () -> Flow<T>, sink: (T) -> Unit): AutoCloseable {
        val job =
            scope.launch {
                while (true) {
                    runCatching { open().collect { sink(it) } }
                    delay(RESUBSCRIBE_MS)
                }
            }
        return AutoCloseable { job.cancel() }
    }

    override val accounts: AccountService =
        object : AccountService {
            override suspend fun login(username: String, password: String, worldId: Int, ip: String): LoginGrant {
                val r =
                    accountsStub.login(
                        PbLogin
                            .newBuilder()
                            .setUsername(username)
                            .setPassword(password)
                            .setWorldId(worldId)
                            .setIp(ip)
                            .build(),
                    )
                return LoginGrant(
                    LoginResult.valueOf(r.result),
                    r.accountId,
                    r.displayName,
                    Rights.valueOf(r.rights.ifEmpty { Rights.PLAYER.name }),
                    if (r.hasSave) r.save else null,
                    r.mutedUntil,
                )
            }

            override suspend fun logout(accountId: Long, worldId: Int, save: String) {
                accountsStub.logout(
                    LogoutRequest
                        .newBuilder()
                        .setAccountId(accountId)
                        .setWorldId(worldId)
                        .setSave(save)
                        .build(),
                )
            }

            override suspend fun register(username: String, password: String, ip: String): String? =
                err(
                    accountsStub
                        .register(
                            RegisterRequest
                                .newBuilder()
                                .setUsername(username)
                                .setPassword(password)
                                .setIp(ip)
                                .build(),
                        ).error,
                )
        }

    override val saves: SaveService =
        object : SaveService {
            override suspend fun save(accountId: Long, worldId: Int, save: String) {
                accountsStub.save(
                    SaveRequest
                        .newBuilder()
                        .setAccountId(accountId)
                        .setWorldId(worldId)
                        .setSave(save)
                        .build(),
                )
            }
        }

    override val worlds: WorldDirectory =
        object : WorldDirectory {
            override suspend fun register(world: WorldInfo) {
                worldsStub.register(
                    PbWorld
                        .newBuilder()
                        .setId(
                            world.id,
                        ).setHost(
                            world.host,
                        ).setPort(world.port)
                        .setActivity(world.activity)
                        .setFlags(world.flags)
                        .setPopulation(world.population)
                        .build(),
                )
            }

            override suspend fun heartbeat(worldId: Int, population: Int) {
                worldsStub.beat(
                    Heartbeat
                        .newBuilder()
                        .setWorldId(worldId)
                        .setPopulation(population)
                        .build(),
                )
            }

            override suspend fun list(): List<WorldInfo> =
                worldsStub
                    .list(
                        PbEmpty.getDefaultInstance(),
                    ).worldsList
                    .map { WorldInfo(it.id, it.host, it.port, it.activity, it.flags, it.population) }
        }

    private fun name(account: Long, name: String) =
        NameRequest
            .newBuilder()
            .setAccountId(account)
            .setName(name)
            .build()

    private fun ref(account: Long): AccountRef = AccountRef.newBuilder().setAccountId(account).build()

    override val social: SocialService =
        object : SocialService {
            override suspend fun friends(accountId: Long): List<FriendEntry> =
                socialStub
                    .friends(
                        AccountRef.newBuilder().setAccountId(accountId).build(),
                    ).friendsList
                    .map { FriendEntry(it.name, it.worldId, it.rank) }

            override suspend fun ignores(accountId: Long): List<String> =
                socialStub
                    .ignores(
                        AccountRef.newBuilder().setAccountId(accountId).build(),
                    ).namesList

            override suspend fun addFriend(accountId: Long, name: String): String? = err(socialStub.addFriend(name(accountId, name)).error)

            override suspend fun removeFriend(accountId: Long, name: String) {
                socialStub.removeFriend(name(accountId, name))
            }

            override suspend fun addIgnore(accountId: Long, name: String): String? = err(socialStub.addIgnore(name(accountId, name)).error)

            override suspend fun removeIgnore(accountId: Long, name: String) {
                socialStub.removeIgnore(name(accountId, name))
            }

            override suspend fun privateMessage(fromAccount: Long, toName: String, text: String): String? =
                err(
                    socialStub
                        .sendPrivateMessage(
                            PrivateMessageRequest
                                .newBuilder()
                                .setFromAccount(fromAccount)
                                .setToName(toName)
                                .setText(text)
                                .build(),
                        ).error,
                )

            override suspend fun joinChannel(accountId: Long, owner: String): String? =
                err(
                    socialStub.joinChannel(name(accountId, owner)).error,
                )

            override suspend fun leaveChannel(accountId: Long) {
                socialStub.leaveChannel(AccountRef.newBuilder().setAccountId(accountId).build())
            }

            override suspend fun channelMessage(accountId: Long, text: String): String? =
                err(socialStub.channelMessage(name(accountId, text)).error)

            override suspend fun channelKick(accountId: Long, name: String): String? =
                err(
                    socialStub.channelKick(name(accountId, name)).error,
                )

            override suspend fun channelSettings(accountId: Long): ChannelSettings =
                socialStub.getChannelSettings(AccountRef.newBuilder().setAccountId(accountId).build()).toModel()

            override suspend fun setChannelSettings(accountId: Long, settings: ChannelSettings) {
                socialStub.setChannelSettings(
                    ChannelSettingsRequest
                        .newBuilder()
                        .setAccountId(accountId)
                        .setSettings(settings.toPb())
                        .build(),
                )
            }

            override suspend fun setChannelRank(accountId: Long, name: String, rank: Int): String? =
                err(
                    socialStub
                        .setChannelRank(
                            RankRequest
                                .newBuilder()
                                .setAccountId(accountId)
                                .setName(name)
                                .setRank(rank)
                                .build(),
                        ).error,
                )

            override suspend fun clanFound(accountId: Long, name: String): String? = err(socialStub.clanFound(name(accountId, name)).error)

            override suspend fun clanInvite(accountId: Long, name: String): String? =
                err(
                    socialStub.clanInvite(name(accountId, name)).error,
                )

            override suspend fun clanAccept(accountId: Long): String? = err(socialStub.clanAccept(ref(accountId)).error)

            override suspend fun clanLeave(accountId: Long): String? = err(socialStub.clanLeave(ref(accountId)).error)

            override suspend fun clanSetRank(accountId: Long, name: String, rank: Int): String? =
                err(
                    socialStub
                        .clanSetRank(
                            RankRequest
                                .newBuilder()
                                .setAccountId(accountId)
                                .setName(name)
                                .setRank(rank)
                                .build(),
                        ).error,
                )

            override suspend fun clanKick(accountId: Long, name: String, ban: Boolean): String? =
                err(
                    socialStub
                        .clanKick(
                            ClanKickRequest
                                .newBuilder()
                                .setAccountId(accountId)
                                .setName(name)
                                .setBan(ban)
                                .build(),
                        ).error,
                )

            override suspend fun clanSetRules(accountId: Long, rules: ClanRules): String? =
                err(
                    socialStub
                        .clanSetRules(
                            ClanRulesRequest
                                .newBuilder()
                                .setAccountId(accountId)
                                .setRules(ClanPb.rules(rules))
                                .build(),
                        ).error,
                )

            override suspend fun clanJoinGuest(accountId: Long, clanName: String): String? =
                err(socialStub.clanJoinGuest(name(accountId, clanName)).error)

            override suspend fun clanLeaveGuest(accountId: Long) {
                socialStub.clanLeaveGuest(ref(accountId))
            }

            override suspend fun clanMessage(accountId: Long, guest: Boolean, text: String): String? =
                err(
                    socialStub
                        .clanMessage(
                            ClanMessageRequest
                                .newBuilder()
                                .setAccountId(accountId)
                                .setGuest(guest)
                                .setText(text)
                                .build(),
                        ).error,
                )

            override fun subscribe(worldId: Int, sink: (SocialEvent) -> Unit): AutoCloseable =
                stream({ socialStub.subscribe(WorldRef.newBuilder().setWorldId(worldId).build()) }) { e ->
                    when {
                        e.hasPrivateMessage() -> {
                            sink(
                                SocialEvent.PrivateMessage(
                                    e.toAccount,
                                    e.privateMessage.from,
                                    Rights.valueOf(e.privateMessage.fromRights),
                                    e.privateMessage.text,
                                ),
                            )
                        }

                        e.hasFriendStatus() -> {
                            sink(SocialEvent.FriendStatus(e.toAccount, e.friendStatus.name, e.friendStatus.worldId))
                        }

                        e.hasMuted() -> {
                            sink(SocialEvent.Muted(e.toAccount, e.muted.until))
                        }

                        e.hasChannelJoined() -> {
                            val j = e.channelJoined
                            sink(
                                SocialEvent.ChannelJoined(
                                    e.toAccount,
                                    j.owner,
                                    j.channelName,
                                    j.kickRank,
                                    j.membersList.map { it.toModel() },
                                ),
                            )
                        }

                        e.hasChannelLeft() -> {
                            sink(SocialEvent.ChannelLeft(e.toAccount, e.channelLeft.kicked))
                        }

                        e.hasChannelMember() -> {
                            sink(SocialEvent.ChannelMemberUpdate(e.toAccount, e.channelMember.member.toModel(), e.channelMember.removed))
                        }

                        e.hasClanState() -> {
                            sink(ClanPb.state(e.toAccount, e.clanState))
                        }

                        e.hasClanMessage() -> {
                            sink(ClanPb.message(e.toAccount, e.clanMessage))
                        }

                        e.hasClanInvite() -> {
                            sink(ClanPb.invite(e.toAccount, e.clanInvite))
                        }

                        e.hasChannelMessage() -> {
                            val m = e.channelMessage
                            sink(
                                SocialEvent.ChannelMessage(
                                    e.toAccount,
                                    m.from,
                                    Rights.valueOf(m.fromRights),
                                    m.channelName,
                                    m.worldId,
                                    m.text,
                                ),
                            )
                        }
                    }
                }
        }

    private fun PbSettings.toModel(): ChannelSettings = ChannelSettings(name.ifEmpty { null }, enterRank, talkRank, kickRank)

    private fun ChannelSettings.toPb(): PbSettings =
        PbSettings
            .newBuilder()
            .setName(name.orEmpty())
            .setEnterRank(enterRank)
            .setTalkRank(talkRank)
            .setKickRank(kickRank)
            .build()

    private fun PbMember.toModel(): ChannelMember = ChannelMember(name, worldId, rank)

    private fun PbOffer.toModel(): GeOffer =
        GeOffer(
            id,
            accountId,
            slot,
            buy,
            obj,
            quantity,
            price,
            completed,
            collectCoins,
            collectItems,
            gold,
            aborted,
            taxRate,
            tax,
        )

    private fun MaybeOffer.toModel(): GeOffer? = if (present) offer.toModel() else null

    override val exchange: ExchangeService =
        object : ExchangeService {
            override suspend fun place(
                accountId: Long,
                slot: Int,
                buy: Boolean,
                obj: Int,
                quantity: Int,
                price: Int,
                taxRate: Int,
            ): GeOffer =
                exchangeStub
                    .place(
                        PlaceOfferRequest
                            .newBuilder()
                            .setAccountId(
                                accountId,
                            ).setSlot(slot)
                            .setBuy(buy)
                            .setObj(obj)
                            .setQuantity(quantity)
                            .setPrice(price)
                            .setTaxRate(taxRate)
                            .build(),
                    ).toModel()

            override suspend fun offers(accountId: Long): List<GeOffer> =
                exchangeStub.offers(AccountRef.newBuilder().setAccountId(accountId).build()).offersList.map { it.toModel() }

            override suspend fun guidePrices(): Map<Int, Int> =
                exchangeStub.guidePrices(PbEmpty.getDefaultInstance()).pricesList.associate { it.obj to it.price }

            override suspend fun collect(accountId: Long, slot: Int, part: CollectPart): GeOffer? =
                exchangeStub
                    .collect(
                        SlotRequest
                            .newBuilder()
                            .setAccountId(accountId)
                            .setSlot(slot)
                            .setPart(part.ordinal)
                            .build(),
                    ).toModel()

            override suspend fun history(accountId: Long): List<GeOffer> =
                exchangeStub.history(AccountRef.newBuilder().setAccountId(accountId).build()).offersList.map { it.toModel() }

            override suspend fun abort(accountId: Long, slot: Int): GeOffer? =
                exchangeStub
                    .abort(
                        SlotRequest
                            .newBuilder()
                            .setAccountId(accountId)
                            .setSlot(slot)
                            .build(),
                    ).toModel()
        }

    override val hiscores: HiscoreService =
        object : HiscoreService {
            override suspend fun submit(accountId: Long, name: String, xpTenths: IntArray) {
                hiscoresStub.submit(
                    HiscoreSubmit
                        .newBuilder()
                        .setAccountId(accountId)
                        .setName(name)
                        .addAllXpTenths(xpTenths.toList())
                        .build(),
                )
            }

            override suspend fun top(stat: Int, count: Int): List<HiscoreEntry> =
                hiscoresStub
                    .top(
                        HiscoreQuery
                            .newBuilder()
                            .setStat(stat)
                            .setCount(count)
                            .build(),
                    ).entriesList
                    .map { HiscoreEntry(it.name, it.level, it.xp) }
        }

    override val moderation: ModerationService =
        object : ModerationService {
            override suspend fun apply(staffAccount: Long, target: String, sanction: Sanction, minutes: Int, reason: String): String? =
                err(
                    moderationStub
                        .apply(
                            SanctionRequest
                                .newBuilder()
                                .setStaffAccount(
                                    staffAccount,
                                ).setTarget(target)
                                .setSanction(sanction.name)
                                .setMinutes(minutes)
                                .setReason(reason)
                                .build(),
                        ).error,
                )

            override fun subscribeKicks(worldId: Int, sink: (Long) -> Unit): AutoCloseable =
                stream({ moderationStub.subscribeKicks(WorldRef.newBuilder().setWorldId(worldId).build()) }) { sink(it.accountId) }
        }

    override val broadcasts: BroadcastService =
        object : BroadcastService {
            override suspend fun broadcast(text: String) {
                broadcastsStub.broadcast(BroadcastRequest.newBuilder().setText(text).build())
            }

            override suspend fun systemUpdate(seconds: Int) {
                broadcastsStub.systemUpdate(SystemUpdateRequest.newBuilder().setSeconds(seconds).build())
            }

            override fun subscribe(sink: (Broadcast) -> Unit): AutoCloseable =
                stream({ broadcastsStub.subscribe(PbEmpty.getDefaultInstance()) }) { e: BroadcastEvent ->
                    when (e.eventCase) {
                        BroadcastEvent.EventCase.MESSAGE -> sink(Broadcast.Message(e.message))
                        BroadcastEvent.EventCase.SYSTEM_UPDATE_SECONDS -> sink(Broadcast.SystemUpdate(e.systemUpdateSeconds))
                        else -> Unit
                    }
                }
        }

    override fun close() {
        scope.cancel()
        channel.shutdown().awaitTermination(SHUTDOWN_SECONDS, TimeUnit.SECONDS)
    }

    private companion object {
        const val RESUBSCRIBE_MS = 2_000L
        const val SHUTDOWN_SECONDS = 5L
    }
}
