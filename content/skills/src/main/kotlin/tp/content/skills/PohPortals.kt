package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.plugin.Teleported
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Seqs
import tp.symbols.SpotAnims
import tp.util.CoordGrid

/** The teleport every house device uses (docs/mechanics/construction.md CO26): the standard cast animation, then the move. */
internal object PohTravel {
    /** The Tele Block text, ours (teleports.md OPEN-TP1). */
    private const val BLOCKED = "A magical force stops you from teleporting."

    /** Ticks between the cast animation and the move: the standard spells' stall (teleports.md T2, speed 4 - 1). */
    private const val TICKS = 3

    /** Animation id -1 stops the current animation (protocol `Animate`). */
    private const val RESET_ANIM = -1

    suspend fun PlayerScript.travel(to: CoordGrid) {
        val p = player
        if ((p[SharedAttrs.TELEBLOCK_UNTIL] ?: 0L) > p.tick) return p.message(BLOCKED)
        p.anim(Seqs.HUMAN_CASTTELEPORT)
        p.spotAnim(SpotAnims.TELEPORT_CASTING)
        delay(TICKS)
        val from = p.coord
        p.teleport(to)
        p.events.publish(Teleported(p, from, to))
        p.anim(RESET_ANIM)
    }
}

/**
 * The directed portals of the portal chamber, shown on the frames (docs/mechanics/construction.md CO26). The destination of each of
 * the three portal spaces is saved: three 8-bit slots in one value, each the index in [TELEPORTS] plus one (0 = none).
 */
internal object PohPortals {
    private const val KEY = "poh.portals"
    private const val SLOT_BITS = 8
    private const val SLOT_MASK = (1L shl SLOT_BITS) - 1

    /** Lower-case name of the portal chamber's portal hotspot. */
    const val PORTAL_SPACE = "portal space"

    /** Cache 2757: the empty `Portal frame` of the teak, mahogany and marble portal (the Wiki infobox ids 13636, 13637, 13638). */
    val FRAMES = listOf(13636, 13637, 13638)

    /** The destinations, in Magic level order (`poh-teleports.tsv`). */
    val TELEPORTS: List<PohTeleport> by lazy { ConstructionData.teleports() }

    /** The destination index saved for portal [slot], or -1. */
    fun destination(p: PlayerRef, slot: Int): Int = (((p.saved(KEY) ?: 0L) ushr (slot * SLOT_BITS)) and SLOT_MASK).toInt() - 1

    fun setDestination(p: PlayerRef, slot: Int, index: Int) {
        val cleared = (p.saved(KEY) ?: 0L) and (SLOT_MASK shl (slot * SLOT_BITS)).inv()
        p.setSaved(KEY, cleared or ((index + 1).toLong() shl (slot * SLOT_BITS)))
    }

    /** The portal slot (0..2: left of the door, opposite, right: Wiki "Portal chamber") of a portal hotspot. */
    fun slotOf(g: HotspotGroup): Int = g.kinds.indexOf(g.ids.first())

    /** Shows the saved destination on a frame that was just placed (a portal whose frame is removed returns with it: Wiki). */
    fun decorate(p: PlayerRef, g: HotspotGroup, piece: Piece) {
        if (g.name != PORTAL_SPACE) return
        val tier = FRAMES.indexOf(piece.id)
        val slot = slotOf(g)
        if (tier < 0 || slot < 0) return
        val index = destination(p, slot)
        val portal = TELEPORTS.getOrNull(index)?.portals?.get(tier) ?: return
        for (loc in g.locs) p.addLoc(portal, loc.coord, loc.shape, loc.angle)
    }
}

/**
 * The portal chamber and the portal nexus (docs/mechanics/construction.md CO26): the teleport focus directs a portal for 100
 * times the runes of its spell (and gives five times the spell's xp); a directed portal teleports for free; the nexus unlocks
 * destinations for 1,000 times the runes and teleports to them.
 */
public class PohPortalsPlugin : ContentPlugin {
    override fun ContentScope.register() {
        for (focus in FOCI) onOpLoc(focus, OP_DIRECT) { direct(it) }
        for ((index, tp) in PohPortals.TELEPORTS.withIndex()) {
            for (loc in tp.portals.flatMap { listOf(it) + listOfNotNull(CHILD[it]) }.distinct()) {
                onOpLoc(loc, OP_ENTER) { with(PohTravel) { travel(PohPortals.TELEPORTS[index].destination) } }
                onOpLoc(loc, OP_REMOVE) { with(HouseMenus) { removePiece(it) } }
            }
        }
        for (nexus in NEXUS.keys) {
            onOpLoc(nexus, OP_TELEPORT) { teleportDefault(it) }
            onOpLoc(nexus, OP_MENU) { teleportMenu(it) }
            onOpLoc(nexus, OP_CONFIGURE) { configure(it) }
        }
    }

    // ---- portal chamber

    private suspend fun PlayerScript.direct(loc: LocRef) {
        val p = player
        val house = p[House.CURRENT] ?: return
        val group = house.groupAt(loc.coord) ?: return
        if (house.owner != p.username.lowercase()) return p.message("Only the owner of the house can direct the portals.")
        val portals =
            house.scans[group.room]
                ?.groups
                .orEmpty()
                .filter { it.name == PohPortals.PORTAL_SPACE && it.built != null }
                .sortedBy { PohPortals.slotOf(it) }
        if (portals.isEmpty()) return p.message("There are no portals to direct here.")
        val portal =
            with(HouseMenus) { pick("Which portal?", portals) { "Portal ${PohPortals.slotOf(it) + 1}" } } ?: return
        val options = PohPortals.TELEPORTS.withIndex().filter { p.level(Stats.MAGIC) >= it.value.level }
        val (index, tp) =
            with(HouseMenus) { pick("Direct the portal to...", options) { it.value.name } } ?: return
        val runes = tp.runes.map { (rune, count) -> rune to count * PORTAL_RUNES }
        if (!p.transaction("poh portal") { runes.all { (rune, count) -> remove(Invs.INV, rune, count) } }) {
            return p.message("You don't have the runes to direct the portal there.")
        }
        p.addXp(Stats.MAGIC, tp.xp * PORTAL_XP)
        PohPortals.setDestination(p, PohPortals.slotOf(portal), index)
        portal.built?.let { PohPortals.decorate(p, portal, it) }
    }

    // ---- portal nexus

    /** Saved: the unlocked destinations as a bit mask of [PohPortals.TELEPORTS] indexes, and the left-click one (index + 1). */
    private fun unlocked(
        p: PlayerRef,
    ): List<Int> = PohPortals.TELEPORTS.indices.filter { ((p.saved(NEXUS_KEY) ?: 0L) ushr it) and 1L == 1L }

    private fun nexusCapacity(house: House, loc: LocRef): Int? = house.groupAt(loc.coord)?.built?.let { NEXUS[it.id] }

    private suspend fun PlayerScript.teleportDefault(loc: LocRef) {
        val p = player
        val house = p[House.CURRENT] ?: return
        nexusCapacity(house, loc) ?: return
        val open = unlocked(p)
        if (open.isEmpty()) return p.message("You haven't added any teleports to the nexus yet.")
        val favourite = (p.saved(NEXUS_DEFAULT_KEY) ?: 0L).toInt() - 1
        val index = if (favourite in open) favourite else open.first()
        with(PohTravel) { travel(PohPortals.TELEPORTS[index].destination) }
    }

    private suspend fun PlayerScript.teleportMenu(loc: LocRef) {
        val p = player
        val house = p[House.CURRENT] ?: return
        nexusCapacity(house, loc) ?: return
        val open = unlocked(p)
        if (open.isEmpty()) return p.message("You haven't added any teleports to the nexus yet.")
        val index = with(HouseMenus) { pick("Teleport to...", open) { PohPortals.TELEPORTS[it].name } } ?: return
        with(PohTravel) { travel(PohPortals.TELEPORTS[index].destination) }
    }

    private suspend fun PlayerScript.configure(loc: LocRef) {
        val p = player
        val house = p[House.CURRENT] ?: return
        val capacity = nexusCapacity(house, loc) ?: return
        if (house.owner != p.username.lowercase()) return p.message("Only the owner of the house can configure the nexus.")
        when (choice("Configure the nexus", "Add a teleport", "Choose the left-click teleport", "Cancel")) {
            1 -> addTeleport(capacity)
            2 -> chooseDefault()
        }
    }

    /** The Magic level that counts is the real one: "temporary magic boosts cannot be used" for the nexus (Wiki "Portal nexus"). */
    private suspend fun PlayerScript.addTeleport(capacity: Int) {
        val p = player
        val open = unlocked(p)
        if (open.size >= capacity) return p.message("Your nexus can't hold any more teleports.")
        val options = PohPortals.TELEPORTS.indices.filter { it !in open && p.baseLevel(Stats.MAGIC) >= PohPortals.TELEPORTS[it].level }
        val index = with(HouseMenus) { pick("Add a teleport", options) { PohPortals.TELEPORTS[it].name } } ?: return
        val runes = PohPortals.TELEPORTS[index].runes.map { (rune, count) -> rune to count * NEXUS_RUNES }
        if (!p.transaction("poh nexus") { runes.all { (rune, count) -> remove(Invs.INV, rune, count) } }) {
            return p.message("You don't have the runes to add that teleport.")
        }
        p.setSaved(NEXUS_KEY, (p.saved(NEXUS_KEY) ?: 0L) or (1L shl index))
    }

    private suspend fun PlayerScript.chooseDefault() {
        val p = player
        val open = unlocked(p)
        if (open.isEmpty()) return p.message("You haven't added any teleports to the nexus yet.")
        val index = with(HouseMenus) { pick("Left-click teleport", open) { PohPortals.TELEPORTS[it].name } } ?: return
        p.setSaved(NEXUS_DEFAULT_KEY, (index + 1).toLong())
    }

    private companion object {
        /** Cache 2757: the focus' op 1 "Direct-portal", the portal's "Enter" and "Remove", the nexus' ops 1..3. */
        const val OP_DIRECT = 1
        const val OP_ENTER = 1
        const val OP_REMOVE = 5
        const val OP_TELEPORT = 1
        const val OP_MENU = 2
        const val OP_CONFIGURE = 3

        /** Cache 2757: teleport focus, greater teleport focus and scrying pool (all op 1 "Direct-portal"). */
        val FOCI = listOf(13640, 13641, 13639)

        /** Wiki "Portal chamber": directing costs 100 times the spell's runes and gives five times its xp. */
        const val PORTAL_RUNES = 100
        const val PORTAL_XP = 5.0

        /** Wiki "Portal nexus": adding a teleport costs 1,000 times the spell's runes and gives no xp. */
        const val NEXUS_RUNES = 1000

        /** Cache 2757 nexus loc (marble, gilded, crystalline) to the teleports it holds (Wiki "Portal nexus"). */
        val NEXUS: Map<Int, Int> = mapOf(33408 to 4, 33409 to 8, 33410 to 41)

        const val NEXUS_KEY = "poh.nexus"
        const val NEXUS_DEFAULT_KEY = "poh.nexus.default"

        /**
         * The cache's multi-loc portals (Varrock / Grand Exchange, Camelot / Seers' Village, Watchtower / Yanille): the loc a click
         * may report is the parent above or its first child (cache 2757 `multiLocs`).
         */
        val CHILD: Map<Int, Int> =
            mapOf(
                13615 to 33092,
                13622 to 33098,
                13629 to 33104,
                13618 to 33094,
                13625 to 33100,
                13632 to 33106,
                13620 to 33096,
                13627 to 33102,
                13634 to 33108,
            )
    }
}
