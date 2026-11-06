package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Prayer training (docs/mechanics/prayer-training.md): "Bury" on every bone and "Scatter" on the ashes of `remains.tsv`
 * (xp from the Wiki `{{Prayer info}}`), and bones used on the Wilderness Chaos Temple altar for 3.5 times the xp with a
 * 50% chance the bone is spared. Gilded altars, the Ectofuntus, offering spells, bonecrushers, ensouled heads and the
 * libation bowl are OPEN (prayer-training.md).
 */
public class PrayerTraining : ContentPlugin {
    override fun ContentScope.register() {
        val remains = RemainsTable.load()
        val bones = remains.filter { it.kind == RemainsKind.BONE }.associateBy { it.obj }
        for (r in remains) {
            when (r.kind) {
                RemainsKind.BONE -> {
                    onItemOp(r.obj, OP_BURY) { click -> bury(click, r) }
                    onUseOnLoc(r.obj, Locs.CHAOSALTAR) { loc -> offer(loc, bones) }
                }

                RemainsKind.ASH -> {
                    onItemOp(r.obj, OP_SCATTER) { click -> scatter(click, r) }
                }
            }
        }
    }

    /** PT1: burying removes the bone and gives the xp at once, then the player is locked for [BURY_DELAY_TICKS] (OPEN-PT1). */
    private fun PlayerRef.bury(click: ButtonClick, r: Remains) {
        if (!take(click, r)) return
        message("You dig a hole in the ground...")
        finish(r, "You bury the bones.")
    }

    /** PT2: scattering ashes works like burying (Wiki "Prayer training": scattered ashes give the `{{Prayer info}}` xp). */
    private fun PlayerRef.scatter(click: ButtonClick, r: Remains) {
        if (!take(click, r)) return
        message("You scatter the ashes in the wind.")
        finish(r, null)
    }

    /** Checks the click, the level and removes the one obj; false if nothing happened. */
    private fun PlayerRef.take(click: ButtonClick, r: Remains): Boolean {
        val inv = inv(Invs.INV)
        if (isDelayed || click.slot !in 0 until inv.size || inv.obj(click.slot) != r.obj) return false
        interrupt()
        if (level(Stats.PRAYER) < r.level) {
            message("You need a Prayer level of ${r.level} to use ${objName(r.obj).lowercase()}.")
            return false
        }
        return transaction("prayer remains") { removeAt(Invs.INV, click.slot, r.obj, 1) }
    }

    private fun PlayerRef.finish(r: Remains, after: String?) {
        anim(Seqs.HUMAN_PICKUPFLOOR)
        addXp(Stats.PRAYER, r.xp)
        startScript {
            delay(BURY_DELAY_TICKS)
            after?.let { player.message(it) }
        }
    }

    /**
     * PT3: a bone used on the Wilderness Chaos Temple altar. The bone offered is the first one from the top left of the
     * backpack, not the one clicked (Wiki "Chaos Temple (church)", 22 February 2023; OPEN-PT3). It gives [ALTAR_XP_MULTIPLIER]
     * times the xp and is kept with a 1 in [SPARE_ODDS] chance. Other altars do nothing with bones.
     */
    private suspend fun PlayerScript.offer(loc: LocRef, bones: Map<Int, Remains>) {
        val p = player
        if (loc.coord != CHAOS_TEMPLE_ALTAR) return p.message("Nothing interesting happens.")
        val inv = p.inv(Invs.INV)
        var offered: Remains? = null
        for (slot in 0 until inv.size) {
            offered = bones[inv.obj(slot)]
            if (offered != null) break
        }
        val bone = offered ?: return
        if (p.level(Stats.PRAYER) < bone.level) {
            return p.message("You need a Prayer level of ${bone.level} to use ${p.objName(bone.obj).lowercase()}.")
        }
        p.stopInteraction() // one offering per click: the delay below would otherwise repeat the op
        val spared = p.random(SPARE_ODDS) == 0
        if (!spared && !p.transaction("chaos altar") { remove(Invs.INV, bone.obj, 1) }) return
        p.anim(Seqs.HUMAN_BONE_SACRIFICE)
        p.addXp(Stats.PRAYER, bone.xp * ALTAR_XP_MULTIPLIER)
        if (spared) p.message("The Dark Lord spares your sacrifice but still rewards you for your efforts.")
        delay(OFFER_DELAY_TICKS)
    }

    private companion object {
        /** Backpack item op texts in the cache (`iops`): "Bury" on bones, "Scatter" on ashes. */
        const val OP_BURY = "Bury"
        const val OP_SCATTER = "Scatter"

        /** Ticks the player is held after burying or scattering (OPEN-PT1: the lead's 1-tick lock; no Wiki page gives a figure). */
        const val BURY_DELAY_TICKS = 1

        /** Ticks between two offerings at the altar (OPEN-PT4: Wiki "Prayer training" implies about 2,000 bones an hour by hand). */
        const val OFFER_DELAY_TICKS = 2

        /** Wiki "Chaos Temple (church)": "3.5x Prayer experience per bone, the same bonus as a gilded altar with two burners lit". */
        const val ALTAR_XP_MULTIPLIER = 3.5

        /** Wiki "Chaos Temple (church)": "every bone offered on the altar has a 50% chance to not be consumed". */
        const val SPARE_ODDS = 2

        /** Chaos altar (cache loc 411) of the Wilderness Chaos Temple (Wiki "Chaos altar (Prayer)": 3239,3608). */
        val CHAOS_TEMPLE_ALTAR = CoordGrid(3239, 3608, 0)
    }
}
