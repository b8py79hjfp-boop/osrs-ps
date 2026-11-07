package tp.content.sailing

import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs

/**
 * Building core parts and facilities on the boat of a shipyard session (docs/mechanics/sailing-building.md BU1-BU9).
 *
 * A build checks the Sailing and Construction level (both boostable; a crystal saw in the backpack adds [CRYSTAL_SAW_BOOST]
 * Construction levels), the lost schematic of the tiers that need one, the tools of a facility, the materials and, when only the
 * Construction level is missing and the cache allows it, a shipwright fee; the player works for [BUILD_TICKS]; then it checks
 * again, takes materials (and fee) in one item transaction, swaps the locs on the deck ([Boats.rebuild]), saves the boat and
 * gives the Construction XP. Nothing is taken if any step fails.
 */
internal class BoatBuilder(private val state: SailingState) {
    /** What a build needs, resolved from a part or a facility row. */
    private class Plan(
        val label: String,
        val row: Int,
        val req: PartRequirements,
        val cost: BuildCost?,
        /** Facilities need a hammer and a saw; core parts need no tool (Wiki Crystal saw, Shipbuilding). */
        val tools: Boolean,
        /** Trailing materials that are needed but not used up (the fabric roll of a flag). */
        val kept: Int,
        /** Facility kind of the full facility bottle it is built from (no materials, level or XP, only the tools; OPEN-BLD7), or null. */
        val bottleKind: Int? = null,
    )

    /** Outcome of [check]: [message] when the build is refused, else the coins the shipwright is paid ([fee], 0 if none). */
    private class Verdict(val message: String?, val fee: Int)

    // ---- core parts

    /** Builds the core part [part] (a row of the boat's option lists) on the session boat. */
    suspend fun PlayerScript.buildPart(s: ShipyardSession, part: BoatPart) {
        val boat = s.boat.boat
        val owned = BoatSave.get(player, s.slot) ?: return
        val updated = withPart(owned, boat, part) ?: return
        if (updated == owned) return player.message("That is already built on your boat.")
        val kept = if (part.kind == PartKind.FLAG) 1 else 0
        val plan = Plan(part.name, part.row, part.requirements, state.boats.build.partCost(part, boat.type), tools = false, kept = kept)
        val done = work(s, plan) { p -> BoatSave.get(p, s.slot)?.let { withPart(it, boat, part) } }
        if (done) {
            player.message(
                BUILD_MESSAGES[part.kind] ?: "You get to work with the shipyard workers to build the ${part.name.lowercase()}...",
            )
        }
    }

    private fun withPart(owned: OwnedBoat, boat: Boat, part: BoatPart): OwnedBoat? {
        val index =
            state.boats.build
                .partRows(boat, part.kind)
                .indexOf(part.row)
        if (index < 0) return null
        return when (part.kind) {
            PartKind.HULL -> owned.copy(hull = index)
            PartKind.SAIL -> owned.copy(sail = index)
            PartKind.HELM -> owned.copy(helm = index)
            PartKind.KEEL -> owned.copy(keel = index)
            PartKind.FLAG -> owned.copy(flag = index + 1)
            PartKind.BRAZIER -> owned.copy(brazier = index + 1)
            PartKind.TRIM -> owned.copy(trim = index)
            PartKind.SAIL_PATTERN -> owned.copy(pattern = index)
        }
    }

    // ---- facilities

    /** Builds [facility] on hotspot [hotspot] of the session boat, from materials or a full facility bottle. */
    suspend fun PlayerScript.buildFacility(s: ShipyardSession, hotspot: Int, facility: BoatFacility) {
        val owned = BoatSave.get(player, s.slot) ?: return
        val boat = s.boat.boat
        val options = state.boats.build.options(boat, hotspot)
        val option = options.indexOfFirst { it.row == facility.row } + 1
        if (option == 0) return
        if (owned.hotspotAt(hotspot) == option) return player.message("That is already built there.")
        limitMessage(owned, boat, hotspot, facility)?.let { return player.message(it) }
        removalBlock(player, s, owned, hotspot)?.let { return player.message(it) }
        val kind = facility.bottleId
        var bottleKind: Int? = null
        if (kind != null && FacilityBottles.has(player, kind) && player.inv(Invs.INV).total(Objs.SAILING_FACILITY_BOTTLE_FULL) > 0) {
            val use = choice("Use the facility bottle?", "Yes, use the bottled facility", "No, build it from materials")
            if (use == 0) return
            if (use == 1) bottleKind = kind
        }
        val plan = Plan(facility.name, facility.row, facility.requirements, state.boats.build.facilityCost(facility), true, 0, bottleKind)
        val done = work(s, plan) { p -> BoatSave.get(p, s.slot)?.withHotspot(hotspot, option) }
        if (done) player.message("You build the ${facility.name.lowercase()}.")
    }

    /** Removes the facility of hotspot [hotspot]; nothing is refunded (OPEN-BLD5). */
    fun removeFacility(p: PlayerRef, s: ShipyardSession, hotspot: Int): Boolean {
        val owned = BoatSave.get(p, s.slot) ?: return false
        if (owned.hotspotAt(hotspot) == 0) return false
        removalBlock(p, s, owned, hotspot)?.let {
            p.message(it)
            return false
        }
        commit(p, s, owned.withHotspot(hotspot, 0))
        return true
    }

    /** The facility built on [hotspot], or null. */
    fun builtAt(s: ShipyardSession, hotspot: Int): BuiltFacility? = s.boat.facilities.firstOrNull { it.hotspot == hotspot }

    /** Why [facility] may not go on [hotspot] (its per-boat limit, [BoatFacility.buildMax], is reached), or null. */
    private fun limitMessage(owned: OwnedBoat, boat: Boat, hotspot: Int, facility: BoatFacility): String? {
        val max = facility.buildMax ?: return null
        val others =
            boat.hotspots.indices.count { i ->
                i != hotspot && owned.hotspotAt(i) > 0 &&
                    state.boats.build
                        .options(boat, i)
                        .getOrNull(owned.hotspotAt(i) - 1)
                        ?.subtype == facility.subtype
            }
        return if (others >= max) "You can't build any more of those on this boat." else null
    }

    /** A cargo hold is only changed or removed when it is empty (OPEN-BLD8: the hold's contents belong to the cargo content, S3). */
    fun removalBlock(p: PlayerRef, s: ShipyardSession, hotspot: Int): String? =
        BoatSave.get(p, s.slot)?.let { removalBlock(p, s, it, hotspot) }

    private fun removalBlock(p: PlayerRef, s: ShipyardSession, owned: OwnedBoat, hotspot: Int): String? {
        val built =
            state.boats.build
                .options(s.boat.boat, hotspot)
                .getOrNull(owned.hotspotAt(hotspot) - 1) ?: return null
        if (built.subtype != ShipbuildingData.CARGO_HOLD_SUBTYPE) return null
        val hold = p.inv(Invs.SAILING_BOAT_1_CARGOHOLD + s.slot)
        return if (hold.freeSlots < hold.size) "Empty the cargo hold first." else null
    }

    // ---- the work

    /**
     * Checks [plan], plays the build, checks again, then pays and applies [change] (a function of the saved boat, null to refuse).
     * Closes the customisation interface first so the player stands still while working.
     */
    private suspend fun PlayerScript.work(s: ShipyardSession, plan: Plan, change: (PlayerRef) -> OwnedBoat?): Boolean {
        player.closeModal()
        var problem = check(player, plan).message
        if (problem == null) {
            player.anim(Seqs.HUMAN_POH_BUILD)
            delay(BUILD_TICKS)
            if (!state.shipyard.valid(player, s)) return false
            val verdict = check(player, plan)
            problem = verdict.message ?: finish(player, s, plan, verdict.fee, change)
        }
        problem?.let { player.message(it) }
        return problem == null
    }

    /** Pays for [plan], applies the change and gives the XP; the message of what went wrong, or null. */
    private fun finish(p: PlayerRef, s: ShipyardSession, plan: Plan, fee: Int, change: (PlayerRef) -> OwnedBoat?): String? {
        val updated = change(p)
        if (updated == null || !pay(p, plan, fee)) return "You don't have the materials to build that."
        commit(p, s, updated)
        // BU5: a build the shipwright did, or one from a bottle, gives no Construction XP (OPEN-BLD4).
        val xp = plan.cost?.xp
        if (plan.bottleKind == null && fee == 0 && xp != null) p.addXp(Stats.CONSTRUCTION, xp)
        return null
    }

    /** The first reason [plan] cannot be built now, as a game message, with the shipwright fee it would cost. */
    private fun check(p: PlayerRef, plan: Plan): Verdict {
        val inv = p.inv(Invs.INV)
        val crystal = inv.total(Objs.EYEGLO_CRYSTAL_SAW) > 0
        val hasTools = inv.total(Objs.HAMMER) > 0 && (inv.total(Objs.POH_SAW) > 0 || crystal)
        val schematic = ShipbuildingData.SCHEMATICS[plan.row]
        if (schematic != null && p.varbit(schematic) == 0) return Verdict("You haven't discovered the schematic for that yet.", 0)
        if (plan.tools && !hasTools) return Verdict("You need a hammer and a saw to build that.", 0)
        if (plan.bottleKind != null) return Verdict(null, 0)
        val req = plan.req
        if (p.level(Stats.SAILING) < req.sailing) return Verdict("You need level ${req.sailing} Sailing to build that.", 0)
        val construction = p.level(Stats.CONSTRUCTION) + if (crystal) CRYSTAL_SAW_BOOST else 0
        var fee = 0
        if (construction < req.construction) {
            val shipwright = plan.cost?.fee
            if (!req.coinsAlternative ||
                shipwright == null
            ) {
                return Verdict("You need level ${req.construction} Construction to build that.", 0)
            }
            if (inv.total(Objs.COINS) < shipwright) {
                return Verdict("You need level ${req.construction} Construction, or $shipwright coins for a shipwright, to build that.", 0)
            }
            fee = shipwright
        }
        if (req.materials.any { inv.total(it.obj) < it.quantity }) return Verdict("You don't have the materials to build that.", 0)
        return Verdict(null, fee)
    }

    /** Takes the materials (not the last [Plan.kept]) and the shipwright [fee] in one transaction. */
    private fun pay(p: PlayerRef, plan: Plan, fee: Int): Boolean {
        val bottle = plan.bottleKind
        if (bottle != null) {
            return p.transaction("sailing facility bottle") { remove(Invs.INV, Objs.SAILING_FACILITY_BOTTLE_FULL, 1) } &&
                FacilityBottles.take(p, bottle)
        }
        val used = plan.req.materials.dropLast(plan.kept)
        return p.transaction("sailing build") {
            (fee == 0 || remove(Invs.INV, Objs.COINS, fee)) && used.all { remove(Invs.INV, it.obj, it.quantity) }
        }
    }

    /** Saves [owned], swaps the locs of the live boat and mirrors the boat varbits the interface reads. */
    fun commit(p: PlayerRef, s: ShipyardSession, owned: OwnedBoat) {
        val boat = s.boat
        val before = BoatSave.get(p, s.slot)
        state.boats.rebuild(p, boat, owned, shipyard = true)
        // Wiki Boat: a boat heals to full whenever its keel or hull is upgraded.
        if (before != null && (before.hull != owned.hull || before.keel != owned.keel)) boat.hp = boat.maxHp
        BoatSave.set(p, s.slot, owned.copy(hp = boat.hp))
        state.shipyard.customisation.sync(p, s)
    }

    internal companion object {
        /** Ticks the build takes (Wiki Eternal brazier recipe `ticks = 5`; OPEN-BLD2: the same for every part). */
        const val BUILD_TICKS = 5

        /** Construction levels a crystal saw in the backpack adds (Wiki Crystal saw: "up to 3 levels higher", also for shipbuilding). */
        const val CRYSTAL_SAW_BOOST = 3

        /** The chat lines of the boat schematics board for hull and sail (Wiki Boat schematics). */
        val BUILD_MESSAGES: Map<PartKind, String> =
            mapOf(
                PartKind.HULL to "You get to work with the shipyard workers to change out the hull of your boat...",
                PartKind.SAIL to "You get to work with the shipyard workers to change out the mast and sail of your boat...",
            )
    }
}
