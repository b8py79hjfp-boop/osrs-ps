package tp.content.raids.tob

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.content.raids.RunState
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs

/**
 * ToB deaths and their aftermath (tob.md TB14-TB16; xarpus.md XA6):
 * - Normal / Hard wipe: the raid ends and unprotected items wait in the reclaim chest (`TOB_SURFACE_GRAVESTONE_CHEST`
 *   32656, "Claim") for 100,000 coins (raids-core `WipePolicy.FailRaid` with inv `tob_reclaim`).
 * - Entry wipe: the room starts again (OPEN-TB5, `tob.entry.wipeRestartsRoom`) and everyone gets 3 Bandages
 *   (OPEN-TB6: the "used supplies" measure is not known); an Entry death also gives 3 Bandages (Entry page).
 * - XA6: after Xarpus, the Dawnbringer is taken from the skeleton by the door to Verzik's room (once a run).
 */
public class TobDeaths : ContentPlugin {
    override fun ContentScope.register() {
        onOpLoc(Locs.TOB_SURFACE_GRAVESTONE_CHEST, 1) { reclaim(player) }
        onOpLoc(Locs.TOB_DUNGEON_XARPUS_DEAD_MERC_MULTI, 1) { dawnbringer(player) }
        events.on<RaidEvent.RoomFailed> { e ->
            if (e.run.raid.id == TobRaid.ID && e.run.state is RunState.InRoom) TobFlow.state(e.run).retryRoom = e.room.id
        }
        events.on<RaidEvent.Died> { e ->
            if (e.run.raid.id == TobRaid.ID && e.run.scaling.mode == TobRaid.ENTRY) TobFlow.state(e.run).bandagesOwed += e.member
        }
        onWorldTick { tick(this) }
    }

    private fun tick(world: WorldRef) {
        for (run in Raids.of(world).activeRuns) {
            if (run.raid.id != TobRaid.ID) continue
            val present =
                run.members.values
                    .filter { it.inside && it.online }
                    .mapNotNull { world.onlinePlayer(it.name) }
            payBandages(world, TobFlow.state(run))
            retry(run, present)
        }
    }

    private fun payBandages(world: WorldRef, state: TobState) {
        for (name in state.bandagesOwed.toList()) {
            val p = world.onlinePlayer(name) ?: continue
            if (p.hitpoints <= 0) continue
            state.bandagesOwed -= name
            giveBandages(p)
        }
    }

    /** OPEN-TB5: the wiped Entry room's NPCs go and it starts again from its entry with every member. */
    private fun retry(run: RaidRun, present: List<PlayerRef>) {
        val state = TobFlow.state(run)
        val room = state.retryRoom ?: return
        val any = present.firstOrNull() ?: return
        if (present.any { it.hitpoints <= 0 }) return
        state.retryRoom = null
        val inst = run.instance ?: return
        val entry = run.raid.rooms[room]?.entry ?: return
        for (n in any.npcsWithin(run.at(entry), ROOM_SCAN)) if (n.coord in inst) n.despawn()
        for (p in present) giveBandages(p)
        Raids.enterRoom(any, run, room)
    }

    /** TB16 / OPEN-TB6: 3 Bandages (raid-only) into the inventory. */
    private fun giveBandages(p: PlayerRef) {
        p.transaction("tob bandages") { add(Invs.INV, Objs.TOB_BANDAGES, BANDAGES) }
    }

    /** TB15: "Claim" pays the fee and returns everything stored, if it all fits in the inventory. */
    private fun reclaim(player: PlayerRef) {
        val store = player.inv(Invs.TOB_RECLAIM, stackAll = true)
        val items = (0 until store.size).filter { store.obj(it) >= 0 }
        if (items.isEmpty()) {
            player.message(EMPTY)
            return
        }
        val fee = TobConfig.reclaimFee
        val ok =
            player.transaction("tob reclaim") {
                (fee <= 0 || remove(Invs.INV, Objs.COINS, fee)) &&
                    items.all { slot -> move(Invs.TOB_RECLAIM, slot, store.obj(slot), store.count(slot), Invs.INV) }
            }
        player.message(if (ok) RECLAIMED else NO_ROOM_OR_COINS)
    }

    /** XA6: the Dawnbringer from the skeleton after Xarpus, once a run. */
    private fun dawnbringer(player: PlayerRef) {
        val run = TobFlow.runOf(player) ?: return
        val state = TobFlow.state(run)
        if (state.dawnbringerTaken || !xarpusDone(run)) return
        val ok = player.transaction("dawnbringer") { add(Invs.INV, Objs.VERZIK_SPECIAL_WEAPON, 1) }
        if (!ok) return
        state.dawnbringerTaken = true
        player.message("You find the Dawnbringer; you feel a pulse of energy burst through it.")
    }

    private fun xarpusDone(run: RaidRun): Boolean = "xarpus" in TobFlow.state(run).roomTicks

    private companion object {
        const val BANDAGES = 3
        const val ROOM_SCAN = 40

        /** OPEN-TB4: the chest's lines are not captured. */
        const val EMPTY = "There is nothing for you to reclaim."
        const val RECLAIMED = "You reclaim your items."
        const val NO_ROOM_OR_COINS = "You need 100,000 coins and enough inventory space to reclaim your items."
    }
}
