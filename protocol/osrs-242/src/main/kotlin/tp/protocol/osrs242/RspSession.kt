package tp.protocol.osrs242

import net.rsprot.protocol.api.Session
import net.rsprot.protocol.api.util.ZonePartialEnclosedCacheBuffer
import net.rsprot.protocol.common.client.OldSchoolClientType
import net.rsprot.protocol.common.game.outgoing.inv.InventoryObject
import net.rsprot.protocol.game.outgoing.info.Infos
import net.rsprot.protocol.game.outgoing.info.util.isEmpty
import net.rsprot.protocol.game.outgoing.info.util.onFailure
import net.rsprot.protocol.game.outgoing.info.util.onSuccess
import net.rsprot.protocol.game.outgoing.info.util.safeReleaseOrThrow
import net.rsprot.protocol.game.outgoing.interfaces.IfCloseSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenSub
import net.rsprot.protocol.game.outgoing.interfaces.IfOpenTop
import net.rsprot.protocol.game.outgoing.interfaces.IfSetEventsV2
import net.rsprot.protocol.game.outgoing.inv.UpdateInvFull
import net.rsprot.protocol.game.outgoing.inv.UpdateInvPartial
import net.rsprot.protocol.game.outgoing.logout.Logout
import net.rsprot.protocol.game.outgoing.map.RebuildLoginV2
import net.rsprot.protocol.game.outgoing.map.RebuildNormalV2
import net.rsprot.protocol.game.outgoing.misc.player.ChatFilterSettings
import net.rsprot.protocol.game.outgoing.misc.player.MessageGame
import net.rsprot.protocol.game.outgoing.misc.player.RunClientScript
import net.rsprot.protocol.game.outgoing.misc.player.SetPlayerOp
import net.rsprot.protocol.game.outgoing.misc.player.UpdateRunEnergy
import net.rsprot.protocol.game.outgoing.misc.player.UpdateStatV2
import net.rsprot.protocol.game.outgoing.util.OpFlags
import net.rsprot.protocol.game.outgoing.varp.VarpLarge
import net.rsprot.protocol.game.outgoing.varp.VarpReset
import net.rsprot.protocol.game.outgoing.varp.VarpSmall
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZoneFullFollows
import net.rsprot.protocol.game.outgoing.zone.header.UpdateZonePartialEnclosed
import net.rsprot.protocol.game.outgoing.zone.payload.MapProjAnimV2
import net.rsprot.protocol.game.outgoing.zone.payload.ObjAdd
import net.rsprot.protocol.game.outgoing.zone.payload.ObjDel
import net.rsprot.protocol.message.OutgoingGameMessage
import net.rsprot.protocol.message.ZoneProt
import tp.protocol.api.Appearance
import tp.protocol.api.AvatarState
import tp.protocol.api.ClientCommand
import tp.protocol.api.ClientSession
import tp.protocol.api.ServerMessage
import tp.protocol.api.Visual
import tp.protocol.api.ZoneOp

/**
 * One logged-in player's RSProt session + info objects. World thread only. RSProt consumers call [deliver] while
 * [pollCommands] drains the session; nothing else touches the sink.
 */
internal class RspSession(
    private val session: Session<RspSession>,
    private val infos: Infos,
    private val username: String,
    private val zoneBuffers: ZonePartialEnclosedCacheBuffer,
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
        if (message is ServerMessage.ZoneOps) {
            // Obj ops are only accepted inside UPDATE_ZONE_PARTIAL_ENCLOSED (RSProt `Session.validateMessage`).
            val ops = message.ops.map(::zoneProt)
            val buf = zoneBuffers.computeZoneForClient(OldSchoolClientType.DESKTOP, ops)
            session.queue(UpdateZonePartialEnclosed(message.dx, message.dz, message.level, buf))
            return
        }
        if (message is ServerMessage.Rebuild) infos.updateRootBuildAreaCenteredOnPlayer(message.x, message.z)
        session.queue(toRsprot(message))
    }

    override fun syncAvatar(coord: AvatarState) {
        val ext = infos.playerInfo.avatar.extendedInfo
        if (!appearanceSent) {
            DefaultAppearance.apply(ext, username)
            appearanceSent = true
            ext.setMoveSpeed(if (coord.running) MOVE_RUN else MOVE_WALK)
            lastRunning = coord.running
        }
        if (coord.running != lastRunning) {
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

    override fun visual(visual: Visual) {
        Visuals.apply(infos.playerInfo.avatar.extendedInfo, visual)
    }

    override fun appearance(appearance: Appearance) {
        val ext = infos.playerInfo.avatar.extendedInfo
        for (pos in 0 until WORN_SLOTS) {
            val obj = appearance.worn.getOrElse(pos) { -1 }
            ext.setWornObj(pos, obj, wornSecondary(appearance, pos, 0), wornSecondary(appearance, pos, 1))
        }
        ext.setCombatLevel(appearance.combatLevel)
        ext.setSkullIcon(appearance.skullIcon)
        ext.setOverheadIcon(appearance.prayerIcon)
    }

    private fun wornSecondary(appearance: Appearance, pos: Int, which: Int): Int =
        appearance.hidden.getOrNull(pos * 2 + which) ?: -1

    override fun publicChat(text: String, colour: Int, effects: Int) {
        infos.playerInfo.avatar.extendedInfo
            .setChat(colour, effects, 0, false, text, null)
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

        /** Appearance wear positions 0..11 (RSProt `setWornObj`). */
        private const val WORN_SLOTS = 12

        /** Root world id for rebuilds (RS Mod `RspCycle.worldId`). */
        private const val ROOT_WORLD = -1
        private const val GAME_MESSAGE_TYPE = 0
        private const val SUB_MODAL = 0
        private const val SUB_OVERLAY = 1
        private const val COMPONENT_SHIFT = 16
        private const val COMPONENT_MASK = 0xFFFF
        private const val EVENTS_LOW_MASK = 0xFFFFFFFFL
        private const val INT_BITS = 32
        private const val INV_COMBINED_BASE = 1234

        /** Every ground op enabled (RSProt `OpFlags.ALL_SHOWN`). */
        private val OBJ_OPS_ALL: Byte = OpFlags.ALL_SHOWN

        /** Player-info move speeds (RSProt `PlayerAvatarExtendedInfo.setMoveSpeed` docs): 1 walk, 2 run, 127 teleport. */
        private const val MOVE_WALK = 1
        private const val MOVE_RUN = 2
        private const val MOVE_TELEPORT = 127

        /** MapProjAnimV2 entity index: players -(index + 1), NPCs index + 1, 0 none (RSProt docs). */
        private fun projIndex(ref: tp.protocol.api.AvatarRef?): Int =
            when (ref) {
                null -> 0
                is tp.protocol.api.AvatarRef.OfPlayer -> -(ref.index + 1)
                is tp.protocol.api.AvatarRef.OfNpc -> ref.index + 1
            }

        private fun zoneProt(op: ZoneOp): ZoneProt =
            when (op) {
                is ZoneOp.ObjAdd -> {
                    ObjAdd(op.id, op.count, op.x, op.z, OBJ_OPS_ALL)
                }

                is ZoneOp.ObjDel -> {
                    ObjDel(op.id, op.count, op.x, op.z)
                }

                is ZoneOp.Projectile -> {
                    MapProjAnimV2(
                        op.id,
                        op.startHeight,
                        op.endHeight,
                        op.startTime,
                        op.endTime,
                        op.angle,
                        op.progress,
                        projIndex(op.source),
                        projIndex(op.target),
                        op.x,
                        op.z,
                        op.endX,
                        op.endZ,
                        op.endLevel,
                    )
                }
            }

        @Suppress("CyclomaticComplexMethod")
        fun toRsprot(message: ServerMessage): OutgoingGameMessage =
            when (message) {
                is ServerMessage.GameMessage -> {
                    MessageGame(GAME_MESSAGE_TYPE, message.text)
                }

                is ServerMessage.OpenTop -> {
                    IfOpenTop(message.interfaceId)
                }

                is ServerMessage.OpenSub -> {
                    IfOpenSub(message.destination, message.interfaceId, if (message.overlay) SUB_OVERLAY else SUB_MODAL)
                }

                is ServerMessage.CloseSub -> {
                    IfCloseSub(message.destination)
                }

                is ServerMessage.InterfaceEvents -> {
                    IfSetEventsV2(
                        message.component ushr COMPONENT_SHIFT,
                        message.component and COMPONENT_MASK,
                        message.start,
                        message.end,
                        (message.events and EVENTS_LOW_MASK).toInt(),
                        (message.events ushr INT_BITS).toInt(),
                    )
                }

                is ServerMessage.Varp -> {
                    if (message.value in
                        Byte.MIN_VALUE..Byte.MAX_VALUE
                    ) {
                        VarpSmall(message.id, message.value)
                    } else {
                        VarpLarge(message.id, message.value)
                    }
                }

                ServerMessage.VarpReset -> {
                    VarpReset
                }

                is ServerMessage.Inventory -> {
                    inventory(message)
                }

                is ServerMessage.Stat -> {
                    UpdateStatV2(message.stat, message.level, message.baseLevel, message.xp)
                }

                is ServerMessage.RunEnergy -> {
                    UpdateRunEnergy(message.energy)
                }

                is ServerMessage.ClientScript -> {
                    RunClientScript(message.id, message.args.map { if (it is String) 's' else 'i' }.toCharArray(), message.args)
                }

                is ServerMessage.PlayerOption -> {
                    SetPlayerOp(message.slot, message.top, message.text)
                }

                is ServerMessage.ChatFilters -> {
                    ChatFilterSettings(message.publicFilter, message.tradeFilter)
                }

                is ServerMessage.Rebuild -> {
                    RebuildNormalV2(message.x shr ZONE_SHIFT, message.z shr ZONE_SHIFT, ROOT_WORLD)
                }

                is ServerMessage.ZoneReset -> {
                    UpdateZoneFullFollows(message.dx, message.dz, message.level)
                }

                is ServerMessage.ZoneOps -> {
                    error("zone ops need the network's enclosed-buffer cache; handled in send()")
                }

                ServerMessage.Logout -> {
                    Logout
                }
            }

        /**
         * Inventories go out with combined id `-(1234 + inv)` and the full update covers slots up to the last occupied
         * one, as observed on live OSRS (RS Mod `UpdateInventory`); RSProt: positive combined ids crash the client.
         */
        private fun inventory(m: ServerMessage.Inventory): OutgoingGameMessage {
            val combined = -(INV_COMBINED_BASE + m.invId)

            fun packed(slot: Int): Long = if (m.objs[slot] < 0) InventoryObject.NULL else InventoryObject(m.objs[slot], m.counts[slot])
            val slots = m.slots ?: return UpdateInvFull(combined, m.invId, m.objs.indexOfLast { it >= 0 } + 1) { packed(it) }
            return UpdateInvPartial(
                combined,
                m.invId,
                object : UpdateInvPartial.IndexedObjectProvider(slots.iterator()) {
                    override fun provide(slot: Int): Long = InventoryObject(slot, m.objs[slot].coerceAtLeast(-1), m.counts[slot])
                },
            )
        }
    }
}
