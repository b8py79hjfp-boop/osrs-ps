package tp.content.pvp

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.util.CoordGrid

/**
 * The Revenant Caves entry fee and its one-way entrances and exits (docs/mechanics/revenant-caves.md RC1–RC4): 100,000
 * coins from the inventory or the bank, paid once until the player dies in the caves or to another player anywhere
 * in the Wilderness; a player killer gets the coins as loot.
 */
public class RevenantCaves : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.WILD_CAVE_ENTRANCE_LOW, OP_FIRST) { enter(player, LOW_ARRIVAL) }
        onOpLoc(Locs.WILD_CAVE_ENTRANCE_HIGH, OP_FIRST) { enter(player, HIGH_ARRIVAL) }
        // RC2: "The entrance at level 26 Wilderness has been made one-way; players will be warned".
        onOpLoc(Locs.WILD_CAVE_ENTRANCE_MID, OP_FIRST) {
            player.choiceDialog("This crevice is a one-way entrance. Jump down?", listOf("Yes.", "No.")) { pick ->
                if (pick == 1) enter(this, MID_ARRIVAL)
            }
        }
        onOpLoc(Locs.WILD_CAVE_EXIT_LOW, OP_FIRST) { player.teleport(LOW_EXIT) }
        onOpLoc(Locs.WILD_CAVE_EXIT_HIGH, OP_FIRST) { player.teleport(HIGH_EXIT) }
        events.on<DeathEvent> { e -> onDeath(e) }
    }

    private fun paid(p: PlayerRef) = (p.saved(FEE_KEY) ?: 0L) > 0

    /** RC1: "The fee can be taken from the bank and does not need to be brought along in the inventory." */
    private fun enter(player: PlayerRef, to: CoordGrid) {
        if (!paid(player)) {
            val ok =
                player.transaction("rev cave fee") { remove(Invs.INV, COINS, FEE) } ||
                    player.transaction("rev cave fee (bank)") { remove(Invs.BANK, COINS, FEE) }
            if (!ok) {
                player.message("You need %,d coins in your inventory or bank to enter the caves.".format(FEE))
                return
            }
            player.setSaved(FEE_KEY, FEE.toLong())
            player.message("You pay the entry fee of %,d coins.".format(FEE))
        }
        player.teleport(to)
    }

    /**
     * RC3: "Upon dying to anything inside the caves (including monsters) or to another player anywhere in the
     * Wilderness, the fee will be lost ... If the killer was another player, the 100,000 coins will be dropped as loot."
     */
    private fun onDeath(e: DeathEvent) {
        val dead = e.entity as? PlayerRef ?: return
        if (!paid(dead)) return
        val killer = e.killer as? PlayerRef
        val inCaves = inCaves(dead.coord)
        if (!inCaves && (killer == null || Wilderness.level(dead.coord) <= 0)) return
        dead.setSaved(FEE_KEY, null)
        if (killer != null) killer.transaction("rev cave fee loot") { createOnGround(COINS, FEE, dead.coord, killer.username) }
    }

    internal companion object {
        private const val OP_FIRST = 1
        private const val FEE = 100_000
        private const val COINS = 995
        private const val FEE_KEY = "rev_cave_fee"

        /** OPEN-RC1: arrival tiles aren't published; walkable floor in the south, middle and north of the caves. */
        private val LOW_ARRIVAL = CoordGrid(3199, 10072)
        private val MID_ARRIVAL = CoordGrid(3238, 10160)
        private val HIGH_ARRIVAL = CoordGrid(3230, 10199)

        /** RC4: the exits come out "by the level 17 and level 40 locations" (beside those entrances; cache placements). */
        private val LOW_EXIT = CoordGrid(3075, 3652)
        private val HIGH_EXIT = CoordGrid(3126, 3829)

        /** Bounds of the caves' revenant spawns (world spawns table). */
        @Suppress("MagicNumber") // map bounds
        fun inCaves(c: CoordGrid): Boolean = c.level == 0 && c.x in 3136..3265 && c.z in 10048..10239
    }
}
