package tp.protocol.osrs242

import net.rsprot.protocol.game.outgoing.info.npcinfo.NpcAvatarExtendedInfo
import net.rsprot.protocol.game.outgoing.info.playerinfo.PlayerAvatarExtendedInfo
import tp.protocol.api.AvatarRef
import tp.protocol.api.Visual

/** [Visual] → RSProt extended info (RS Mod `RspCycle` / `RspNpcInfo` do the same calls). */
internal object Visuals {
    /** Pathing-entity index space: NPCs 0..65535, players 65536 + index (RSProt `setFacePathingEntity` docs). */
    private const val PLAYER_ENTITY_OFFSET = 0x10000

    private fun pathingIndex(ref: AvatarRef?): Int =
        when (ref) {
            null -> -1
            is AvatarRef.OfNpc -> ref.index
            is AvatarRef.OfPlayer -> PLAYER_ENTITY_OFFSET + ref.index
        }

    /** Hit/head-bar source index: NPCs index, players 65536 + index, -1 none (same space as pathing entities). */
    private fun sourceIndex(ref: AvatarRef?): Int = pathingIndex(ref)

    @Suppress("DEPRECATION") // setFacePathingEntity: single call for both entity kinds, as RS Mod uses it
    fun apply(ext: PlayerAvatarExtendedInfo, v: Visual) {
        when (v) {
            is Visual.Animate -> ext.setSequence(v.seq, v.delay)
            is Visual.SpotAnim -> ext.setSpotAnim(v.slot, v.id, v.delay, v.height)
            is Visual.Hit -> ext.addHitMark(sourceIndex(v.source), v.selfType, v.sourceType, v.otherType, v.value, v.delay)
            is Visual.HeadBar -> ext.addHeadBar(sourceIndex(v.source), v.bar, v.bar, v.fill, v.fill)
            is Visual.Face -> ext.setFacePathingEntity(pathingIndex(v.target))
            is Visual.Say -> ext.setSay(v.text)
            is Visual.Transform -> ext.setTransmogrification(v.id)
        }
    }

    @Suppress("DEPRECATION")
    fun apply(ext: NpcAvatarExtendedInfo, v: Visual) {
        when (v) {
            is Visual.Animate -> ext.setSequence(v.seq, v.delay)
            is Visual.SpotAnim -> ext.setSpotAnim(v.slot, v.id, v.delay, v.height)
            is Visual.Hit -> ext.addHitMark(sourceIndex(v.source), v.sourceType, v.otherType, v.value, v.delay)
            is Visual.HeadBar -> ext.addHeadBar(sourceIndex(v.source), v.bar, v.bar, v.fill, v.fill)
            is Visual.Face -> ext.setFacePathingEntity(pathingIndex(v.target))
            is Visual.Say -> ext.setSay(v.text)
            is Visual.Transform -> ext.setTransmogrification(v.id)
        }
    }
}
