package tp.content.combat

import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.RaidRoomFrame
import tp.plugin.SharedAttrs
import tp.util.CoordGrid

/**
 * Shared helpers for the Chambers of Xeric room fights (the room docs under docs/mechanics/raids/cox): the room an NPC was spawned in
 * (raids-cox sets [SharedAttrs.RAID_ROOM]), the raiders inside it, Challenge Mode, and max hits that follow the raid's
 * stat scaling (the wiki's maxed-solo max hits come from the standard NPC max-hit formula on those levels).
 */
internal object CoxKit {
    fun frame(npc: NpcRef): RaidRoomFrame? = npc[SharedAttrs.RAID_ROOM]

    fun cm(npc: NpcRef): Boolean = npc[SharedAttrs.RAID_MODE] == CHALLENGE

    /** Living raiders inside [npc]'s room (or near it when it has no room). */
    fun raiders(npc: NpcRef): List<PlayerRef> {
        val f = frame(npc) ?: return TfaKit.arena(npc)
        return npc.playersWithin(SCAN).filter { !it.isDying && it.coord in f }
    }

    /** Whether [p] is in [npc]'s room. */
    fun inRoom(npc: NpcRef, p: EntityRef): Boolean = frame(npc)?.let { p.coord in it } ?: true

    /**
     * The standard NPC max hit, floor((level + 9) x (bonus + 64) / 640 + 1/2), on the NPC's own (scaled) [stat] level:
     * Tekton 390 / +20 -> 52, enraged +30 -> 59, CM 585 -> 78 / 87 (tekton.md), so the infobox max hits follow it.
     */
    fun max(npc: NpcRef, stat: Int, bonus: Int): Int = ((npc.baseLevel(stat) + EFFECTIVE) * (bonus + BONUS_BASE) + HALF_DIVISOR) / DIVISOR

    /** A max hit stated for a maxed solo player ([soloMax] at base [baseLevel]), scaled with the NPC's [stat] level. */
    fun scaledMax(npc: NpcRef, stat: Int, baseLevel: Int, soloMax: Int): Int =
        maxOf(1, soloMax * (npc.baseLevel(stat) + EFFECTIVE) / (baseLevel + EFFECTIVE))

    /** Players standing in the 3 x 3 (radius 1) around [tile]. */
    fun inArea(npc: NpcRef, tile: CoordGrid, radius: Int = 1): List<PlayerRef> = BossCombat.playersOn(npc, tile, radius)

    /** Whether [p] stands under [npc]'s footprint. */
    fun under(npc: NpcRef, p: EntityRef): Boolean = TfaKit.under(npc, p.coord)

    /** NPCs of [types] in [npc]'s room (siblings spawned with it). */
    fun roomNpcs(npc: NpcRef, types: Set<Int>): List<NpcRef> {
        val f = frame(npc)
        return npc.npcsWithin(npc.coord, SCAN).filter { it.type in types && it.isInWorld && !it.isDying && (f == null || it.coord in f) }
    }

    /** One of [types] at random. */
    fun <T> pick(npc: NpcRef, items: List<T>): T? = if (items.isEmpty()) null else items[npc.random(items.size)]

    /** A repeating 1-tick loop on [npc] while it lives. */
    fun loop(npc: NpcRef, body: () -> Unit) {
        npc.queueHit(1, strong = false) {
            if (!npc.isInWorld || npc.isDying) return@queueHit
            body()
            loop(npc, body)
        }
    }

    const val CHALLENGE = "challenge"
    private const val SCAN = 40
    private const val EFFECTIVE = 9
    private const val BONUS_BASE = 64
    private const val DIVISOR = 640
    private const val HALF_DIVISOR = 320
}
