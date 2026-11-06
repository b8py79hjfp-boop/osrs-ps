package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Runecraft (docs/mechanics/runecraft.md): the mysterious ruins of the thirteen regular runic altars, crafting runes on
 * them, and making talisman tiaras. A talisman used on the ruins, or "Enter" while wearing the tiara (or a Runecraft
 * cape), takes the player to the altar's temple; the temple's portal brings them back. "Craft-rune" or essence used on the
 * altar binds every essence in the backpack at once (RC4). The Abyss, the Zeah altars, combination runes, essence pouches
 * and the Ourania altar are OPEN (runecraft.md).
 */
public class Runecraft : ContentPlugin {
    override fun ContentScope.register() {
        for (temple in RunecraftData.TEMPLES) {
            ruins(temple)
            altar(temple)
            onOpLoc(temple.portalLoc, OP_USE) { leave(player, temple) }
        }
        onLogin {
            syncEntry()
            timer(SYNC, SYNC_TICKS, soft = true) { syncEntry() }
        }
    }

    private fun ContentScope.ruins(t: Temple) {
        val talismans = listOf(t.talisman, t.group.talisman)
        for (ruin in t.ruinLocs) {
            onOpLoc(ruin, OP_ENTER) { enterWorn(player, t) }
            for (talisman in talismans) onUseOnLoc(talisman, ruin) { enterWith(player, t, talisman) }
        }
    }

    private fun ContentScope.altar(t: Temple) {
        onOpLoc(t.altarLoc, OP_CRAFT) { craft(player, t) }
        for (essence in listOf(RunecraftData.RUNE_ESSENCE, RunecraftData.PURE_ESSENCE)) {
            onUseOnLoc(essence, t.altarLoc) { craft(player, t) }
        }
        for (obj in listOf(Objs.TIARA, Objs.TIARA_GOLD, t.talisman, t.group.talisman)) {
            onUseOnLoc(obj, t.altarLoc) { makeTiara(player, t) }
        }
    }

    // ---- ruins and portals

    /** RC1: the left-click "Enter" needs the altar's tiara, its elemental / catalytic tiara, or a Runecraft cape on. */
    private fun enterWorn(p: PlayerRef, t: Temple) {
        val worn = p.inv(Invs.WORN)
        if (!t.grantsEntry(worn.obj(RunecraftData.HEAD_SLOT), worn.obj(RunecraftData.CAPE_SLOT))) {
            return p.message(NOTHING)
        }
        enter(p, t)
    }

    /** RC1: a talisman of the altar (or its group) used on the ruins. */
    private fun enterWith(p: PlayerRef, t: Temple, talisman: Int) {
        if (p.inv(Invs.INV).total(talisman) == 0L) return
        enter(p, t)
    }

    private fun enter(p: PlayerRef, t: Temple) {
        p.message("You feel a powerful force take hold of you...")
        p.teleport(landing(p, t.portals[p.random(t.portals.size)], t.altar))
    }

    /** RC2: the portal returns the player to the tile beside their ruins. */
    private fun leave(p: PlayerRef, t: Temple) {
        p.teleport(outsideRuins(p, t.ruin))
    }

    /** The walkable tile next to [portal] that is nearest [altar]: the temple's arrival tile (RC2, OPEN-RC1). */
    private fun landing(p: PlayerRef, portal: CoordGrid, altar: CoordGrid): CoordGrid {
        val around = NEIGHBOURS.map { (dx, dz) -> portal.translate(dx, dz) }
        return around.sortedBy { distanceSquared(it, altar) }.firstOrNull { p.isWalkable(it) } ?: portal
    }

    /** The walkable tile around the 3x3 ruins at [ruin] (its south-west tile) nearest the middle of its south side (RC2, OPEN-RC1). */
    private fun outsideRuins(p: PlayerRef, ruin: CoordGrid): CoordGrid {
        val front = ruin.translate(RUIN_SIZE / 2, -1)
        val ring = ArrayList<CoordGrid>(RING_TILES)
        for (dx in -1..RUIN_SIZE) {
            for (dz in -1..RUIN_SIZE) {
                if (dx in 0 until RUIN_SIZE && dz in 0 until RUIN_SIZE) continue
                ring += ruin.translate(dx, dz)
            }
        }
        return ring.sortedBy { distanceSquared(it, front) }.firstOrNull { p.isWalkable(it) } ?: front
    }

    private fun distanceSquared(a: CoordGrid, b: CoordGrid): Int {
        val dx = a.x - b.x
        val dz = a.z - b.z
        return dx * dx + dz * dz
    }

    // ---- crafting

    /**
     * RC3-RC5: every rune and pure essence in the backpack becomes runes in one action (rune essence only where the altar
     * allows it), [Temple.runesPerEssence] per essence at the level held before the action, and xp per essence.
     */
    private fun craft(p: PlayerRef, t: Temple) {
        val inv = p.inv(Invs.INV)
        val rune = if (t.pureOnly) 0 else inv.total(RunecraftData.RUNE_ESSENCE).toInt()
        val pure = inv.total(RunecraftData.PURE_ESSENCE).toInt()
        val essence = rune + pure
        if (essence == 0) {
            val wrongKind = t.pureOnly && inv.total(RunecraftData.RUNE_ESSENCE) > 0
            return p.message(if (wrongKind) "You need pure essence to bind ${t.name} runes." else "You have no essence to bind.")
        }
        val level = p.level(Stats.RUNECRAFT)
        if (level < t.level) return p.message("You need a Runecraft level of ${t.level} to craft ${t.name} runes.")
        val runes = essence * t.runesPerEssence(level)
        val done =
            p.transaction("runecraft") {
                (rune == 0 || remove(Invs.INV, RunecraftData.RUNE_ESSENCE, rune)) &&
                    (pure == 0 || remove(Invs.INV, RunecraftData.PURE_ESSENCE, pure)) &&
                    add(Invs.INV, t.rune, runes)
            }
        if (!done) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into ${t.name} runes.")
        p.addXp(Stats.RUNECRAFT, t.xp * essence)
    }

    /**
     * RC6: a tiara and the altar's talisman (or a gold tiara and the elemental / catalytic talisman) in the backpack, either
     * one used on the altar, make the talisman tiara; both are used up and the Wiki's xp is given.
     */
    private fun makeTiara(p: PlayerRef, t: Temple) {
        val inv = p.inv(Invs.INV)
        val plain = inv.total(Objs.TIARA) > 0 && inv.total(t.talisman) > 0
        val shared = inv.total(Objs.TIARA_GOLD) > 0 && inv.total(t.group.talisman) > 0
        val binding =
            when {
                plain -> Binding(Objs.TIARA, t.talisman, t.tiara, t.tiaraXp)
                shared -> Binding(Objs.TIARA_GOLD, t.group.talisman, t.group.tiara, t.groupTiaraXp)
                else -> return p.message(NOTHING)
            }
        val done =
            p.transaction("tiara") {
                remove(Invs.INV, binding.blank, 1) && remove(Invs.INV, binding.talisman, 1) && add(Invs.INV, binding.result, 1)
            }
        if (done) p.addXp(Stats.RUNECRAFT, binding.xp)
    }

    private data class Binding(val blank: Int, val talisman: Int, val result: Int, val xp: Double)

    // ---- the "Enter" op

    /**
     * RC1: the client offers "Enter" on a ruin only while its multi-loc varbit is 1 (cache loc 34813 etc.: varbit
     * `rc_no_tally_required_<rune>`, child 0 has no op, child 1 has "Enter"). There is no equipment-changed event, so the
     * head and cape slots are polled; the varbits are rewritten only when one of them changed (OPEN-RC4).
     */
    private fun PlayerRef.syncEntry() {
        val worn = inv(Invs.WORN)
        val head = worn.obj(RunecraftData.HEAD_SLOT)
        val cape = worn.obj(RunecraftData.CAPE_SLOT)
        val last = this[LAST_WORN] ?: IntArray(2) { UNSYNCED }.also { this[LAST_WORN] = it }
        if (last[0] == head && last[1] == cape) return
        last[0] = head
        last[1] = cape
        for (t in RunecraftData.TEMPLES) setVarbit(t.noTallyVarbit, if (t.grantsEntry(head, cape)) 1 else 0)
    }

    private companion object {
        /** Loc op 1 of the ruins ("Enter"), the altars ("Craft-rune") and the exit portals ("Use") in the cache. */
        const val OP_ENTER = 1
        const val OP_CRAFT = 1
        const val OP_USE = 1

        const val NOTHING = "Nothing interesting happens."

        /** The eight tiles around a portal. */
        val NEIGHBOURS = listOf(-1 to -1, 0 to -1, 1 to -1, -1 to 0, 1 to 0, -1 to 1, 0 to 1, 1 to 1)

        /** Width of the mysterious ruins loc (cache loc 28914: width 3, length 3). */
        const val RUIN_SIZE = 3

        /** Tiles in the ring around a 3x3 loc. */
        const val RING_TILES = 16

        /** Ticks between two polls of the worn head and cape. A lag of a few ticks only delays when the client offers "Enter". */
        const val SYNC_TICKS = 3

        /** A worn obj id no slot holds, so the first poll always writes the varbits. */
        const val UNSYNCED = -2

        val SYNC = TimerId("runecraft.entry")
        val LAST_WORN = AttrKey<IntArray>("runecraft.worn")
    }
}
