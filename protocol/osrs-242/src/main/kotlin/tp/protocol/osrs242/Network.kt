package tp.protocol.osrs242

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.Unpooled
import net.rsprot.compression.HuffmanCodec
import net.rsprot.compression.provider.DefaultHuffmanCodecProvider
import net.rsprot.compression.provider.HuffmanCodecProvider
import net.rsprot.crypto.rsa.RsaKeyPair
import net.rsprot.protocol.api.AbstractNetworkServiceFactory
import net.rsprot.protocol.api.GameConnectionHandler
import net.rsprot.protocol.api.InetAddressTracker
import net.rsprot.protocol.api.NetworkService
import net.rsprot.protocol.api.handlers.ExceptionHandlers
import net.rsprot.protocol.api.handlers.INetAddressHandlers
import net.rsprot.protocol.api.implementation.DefaultInetAddressTracker
import net.rsprot.protocol.api.implementation.DefaultInetAddressValidator
import net.rsprot.protocol.api.js5.Js5GroupProvider
import net.rsprot.protocol.api.login.GameLoginResponseHandler
import net.rsprot.protocol.api.util.ZonePartialEnclosedCacheBuffer
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.common.loginprot.incoming.codec.shared.JxLayoutStats
import net.rsprot.protocol.game.incoming.buttons.If3Button
import net.rsprot.protocol.game.incoming.buttons.IfButtonD
import net.rsprot.protocol.game.incoming.buttons.IfScriptTrigger
import net.rsprot.protocol.game.incoming.friendchat.FriendChatJoinLeave
import net.rsprot.protocol.game.incoming.friendchat.FriendChatKick
import net.rsprot.protocol.game.incoming.friendchat.FriendChatSetRank
import net.rsprot.protocol.game.incoming.locs.OpLocV2
import net.rsprot.protocol.game.incoming.locs.OpLocV3
import net.rsprot.protocol.game.incoming.messaging.MessagePrivate
import net.rsprot.protocol.game.incoming.messaging.MessagePublic
import net.rsprot.protocol.game.incoming.misc.client.MapBuildComplete
import net.rsprot.protocol.game.incoming.misc.client.WindowStatus
import net.rsprot.protocol.game.incoming.misc.user.ClientCheat
import net.rsprot.protocol.game.incoming.misc.user.CloseModal
import net.rsprot.protocol.game.incoming.misc.user.MoveGameClick
import net.rsprot.protocol.game.incoming.misc.user.MoveMinimapClick
import net.rsprot.protocol.game.incoming.misc.user.SendSnapshot
import net.rsprot.protocol.game.incoming.npcs.OpNpcT
import net.rsprot.protocol.game.incoming.npcs.OpNpcV2
import net.rsprot.protocol.game.incoming.objs.OpObjV2
import net.rsprot.protocol.game.incoming.players.OpPlayer
import net.rsprot.protocol.game.incoming.players.OpPlayerT
import net.rsprot.protocol.game.incoming.resumed.ResumePCountDialog
import net.rsprot.protocol.game.incoming.resumed.ResumePObjDialog
import net.rsprot.protocol.game.incoming.resumed.ResumePStringDialog
import net.rsprot.protocol.game.incoming.resumed.ResumePauseButton
import net.rsprot.protocol.game.incoming.social.FriendListAdd
import net.rsprot.protocol.game.incoming.social.FriendListDel
import net.rsprot.protocol.game.incoming.social.IgnoreListAdd
import net.rsprot.protocol.game.incoming.social.IgnoreListDel
import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatar
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.worldentityinfo.WorldEntityAvatar
import net.rsprot.protocol.loginprot.incoming.util.AuthenticationType
import net.rsprot.protocol.loginprot.incoming.util.LoginBlock
import net.rsprot.protocol.loginprot.outgoing.LoginResponse
import net.rsprot.protocol.loginprot.outgoing.util.AuthenticatorResponse
import net.rsprot.protocol.message.codec.incoming.GameMessageConsumerRepositoryBuilder
import net.rsprot.protocol.message.codec.incoming.provider.DefaultGameMessageConsumerRepositoryProvider
import net.rsprot.protocol.message.codec.incoming.provider.GameMessageConsumerRepositoryProvider
import org.jire.netty.haproxy.HAProxyMode
import org.jire.netty.haproxy.HAProxyTrustPredicate
import tp.cache.js5.Js5Cache
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.GameNetwork
import tp.protocol.api.LoginDetails
import tp.protocol.api.LoginGate
import tp.protocol.api.LoginRejection
import tp.protocol.api.LoginRequest
import tp.protocol.api.SocialAction
import tp.symbols.Seqs
import java.io.IOException

private val logger = KotlinLogging.logger {}

/** RSProt `DefaultInetAddressValidator` default: 10 connections per IP. */
private const val DEFAULT_MAX_PER_IP = 10

/**
 * Data every world in a JVM shares: the pre-encoded JS5 responses (~200 MiB) and the Huffman codec (brief §7: the
 * immutable cache is shared across worlds). Build once per world-node.
 */
class Osrs242Shared(cache: Js5Cache) {
    internal val js5: Js5Responses = Js5Responses.build(cache)
    internal val huffman: HuffmanCodecProvider = Osrs242Network.loadHuffman(cache)
    private var networks = 0

    /**
     * RSProt's thread guard is process-wide (one world per JVM). With one network it pins the world thread; once a
     * second world starts in this JVM it is cleared, since each network is only ever driven by its own world thread
     * (DECISIONS D-026).
     */
    @Synchronized
    internal fun guardThread(service: NetworkService<*>) {
        networks++
        service.setCommunicationThread(if (networks == 1) Thread.currentThread() else null)
    }
}

/** Revision-242 [GameNetwork] on RSProt (D-005). Listens on [port] for game + JS5 (they share it). */
@OptIn(ExperimentalUnsignedTypes::class, ExperimentalStdlibApi::class)
class Osrs242Network(
    private val shared: Osrs242Shared,
    private val rsa: RsaKeyPair,
    private val port: Int,
    /** Game and JS5 connections allowed per IP address (RSProt default 10); raise for local bot swarms. */
    private val maxConnectionsPerIp: Int = DEFAULT_MAX_PER_IP,
    /** Address to bind (null = all). Lets several worlds share port 43594 on different addresses. */
    private val bindHost: String? = null,
    /**
     * PROXY protocol v2 (brief §12 DDoS setup): connections must come from a trusted proxy ([proxyTrust] CIDRs,
     * loopback when empty), which prepends the real client address. Off: direct connections.
     */
    private val proxyProtocol: Boolean = false,
    private val proxyTrust: List<String> = emptyList(),
) : GameNetwork {
    private val js5 = shared.js5
    private val huffman = shared.huffman
    private var service: NetworkService<RspSession>? = null

    val js5Summary: String get() = "${js5.groupCount} groups, ${js5.totalBytes / BYTES_PER_MIB} MiB pre-encoded"

    override fun start(loginGate: LoginGate) {
        val factory = Factory(loginGate)
        val built = factory.build()
        factory.service = built
        shared.guardThread(built)
        built.start()
        service = built
        logger.info { "rev 242 network listening on ${bindHost ?: "*"}:$port (js5: $js5Summary)" }
    }

    override fun buildInfos() {
        service?.infoProtocols?.update()
    }

    /** Shared per-tick zone-enclosed buffers (RSProt retains them a few ticks, then releases). World thread. */
    private val zoneBuffers = ZonePartialEnclosedCacheBuffer(listOf(OldSchoolClientType.DESKTOP))

    override fun afterFlush() {
        zoneBuffers.releaseBuffers()
    }

    private val npcAvatars = HashMap<Int, NpcAvatar>()
    private val npcLastCoord = HashMap<Int, IntArray>()

    override fun npcAdded(index: Int, type: Int, level: Int, x: Int, z: Int) {
        val net = service ?: return
        npcAvatars[index] = net.npcAvatarFactory.alloc(index = index, id = type, level = level, x = x, z = z)
        npcLastCoord[index] = intArrayOf(level, x, z)
    }

    override fun npcRemoved(index: Int) {
        val net = service ?: return
        npcAvatars.remove(index)?.let(net.npcAvatarFactory::release)
        npcLastCoord.remove(index)
    }

    /** One-tile change = walk step (NpcAvatar.walk takes a delta); anything else = teleport. */
    override fun npcSync(index: Int, level: Int, x: Int, z: Int) {
        val avatar = npcAvatars[index] ?: return
        val last = npcLastCoord.getValue(index)
        val dx = x - last[1]
        val dz = z - last[2]
        if (level == last[0] && dx == 0 && dz == 0) return
        if (level == last[0] && dx in -1..1 && dz in -1..1) avatar.walk(dx, dz) else avatar.teleport(level, x, z, jump = true)
        last[0] = level
        last[1] = x
        last[2] = z
    }

    override fun npcVisual(index: Int, visual: tp.protocol.api.Visual) {
        val avatar = npcAvatars[index] ?: return
        // A transform also changes the avatar's id, so new observers see the new type (RSProt `setTransmogrification`).
        if (visual is tp.protocol.api.Visual.Transform) avatar.setId(visual.id)
        Visuals.apply(avatar.extendedInfo, visual)
    }

    private val worldEntityAvatars = HashMap<Int, WorldEntityAvatar>()

    /** Live world entities by index, read by each session to rebuild a newly visible one (WE2). */
    internal val worldEntities = HashMap<Int, tp.protocol.api.WorldEntitySpec>()

    override fun worldEntityAdded(spec: tp.protocol.api.WorldEntitySpec) {
        val net = service ?: return
        worldEntities[spec.index] = spec
        worldEntityAvatars[spec.index] =
            net.worldEntityAvatarFactory.alloc(
                spec.index,
                spec.id,
                spec.ownerIndex,
                spec.sizeX,
                spec.sizeZ,
                spec.southWestZoneX,
                spec.southWestZoneZ,
                spec.minLevel,
                spec.maxLevel,
                spec.fineX,
                spec.fineZ,
                spec.projectedLevel,
                spec.activeLevel,
                spec.angle,
            )
    }

    override fun worldEntityRemoved(index: Int) {
        val net = service ?: return
        worldEntities.remove(index)
        worldEntityAvatars.remove(index)?.let(net.worldEntityAvatarFactory::release)
    }

    override fun worldEntityMoved(index: Int, level: Int, fineX: Int, fineZ: Int, angle: Int, teleport: Boolean) {
        val avatar = worldEntityAvatars[index] ?: return
        avatar.updateCoord(level, fineX, fineZ, teleport)
        avatar.updateAngle(angle)
    }

    override fun shutdown() {
        service?.shutdown()
    }

    private inner class Factory(private val gate: LoginGate) : AbstractNetworkServiceFactory<RspSession>() {
        var service: NetworkService<RspSession>? = null
        override val ports: List<Int> = listOf(port)
        override val host: String? = bindHost
        override val haproxyMode: HAProxyMode = if (proxyProtocol) HAProxyMode.ON else HAProxyMode.OFF
        override val haproxyTrustPredicate: HAProxyTrustPredicate =
            if (proxyTrust.isEmpty()) HAProxyTrustPredicate.LOOPBACK_ONLY else HAProxyTrustPredicate.ofCidrs(*proxyTrust.toTypedArray())
        override val supportedClientTypes: List<OldSchoolClientType> = listOf(OldSchoolClientType.DESKTOP)

        override fun getRsaKeyPair(): RsaKeyPair = rsa

        /**
         * Channel exceptions: a client closing its socket (logout, hop, closed window) shows up as an IOException such as
         * "Connection reset". That's normal, so it's logged at debug; anything else stays an error.
         */
        override fun getExceptionHandlers(): ExceptionHandlers<RspSession> =
            ExceptionHandlers({ ctx, cause ->
                if (ctx.channel().isActive) ctx.close()
                if (cause is java.io.IOException) {
                    logger.debug { "channel closed by peer ${ctx.channel()}: ${cause.message}" }
                } else {
                    logger.error(cause) { "Exception in channel ${ctx.channel()}" }
                }
            })

        override fun getHuffmanCodecProvider(): HuffmanCodecProvider = huffman

        override fun getJs5GroupProvider(): Js5GroupProvider = js5

        /** Log JS5 and game connections (not per-request) so client connectivity problems are visible. */
        override fun getINetAddressHandlers(): INetAddressHandlers =
            INetAddressHandlers(
                inetAddressValidator = DefaultInetAddressValidator(maxConnectionsPerIp),
                js5InetAddressTracker = LoggingTracker("js5"),
                gameInetAddressTracker = LoggingTracker("game"),
            )

        override fun getGameMessageConsumerRepositoryProvider(): GameMessageConsumerRepositoryProvider<RspSession> =
            consumers()

        override fun getGameConnectionHandler(): GameConnectionHandler<RspSession> =
            object : GameConnectionHandler<RspSession> {
                override fun onLogin(responseHandler: GameLoginResponseHandler<RspSession>, block: LoginBlock<AuthenticationType>) {
                    val net = service ?: return responseHandler.writeFailedResponse(LoginResponse.LoginServerOffline)
                    logger.info {
                        "login request: ${block.username} (crc ok=${block.crc.validate(js5.archiveCrcs)}, " +
                            "jx section=${block.jxSessionId != null}; layouts so far: no-jx=${JxLayoutStats.noJx.get()}, " +
                            "jx=${JxLayoutStats.withJx.get()})"
                    }
                    gate.onLogin(
                        RspLoginRequest(net, responseHandler, block, block.crc.validate(js5.archiveCrcs), zoneBuffers, worldEntities::get),
                    )
                }

                // Reconnection lands in Phase 5 (session handover); refuse cleanly for now.
                override fun onReconnect(
                    responseHandler: GameLoginResponseHandler<RspSession>,
                    block: LoginBlock<net.rsprot.crypto.xtea.XteaKey>,
                ) {
                    responseHandler.writeFailedResponse(LoginResponse.ConnectFail)
                }
            }
    }

    private class LoggingTracker(private val kind: String) : InetAddressTracker {
        private val delegate = DefaultInetAddressTracker()

        override fun register(address: String) {
            delegate.register(address)
            logger.info { "$kind connection from $address (${delegate.getCount(address)} open)" }
        }

        override fun deregister(address: String) {
            delegate.deregister(address)
            logger.info { "$kind disconnect from $address (${delegate.getCount(address)} open)" }
        }

        override fun getCount(address: String): Int = delegate.getCount(address)
    }

    /** Item / spell use-on packets: OPLOCT, OPOBJT, IF_BUTTONT; examine packets OPLOC6, OPNPC6, OPOBJ6. */
    private fun targetedConsumers(b: GameMessageConsumerRepositoryBuilder<RspSession>) {
        b.addListener(net.rsprot.protocol.game.incoming.locs.OpLoc6V1::class.java) { s, m -> s.deliver(ClientCommand.Examine(0, m.id)) }
        b.addListener(net.rsprot.protocol.game.incoming.locs.OpLoc6V2::class.java) { s, m -> s.deliver(ClientCommand.Examine(0, m.id)) }
        b.addListener(net.rsprot.protocol.game.incoming.npcs.OpNpc6::class.java) { s, m -> s.deliver(ClientCommand.Examine(1, m.id)) }
        b.addListener(net.rsprot.protocol.game.incoming.objs.OpObj6::class.java) { s, m -> s.deliver(ClientCommand.Examine(2, m.id)) }
        b.addListener(net.rsprot.protocol.game.incoming.misc.user.SetHeading::class.java) { s, m ->
            s.deliver(ClientCommand.SetHeading(m.heading))
        }
        b.addListener(net.rsprot.protocol.game.incoming.worldentities.OpWorldEntity::class.java) { s, m ->
            s.deliver(ClientCommand.OpWorldEntity(m.op, m.index, m.controlKey))
        }
        b.addListener(net.rsprot.protocol.game.incoming.locs.OpLocTV1::class.java) { s, m ->
            s.deliver(ClientCommand.OpLocT(m.id, m.x, m.z, m.selectedCombinedId, m.selectedSub, m.selectedObj, m.controlKey))
        }
        b.addListener(net.rsprot.protocol.game.incoming.locs.OpLocTV2::class.java) { s, m ->
            s.deliver(ClientCommand.OpLocT(m.id, m.x, m.z, m.selectedCombinedId, m.selectedSub, m.selectedObj, m.controlKey))
        }
        b.addListener(net.rsprot.protocol.game.incoming.objs.OpObjT::class.java) { s, m ->
            s.deliver(ClientCommand.OpObjT(m.id, m.x, m.z, m.selectedCombinedId, m.selectedSub, m.selectedObj, m.controlKey))
        }
        b.addListener(net.rsprot.protocol.game.incoming.buttons.IfButtonT::class.java) { s, m ->
            s.deliver(
                ClientCommand.ButtonT(m.selectedCombinedId, m.selectedSub, m.selectedObj, m.targetCombinedId, m.targetSub, m.targetObj),
            )
        }
    }

    /** Clan channel member menu: kick and ban (docs/mechanics/clans.md CL7). */
    private fun clanConsumers(b: GameMessageConsumerRepositoryBuilder<RspSession>) {
        b.addListener(net.rsprot.protocol.game.incoming.clan.ClanChannelFullRequest::class.java) { s, m ->
            s.deliver(ClientCommand.ClanFullRequest(m.clanId, settings = false))
        }
        b.addListener(net.rsprot.protocol.game.incoming.clan.ClanSettingsFullRequest::class.java) { s, m ->
            s.deliver(ClientCommand.ClanFullRequest(m.clanId, settings = true))
        }
        b.addListener(net.rsprot.protocol.game.incoming.clan.ClanChannelKickUser::class.java) { s, m ->
            s.deliver(ClientCommand.Social(SocialAction.CLAN_KICK, m.name, rank = m.clanId))
        }
        b.addListener(net.rsprot.protocol.game.incoming.clan.AffinedClanSettingsAddBannedFromChannel::class.java) { s, m ->
            s.deliver(ClientCommand.Social(SocialAction.CLAN_BAN, m.name, rank = m.clanId))
        }
    }

    /** Decoded packets -> [ClientCommand]s, delivered to the session currently being polled. */
    private fun consumers(): GameMessageConsumerRepositoryProvider<RspSession> {
        val b = GameMessageConsumerRepositoryBuilder<RspSession>()
        b.addListener(
            MoveGameClick::class.java,
        ) { s, m -> s.deliver(ClientCommand.MoveClick(m.x, m.z, m.keyCombination == CTRL_HELD, false)) }
        b.addListener(MoveMinimapClick::class.java) { s, m ->
            s.deliver(ClientCommand.MoveClick(m.x, m.z, m.keyCombination == CTRL_HELD, true))
        }
        b.addListener(ClientCheat::class.java) { s, m -> s.deliver(ClientCommand.Cheat(m.command)) }
        b.addListener(MapBuildComplete::class.java) { s, _ -> s.deliver(ClientCommand.MapBuildComplete) }
        b.addListener(OpLocV2::class.java) { s, m -> s.deliver(ClientCommand.OpLoc(m.op, m.id, m.x, m.z, m.controlKey)) }
        b.addListener(OpLocV3::class.java) { s, m -> s.deliver(ClientCommand.OpLoc(m.op, m.id, m.x, m.z, m.controlKey)) }
        targetedConsumers(b)
        b.addListener(OpNpcV2::class.java) { s, m -> s.deliver(ClientCommand.OpNpc(m.op, m.index, m.controlKey)) }
        b.addListener(OpPlayer::class.java) { s, m -> s.deliver(ClientCommand.OpPlayer(m.op, m.index, m.controlKey)) }
        b.addListener(OpPlayerT::class.java) { s, m ->
            s.deliver(ClientCommand.OpPlayerT(m.index, m.selectedCombinedId, m.selectedSub, m.selectedObj, m.controlKey))
        }
        b.addListener(OpNpcT::class.java) { s, m ->
            s.deliver(ClientCommand.OpNpcT(m.index, m.selectedCombinedId, m.selectedSub, m.selectedObj, m.controlKey))
        }
        b.addListener(OpObjV2::class.java) { s, m -> s.deliver(ClientCommand.OpObj(m.op, m.id, m.x, m.z, m.controlKey)) }
        b.addListener(If3Button::class.java) { s, m -> s.deliver(ClientCommand.Button(m.combinedId, m.op, m.sub, m.obj)) }
        b.addListener(net.rsprot.protocol.game.incoming.buttons.IfSubOp::class.java) { s, m ->
            s.deliver(ClientCommand.Button(m.combinedId, m.op, m.sub, m.obj, m.subop))
        }
        b.addListener(IfButtonD::class.java) { s, m ->
            fun opt(v: Int) = if (v == NONE_U16) -1 else v
            s.deliver(
                ClientCommand.ButtonDrag(
                    m.selectedCombinedId,
                    opt(m.selectedSub),
                    opt(m.selectedObj),
                    m.targetCombinedId,
                    opt(m.targetSub),
                    opt(m.targetObj),
                ),
            )
        }
        b.addListener(net.rsprot.protocol.game.incoming.misc.user.SetChatFilterSettings::class.java) { s, m ->
            s.deliver(ClientCommand.ChatFilters(m.publicChatFilter, m.privateChatFilter, m.tradeChatFilter))
        }
        b.addListener(MessagePublic::class.java) { s, m ->
            s.deliver(ClientCommand.PublicChat(m.message, m.colour, m.effect, m.type))
        }
        b.addListener(FriendListAdd::class.java) { s, m -> s.deliver(ClientCommand.Social(SocialAction.FRIEND_ADD, m.name)) }
        b.addListener(FriendListDel::class.java) { s, m -> s.deliver(ClientCommand.Social(SocialAction.FRIEND_DEL, m.name)) }
        b.addListener(IgnoreListAdd::class.java) { s, m -> s.deliver(ClientCommand.Social(SocialAction.IGNORE_ADD, m.name)) }
        b.addListener(IgnoreListDel::class.java) { s, m -> s.deliver(ClientCommand.Social(SocialAction.IGNORE_DEL, m.name)) }
        b.addListener(FriendChatJoinLeave::class.java) { s, m ->
            val name = m.name
            s.deliver(
                if (name ==
                    null
                ) {
                    ClientCommand.Social(SocialAction.CHANNEL_LEAVE, "")
                } else {
                    ClientCommand.Social(SocialAction.CHANNEL_JOIN, name)
                },
            )
        }
        clanConsumers(b)
        b.addListener(FriendChatKick::class.java) { s, m -> s.deliver(ClientCommand.Social(SocialAction.CHANNEL_KICK, m.name)) }
        b.addListener(FriendChatSetRank::class.java) { s, m ->
            // Client rank byte: 0 = friend .. 6 = general; "not ranked" arrives as 255 (-1 as a signed byte).
            s.deliver(ClientCommand.Social(SocialAction.CHANNEL_RANK, m.name, rank = m.rank.toByte().toInt()))
        }
        b.addListener(MessagePrivate::class.java) { s, m ->
            s.deliver(ClientCommand.Social(SocialAction.PRIVATE_MESSAGE, m.name, m.message))
        }
        b.addListener(ResumePauseButton::class.java) { s, m -> s.deliver(ClientCommand.ResumePause(m.combinedId, m.sub)) }
        b.addListener(ResumePStringDialog::class.java) { s, m -> s.deliver(ClientCommand.ResumeString(m.string)) }
        b.addListener(ResumePCountDialog::class.java) { s, m -> s.deliver(ClientCommand.ResumeCount(m.count)) }
        b.addListener(IfScriptTrigger::class.java) { s, m ->
            val bytes = ByteArray(m.buffer.readableBytes()).also { m.buffer.getBytes(m.buffer.readerIndex(), it) }
            m.buffer.release()
            s.deliver(ClientCommand.ScriptTrigger(m.combinedId, m.sub, m.obj, m.crc, bytes))
        }
        b.addListener(SendSnapshot::class.java) { s, m -> s.deliver(ClientCommand.ReportAbuse(m.name, m.ruleId, m.mute)) }
        b.addListener(ResumePObjDialog::class.java) { s, m -> s.deliver(ClientCommand.ResumeObj(m.obj)) }
        b.addListener(CloseModal::class.java) { s, _ -> s.deliver(ClientCommand.CloseModal) }
        b.addListener(WindowStatus::class.java) { s, m ->
            s.deliver(ClientCommand.WindowStatus(m.windowMode, m.frameWidth, m.frameHeight))
        }
        return DefaultGameMessageConsumerRepositoryProvider(b.build())
    }

    private class RspLoginRequest(
        private val service: NetworkService<RspSession>,
        private val handler: GameLoginResponseHandler<RspSession>,
        private val block: LoginBlock<AuthenticationType>,
        override val crcValid: Boolean,
        private val zoneBuffers: ZonePartialEnclosedCacheBuffer,
        private val worldEntityOf: (Int) -> tp.protocol.api.WorldEntitySpec?,
    ) : LoginRequest {
        override val username: String = block.username

        // Copied once: RSProt clears the login block's password after the response is written.
        override val password: String = (block.authentication as? AuthenticationType.PasswordAuthentication)?.password?.asString().orEmpty()

        override val address: String = ClientAddress.of(handler.ctx.channel())

        override val resizable: Boolean = block.resizable

        override fun accept(slot: Int, details: LoginDetails): ClientSession {
            val response =
                LoginResponse.Ok(
                    authenticatorResponse = AuthenticatorResponse.NoAuthenticator,
                    staffModLevel = details.staffModLevel,
                    playerMod = false,
                    index = slot,
                    member = details.member,
                    accountHash = slot.toLong(),
                    userId = slot.toLong(),
                    userHash = slot.toLong(),
                )
            val session = handler.writeSuccessfulResponse(response, block)
            val infos = service.infoProtocols.alloc(slot, OldSchoolClientType.DESKTOP)
            val rsp =
                RspSession(
                    session,
                    infos,
                    username,
                    zoneBuffers,
                    worldEntityOf,
                ) { closed -> service.infoProtocols.dealloc(closed.release()) }
            session.setDisconnectionHook { rsp.markDisconnected() }
            return rsp
        }

        override fun reject(reason: LoginRejection) {
            logger.info { "login rejected: $username ($reason)" }
            handler.writeFailedResponse(
                when (reason) {
                    LoginRejection.INVALID_CREDENTIALS -> LoginResponse.InvalidUsernameOrPassword
                    LoginRejection.ALREADY_ONLINE -> LoginResponse.Duplicate
                    LoginRejection.WORLD_FULL -> LoginResponse.ServerFull
                    LoginRejection.OUT_OF_DATE -> LoginResponse.ClientOutOfDate
                    LoginRejection.LOGIN_LIMIT_EXCEEDED -> LoginResponse.IPLimit
                    LoginRejection.UPDATE_IN_PROGRESS -> LoginResponse.UpdateInProgress
                    LoginRejection.SERVER_ERROR -> LoginResponse.LoginServerLoadError
                    LoginRejection.BANNED -> LoginResponse.Banned
                    LoginRejection.TOO_MANY_ATTEMPTS -> LoginResponse.TooManyAttempts
                    LoginRejection.HOP_BLOCKED -> LoginResponse.HopBlocked
                },
            )
        }
    }

    companion object {
        /** MOVE_GAMECLICK keyCombination value when ctrl is held (RSProt `MoveGameClick` docs: 1 = ctrl). */
        private const val CTRL_HELD = 1
        private const val BYTES_PER_MIB = 1L shl 20
        private const val BINARY_ARCHIVE = 10

        /** Huffman table: binary archive group named "huffman", file 0 (RS Mod `HuffmanProvider`). */
        internal fun loadHuffman(cache: Js5Cache): HuffmanCodecProvider {
            val index = cache.index(BINARY_ARCHIVE)
            val group = index.groupIdByName("huffman")
            if (group < 0) throw IOException("No huffman group in archive $BINARY_ARCHIVE")
            val files = cache.readFiles(BINARY_ARCHIVE, group) ?: throw IOException("Huffman group $group missing")
            return DefaultHuffmanCodecProvider(HuffmanCodec.create(Unpooled.wrappedBuffer(files.data[0])))
        }
    }
}

/**
 * Placeholder look until accounts own appearance (Phase 5): RS Mod's default male (`Appearance.assignDefaultIdentKit`,
 * `assignDefaultColours`) with the human base animations.
 */
internal object DefaultAppearance {
    private val IDENT_KITS = intArrayOf(9, 14, 109, 26, 33, 36, 42)
    private val COLOURS = intArrayOf(0, 3, 2, 0, 0)

    /** Combat level shown before stats exist (a fresh account is level 3). */
    private const val FRESH_COMBAT_LEVEL = 3

    fun apply(ext: PlayerAvatarExtendedInfo, name: String) {
        ext.setName(name)
        ext.setCombatLevel(FRESH_COMBAT_LEVEL)
        IDENT_KITS.forEachIndexed { slot, kit -> ext.setIdentKit(slot, kit) }
        COLOURS.forEachIndexed { slot, colour -> ext.setColour(slot, colour) }
        BaseAnims.apply(ext, null)
    }
}

/** The player's base animation set; a weapon's own anims (protocol-api `Appearance.renderAnims`) replace the defaults. */
internal object BaseAnims {
    private val DEFAULTS =
        intArrayOf(
            Seqs.HUMAN_READY,
            Seqs.HUMAN_TURNONSPOT,
            Seqs.HUMAN_WALK_F,
            Seqs.HUMAN_WALK_B,
            Seqs.HUMAN_WALK_L,
            Seqs.HUMAN_WALK_R,
            Seqs.HUMAN_RUNNING,
        )

    fun apply(ext: PlayerAvatarExtendedInfo, anims: IntArray?) {
        fun at(i: Int): Int = anims?.getOrNull(i)?.takeIf { it >= 0 } ?: DEFAULTS[i]
        @Suppress("MagicNumber") // the seven slots of the set, in order
        ext.setBaseAnimationSet(
            readyAnim = at(0),
            turnAnim = at(1),
            walkAnim = at(2),
            walkAnimBack = at(3),
            walkAnimLeft = at(4),
            walkAnimRight = at(5),
            runAnim = at(6),
        )
    }
}

/** RSProt reports an absent sub/obj as 0xFFFF. */
private const val NONE_U16 = 0xFFFF
