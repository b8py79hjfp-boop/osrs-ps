package tp.content.magic

import tp.plugin.AttrKey
import tp.plugin.ItemOnItem
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Varbits

/** Shared rules of the non-combat spells (docs/mechanics/magic-utility.md MU1-MU3). */
internal object Casting {
    const val NO_RUNES = "You do not have enough runes to cast this spell."

    /** Tick before which the next spell can't be cast (MU2). */
    private val NEXT_CAST = AttrKey<Long>("magic_next_cast")

    /**
     * Tick at which the one queued cast is due: the game keeps at most one waiting (Wiki "Superheat Item" changes,
     * 21 August 2014). A queue entry that an interruption removed leaves a due tick in the past, which counts as empty.
     */
    private val QUEUED_UNTIL = AttrKey<Long>("magic_cast_queued_until")

    /**
     * The backpack slot an item-targeted spell was cast on, or null. The engine only checks that the spellbook and
     * backpack interfaces are open for a spell on an item, so slot and obj are re-checked here (brief section 12).
     */
    fun targetSlot(player: PlayerRef, event: ItemOnItem): Int? {
        if (event.to != Components.Inventory.ITEMS || event.toObj < 0) return null
        val backpack = player.inv(Invs.INV)
        val valid = event.toSlot in 0 until backpack.size && backpack.obj(event.toSlot) == event.toObj
        return if (valid) event.toSlot else null
    }

    /**
     * Whether [spell] may be cast now (MU1): the book it belongs to is the active one and the Magic level is enough. A
     * cast that comes sooner than the previous spell allows is queued once and [retry] (if any) runs when it is due (MU2).
     */
    fun ready(player: PlayerRef, spell: UtilitySpell, retry: (PlayerRef.() -> Unit)? = null): Boolean {
        if (player.varbit(Varbits.SPELLBOOK) != spell.book) return false
        if (player.level(Stats.MAGIC) < spell.level) {
            player.message("You need a Magic level of ${spell.level} to cast this spell.")
            return false
        }
        val wait = (player[NEXT_CAST] ?: 0L) - player.tick
        if (wait <= 0) return true
        if (retry != null && (player[QUEUED_UNTIL] ?: 0L) <= player.tick) {
            player[QUEUED_UNTIL] = player.tick + wait
            player.weakQueue(wait.toInt()) { player.retry() }
        }
        return false
    }

    /** Applies the cast itself once its effect has gone through (MU2, MU3): clock, animation, graphic and Magic xp. */
    fun finish(player: PlayerRef, spell: UtilitySpell) {
        player[NEXT_CAST] = player.tick + spell.ticks
        player.anim(spell.seq)
        if (spell.spot >= 0) player.spotAnim(spell.spot)
        player.addXp(Stats.MAGIC, spell.xp)
        Spellbooks.onCast(player)
    }
}
