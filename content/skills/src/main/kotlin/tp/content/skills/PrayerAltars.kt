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

/** A filled libation bowl: [shards] more blessed bone shards can be sacrificed, each worth [xp] Prayer xp. */
private class Bowl(var shards: Int, var xp: Int)

/**
 * Prayer-restoring altars and the libation bowl (docs/mechanics/prayer-training.md PT7-PT8). "Pray-at" on any loc named
 * "Altar" and "Bask" at a Shrine of Ralos restore Prayer points to the base level; the libation bowl at the Teomat is filled with
 * a jug of blessed (sunfire) wine, which lets blessed bone shards be sacrificed for Prayer xp at a cost of 2 Prayer points per
 * 100 shards.
 */
public class PrayerAltars : ContentPlugin {
    override fun ContentScope.register() {
        onOpLocNamed("Altar", "Pray-at") { restore(player, "You pray at the altar...") }
        onOpLoc(SHRINE_OF_RALOS, OP_BASK) { restore(player, "You bask in the sun god's light...") }
        for ((empty, filled) in BOWLS) {
            onOpLoc(empty, OP_FILL) { loc -> fill(loc, filled) }
            onOpLoc(filled, OP_SACRIFICE) { loc -> sacrifice(loc) }
            onOpLoc(filled, OP_CHECK) { loc -> check(loc) }
        }
    }

    /** PT7: restores Prayer to the base level (Wiki "Altar": "your prayer points will be recharged"). */
    private fun PlayerScript.restore(p: PlayerRef, text: String) {
        p.stopInteraction()
        if (p.level(Stats.PRAYER) >= p.baseLevel(Stats.PRAYER)) return p.message("You already have full Prayer points.")
        p.anim(Seqs.HUMAN_PRAY)
        p.message(text)
        p.setLevel(Stats.PRAYER, p.baseLevel(Stats.PRAYER))
        p.message("Your Prayer points have been restored.")
    }

    private fun bowl(p: PlayerRef, loc: LocRef): Bowl? = p.worldAttr(BOWLS_KEY)?.get(loc.coord.packed)

    /** PT8: a jug of blessed sunfire wine (6 xp a shard) or blessed wine (5) fills the bowl with 400 shards' worth; needs level 30. */
    private fun PlayerScript.fill(loc: LocRef, filled: Int) {
        val p = player
        p.stopInteraction()
        if (p.level(Stats.PRAYER) <
            SACRIFICE_LEVEL
        ) {
            return p.message("You need a Prayer level of $SACRIFICE_LEVEL to use the libation bowl.")
        }
        val inv = p.inv(Invs.INV)
        val jug =
            when {
                inv.total(BLESSED_SUNFIRE_WINE) > 0 -> BLESSED_SUNFIRE_WINE to SUNFIRE_XP
                inv.total(BLESSED_WINE) > 0 -> BLESSED_WINE to WINE_XP
                else -> return p.message("You need a jug of blessed wine to fill the libation bowl.")
            }
        if (!p.transaction("libation bowl") { remove(Invs.INV, jug.first, 1) }) return
        val bowls = p.worldAttr(BOWLS_KEY) ?: HashMap<Int, Bowl>().also { p.setWorldAttr(BOWLS_KEY, it) }
        val state = bowls.getOrPut(loc.coord.packed) { Bowl(0, jug.second) }
        state.shards += SHARDS_PER_JUG
        state.xp = jug.second
        p.addLoc(filled, loc.coord, loc.shape, loc.angle)
    }

    /** PT8: sacrifices up to [SHARDS_PER_ACTION] shards a tick while shards, charges and 2 Prayer points last. */
    private suspend fun PlayerScript.sacrifice(loc: LocRef) {
        val p = player
        p.stopInteraction()
        if (p.level(Stats.PRAYER) <
            SACRIFICE_LEVEL
        ) {
            return p.message("You need a Prayer level of $SACRIFICE_LEVEL to use the libation bowl.")
        }
        val state = bowl(p, loc) ?: return
        while (state.shards > 0 && p.level(Stats.PRAYER) >= PRAYER_PER_ACTION) {
            val have = p.inv(Invs.INV).total(BLESSED_BONE_SHARDS).toInt()
            val count = minOf(SHARDS_PER_ACTION, have, state.shards)
            if (count <= 0 || !p.transaction("libation bowl") { remove(Invs.INV, BLESSED_BONE_SHARDS, count) }) break
            state.shards -= count
            p.setLevel(Stats.PRAYER, p.level(Stats.PRAYER) - PRAYER_PER_ACTION)
            p.addXp(Stats.PRAYER, count.toDouble() * state.xp)
            delay(1)
        }
        if (state.shards <= 0) {
            p.worldAttr(BOWLS_KEY)?.remove(loc.coord.packed)
            p.revertLoc(loc.coord, loc.shape)
            p.message("The libation bowl is empty.")
        }
    }

    private fun PlayerScript.check(loc: LocRef) {
        val p = player
        p.stopInteraction()
        p.message("The libation bowl has enough wine for ${bowl(p, loc)?.shards ?: 0} more blessed bone shards.")
    }

    private companion object {
        val BOWLS_KEY = AttrKey<HashMap<Int, Bowl>>("prayer.libation_bowls")

        /** Cache loc "Shrine of Ralos" (Sun version, op 1 "Bask"; Wiki "Shrine of Ralos" id1 52405). */
        const val SHRINE_OF_RALOS = 52405
        const val OP_BASK = 1

        /** Cache locs "Libation bowl": empty 52797 / 52798 (op 1 "Fill") and filled 53016 / 53017 (op 1 "Sacrifice", op 2 "Check"). */
        val BOWLS = listOf(52797 to 53016, 52798 to 53017)
        const val OP_FILL = 1
        const val OP_SACRIFICE = 1
        const val OP_CHECK = 2

        /** Cache objs "Jug of blessed wine" 29386, "Jug of blessed sunfire wine" 29384, "Blessed bone shards" 29381. */
        const val BLESSED_WINE = 29386
        const val BLESSED_SUNFIRE_WINE = 29384
        const val BLESSED_BONE_SHARDS = 29381

        /** Wiki "Libation bowl": level 30 Prayer; 5 xp a shard with blessed wine, 6 with sunfire wine; 400 shards a jug. */
        const val SACRIFICE_LEVEL = 30
        const val WINE_XP = 5
        const val SUNFIRE_XP = 6
        const val SHARDS_PER_JUG = 400

        /** Wiki "Libation bowl": up to 100 shards an action, each action draining 2 Prayer points (216 for 10,800 shards). */
        const val SHARDS_PER_ACTION = 100
        const val PRAYER_PER_ACTION = 2
    }
}
