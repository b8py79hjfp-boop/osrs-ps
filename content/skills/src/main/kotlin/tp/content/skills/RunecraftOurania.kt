package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.OpenBankRequest
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.symbols.Varbits
import tp.util.CoordGrid

/** The tiles beside a loc, for content that moves the player to "the other side" of one (cracks, ladders, rifts). */
internal object RcTiles {
    /** The walkable tile within two tiles of [target] that is nearest to it and not within one tile of [avoid] (if given). */
    fun beside(p: PlayerRef, target: CoordGrid, avoid: CoordGrid? = null): CoordGrid {
        var best: CoordGrid? = null
        var bestDist = Int.MAX_VALUE
        for (dx in -BESIDE..BESIDE) {
            for (dz in -BESIDE..BESIDE) {
                val tile = target.translate(dx, dz)
                val d = dx * dx + dz * dz
                if (d == 0 || d >= bestDist || !p.isWalkable(tile)) continue
                if (avoid != null && maxOf(kotlin.math.abs(tile.x - avoid.x), kotlin.math.abs(tile.z - avoid.z)) <= 1) continue
                best = tile
                bestDist = d
            }
        }
        return best ?: target
    }

    private const val BESIDE = 2
}

/**
 * The Ourania (ZMI) altar (docs/mechanics/runecraft.md RC16): pure and daeyalt essence become random runes by Runecraft level,
 * 1.7 times the usual xp, with the Ardougne medium diary bonus; Eniola banks for 20 runes of one type; the ladder and the crack
 * lead to the altar.
 */
public class RunecraftOurania : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR, OP_CRAFT) { craft(player) }
        onUseOnLoc(Objs.BLANKRUNE_HIGH, Locs.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR) { craft(player) }
        onUseOnLoc(Objs.BLANKRUNE_DAEYALT, Locs.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR) { craft(player) }
        onUseOnLoc(Objs.BLANKRUNE, Locs.RC_ZMI_DUNGEON_CRACKED_CENTER_ALTAR) { player.message(RUNE_ESSENCE_REFUSED) }

        onOpLoc(Locs.RC_ZMI_DUNGEON_ENTRANCE, OP_CLIMB) { player.teleport(RcTiles.beside(player, CAVE_LADDER)) }
        onOpLoc(Locs.RC_ZMI_DUNGEON_ENTRANCE_LADDER, OP_CLIMB) { player.teleport(RcTiles.beside(player, SURFACE_LADDER)) }
        onOpLoc(Locs.RC_ZMI_DUNGEON_WALL_CRACK_ENTRANCE, OP_SQUEEZE) { player.teleport(RcTiles.beside(player, CRACK_B, CRACK_A)) }
        onOpLoc(Locs.RC_ZMI_DUNGEON_WALL_CRACK_ENTRANCE_EXIT, OP_SQUEEZE) { player.teleport(RcTiles.beside(player, CRACK_A, CRACK_B)) }

        onOpNpc(Npcs.RC_ZMI_BANKER, OP_BANK) { bank(player) }
        onWorldStart { spawnNpc(Npcs.RC_ZMI_BANKER, ENIOLA_AT, 0) }
    }

    /** RC16: every pure / daeyalt essence in the backpack becomes one random rune each; rune essence is refused. */
    private fun craft(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val pure = inv.total(Objs.BLANKRUNE_HIGH).toInt()
        val daeyalt = inv.total(Objs.BLANKRUNE_DAEYALT).toInt()
        if (pure + daeyalt == 0) {
            return p.message(if (inv.total(Objs.BLANKRUNE) > 0) RUNE_ESSENCE_REFUSED else "You have no essence to bind.")
        }
        val level = p.level(Stats.RUNECRAFT)
        val band = BANDS.last { level >= it.from }
        val made = LinkedHashMap<Int, Int>()
        val diary = p.varbit(Varbits.ARDOUGNE_DIARY_MEDIUM_COMPLETE) == 1
        var xp = 0.0
        repeat(pure + daeyalt) { i ->
            val rune = roll(p, band)
            made.merge(rune.obj, 1 + if (diary && p.random(PERMILLE) < rune.bonusPermille) 1 else 0, Int::plus)
            xp += rune.xp * ALTAR_XP * if (i >= pure) DAEYALT_XP else 1.0
        }
        val done =
            p.transaction("ourania") {
                (pure == 0 || remove(Invs.INV, Objs.BLANKRUNE_HIGH, pure)) &&
                    (daeyalt == 0 || remove(Invs.INV, Objs.BLANKRUNE_DAEYALT, daeyalt)) &&
                    made.all { (obj, n) -> add(Invs.INV, obj, n) }
            }
        if (!done) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_RUNECRAFT)
        p.spotAnim(SpotAnims.RUNECRAFTING)
        p.message("You bind the temple's power into runes.")
        p.addXp(Stats.RUNECRAFT, xp)
    }

    /** A number below 10,000 compared with the band's cumulative thresholds in rune order (Mod Ash, via the Wiki). */
    private fun roll(p: PlayerRef, band: Band): OuraniaRune {
        val r = p.random(TOTAL)
        val i = band.thresholds.indexOfFirst { r < it }
        return RUNES[if (i < 0) RUNES.lastIndex else i]
    }

    /** Eniola: "20 runes of any one type" (not combination runes), then the bank opens (Wiki "Eniola"). */
    private fun bank(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        for (slot in 0 until inv.size) {
            val obj = inv.obj(slot)
            if (obj < 0 || obj in COMBINATION_RUNES || !p.objName(obj).endsWith(" rune")) continue
            if (inv.total(obj) < BANK_COST) continue
            if (!p.transaction("eniola") { remove(Invs.INV, obj, BANK_COST) }) return
            return p.events.publish(OpenBankRequest(p))
        }
        p.message("You need $BANK_COST runes of one type to use the bank.")
    }

    private class OuraniaRune(val obj: Int, val xp: Double, val bonusPermille: Int)

    private class Band(val from: Int, val thresholds: IntArray)

    private companion object {
        const val OP_CRAFT = 1
        const val OP_CLIMB = 1
        const val OP_SQUEEZE = 1
        const val OP_BANK = 1

        /** Wiki "Ourania Altar": a number "up to 10,000" is picked. */
        const val TOTAL = 10_000

        /** Wiki "Ourania Altar": 170% of the normal experience, and 1.5x that for daeyalt essence. */
        const val ALTAR_XP = 1.7
        const val DAEYALT_XP = 1.5

        /** The diary's bonus chances are kept in tenths of a percent (the Wiki gives 22.5% and 17.5%). */
        const val PERMILLE = 1000

        /** Wiki "Eniola": one banking session costs a stack of 20 runes. */
        const val BANK_COST = 20

        /** Wiki "Eniola" infobox map pin, and the ladder / crack / altar placements checked with `cache-dump find-locs`. */
        val ENIOLA_AT = CoordGrid(3013, 5625, 0)
        val SURFACE_LADDER = CoordGrid(2452, 3231, 0)
        val CAVE_LADDER = CoordGrid(3015, 5630, 0)
        val CRACK_A = CoordGrid(3053, 5587, 0)
        val CRACK_B = CoordGrid(3055, 5586, 0)

        const val RUNE_ESSENCE_REFUSED = "Rune essence cannot be used at this altar."

        val COMBINATION_RUNES = setOf(Objs.MISTRUNE, Objs.DUSTRUNE, Objs.MUDRUNE, Objs.SMOKERUNE, Objs.STEAMRUNE, Objs.LAVARUNE)

        /**
         * Wiki "Module:Ourania Altar Table" `rune_order` (thresholds are cumulative in this order), `rune_exp` (base xp) and
         * the Ardougne medium diary's extra-rune chance (Ourania Altar, Ardougne diary), in tenths of a percent.
         */
        @Suppress("MagicNumber")
        val RUNES: List<OuraniaRune> =
            listOf(
                OuraniaRune(Objs.SOULRUNE, 11.0, 100),
                OuraniaRune(Objs.BLOODRUNE, 10.5, 150),
                OuraniaRune(Objs.DEATHRUNE, 10.0, 175),
                OuraniaRune(Objs.LAWRUNE, 9.5, 200),
                OuraniaRune(Objs.NATURERUNE, 9.0, 225),
                OuraniaRune(Objs.ASTRALRUNE, 8.7, 250),
                OuraniaRune(Objs.CHAOSRUNE, 8.5, 250),
                OuraniaRune(Objs.COSMICRUNE, 8.0, 250),
                OuraniaRune(Objs.BODYRUNE, 7.5, 250),
                OuraniaRune(Objs.FIRERUNE, 7.0, 250),
                OuraniaRune(Objs.EARTHRUNE, 6.5, 250),
                OuraniaRune(Objs.WATERRUNE, 6.0, 250),
                OuraniaRune(Objs.MINDRUNE, 5.5, 250),
                OuraniaRune(Objs.AIRRUNE, 5.0, 250),
            )

        /** Wiki "Module:Ourania Altar Table" `thresholds`: one cumulative row per level band ("1-9", "10-19", ..., "99+"). */
        @Suppress("MagicNumber")
        val BANDS: List<Band> =
            listOf(
                Band(1, intArrayOf(2, 7, 15, 30, 60, 105, 165, 250, 400, 700, 1300, 2500, 5000, 10000)),
                Band(10, intArrayOf(3, 9, 21, 45, 85, 145, 225, 400, 1000, 2200, 4600, 6700, 8500, 10000)),
                Band(20, intArrayOf(8, 23, 55, 110, 220, 430, 850, 1650, 3250, 4750, 6150, 7500, 8800, 10000)),
                Band(30, intArrayOf(20, 60, 120, 250, 500, 1000, 2000, 4000, 5300, 6500, 7600, 8500, 9300, 10000)),
                Band(40, intArrayOf(40, 120, 240, 500, 1000, 2000, 4000, 5500, 6500, 7300, 8050, 8750, 9400, 10000)),
                Band(50, intArrayOf(80, 250, 600, 1300, 2650, 4150, 5250, 6250, 7000, 7700, 8350, 8950, 9500, 10000)),
                Band(60, intArrayOf(100, 300, 700, 1500, 3050, 4450, 5500, 6450, 7200, 7900, 8500, 9050, 9550, 10000)),
                Band(70, intArrayOf(200, 700, 1700, 3500, 5000, 6200, 7100, 7800, 8300, 8700, 9100, 9400, 9700, 10000)),
                Band(80, intArrayOf(400, 1000, 2450, 3900, 5250, 6300, 7100, 7800, 8400, 8900, 9300, 9600, 9800, 10000)),
                Band(90, intArrayOf(650, 1650, 3300, 4750, 6100, 7100, 7800, 8400, 8900, 9300, 9600, 9800, 9900, 10000)),
                Band(99, intArrayOf(900, 2200, 3750, 5200, 6550, 7500, 8100, 8600, 9000, 9300, 9600, 9800, 9900, 10000)),
            )
    }
}
