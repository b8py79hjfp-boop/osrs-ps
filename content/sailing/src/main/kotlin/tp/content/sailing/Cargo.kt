package tp.content.sailing

import tp.plugin.AppearanceChanged
import tp.plugin.PlayerRef
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * Courier crates in a player's hands and the cargo hold inventories (docs/mechanics/sailing-port-tasks.md PT5, PT6). A
 * crate is a two-handed "weapon" (cache: wear position 3 and 5, category [CRATE_CATEGORY]), so carrying one is having it in
 * the worn weapon slot; the cache varbit `SAILING_CARRYING_CARGO` mirrors that for the multi locs of ledger tables and
 * holds, which the client resolves through it.
 */
internal object Cargo {
    /** Worn-slot index of the weapon, where a crate is carried (`ObjType.wearPos1` of the crates is 3). */
    const val WEAPON_SLOT: Int = 3

    /** Worn-slot index of the shield, which a two-handed crate also occupies (`wearPos2` of the crates is 5). */
    const val SHIELD_SLOT: Int = 5

    /** Obj category of the cargo crates (cache objs 32435-33001) and the db 196 whitelist entry for them. */
    const val CRATE_CATEGORY: Int = 2294

    /** The hold of boat slot [boatSlot] (0..4) is inventory 963 + slot (`SAILING_BOAT_1_CARGOHOLD` .. `_5_`). */
    fun holdInv(boatSlot: Int): Int = Invs.SAILING_BOAT_1_CARGOHOLD + boatSlot

    fun isCrate(p: PlayerRef, obj: Int): Boolean = obj >= 0 && p.objCategory(obj) == CRATE_CATEGORY

    /** The crate in the player's hands, or -1. */
    fun held(p: PlayerRef): Int = p.inv(Invs.WORN).obj(WEAPON_SLOT).takeIf { isCrate(p, it) } ?: -1

    /** Both hands free: nothing in the weapon and shield slots (Wiki Courier tasks). */
    fun handsFree(p: PlayerRef): Boolean {
        val worn = p.inv(Invs.WORN)
        return worn.obj(WEAPON_SLOT) < 0 && worn.obj(SHIELD_SLOT) < 0
    }

    /** Brings the carrying varbit and the player's look in line with the worn crate after a change of the weapon slot. */
    fun refresh(p: PlayerRef) {
        p.setVarbit(Varbits.SAILING_CARRYING_CARGO, if (held(p) >= 0) 1 else 0)
        p.events.publish(AppearanceChanged(p))
    }
}
