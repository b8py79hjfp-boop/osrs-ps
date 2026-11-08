package tp.content.raids.tob

import tp.content.raids.RaidEvent
import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EntityRef
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.WorldRef
import tp.symbols.Locs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The treasure room after Verzik (lobby-rewards LR14; client-contract 2.7 and 5). One Monumental chest per party slot
 * stands in the north chamber; each chest is a loc multi on the viewer's own varbit 6450 + slot, so every player sees
 * their own chest marked and a unique as the purple one. Only the owner opens a chest: it swaps to its opened loc and
 * shows interface 23 over the claim inventory ([TobChestUi]). The Teleport crystal leaves the raid.
 */
internal object TobVault {
    /**
     * Verzik's throne (client-contract 2.8: multi 32736 at (3166, 4324, L1), 5x4). Its door (OPEN-TB7) is placed on the
     * arena level (0), at the arena's north edge z 4323.
     */
    private val THRONE = CoordGrid(3166, 4323)
    private const val THRONE_WIDTH = 5
    private const val THRONE_LENGTH = 4

    /**
     * OPEN-LR16: the Wiki's five chest anchors (client-contract 5); which anchor is `TOB_TREASUREROOM_CHEST_LOC0..4` is
     * not known, so chest k stands at anchor k in the order the Wiki lists them.
     */
    val CHEST_ANCHORS =
        listOf(CoordGrid(3233, 4330), CoordGrid(3226, 4327), CoordGrid(3226, 4323), CoordGrid(3240, 4327), CoordGrid(3240, 4323))

    /** Client-contract 2.7: the chest multis' values (own state bit 2, rare bit 1: not mine standard / rare, mine standard / rare). */
    private const val CHEST_MINE = 2
    private const val CHEST_RARE = 1

    /**
     * Verzik is dead: the throne opens onto the treasure room (the door loc 32738 "Treasure room" replaces the throne).
     * The rewards are computed when the party steps in ([start]): completing here would end the run before the room is
     * cleared (Raids.roomCleared needs it in the room, and the door needs it between rooms).
     */
    fun prepare(run: RaidRun, any: EntityRef) {
        any.addLoc(
            Locs.TOB_DUNGEON_VERZIK_THRONE_DOOR_OPENED,
            run.at(THRONE, THRONE_WIDTH, THRONE_LENGTH),
            TobSupplies.SHAPE_CENTREPIECE,
            0,
        )
    }

    /** The party is in the treasure room: the raid completes (rewards rolled once, paid into inv 612) and the chests appear. */
    fun start(run: RaidRun, any: EntityRef) {
        Raids.complete(any, run, endedAt = TobFlow.state(run).verzikDeadTick.takeIf { it >= 0 } ?: any.tick)
        for (k in run.party.members.indices
            .take(CHEST_ANCHORS.size)) {
            any.addLoc(Locs.TOB_TREASUREROOM_CHEST_LOC0 + k, run.at(CHEST_ANCHORS[k]), TobSupplies.SHAPE_CENTREPIECE, 0)
        }
        for (p in run.presentPlayers(any)) {
            setChestVarbits(run, p)
            TobChestUi.sync(p)
        }
    }

    /** [p]'s view of every chest: 0 not mine standard, 1 not mine rare, 2 mine standard, 3 mine rare (empty slots 0). */
    fun setChestVarbits(run: RaidRun, p: PlayerRef) {
        for (k in 0 until CHEST_ANCHORS.size) {
            val owner = run.party.members.getOrNull(k)
            val value =
                if (owner == null) {
                    0
                } else {
                    (if (owner.equals(p.username, ignoreCase = true)) CHEST_MINE else 0) + (if (isRare(run, owner)) CHEST_RARE else 0)
                }
            p.setVarbit(Varbits.TOB_TREASUREROOM_CHEST_0 + k, value)
        }
    }

    fun clearChestVarbits(p: PlayerRef) {
        for (k in 0 until CHEST_ANCHORS.size) p.setVarbit(Varbits.TOB_TREASUREROOM_CHEST_0 + k, 0)
    }

    private fun isRare(run: RaidRun, owner: String): Boolean = run.member(owner)?.reward?.unique == true

    /** The chest slot [loc] stands at, or null when it is not one of the run's chests. */
    private fun slotOf(run: RaidRun, loc: LocRef): Int? = CHEST_ANCHORS.indices.firstOrNull { run.at(CHEST_ANCHORS[it]) == loc.coord }

    /** The chest [loc] belongs to [player] (party slot k is chest k; hostile input: a click on someone else's chest is refused). */
    private fun ownSlot(run: RaidRun, player: PlayerRef, loc: LocRef): Int? {
        val k = slotOf(run, loc) ?: return null
        return k.takeIf {
            run.party.members
                .getOrNull(it)
                ?.equals(player.username, ignoreCase = true) == true
        }
    }

    /** Closed chest "Open": only its owner. The chest swaps to its opened loc (the unique's when rare) and the loot shows. */
    fun open(player: PlayerRef, loc: LocRef) {
        val run = TobFlow.runOf(player) ?: return
        val k = ownSlot(run, player, loc)
        if (k == null) {
            if (slotOf(run, loc) != null) player.message(NOT_YOURS)
            return
        }
        val opened = if (isRare(run, run.party.members[k])) Locs.TOB_TREASUREROOM_CHEST_OPEN_RARE else Locs.TOB_TREASUREROOM_CHEST_OPEN
        player.addLoc(opened, loc.coord, loc.shape, loc.angle)
        TobChestUi.open(player)
    }

    /** Opened chest "Search": the owner's loot interface again. */
    fun search(player: PlayerRef, loc: LocRef) {
        val run = TobFlow.runOf(player) ?: return
        if (ownSlot(run, player, loc) == null) {
            if (slotOf(run, loc) != null) player.message(NOT_YOURS)
            return
        }
        TobChestUi.open(player)
    }

    /** The Teleport crystal 32996 "Use": out of the raid (raid-only items go, the claim inventory stays). */
    fun leave(player: PlayerRef) {
        val run = TobFlow.runOf(player) ?: return
        Raids.leave(player, run)
    }

    /** OPEN-LR22: the refusal for someone else's chest is not captured. */
    private const val NOT_YOURS = "This is not your chest."
}

/** The treasure room's locs: the chests, the Teleport crystal, and the chest varbits cleared on leaving. */
public class TobVaultPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        // The map holds no chest: the server spawns the multi parent (33086..33090); a client that resolves it sends a child.
        for (k in TobVault.CHEST_ANCHORS.indices) onOpLoc(Locs.TOB_TREASUREROOM_CHEST_LOC0 + k, OP1) { loc -> TobVault.open(player, loc) }
        for (id in CLOSED_CHILDREN) onOpLoc(id, OP1) { loc -> TobVault.open(player, loc) }
        onOpLoc(Locs.TOB_TREASUREROOM_CHEST_OPEN, OP1) { loc -> TobVault.search(player, loc) }
        onOpLoc(Locs.TOB_TREASUREROOM_CHEST_OPEN_RARE, OP1) { loc -> TobVault.search(player, loc) }
        onOpLoc(Locs.TOB_TREASUREROOM_TELEPORTOUT, OP1) { TobVault.leave(player) }
        events.on<RaidEvent.Left> { e -> if (e.run.raid.id == TobRaid.ID) world?.onlinePlayer(e.member)?.let(TobVault::clearChestVarbits) }
    }

    private companion object {
        const val OP1 = 1
        val CLOSED_CHILDREN =
            intArrayOf(
                Locs.TOB_TREASUREROOM_CHEST_NOTMINE_STANDARD,
                Locs.TOB_TREASUREROOM_CHEST_NOTMINE_RARE,
                Locs.TOB_TREASUREROOM_CHEST_MINE_STANDARD,
                Locs.TOB_TREASUREROOM_CHEST_MINE_RARE,
            )
    }
}
