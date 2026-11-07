package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Thievable chests with a success roll (docs/mechanics/thieving.md TH11): "Picklock" on a chest of `thieving-chests.tsv`
 * (the Aldarin Villas chests). The unlock is rolled two ticks after the click like a stall's theft; a lockpick in the backpack uses
 * the chest's second chart; a success gives the loot and xp and shows the open chest for the Wiki's time, a failure may teleport
 * the player out. The trapped chests of Ardougne, Rellekka and the Wilderness are not modelled (OPEN-TH15).
 */
public class ThievingChests : ContentPlugin {
    override fun ContentScope.register() {
        for (chest in ThievingData.CHESTS.values) {
            onOpLoc(chest.closedLoc, OP_PICKLOCK) { loc -> picklock(loc, chest) }
        }
    }

    private suspend fun PlayerScript.picklock(loc: LocRef, chest: Chest) {
        val p = player
        if (p.level(Stats.THIEVING) < chest.level) {
            p.message("You need a Thieving level of ${chest.level} to open this chest.")
            return
        }
        p.stopInteraction() // one attempt per click (the delay would otherwise leave the interaction running)
        p.anim(Seqs.HUMAN_PICKLOCK_CHEST)
        p.message("You attempt to pick the lock on the chest.")
        delay(PICK_TICKS)
        val rate = if (p.inv(Invs.INV).total(LOCKPICK) > 0) chest.lockpickRate else chest.rate
        if (p.statRandom(Stats.THIEVING, rate.low, rate.high)) {
            open(p, loc, chest)
        } else {
            p.message("You fail to pick the lock.")
            if (p.statRandom(Stats.THIEVING, chest.teleportRate.low, chest.teleportRate.high)) {
                p.teleport(TELEPORT_WELL)
            }
        }
    }

    private fun open(p: PlayerRef, loc: LocRef, chest: Chest) {
        val row = roll(p, chest)
        val amount = row.min + p.random(row.max - row.min + 1)
        if (!p.transaction("chest") { add(Invs.INV, row.obj, amount) }) return p.message("You don't have enough inventory space.")
        p.message("You find some treasure in the chest.")
        p.addXp(Stats.THIEVING, chest.xp)
        p.addLoc(chest.openLoc, loc.coord, loc.shape, loc.angle, chest.ticks)
    }

    /** One roll of the chest's table (the Wiki rarities add up to 1; the last row absorbs rounding). */
    private fun roll(p: PlayerRef, chest: Chest): LootRow {
        val roll = p.random(ROLL_RESOLUTION).toDouble() / ROLL_RESOLUTION
        var sum = 0.0
        for (row in chest.loot) {
            sum += row.chance
            if (roll < sum) return row
        }
        return chest.loot.last()
    }

    private companion object {
        /** Op 1 of the chest locs ("Picklock"; cache loc 54773). */
        const val OP_PICKLOCK = 1

        /** Ticks from the click to the roll (OPEN-TH15: as for stalls, Wiki "Stall/Thievable": two ticks of animation). */
        const val PICK_TICKS = 2

        /** Cache obj "Lockpick": "A lockpick increases the rate of success" (Wiki "Chest (Aldarin Villas)"). */
        const val LOCKPICK = 1523

        /** Resolution of the loot roll (2^24 slices, finer than the smallest Wiki rarity). */
        const val ROLL_RESOLUTION = 1 shl 24

        /** The well a failed player is teleported to (Wiki "Chest (Aldarin Villas)": teleport location 1379,2971; OPEN-TH15). */
        val TELEPORT_WELL = CoordGrid(1379, 2971, 0)
    }
}
