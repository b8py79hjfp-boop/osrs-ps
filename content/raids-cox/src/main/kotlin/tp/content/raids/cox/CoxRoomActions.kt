package tp.content.raids.cox

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * Raider actions inside the combat rooms that are not fights: cutting the Muttadiles' meat tree (muttadiles.md MU8),
 * feeding a stung lux grub (vespula.md VE3) and the mystics' portals after the last mystic (mystics.md MY5).
 */
internal object CoxRoomActions {
    fun register(scope: ContentScope) {
        with(scope) {
            onOpNpc(Npcs.RAIDS_DOGODILE_MEAT_TREE, OP1) { tree -> cut(player, tree) }
            for (grub in STUNG_GRUBS) onOpNpc(grub, OP1) { npc -> feed(player, npc) }
            onOpLoc(Locs.RAIDS_MYSTICS_PORTAL_START, OP1) { travel(player, END) }
            onOpLoc(Locs.RAIDS_MYSTICS_PORTAL_MIDDLE, OP1) { travel(player, END) }
            onOpLoc(Locs.RAIDS_MYSTICS_PORTAL_MIDDLE, OP2) { travel(player, START) }
            onOpLoc(Locs.RAIDS_MYSTICS_PORTAL_END, OP1) { travel(player, START) }
        }
    }

    /** MU8: the tree's HP is 5 x the party's total Woodcutting level, at least 100 (Mod Ash). */
    fun treeSpawned(run: RaidRun, tree: NpcRef) {
        val total = run.scaling.skillSums.getOrElse(Stats.WOODCUTTING) { 0 }
        val hp = maxOf(TREE_MIN_HP, TREE_HP_PER_LEVEL * total)
        tree.overrideStats(intArrayOf(1, 1, 1, hp, 1, 1))
        tree.hitpoints = hp
    }

    /**
     * MU8: with an axe (any tier, Mod Ash), one swing every 5 ticks (RuneLite SP: 5, 6 after a hit): the chance runs from
     * 20% at level 1 to 78% at 99 (OPEN-MU11: linear), a hit takes half the Woodcutting level -7 / +3; cutting it down
     * ends the healing. OPEN-CX14: the points for it.
     */
    private suspend fun tp.plugin.PlayerScript.cut(player: PlayerRef, tree: NpcRef) {
        if (!hasAxe(player)) {
            player.message(NEED_AXE)
            return
        }
        while (tree.isInWorld && tree.hitpoints > 0) {
            player.anim(CHOP_ANIM)
            val level = player.level(Stats.WOODCUTTING)
            val chance = CHANCE_MIN + (CHANCE_MAX - CHANCE_MIN) * (level - 1) / (Stats.MAX_LEVEL - 1)
            if (player.random(PERCENT) < chance) {
                player.message(HACK)
                val dmg = maxOf(1, level / 2 - DAMAGE_BELOW + player.random(DAMAGE_BELOW + DAMAGE_ABOVE + 1))
                tree.hitpoints = maxOf(0, tree.hitpoints - dmg)
                if (tree.hitpoints == 0) {
                    tree.despawn()
                    return
                }
                delay(SWING + 1)
            } else {
                delay(SWING)
            }
        }
    }

    private fun hasAxe(p: PlayerRef): Boolean {
        fun isAxe(obj: Int): Boolean {
            val n = p.objName(obj).lowercase()
            return obj > 0 && n.endsWith(" axe") && "pickaxe" !in n
        }
        if (isAxe(p.inv(Invs.WORN).obj(WEAPON_SLOT))) return true
        val inv = p.inv(Invs.INV)
        return (0 until inv.size).any { isAxe(inv.obj(it)) }
    }

    /** VE3: a medivaemia blossom on a stung grub fills its meter again (Strat: heals it 30). */
    private fun feed(player: PlayerRef, grub: NpcRef) {
        val inv = player.inv(Invs.INV)
        val slot = (0 until inv.size).firstOrNull { player.objName(inv.obj(it)).equals(BLOSSOM, ignoreCase = true) }
        if (slot == null) {
            player.message(NEED_BLOSSOM)
            return
        }
        val obj = inv.obj(slot)
        if (!player.transaction("cox_feed_grub") { removeAt(Invs.INV, slot, obj) }) return
        grub.hitpoints = minOf(grub.maxHitpoints, grub.hitpoints + GRUB_HEAL)
        grub[GRUB_METER] = METER_FULL
        grub.transform(Npcs.RAIDS_VESPULA_CATERPILLAR_SICKLY, restoreStats = false)
    }

    /** MY5: when the last mystic dies, three portals: at the start, the middle and the end of the room. */
    fun mysticsDone(run: RaidRun, cell: CoxCell, any: EntityRef) {
        val geo = CoxRoomGeo(any, run, cell)
        val start = CoxMap.arrival(any, run, cell, cell.entrySide)
        val end = CoxMap.arrival(any, run, cell, cell.exitSide)
        val st = CoxFlow.state(run) ?: return
        st.mysticPortals[cell.index] = start to end
        for ((id, at) in listOf(
            Locs.RAIDS_MYSTICS_PORTAL_START to start,
            Locs.RAIDS_MYSTICS_PORTAL_MIDDLE to geo.centre(1),
            Locs.RAIDS_MYSTICS_PORTAL_END to end,
        )) {
            any.addLoc(id, step(any, at), CENTREPIECE, 0)
        }
    }

    /** A portal tile beside [at] (not on the arrival tile itself). */
    private fun step(any: EntityRef, at: CoordGrid): CoordGrid {
        for ((dx, dz) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
            val t = CoordGrid(at.x + dx, at.z + dz, at.level)
            if (any.isWalkable(t)) return t
        }
        return at
    }

    private fun travel(player: PlayerRef, to: Int) {
        val run = CoxFlow.runOf(player) ?: return
        val st = CoxFlow.state(run) ?: return
        val cell = CoxMap.cellAt(st.plan, run, player.coord) ?: return
        val (start, end) = st.mysticPortals[cell.index] ?: return
        player.teleport(if (to == START) start else end)
    }

    /** The meter key the Vespula fight reads (content/combat `CoxVespula.GRUB_METER`). */
    private val GRUB_METER = tp.plugin.AttrKey<Int>("cox_grub_meter")
    private const val METER_FULL = 60
    private val STUNG_GRUBS = intArrayOf(Npcs.RAIDS_VESPULA_CATERPILLAR_SICKLY, Npcs.RAIDS_VESPULA_CATERPILLAR_INFECTED)
    private const val BLOSSOM = "Medivaemia blossom"
    private const val GRUB_HEAL = 30
    private const val OP1 = 1
    private const val OP2 = 2
    private const val START = 0
    private const val END = 1
    private const val CENTREPIECE = 10
    private const val WEAPON_SLOT = 3
    private const val TREE_MIN_HP = 100
    private const val TREE_HP_PER_LEVEL = 5
    private const val CHANCE_MIN = 20
    private const val CHANCE_MAX = 78
    private const val PERCENT = 100
    private const val DAMAGE_BELOW = 7
    private const val DAMAGE_ABOVE = 3
    private const val SWING = 5

    /** The generic axe swing (cache seq `HUMAN_WOODCUTTING_BRONZE_AXE`). */
    private val CHOP_ANIM = tp.symbols.Seqs.HUMAN_WOODCUTTING_BRONZE_AXE

    /** RuneLite SP: the success line; OPEN-MU11: the others are not captured. */
    private const val HACK = "You hack away some of the meat."
    private const val NEED_AXE = "You need an axe to cut the meat tree."
    private const val NEED_BLOSSOM = "You need a medivaemia blossom to feed the grub."
}
