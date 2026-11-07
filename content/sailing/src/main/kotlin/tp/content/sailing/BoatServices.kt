package tp.content.sailing

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.Event
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Published when a boat is moved without sailing (recovered, summoned, bottled, unbottled): the cargo content (S3) clears the
 * crates, bounty items, salvage and fish of its hold when [loseCargo] is set (Wiki Boat, Cargo hold: recovery and the Summon
 * Boat spell lose them; cannonballs, repair kits, fish offcuts and empty crates stay).
 */
internal class BoatRelocated(val owner: String, val slot: Int, val loseCargo: Boolean) : Event

/**
 * The shipwright services (docs/mechanics/sailing-building.md SW1-SW9): buying, recovering, destroying, repairing, renaming and
 * bottling boats, and summoning one. The cache ops of the shipwrights are op 1 "Talk-to", op 3 "Buy-boat", op 4 "Customise-boat"
 * ([Shipyard]) and op 5 "Recover-boat"; Shipwright Seb has op 4 "Repair-boat"; Sleve McDichael op 2 "Rename-boat". Boats are
 * picked with chatbox menus ([BoatPicker]) because the boat selection interface 934 is not driven (OPEN-SEL1).
 */
internal class BoatServices(private val state: SailingState) {
    fun ContentScope.register() {
        for (npc in Shipyard.SHIPWRIGHT_NPCS) {
            onOpNpc(npc, OP_BUY) { with(this@BoatServices) { buy(it.coord) } }
            onOpNpc(npc, OP_RECOVER) { with(this@BoatServices) { recover(it.coord) } }
        }
        // Junior Jim's Talk-to belongs to the Pandemonium quest.
        for (npc in Npcs.SAILING_SHIPWRIGHT_PORT_SARIM..Npcs.SAILING_SHIPWRIGHT_PRIFDDINAS) {
            onOpNpc(npc, OP_TALK) { with(this@BoatServices) { talk(it.coord) } }
        }
        for (npc in listOf(Npcs.SAILING_SHIPYARD_MANAGER, Npcs.SAILING_SHIPYARD_MANAGER_VIS)) {
            onOpNpc(npc, OP_REPAIR) { with(this@BoatServices) { repair() } }
        }
        for (npc in Npcs.SLEVE_MCDICHAEL..Npcs.SLEVE_MCDICHAEL_2OP) {
            onOpNpc(npc, OP_RENAME) { with(this@BoatServices) { rename() } }
        }
        onItemOp(Objs.SAILING_BOAT_BOTTLE_EMPTY, "Bottle-boat") { click -> startScript { with(this@BoatServices) { bottleBoat(click) } } }
        onItemOp(
            Objs.SAILING_BOAT_BOTTLE_FULL,
            "Unbottle-boat",
        ) { click -> startScript { with(this@BoatServices) { unbottleBoat(click) } } }
        onItemOp(Objs.SAILING_BOAT_BOTTLE_FULL, "Check") { checkBoatBottle(this) }
        onItemOp(Objs.SAILING_FACILITY_BOTTLE_FULL, "Check") { checkFacilityBottle(this) }
        onItemOp(Objs.SAILING_FACILITY_BOTTLE_FULL, "Empty") { click -> emptyFacilityBottle(this, click) }
    }

    // ---- shipwright

    /** "Talk-to": what the shipwright can do. Below the level to dock at his port only destroying is offered (Wiki Shipwright). */
    private suspend fun PlayerScript.talk(npcTile: CoordGrid) {
        player.stopInteraction()
        val site = Ports.nearest(state.sites, npcTile)
        if (site != null && player.baseLevel(Stats.SAILING) >= site.dock.levelRequired) {
            when (
                choice(
                    "What would you like to do?",
                    "Buy a boat",
                    "Customise a boat",
                    "Recover a boat",
                    "Destroy a boat",
                    "Never mind",
                )
            ) {
                1 -> {
                    buy(npcTile)
                }

                2 -> {
                    with(state.shipyard) { customise(npcTile) }
                }

                3 -> {
                    recover(npcTile)
                }

                4 -> {
                    destroy()
                }

                else -> {}
            }
        } else if (choice("You can't dock here yet.", "Destroy a boat", "Never mind") == 1) {
            destroy()
        }
    }

    /** Op 3 "Buy-boat": a new boat of a type the player's Sailing level allows, moored at this port (one boat per port). */
    private suspend fun PlayerScript.buy(npcTile: CoordGrid) {
        player.stopInteraction()
        val site = dockOf(npcTile) ?: return
        val level = player.baseLevel(Stats.SAILING)
        val slot = (0 until BoatSave.slotsAt(level)).firstOrNull { BoatSave.get(player, it) == null }
        val blocked =
            when {
                slot == null -> "You can't own any more boats at your Sailing level."
                BoatSave.owned(player).any { BoatSave.get(player, it)?.dock == site.dock.id } -> "You already have a boat moored here."
                else -> null
            }
        if (blocked != null || slot == null) return player.message(blocked.orEmpty())
        val type = askBoatType(level) ?: return
        player.message(purchase(site, slot, type))
    }

    /** The boat type the player chooses among those his level allows (a yes/no question when only one does), or null. */
    private suspend fun PlayerScript.askBoatType(level: Int): Int? {
        val types = state.data.boats.filter { !it.temporary && it.levelRequired <= level && PRICES.containsKey(it.type) }
        val labels = types.map { "${it.name} (${PRICES.getValue(it.type)} coins)" }
        val pick =
            if (labels.size == 1) {
                val question = "Buy a ${types.first().name.lowercase()} for ${PRICES.getValue(types.first().type)} coins?"
                if (choice(question, "Yes", "No") == 1) 1 else 0
            } else {
                choice("Which boat do you want?", *labels.toTypedArray())
            }
        return types.getOrNull(pick - 1)?.type
    }

    /** Pays for a new boat of [type] moored at [site] in [slot]; the message to show. */
    private fun PlayerScript.purchase(site: DockSite, slot: Int, type: Int): String {
        val price = PRICES.getValue(type)
        if (!player.transaction("sailing buy boat") { remove(Invs.INV, Objs.COINS, price) }) return "You can't afford that."
        val fresh = OwnedBoat(type, site.dock.id, 0, 0, 0, 0, 0, player.random(NAME_RANGE))
        BoatSave.set(player, slot, fresh.copy(hp = state.boats.maxHp(fresh)))
        return "You buy a ${state.data
            .boat(type)
            ?.name
            ?.lowercase() ?: "boat"}. It is moored at ${site.dock.name}."
    }

    /** Op 5 "Recover-boat": brings a boat from another port, or a capsized or lost one, here for the recovery fee (rule SW4). */
    private suspend fun PlayerScript.recover(npcTile: CoordGrid) {
        player.stopInteraction()
        val site = dockOf(npcTile) ?: return
        val slot =
            with(BoatPicker) { pickBoat(state, "Which boat do you want to recover?", recoverable(site)) }
                ?: return player.message("You have no boat to recover.")
        val owned = BoatSave.get(player, slot) ?: return
        val fee = recoveryCost(owned)
        if (choice("Recover your boat for $fee coins?", "Yes", "No") == 1) player.message(recoverTo(site, slot, owned, fee))
    }

    /** Slots of the boats that can be recovered to [site]: elsewhere, capsized, or bottled with the bottle gone. */
    private fun PlayerScript.recoverable(site: DockSite): List<Int> {
        val bottlesHeld = bottlesHeld(player)
        var bottledSeen = 0
        return BoatSave.owned(player).filter { slot ->
            val owned = BoatSave.get(player, slot) ?: return@filter false
            if (owned.bottled && ++bottledSeen <= bottlesHeld) return@filter false
            owned.capsized || owned.bottled || owned.dock != site.dock.id
        }
    }

    /** Moves the boat of [slot] to [site] for [fee] coins; the message to show. */
    private fun PlayerScript.recoverTo(site: DockSite, slot: Int, owned: OwnedBoat, fee: Int): String {
        val moored =
            BoatSave.owned(player).any { other ->
                val boat = BoatSave.get(player, other)
                other != slot && boat != null && boat.dock == site.dock.id && !boat.bottled
            }
        return when {
            moored -> {
                "You already have a boat moored here."
            }

            !removeFromWorld(player, slot) -> {
                "Everyone must get off that boat first."
            }

            !player.transaction("sailing recover boat") { remove(Invs.INV, Objs.COINS, fee) } -> {
                "You can't afford that."
            }

            else -> {
                val max = state.boats.maxHp(owned)
                BoatSave.set(player, slot, owned.copy(dock = site.dock.id, bottled = false, capsized = false, hp = max))
                player.events.publish(BoatRelocated(player.username.lowercase(), slot, loseCargo = true))
                "The port wizard brings your boat to ${site.dock.name}."
            }
        }
    }

    /** Destroys a boat the player picks, after a confirmation (Wiki Boat: "Destroy a boat"); its parts and cargo are lost. */
    private suspend fun PlayerScript.destroy() {
        player.stopInteraction()
        val slots = BoatSave.owned(player).filter { BoatSave.get(player, it)?.bottled == false }
        val slot =
            with(BoatPicker) { pickBoat(state, "Which boat do you want to destroy?", slots) }
                ?: return player.message("You have no boat to destroy.")
        if (choice("Destroy your boat? This cannot be undone.", "Yes, destroy it", "No") != 1) return
        if (!removeFromWorld(player, slot)) return player.message("Everyone must get off that boat first.")
        BoatSave.set(player, slot, null)
        player.events.publish(BoatRelocated(player.username.lowercase(), slot, loseCargo = true))
        player.message("Your boat is broken up.")
    }

    // ---- Seb and Sleve

    /** Seb's "Repair-boat": a boat back to full hitpoints for [REPAIR_COST_PER_HP] coins each, from the bank if the backpack is short. */
    private suspend fun PlayerScript.repair() {
        player.stopInteraction()
        val damaged = BoatSave.owned(player).filter { slot -> BoatSave.get(player, slot)?.let { missing(player, it, slot) > 0 } == true }
        val slot =
            with(BoatPicker) { pickBoat(state, "Which boat do you want to repair?", damaged) }
                ?: return player.message("None of your boats needs repairing.")
        val owned = BoatSave.get(player, slot) ?: return
        val cost = missing(player, owned, slot) * REPAIR_COST_PER_HP
        if (choice("Repair your boat for $cost coins?", "Yes", "No") != 1) return
        val inPack = minOf(player.inv(Invs.INV).total(Objs.COINS), cost.toLong()).toInt()
        val paid =
            player.transaction("sailing repair") {
                (inPack == 0 || remove(Invs.INV, Objs.COINS, inPack)) && (cost == inPack || remove(Invs.BANK, Objs.COINS, cost - inPack))
            }
        if (!paid) return player.message("You don't have enough coins in your backpack and bank.")
        val max = state.boats.maxHp(owned)
        BoatSave.set(player, slot, owned.copy(hp = max))
        ActiveBoats.ownedBy(player, player.username.lowercase())?.takeIf { it.slot == slot }?.let { it.hp = it.maxHp }
        player.message("Seb repairs your boat.")
    }

    /** Hitpoints the boat of [slot] lacks; a live boat's current value counts (a saved 0 reads as full, see [Boats.spawn]). */
    private fun missing(p: PlayerRef, owned: OwnedBoat, slot: Int): Int {
        val live = ActiveBoats.ownedBy(p, p.username.lowercase())?.takeIf { it.slot == slot }
        val max = state.boats.maxHp(owned)
        val hp = live?.hp ?: owned.hp.takeIf { it > 0 || owned.capsized } ?: max
        return (max - hp).coerceAtLeast(0)
    }

    /** Sleve McDichael's "Rename-boat": a new random name for one unnoted cider; it can't be undone (Wiki Sleve McDichael). */
    private suspend fun PlayerScript.rename() {
        player.stopInteraction()
        val slot =
            with(BoatPicker) { pickBoat(state, "Which boat do you want to rename?", BoatSave.owned(player)) }
                ?: return player.message("You have no boat.")
        if (player.inv(Invs.INV).total(Objs.CIDER) == 0L) return player.message("Sleve wants a cider for that.")
        val owned = BoatSave.get(player, slot) ?: return
        if (!player.transaction("sailing rename boat") { remove(Invs.INV, Objs.CIDER, 1) }) return
        BoatSave.set(player, slot, owned.copy(name = player.random(NAME_RANGE)))
        player.message("Sleve gives your boat a new name.")
    }

    // ---- bottles

    /** "Bottle-boat" on an empty boat bottle: the player's boat moored at the dock in reach goes into the bottle (rule BB1-BB2). */
    private suspend fun PlayerScript.bottleBoat(click: ButtonClick) {
        if (player.inv(Invs.INV).obj(click.slot) != Objs.SAILING_BOAT_BOTTLE_EMPTY) return
        val site = Ports.nearest(state.sites, player.coord)?.takeIf { (it.land?.chebyshev(player.coord) ?: Int.MAX_VALUE) <= BOTTLE_RANGE }
        if (site == null) return player.message("You need to be at a docking point to bottle your boat.")
        val slots =
            BoatSave.owned(player).filter {
                val b = BoatSave.get(player, it)
                b != null && b.dock == site.dock.id && !b.bottled && !b.capsized
            }
        val slot =
            with(BoatPicker) { pickBoat(state, "Which boat do you want to bottle?", slots) }
                ?: return player.message("You have no boat moored here.")
        val hold = player.inv(Invs.SAILING_BOAT_1_CARGOHOLD + slot)
        if (hold.freeSlots < hold.size) return player.message("Empty the cargo hold first.")
        val owned = BoatSave.get(player, slot) ?: return
        if (!removeFromWorld(player, slot)) return player.message("Everyone must get off that boat first.")
        val done =
            player.transaction("sailing bottle boat") {
                replaceAt(Invs.INV, click.slot, Objs.SAILING_BOAT_BOTTLE_EMPTY, Objs.SAILING_BOAT_BOTTLE_FULL)
            }
        if (!done) return
        BoatSave.set(player, slot, owned.copy(bottled = true))
        player.message("Your boat shrinks and slips into the bottle.")
    }

    /** "Unbottle-boat" on a full boat bottle: after a confirmation the bottled boat is moored at the dock in reach (rule BB3). */
    private suspend fun PlayerScript.unbottleBoat(click: ButtonClick) {
        if (player.inv(Invs.INV).obj(click.slot) != Objs.SAILING_BOAT_BOTTLE_FULL) return
        val slot =
            BoatSave.owned(player).firstOrNull { BoatSave.get(player, it)?.bottled == true }
                ?: return player.message("The bottle is empty.")
        val site = Ports.nearest(state.sites, player.coord)?.takeIf { (it.land?.chebyshev(player.coord) ?: Int.MAX_VALUE) <= BOTTLE_RANGE }
        if (site == null) return player.message("You need to be at a docking point to release your boat.")
        if (player.baseLevel(Stats.SAILING) <
            site.dock.levelRequired
        ) {
            return player.message("You need level ${site.dock.levelRequired} Sailing to dock at ${site.dock.name}.")
        }
        if (BoatSave.owned(player).any { BoatSave.get(player, it)?.let { b -> b.dock == site.dock.id && !b.bottled } == true }) {
            return player.message("You already have a boat moored here.")
        }
        if (choice("Release your boat here? The bottle will be lost.", "Yes", "No") != 1) return
        val owned = BoatSave.get(player, slot) ?: return
        if (!player.transaction("sailing unbottle boat") { remove(Invs.INV, Objs.SAILING_BOAT_BOTTLE_FULL, 1) }) return
        BoatSave.set(player, slot, owned.copy(bottled = false, dock = site.dock.id))
        player.events.publish(BoatRelocated(player.username.lowercase(), slot, loseCargo = false))
        player.message("Your boat grows back to full size at ${site.dock.name}.")
    }

    private fun checkBoatBottle(p: PlayerRef) {
        val names = BoatSave.owned(p).filter { BoatSave.get(p, it)?.bottled == true }.map { BoatPicker.label(state, p, it) }
        p.message(if (names.isEmpty()) "The bottle is empty." else "The bottle holds your ${names.first().lowercase()}.")
    }

    private fun checkFacilityBottle(p: PlayerRef) {
        val names = FacilityBottles.list(p).map { facilityName(it) }
        p.message(if (names.isEmpty()) "The bottle is empty." else "Bottled facilities: ${names.joinToString(", ")}.")
    }

    /** "Empty" on a full facility bottle: the oldest stored facility is poured away (OPEN-FB2: the item does not say which). */
    private fun emptyFacilityBottle(p: PlayerRef, click: ButtonClick) {
        if (p.inv(Invs.INV).obj(click.slot) != Objs.SAILING_FACILITY_BOTTLE_FULL) return
        val oldest = FacilityBottles.list(p).firstOrNull() ?: return
        val done =
            p.transaction("sailing facility bottle") {
                replaceAt(Invs.INV, click.slot, Objs.SAILING_FACILITY_BOTTLE_FULL, Objs.SAILING_FACILITY_BOTTLE_EMPTY)
            }
        if (done && FacilityBottles.take(p, oldest)) p.message("You pour the ${facilityName(oldest).lowercase()} out of the bottle.")
    }

    private fun facilityName(kind: Int): String =
        state.data.facilities
            .firstOrNull { it.bottleId == kind }
            ?.name ?: "facility"

    // ---- shared

    /**
     * Summons the boat of [slot] to [site] (rule SW7; the Summon Boat spell of S11 calls this): it needs a teleport focus built, must
     * not be bottled or capsized, and must not clash with another boat at that port. Its cargo is lost. False with a message otherwise.
     */
    fun summon(p: PlayerRef, slot: Int, site: DockSite): Boolean {
        val owned = BoatSave.get(p, slot) ?: return false
        val boat = state.data.boat(owned.type) ?: return false
        val focus =
            boat.hotspots.indices.any { i ->
                owned.hotspotAt(i) > 0 &&
                    state.boats.build
                        .options(boat, i)
                        .getOrNull(owned.hotspotAt(i) - 1)
                        ?.subtype == ShipbuildingData.TELEPORT_FOCUS_SUBTYPE
            }
        val blocked =
            when {
                !focus -> "That boat has no teleport focus."

                owned.bottled || owned.capsized -> "That boat can't be summoned."

                p.baseLevel(
                    Stats.SAILING,
                ) < site.dock.levelRequired -> "You need level ${site.dock.levelRequired} Sailing to dock at ${site.dock.name}."

                BoatSave.owned(p).any {
                    it != slot && BoatSave.get(p, it)?.let { b ->
                        b.dock == site.dock.id && !b.bottled
                    } == true
                } -> "You already have a boat moored there."

                else -> null
            }
        if (blocked != null) {
            p.message(blocked)
            return false
        }
        if (!removeFromWorld(p, slot)) {
            p.message("Everyone must get off that boat first.")
            return false
        }
        BoatSave.set(p, slot, owned.copy(dock = site.dock.id))
        p.events.publish(BoatRelocated(p.username.lowercase(), slot, loseCargo = true))
        return true
    }

    /** Recovery cost: the cache base `RETRIEVAL_COST` plus the hull's percentage (rule B2, OPEN-B2). */
    fun recoveryCost(owned: OwnedBoat): Int {
        val boat = state.data.boat(owned.type) ?: return 0
        val hull = boat.hullRows.getOrNull(owned.hull)?.let(state.data::part)
        val percent = state.data.statsOf(hull?.statsRow)?.additionalRecoveryCostPercentage ?: 0
        return boat.retrievalCost + boat.retrievalCost * percent / PERCENT
    }

    /** Takes the live boat of [slot] out of the world; false if players other than the owner are aboard. */
    private fun removeFromWorld(p: PlayerRef, slot: Int): Boolean {
        val live = ActiveBoats.ownedBy(p, p.username.lowercase())?.takeIf { it.slot == slot } ?: return true
        if (p.playersInWorld().any { it.coord in live.ref }) return false
        BoatSave.get(p, slot)?.let { BoatSave.set(p, slot, it.copy(dock = live.dock, hp = live.hp)) }
        state.boats.despawn(p, live)
        return true
    }

    private fun dockOf(npcTile: CoordGrid): DockSite? = Ports.nearest(state.sites, npcTile)

    /** Full boat bottles in the backpack and bank. */
    private fun bottlesHeld(p: PlayerRef): Int =
        (
            p.inv(Invs.INV).total(Objs.SAILING_BOAT_BOTTLE_FULL) +
                p.inv(Invs.BANK).total(Objs.SAILING_BOAT_BOTTLE_FULL)
        ).toInt()

    private companion object {
        const val OP_TALK = 1
        const val OP_BUY = 3
        const val OP_RECOVER = 5
        const val OP_REPAIR = 4
        const val OP_RENAME = 2

        /** Base boat prices (Wiki Shipwright "Boat And Recovery Costs", Boat). */
        val PRICES: Map<Int, Int> = mapOf(BoatTemplates.RAFT to 1_000, BoatTemplates.SKIFF to 15_000, BoatTemplates.SLOOP to 200_000)

        /** Seb repairs for 50 coins per hitpoint (Wiki Shipwright Seb, 3 December 2025). */
        const val REPAIR_COST_PER_HP = 50

        /** Name word indices are three bytes, as [Pandemonium.giveRaft] rolls them (OPEN-B8). */
        const val NAME_RANGE = 1 shl 24

        /** Tiles from a gangplank or mooring point within which the player counts as at the docking point (same as [Docking], OPEN-P5). */
        const val BOTTLE_RANGE = 15
        const val PERCENT = 100
    }
}
