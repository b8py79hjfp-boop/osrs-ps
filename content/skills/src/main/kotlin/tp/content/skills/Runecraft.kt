package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/**
 * Runecraft (docs/mechanics/runecraft.md): the mysterious ruins of the thirteen regular runic altars, crafting runes on
 * them, and making talisman tiaras. A talisman used on the ruins, or "Enter" while wearing the tiara (or a Runecraft
 * cape), takes the player to the altar's temple; the temple's portal brings them back. "Craft-rune" or essence used on the
 * altar binds every essence in the backpack at once (RC4). Combination runes, pouches, the Abyss, the Ourania and Arceuus altars
 * are in the Runecraft* files next to this one (runecraft.md RC7-RC21); the Astral altar is here. Guardians of the Rift is
 * `RunecraftGotr*.kt` (RC22).
 */
public class Runecraft : ContentPlugin {
    override fun ContentScope.register() {
        for (temple in RunecraftData.TEMPLES) {
            ruins(temple)
            altar(temple)
            onOpLoc(temple.portalLoc, OP_USE) { leave(player, temple) }
        }
        onOpLoc(Locs.ASTRAL_ALTAR, OP_CRAFT) { astral(player) }
        for (essence in listOf(RunecraftData.RUNE_ESSENCE, RunecraftData.PURE_ESSENCE)) {
            onUseOnLoc(essence, Locs.ASTRAL_ALTAR) { astral(player) }
        }
        RunecraftSunfire.register(this)
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
        Combinations.register(this, t) { p, at, other -> Combinations.craft(p, at, other, NOTHING) }
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

    private fun enter(p: PlayerRef, t: Temple) = TempleEntry.enter(p, t)

    /** RC2: the portal returns the player to the tile beside their ruins. */
    private fun leave(p: PlayerRef, t: Temple) {
        if (GotrAltars.returnFromAltar(p)) return
        p.teleport(outsideRuins(p, t.ruin))
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

    private fun distanceSquared(a: CoordGrid, b: CoordGrid): Int = TempleEntry.distanceSquared(a, b)

    // ---- crafting

    /**
     * RC3-RC5: every rune and pure essence in the backpack becomes runes in one action (rune essence only where the altar
     * allows it), [Temple.runesPerEssence] per essence at the level held before the action, and xp per essence.
     */
    private fun craft(p: PlayerRef, t: Temple) {
        // Guardians of the Rift visitors imbue guardian essence and uncharged cells here (runecraft.md RC22).
        if (GotrAltars.imbue(p, t)) return
        bind(p, t.name, t.rune, t.level, t.xp, t.pureOnly) { t.runesPerEssence(it) }
    }

    private fun bind(p: PlayerRef, name: String, rune: Int, level: Int, xp: Double, pureOnly: Boolean, perEssence: (Int) -> Int) {
        val inv = p.inv(Invs.INV)
        val rune0 = if (pureOnly) 0 else inv.total(RunecraftData.RUNE_ESSENCE).toInt()
        val pure = inv.total(RunecraftData.PURE_ESSENCE).toInt()
        val essence = rune0 + pure
        if (essence == 0) {
            val wrongKind = pureOnly && inv.total(RunecraftData.RUNE_ESSENCE) > 0
            return p.message(if (wrongKind) "You need pure essence to bind $name runes." else "You have no essence to bind.")
        }
        val current = p.level(Stats.RUNECRAFT)
        if (current < level) return p.message("You need a Runecraft level of $level to craft $name runes.")
        val runes = essence * perEssence(current)
        val done =
            p.transaction("runecraft") {
                (rune0 == 0 || remove(Invs.INV, RunecraftData.RUNE_ESSENCE, rune0)) &&
                    (pure == 0 || remove(Invs.INV, RunecraftData.PURE_ESSENCE, pure)) &&
                    add(Invs.INV, rune, runes)
            }
        if (!done) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into $name runes.")
        p.addXp(Stats.RUNECRAFT, xp * essence)
    }

    /** RC21: the Astral altar on Lunar Isle needs Lunar Diplomacy (Wiki "Astral Altar"); astral runes are made from pure essence. */
    private fun astral(p: PlayerRef) {
        if (!RunecraftQuests.LUNAR_DIPLOMACY.done(p)) return p.message("You need to have completed Lunar Diplomacy to craft astral runes.")
        bind(p, "astral", Objs.ASTRALRUNE, ASTRAL_LEVEL, ASTRAL_XP, true) { level -> 1 + ASTRAL_MULTIPLES.count { level >= it } }
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

        /** Wiki "Astral Altar" / "Runecraft": astral runes at level 40, 8.7 xp per pure essence, a second rune per essence at 82. */
        const val ASTRAL_LEVEL = 40
        const val ASTRAL_XP = 8.7

        @Suppress("MagicNumber")
        val ASTRAL_MULTIPLES = listOf(82)

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
