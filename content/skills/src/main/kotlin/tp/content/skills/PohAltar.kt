@file:Suppress("MagicNumber") // data tables: Wiki "Chapel" (altar multipliers) and the burner / altar loc ids of cache 2757

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * A chapel altar: its [locs] (Saradomin, Guthix, Zamorak and gnome child versions; the first is the one that is built) and the
 * percent of a bone's xp it gives with 0, 1 and 2 burners lit (Wiki "Chapel", Altar table; "Gilded altar", experience per bone).
 */
internal class ChapelAltar(val name: String, val locs: List<Int>, val percent: List<Int>)

/** An incense burner: the [unlit] loc and its [lit] twin (Wiki infobox `id1` / `id2`; cache ops "Light" and "Re-light"). */
internal class Burner(val unlit: Int, val lit: Int)

/**
 * The chapel's altar and incense burners (docs/mechanics/construction.md PO1-PO3): "Pray" restores prayer points, a bone used on
 * the altar is sacrificed for the altar's multiple of its xp (the more burners are lit the more), and a burner is lit with a
 * tinderbox and a marrentill. Built by [HouseMenus]; the bones are the table of [PrayerTraining] (`remains.tsv`).
 */
public class PohAltar : ContentPlugin {
    override fun ContentScope.register() {
        val bones = RemainsTable.load().filter { it.kind == RemainsKind.BONE }.associateBy { it.obj }
        val byLoc = HashMap<Int, ChapelAltar>()
        for (altar in ALTARS) for (loc in altar.locs) byLoc[loc] = altar
        for ((loc, altar) in byLoc) {
            onOpLoc(loc, OP_PRAY) { pray(player) }
            for (bone in bones.values) onUseOnLoc(bone.obj, loc) { offer(it, altar, bones) }
        }
        for (burner in BURNERS) {
            onOpLoc(burner.unlit, OP_LIGHT) { light(it, burner) }
            onOpLoc(burner.lit, OP_LIGHT) { light(it, burner) }
        }
    }

    /** The altar restores prayer points (Wiki "Chapel": "Altars can be used to recharge Prayer points"). */
    private fun pray(p: PlayerRef) {
        val base = p.baseLevel(Stats.PRAYER)
        if (p.level(Stats.PRAYER) >= base) return p.message("You already have full prayer points.")
        p.anim(Seqs.HUMAN_PRAY)
        p.setLevel(Stats.PRAYER, base)
        p.message("You pray at the altar and recharge your prayer points.")
    }

    /**
     * PO2: the first bone of the backpack (Wiki "Gilded altar", 15 February 2023: bones are consumed from the top left, not the one
     * used) is sacrificed for xp times the altar's percent for the burners lit in its room.
     */
    private suspend fun PlayerScript.offer(loc: LocRef, altar: ChapelAltar, bones: Map<Int, Remains>) {
        val p = player
        val house = p[House.CURRENT] ?: return p.message("Nothing interesting happens.")
        val group = house.groupAt(loc.coord) ?: return p.message("Nothing interesting happens.")
        val inv = p.inv(Invs.INV)
        var bone: Remains? = null
        for (slot in 0 until inv.size) {
            bone = bones[inv.obj(slot)]
            if (bone != null) break
        }
        val offered = bone ?: return
        if (p.level(Stats.PRAYER) < offered.level) {
            return p.message("You need a Prayer level of ${offered.level} to use ${p.objName(offered.obj).lowercase()}.")
        }
        p.stopInteraction() // one offering per click: the delay below would otherwise repeat the op
        if (!p.transaction("poh altar") { remove(Invs.INV, offered.obj, 1) }) return
        val lit = house.lit.count { it.room === group.room }.coerceAtMost(altar.percent.size - 1)
        p.anim(Seqs.HUMAN_BONE_SACRIFICE)
        p.addXp(Stats.PRAYER, offered.xp * altar.percent[lit] / PERCENT)
        p.message(if (lit == 2) "The gods are very pleased with your offering." else "The gods are pleased with your offering.")
        delay(OFFER_DELAY_TICKS)
    }

    /** PO3: lights (or refuels) one burner: a tinderbox and one marrentill (Wiki "Chapel", Lamps). */
    private suspend fun PlayerScript.light(loc: LocRef, burner: Burner) {
        val p = player
        val house = p[House.CURRENT] ?: return
        val group = house.groupAt(loc.coord) ?: return
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.TINDERBOX) == 0L) return p.message("You need a tinderbox to light the burner.")
        if (inv.total(Objs.MARENTILL) == 0L) return p.message("You need a clean marrentill to burn.")
        val piece = group.built ?: return
        if (!p.transaction("incense burner") { remove(Invs.INV, Objs.MARENTILL, 1) }) return
        house.lit += group
        for (l in group.locs) p.addLoc(burner.lit, l.coord, l.shape, l.angle)
        p.message("You burn some marrentill in the incense burner.")
        val stamp = ++house.burnStamp
        house.litStamp[group] = stamp
        p.weakQueue(BURN_TICKS) {
            if (!house.instance.destroyed && house.litStamp[group] == stamp && group.built === piece) {
                house.lit -= group
                house.litStamp.remove(group)
                for (l in group.locs) player.addLoc(burner.unlit, l.coord, l.shape, l.angle)
            }
        }
    }

    private companion object {
        /** Cache 2757: altar ops are 1 "Pray" and 5 "Remove"; burners 1 "Light" (unlit) or "Re-light" (lit). */
        const val OP_PRAY = 1
        const val OP_LIGHT = 1
        const val PERCENT = 100.0

        /** Ticks between two offerings (OPEN-PT4 in docs/mechanics/prayer-training.md: no source, the Chaos altar's 2 ticks). */
        const val OFFER_DELAY_TICKS = 2

        /**
         * OPEN-CO17: how long a burner stays lit. The Wiki says it depends on Firemaking level and gives no number; 100 ticks
         * (one minute) is a placeholder behind this name.
         */
        const val BURN_TICKS = 100

        /** Wiki "Chapel": altar table (burners 0 / 1 / 2) and the loc ids of "Oak altar" ... "Gilded altar" (infobox id1-id4). */
        val ALTARS =
            listOf(
                ChapelAltar("Oak altar", listOf(13179, 13181, 13180, 40872), listOf(100, 150, 200)),
                ChapelAltar("Teak altar", listOf(13182, 13184, 13183, 40873), listOf(110, 160, 210)),
                ChapelAltar("Cloth altar", listOf(13185, 13186, 13187, 40874), listOf(125, 175, 225)),
                ChapelAltar("Mahogany altar", listOf(13188, 13190, 13189, 40875), listOf(150, 200, 250)),
                ChapelAltar("Limestone altar", listOf(13191, 13193, 13192, 40876), listOf(175, 225, 275)),
                ChapelAltar("Marble altar", listOf(13194, 13196, 13195, 40877), listOf(200, 250, 300)),
                ChapelAltar("Gilded altar", listOf(13197, 13199, 13198, 40878), listOf(250, 300, 350)),
            )

        /** Wiki infoboxes "Incense burner" (oak), "Mahogany burner" and "Marble burners": `id1` unlit, `id2` lit. */
        val BURNERS = listOf(Burner(13208, 13209), Burner(13210, 13211), Burner(13212, 13213))
    }
}
