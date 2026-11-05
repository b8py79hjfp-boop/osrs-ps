package tp.protocol.osrs242

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.onFailure
import net.rsprot.protocol.game.outgoing.info.util.onSuccess
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.logout.Logout
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import tp.protocol.api.AvatarState
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage

/**
 * One logged-in player's RSProt session + info objects. World thread only. RSProt consumers append decoded commands
 * to [pending] while [pollCommands] drains the session; nothing else touches it.
 */
internal class RspSession(
    private val session: Session<RspSession>,
    private val infos: Infos,
    private val username: String,
    private val onClose: (RspSession) -> Unit,
) : ClientSession {
    private var sink: ((ClientCommand) -> Unit)? = null
    private var appearanceSent = false
    private var lastRunning = false
    private var closed = false

    @Volatile private var disconnected = false

    override val isOpen: Boolean get() = !closed && !disconnected

    /** Called by RSProt from a Netty thread when the channel goes away. */
    internal fun markDisconnected() {
        disconnected = true
    }

    /** Login: first rebuild carries the player-info init block (RSProt README, "Updating Infos"). */
    override fun beginGame(state: AvatarState) {
        infos.updateRootCoord(state.level, state.x, state.z)
        infos.updateRootBuildAreaCenteredOnPlayer(state.x, state.z)
        session.queue(RebuildLoginV2(state.x shr ZONE_SHIFT, state.z shr ZONE_SHIFT, ROOT_WORLD, infos.playerInfo))
    }

    internal fun deliver(command: ClientCommand) {
        sink?.invoke(command)
    }

    override fun pollCommands(sink: (ClientCommand) -> Unit) {
        this.sink = sink
        try {
            session.processIncomingPackets(this)
        } finally {
            this.sink = null
        }
    }

    override fun send(message: ServerMessage) {
        when (message) {
            is ServerMessage.GameMessage -> {
                session.queue(MessageGame(GAME_MESSAGE_TYPE, message.text))
            }

            is ServerMessage.OpenTop -> {
                session.queue(IfOpenTop(message.interfaceId))
            }

            is ServerMessage.OpenSub -> {
                session.queue(IfOpenSub(message.destination, message.interfaceId, if (message.overlay) SUB_OVERLAY else SUB_MODAL))
            }

            is ServerMessage.Varp -> {
                session.queue(
                    if (message.value in
                        Byte.MIN_VALUE..Byte.MAX_VALUE
                    ) {
                        VarpSmall(message.id, message.value)
                    } else {
                        VarpLarge(message.id, message.value)
                    },
                )
            }

            is ServerMessage.Rebuild -> {
                infos.updateRootBuildAreaCenteredOnPlayer(message.x, message.z)
                session.queue(RebuildNormalV2(message.x shr ZONE_SHIFT, message.z shr ZONE_SHIFT, ROOT_WORLD))
            }

            ServerMessage.Logout -> {
                session.queue(Logout)
            }
        }
    }

    override fun syncAvatar(coord: AvatarState) {
        val ext = infos.playerInfo.avatar.extendedInfo
        if (!appearanceSent) {
            DefaultAppearance.apply(ext, username)
            appearanceSent = true
        }
        if (coord.running != lastRunning || !appearanceSent) {
            ext.setMoveSpeed(if (coord.running) MOVE_RUN else MOVE_WALK)
            lastRunning = coord.running
        }
        when {
            coord.teleported -> ext.setTempMoveSpeed(MOVE_TELEPORT)

            // A one-tile step while in run mode is shown as a walk step (RS Mod `RspCycle.updateMoveSpeed`).
            coord.running && coord.moveSteps == 1 -> ext.setTempMoveSpeed(MOVE_WALK)
        }
        infos.updateRootCoord(coord.level, coord.x, coord.z)
    }

    /** Info packets in the order of RSProt's README example; npc info is omitted (and released) when empty. */
    override fun flushInfos() {
        val root = infos.getPackets().rootWorldInfoPackets
        session.queue(root.activeWorld)
        session.queue(root.npcUpdateOrigin)
        root.worldEntityInfo.onSuccess(session::queue).onFailure { throw it }
        root.playerInfo.onSuccess(session::queue).onFailure { throw it }
        if (root.npcInfo.isEmpty()) {
            root.npcInfo.safeReleaseOrThrow()
        } else {
            root.npcInfo.onSuccess(session::queue).onFailure { throw it }
        }
        session.queue(root.activeWorld)
        session.flush()
    }

    override fun close() {
        if (closed) return
        closed = true
        session.requestClose()
        onClose(this)
    }

    internal fun release(): Infos = infos

    companion object {
        private const val ZONE_SHIFT = 3

        /** Root world id for rebuilds (RS Mod `RspCycle.worldId`). */
        private const val ROOT_WORLD = -1
        private const val GAME_MESSAGE_TYPE = 0
        private const val SUB_MODAL = 0
        private const val SUB_OVERLAY = 1

        /** Player-info move speeds (RSProt `PlayerAvatarExtendedInfo.setMoveSpeed` docs): 1 walk, 2 run, 127 teleport. */
        private const val MOVE_WALK = 1
        private const val MOVE_RUN = 2
        private const val MOVE_TELEPORT = 127
    }
}
