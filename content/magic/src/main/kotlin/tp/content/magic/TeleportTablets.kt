package tp.content.magic

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.symbols.Invs

/**
 * Magic tablets, "Break" (docs/mechanics/teleports.md B1-B2): no Magic level, runes or XP (Wiki "Magic tablet"), one
 * tablet consumed per teleport, the same destination, Wilderness limit and Tele Block rules as the spell.
 */
public class TeleportTablets : ContentPlugin {
    override fun ContentScope.register() {
        onItemOp("Break") { click -> breakTablet(click) }
    }

    private fun PlayerRef.breakTablet(click: ButtonClick) {
        val inv = inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val spell = SpellTeleports.BY_TABLET[click.obj] ?: return
        if (isDelayed || Teleports.isBusy(this)) return
        val denial = Teleports.denial(this, Teleports.WILDERNESS_LIMIT)
        if (denial != null) return message(denial)
        if (!transaction("break tablet") { removeAt(Invs.INV, click.slot, click.obj, 1) }) return
        Teleports.start(this, TABLET_STAGES, spell.style.ticks, spell.destination)
    }
}
