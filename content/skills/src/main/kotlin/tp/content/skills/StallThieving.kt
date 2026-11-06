package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Seqs

/**
 * Stalls (docs/mechanics/thieving.md TH6-TH7). Stealing never fails: the animation plays on the arrival tick, the loot
 * and xp come two ticks later, and the stall then stays empty for its Wiki respawn ([Stall.respawn]). Guards and the stall
 * owner catching the player is not modelled (OPEN-TH6).
 */
public class StallThieving : ContentPlugin {
    /** When a stall opens again; [stolenAt] lets players who stole on the same tick all succeed (Wiki "Pickpocketing"). */
    private class StallState(var readyAt: Long, var stolenAt: Long)

    override fun ContentScope.register() {
        for (id in ThievingData.STALLS.keys) onOpLoc(id, OP_STEAL) { loc -> steal(loc) }
    }

    private suspend fun PlayerScript.steal(loc: LocRef) {
        val p = player
        val stall = ThievingData.STALLS[loc.id] ?: return
        if (p.level(Stats.THIEVING) < stall.level) {
            p.message("You need a Thieving level of ${stall.level} to steal from the ${stall.name.lowercase()}.")
            return
        }
        val state = states(p).getOrPut(loc.coord.packed) { StallState(0, -1) }
        if (state.readyAt > p.tick && state.stolenAt != p.tick) {
            p.message("The stall has been emptied. It needs a moment to restock.")
            return
        }
        p.stopInteraction() // one theft per click (the delay would otherwise leave the interaction running)
        p.anim(Seqs.HUMAN_PICKPOCKET)
        p.message("You attempt to steal from the ${stall.name.lowercase()}.")
        delay(STEAL_TICKS)
        take(p, loc, stall, state)
    }

    private fun take(p: PlayerRef, loc: LocRef, stall: Stall, state: StallState) {
        val now = p.tick
        if (state.readyAt > now && state.stolenAt != now) return p.message("The stall has been emptied. It needs a moment to restock.")
        val row = roll(p, stall)
        val amount = row.min + p.random(row.max - row.min + 1)
        if (!p.transaction("stall") { add(Invs.INV, row.obj, amount) }) return p.message("You don't have enough inventory space.")
        p.message("You steal ${p.objName(row.obj).lowercase()} from the ${stall.name.lowercase()}.")
        p.addXp(Stats.THIEVING, stall.xp)
        if (state.stolenAt == now) return
        val respawn = RESPAWN_OVERRIDES[loc.id] ?: stall.respawn
        state.stolenAt = now
        state.readyAt = now + respawn
        if (loc.id !in NO_EMPTY_VARIANT) p.addLoc(EMPTY_STALL, loc.coord, loc.shape, loc.angle, respawn)
    }

    /** One roll of the stall's mutually exclusive table (the Wiki rarities add up to 1; the last row absorbs rounding). */
    private fun roll(p: PlayerRef, stall: Stall): LootRow {
        val roll = p.random(ROLL_RESOLUTION).toDouble() / ROLL_RESOLUTION
        var sum = 0.0
        for (row in stall.loot) {
            sum += row.chance
            if (roll < sum) return row
        }
        return stall.loot.last()
    }

    private fun states(p: PlayerRef): HashMap<Int, StallState> =
        p.worldAttr(STATES) ?: HashMap<Int, StallState>().also { p.setWorldAttr(STATES, it) }

    private companion object {
        val STATES = AttrKey<HashMap<Int, StallState>>("thieving.stalls")

        /** Op 2 of every thievable stall ("Steal-from"; cache loc ops). */
        const val OP_STEAL = 2

        /** Ticks from the arrival tick to the loot (Wiki "Stall/Thievable": one tick to start, two ticks of animation). */
        const val STEAL_TICKS = 2

        /** Resolution of the loot roll (2^24 slices, finer than the smallest Wiki rarity). */
        const val ROLL_RESOLUTION = 1 shl 24

        /** The "Market stall" loc (cache 634, 2x2) shown while a 2x2 stall is empty (OPEN-TH7: the real empty locs are not sourced). */
        const val EMPTY_STALL = 634

        /** Stalls that are not 2x2, so the empty 2x2 loc does not fit: they only keep their cooldown (OPEN-TH7). */
        val NO_EMPTY_VARIANT = setOf(4874, 6945, 27537, 28823)

        /** Respawn ticks that differ from the stall page's: the Keldagrim bakery, 9.6 s (Wiki "Stall/Thievable"). */
        val RESPAWN_OVERRIDES = mapOf(6163 to 16)
    }
}
