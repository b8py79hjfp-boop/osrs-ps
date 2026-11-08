package tp.content.combat

import tp.plugin.AttrKey
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.util.CoordGrid

/** Shared pieces of the Fractured Archive boss scripts (docs/mechanics/raids/tfa/ one file per boss). */
internal object TfaKit {
    private val SCALED = AttrKey<Int>("tfa_scaled")

    /** TFA9: the party size a raid NPC scales for (solo = 2), 2..8. */
    fun size(npc: NpcRef): Int = (npc[SharedAttrs.RAID_SCALE] ?: MIN_SCALE).coerceIn(MIN_SCALE, MAX_SCALE)

    /**
     * Gives [npc] its HP for the party size once ([hp]: values for 2..8 players, the wiki tables where 1-2 share the
     * first value). Called from the first attack or hit, after raids-core has tagged the NPC with its scale.
     */
    fun scale(npc: NpcRef, hp: IntArray) {
        if (npc[SCALED] != null) return
        val max = hp[size(npc) - MIN_SCALE]
        npc[SCALED] = max
        setPool(npc, max)
    }

    /** The NPC's HP max is [hp] and its hitpoints are full. */
    fun setPool(npc: NpcRef, hp: Int) {
        val levels = IntArray(NPC_STATS) { npc.baseLevel(it) }
        levels[NPC_HITPOINTS] = hp
        npc.overrideStats(levels)
        npc.hitpoints = hp
    }

    /** Living players within [radius] of [npc] (a room); a second list only when someone in range is dying. */
    fun arena(npc: NpcRef, radius: Int = ROOM_RADIUS): List<PlayerRef> {
        val out = npc.playersWithin(radius)
        if (out.any { it.isDying }) return out.filter { !it.isDying }
        return out
    }

    /** The tiles of a plus shape of arm length [arm] centred on [c]. */
    fun cross(c: CoordGrid, arm: Int): List<CoordGrid> {
        val out = ArrayList<CoordGrid>(arm * ARMS + 1)
        out += c
        for (d in 1..arm) {
            out += CoordGrid(c.x + d, c.z, c.level)
            out += CoordGrid(c.x - d, c.z, c.level)
            out += CoordGrid(c.x, c.z + d, c.level)
            out += CoordGrid(c.x, c.z - d, c.level)
        }
        return out
    }

    /** Whether [p] stands on any of [tiles]. */
    fun on(p: PlayerRef, tiles: Collection<CoordGrid>): Boolean =
        tiles.any {
            it.x == p.coord.x && it.z == p.coord.z &&
                it.level == p.coord.level
        }

    /** Whether tile [t] is under [npc]'s footprint. */
    fun under(npc: NpcRef, t: CoordGrid): Boolean =
        t.level == npc.coord.level && t.x - npc.coord.x in 0 until npc.size && t.z - npc.coord.z in 0 until npc.size

    /** Chebyshev distance from [p] to the nearest tile of [npc]'s footprint (0 = underneath). */
    fun gap(npc: NpcRef, p: tp.plugin.EntityRef): Int {
        val dx = maxOf(npc.coord.x - p.coord.x, 0, p.coord.x - (npc.coord.x + npc.size - 1))
        val dz = maxOf(npc.coord.z - p.coord.z, 0, p.coord.z - (npc.coord.z + npc.size - 1))
        return maxOf(dx, dz)
    }

    /**
     * Whether any of [cover] (NPC footprints) stands on the line from [npc]'s centre to [to], strictly between: a
     * pillar, cart or rubble shielding a player.
     */
    fun covered(npc: NpcRef, to: CoordGrid, cover: List<NpcRef>): Boolean {
        val cx = npc.coord.x + npc.size / 2
        val cz = npc.coord.z + npc.size / 2
        val steps = maxOf(kotlin.math.abs(to.x - cx), kotlin.math.abs(to.z - cz))
        for (i in 1 until steps) {
            val t = CoordGrid(cx + (to.x - cx) * i / steps, cz + (to.z - cz) * i / steps, to.level)
            if (cover.any { under(it, t) }) return true
        }
        return false
    }

    /** The tiles on the straight line from [a] to [b] (both ends included). */
    fun line(a: CoordGrid, b: CoordGrid): List<CoordGrid> {
        val steps = maxOf(kotlin.math.abs(b.x - a.x), kotlin.math.abs(b.z - a.z), 1)
        return (0..steps).map { i -> CoordGrid(a.x + (b.x - a.x) * i / steps, a.z + (b.z - a.z) * i / steps, a.level) }.distinct()
    }

    /** The first raid party member (orb order, leader first) alive near [npc], else the nearest player. */
    fun firstInOrbOrder(npc: NpcRef): PlayerRef? {
        val players = arena(npc)
        val order = npc[SharedAttrs.RAID_ORDER] ?: return players.minByOrNull { gap(npc, it) }
        return order.firstNotNullOfOrNull { n -> players.firstOrNull { it.username.equals(n, ignoreCase = true) } }
    }

    /** Adds [amount] special energy (varp `sa_energy`, 1,000 = 100%). */
    fun addSpecEnergy(p: PlayerRef, amount: Int) {
        p.setVarp(tp.symbols.Varps.SA_ENERGY, minOf(FULL_SPEC, p.varp(tp.symbols.Varps.SA_ENERGY) + amount))
    }

    private const val FULL_SPEC = 1000
    private const val MIN_SCALE = 2
    private const val MAX_SCALE = 8
    private const val NPC_STATS = 6
    private const val NPC_HITPOINTS = 3
    private const val ARMS = 4

    /** A TFA side room fits within 24 tiles of its boss. */
    private const val ROOM_RADIUS = 24
}
