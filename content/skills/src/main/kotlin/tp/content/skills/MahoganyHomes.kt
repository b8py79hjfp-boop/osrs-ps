package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.Expression
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits

/** Experience from the carpenter's outfit (Wiki "Carpenter's outfit"): tenths of a percent per piece, plus the set bonus. */
internal object CarpenterOutfit {
    /** Wiki "Carpenter's outfit": helmet 0.4%, shirt 0.8%, trousers 0.6%, boots 0.2%, full set another 0.5% (tenths of a percent). */
    private val PIECES: Map<Int, Int> =
        mapOf(Objs.CONSTRUCTION_HAT to 4, Objs.CONSTRUCTION_SHIRT to 8, Objs.CONSTRUCTION_TROUSERS to 6, Objs.CONSTRUCTION_BOOTS to 2)
    private const val SET_BONUS_TENTHS = 5
    private const val TENTHS_PER_UNIT = 1000.0

    /** The Construction xp multiplier of the outfit worn by [p] (it does not apply to the bone turn-in at Barlak). */
    fun multiplier(p: PlayerRef): Double {
        val worn = p.inv(Invs.WORN)
        var tenths = 0
        var pieces = 0
        for (slot in 0 until worn.size) {
            val bonus = PIECES[worn.obj(slot)] ?: continue
            tenths += bonus
            pieces++
        }
        if (pieces == PIECES.size) tenths += SET_BONUS_TENTHS
        return 1.0 + tenths / TENTHS_PER_UNIT
    }
}

/**
 * Mahogany Homes (docs/mechanics/mahogany-homes.md): Amy and her three contractors give a contract of one of four tiers; the
 * contract is a layout of furniture in one of twelve client houses, drawn by the client varbits 10554..10561. At each piece the
 * player removes the old furniture, builds the new one from planks of the tier, or repairs it with a plank or a steel bar; the
 * client pays carpenter points and experience when every piece is done. Points buy rewards from Amy.
 */
public class MahoganyHomes : ContentPlugin {
    private var bolt = Objs.CLOTH
    private var crate: List<CrateRoll> = emptyList()

    override fun ContentScope.register() {
        check(MahoganyData.LAYOUTS.values.all { homes -> homes.all { it.isNotEmpty() } }) { "mahogany layouts missing for a home" }
        onWorldStart { resolve(this) }
        onLogin { sync(this) }
        for (npc in listOf(Npcs.CON_CONTRACTOR_1OP, Npcs.CON_CONTRACTOR_3OP)) onOpNpc(npc, OP_TALK) { talkContractor(it, amy = true) }
        val others = listOf(Npcs.CON_CONTRACTOR_VARROCK_1OP, Npcs.CON_CONTRACTOR_ARDOUGNE_1OP, Npcs.CON_CONTRACTOR_HOSIDIUS_1OP)
        for (npc in others) onOpNpc(npc, OP_TALK) { talkContractor(it, amy = false) }
        onOpNpc(Npcs.CON_CONTRACTOR_3OP, OP_CONTRACT) { contract(it, amy = true) }
        onOpNpc(Npcs.CON_CONTRACTOR_3OP, OP_LAST_TIER) { lastTier(it, amy = true) }
        onOpNpc(Npcs.CON_CONTRACTOR_3OP, OP_REWARDS) { rewards() }
        val contractors = listOf(Npcs.CON_CONTRACTOR_VARROCK_2OP, Npcs.CON_CONTRACTOR_ARDOUGNE_2OP, Npcs.CON_CONTRACTOR_HOSIDIUS_2OP)
        for (npc in contractors) {
            onOpNpc(npc, OP_CONTRACT) { contract(it, amy = false) }
            onOpNpc(npc, OP_LAST_TIER) { lastTier(it, amy = false) }
        }
        for ((index, home) in MahoganyData.HOMES.withIndex()) onOpNpc(home.npc, OP_TALK) { talkClient(it, index) }
        for (parent in MahoganyData.HOTSPOTS.keys) {
            for (op in listOf(OP_REMOVE, OP_REPAIR, OP_BUILD)) onOpLoc(parent, op) { hotspot(it) }
        }
        onItemOp(Objs.CONSTRUCTION_SUPPLY_CRATE, "Open") { openCrates(this, 1) }
        onItemOp(Objs.CONSTRUCTION_SUPPLY_CRATE, "Open-all") { openCrates(this, Int.MAX_VALUE) }
    }

    private fun resolve(world: WorldRef) {
        bolt = world.objIdsNamed("Bolt of cloth").minOrNull() ?: Objs.CLOTH
        crate = CRATE.map { CrateRoll(if (it.name == BOLT) bolt else it.obj, it.name, it.weights) }
    }

    // ---- contracts

    private suspend fun PlayerScript.talkContractor(npc: NpcRef, amy: Boolean) {
        chatNpc(npc, "Hello there! Are you looking for work with Mahogany Homes?", Expression.HAPPY)
        val pick =
            if (amy) {
                choice("Select an Option", "I'd like a contract.", "Show me your rewards.", "No thanks.")
            } else {
                choice("Select an Option", "I'd like a contract.", "No thanks.")
            }
        when (pick) {
            1 -> contract(npc, amy)
            2 -> if (amy) rewards()
        }
    }

    private suspend fun PlayerScript.contract(npc: NpcRef, amy: Boolean) {
        val p = player
        if (!amy && p.saved(JOINED_KEY) == null) {
            return chatNpc(npc, "You should speak to Amy in Falador before I can give you a contract.", Expression.NEUTRAL)
        }
        current(p)?.let { return busy(npc, it) }
        val labels = MahoganyTier.entries.map { "${it.label} (level ${it.level})" }
        val tier = MahoganyTier.entries.getOrNull(choice("Which contract would you like?", *labels.toTypedArray()) - 1) ?: return
        assign(npc, tier, amy)
    }

    private suspend fun PlayerScript.lastTier(npc: NpcRef, amy: Boolean) {
        val p = player
        current(p)?.let { return busy(npc, it) }
        val last = (p.saved(LAST_KEY) ?: 0L).toInt()
        val tier =
            MahoganyTier.entries.getOrNull(last - 1) ?: return chatNpc(npc, "You haven't taken a contract from us yet.", Expression.NEUTRAL)
        if (!amy && p.saved(JOINED_KEY) == null) return chatNpc(npc, "You should speak to Amy in Falador first.", Expression.NEUTRAL)
        assign(npc, tier, amy)
    }

    private suspend fun PlayerScript.busy(npc: NpcRef, layout: MahoganyLayout) {
        val home = MahoganyData.HOMES[layout.home]
        chatNpc(npc, "You're already on a job: go and see ${home.name} in ${home.town}.", Expression.NEUTRAL)
    }

    /** Picks a home and a layout at random (the Wiki: "the location of the contract chosen at random") and starts the contract. */
    private suspend fun PlayerScript.assign(npc: NpcRef, tier: MahoganyTier, amy: Boolean) {
        val p = player
        if (p.level(Stats.CONSTRUCTION) < tier.level) {
            return chatNpc(
                npc,
                "You need a Construction level of ${tier.level} for a ${tier.label.lowercase()} contract.",
                Expression.NEUTRAL,
            )
        }
        val home = p.random(MahoganyData.HOMES.size)
        val layouts = MahoganyData.LAYOUTS.getValue(tier)[home]
        val index = p.random(layouts.size)
        var packed = 0L
        for (s in layouts[index].slots) packed = packed or (s.state.toLong() shl (STATE_BITS * s.slot))
        p.setSaved(TIER_KEY, (tier.ordinal + 1).toLong())
        p.setSaved(LAST_KEY, (tier.ordinal + 1).toLong())
        p.setSaved(HOME_KEY, home.toLong())
        p.setSaved(LAYOUT_KEY, index.toLong())
        p.setSaved(SLOTS_KEY, packed)
        if (amy) p.setSaved(JOINED_KEY, 1L)
        sync(p)
        val client = MahoganyData.HOMES[home]
        chatNpc(npc, "Your ${tier.label.lowercase()} contract is for ${client.name} in ${client.town}.", Expression.HAPPY)
        chatNpc(npc, "Bring a hammer, a saw, ${tier.label.lowercase()}-grade planks and a few steel bars.", Expression.NEUTRAL)
    }

    // ---- clients

    private suspend fun PlayerScript.talkClient(npc: NpcRef, home: Int) {
        val p = player
        val layout = current(p)
        if (layout == null || layout.home != home) return chatNpc(npc, "Hello there.", Expression.NEUTRAL)
        p.setVarbit(Varbits.CON_CONTRACT_DISCUSSED, 1)
        val todo = layout.slots.filter { slotState(p, it.slot) in PENDING }
        if (todo.isEmpty()) return complete(npc, layout)
        val things =
            todo.joinToString(
                ", ",
            ) { s -> "${s.name.lowercase()} ${if (s.state == MahoganyData.REPAIR) "repaired" else "replaced"}" }
        chatNpc(npc, "I still need: $things.", Expression.NEUTRAL)
    }

    /** MH7: the xp, the points, the tea and the clean up of a finished contract. */
    private suspend fun PlayerScript.complete(npc: NpcRef, layout: MahoganyLayout) {
        val p = player
        val tier = layout.tier
        chatNpc(npc, "That's wonderful, thank you! Have a cup of tea.", Expression.HAPPY)
        p.addXp(Stats.CONSTRUCTION, tier.done * CarpenterOutfit.multiplier(p))
        p.setSaved(POINTS_KEY, (p.saved(POINTS_KEY) ?: 0L) + tier.points)
        p.setSaved(DONE_KEY, minOf((p.saved(DONE_KEY) ?: 0L) + 1, MAX_COUNT))
        p.runEnergy = FULL_RUN_ENERGY
        clear(p)
        p.message("You drink the tea and feel refreshed. You have ${p.saved(POINTS_KEY)} carpenter points.")
    }

    // ---- hotspots

    private suspend fun PlayerScript.hotspot(loc: LocRef) {
        val p = player
        val layout = current(p) ?: return p.message(LEAVE_ALONE)
        val spot =
            MahoganyData.HOTSPOTS[loc.id]
                ?.filter { it.home == layout.home && it.level == loc.coord.level }
                ?.minByOrNull { maxOf(kotlin.math.abs(it.x - loc.coord.x), kotlin.math.abs(it.z - loc.coord.z)) }
                ?.takeIf { maxOf(kotlin.math.abs(it.x - loc.coord.x), kotlin.math.abs(it.z - loc.coord.z)) <= NEAR }
        val slot = layout.slots.firstOrNull { it.slot == spot?.slot } ?: return p.message(LEAVE_ALONE)
        when (slotState(p, slot.slot)) {
            MahoganyData.REPLACE -> removeOld(slot, loc)
            MahoganyData.BUILD -> build(layout.tier, slot, loc)
            MahoganyData.REPAIR -> repair(layout.tier, slot, loc)
            else -> p.message(LEAVE_ALONE)
        }
    }

    private suspend fun PlayerScript.removeOld(slot: MahoganySlot, loc: LocRef) {
        player.anim(animFor(loc.shape))
        delay(REMOVE_TICKS)
        if (slotState(player, slot.slot) == MahoganyData.REPLACE) setSlot(player, slot.slot, MahoganyData.BUILD)
    }

    private suspend fun PlayerScript.build(tier: MahoganyTier, slot: MahoganySlot, loc: LocRef) {
        val p = player
        shortfall(p, tier.plank, slot.count, needSaw = true)?.let { return p.message(it) }
        p.anim(animFor(loc.shape))
        delay(WORK_TICKS)
        if (slotState(p, slot.slot) != MahoganyData.BUILD) return
        shortfall(p, tier.plank, slot.count, needSaw = true)?.let { return p.message(it) }
        if (!take(p, tier.plank, slot.count)) return p.message(NO_MATERIALS)
        setSlot(p, slot.slot, tier.state)
        p.addXp(Stats.CONSTRUCTION, slot.count * tier.build * CarpenterOutfit.multiplier(p))
    }

    private suspend fun PlayerScript.repair(tier: MahoganyTier, slot: MahoganySlot, loc: LocRef) {
        val p = player
        val material = if (slot.bar) Objs.STEEL_BAR else tier.plank
        shortfall(p, material, slot.count, needSaw = false)?.let { return p.message(it) }
        p.anim(animFor(loc.shape))
        delay(WORK_TICKS)
        if (slotState(p, slot.slot) != MahoganyData.REPAIR) return
        shortfall(p, material, slot.count, needSaw = false)?.let { return p.message(it) }
        if (!take(p, material, slot.count)) return p.message(NO_MATERIALS)
        setSlot(p, slot.slot, MahoganyData.FIXED)
        val xp = if (slot.bar) BAR_REPAIR_XP * slot.count else tier.repair
        p.addXp(Stats.CONSTRUCTION, xp * CarpenterOutfit.multiplier(p))
    }

    private fun shortfall(p: PlayerRef, material: Int, count: Int, needSaw: Boolean): String? =
        when {
            !hasAny(p, HAMMERS) -> "You need a hammer to do that."
            needSaw && !hasAny(p, SAWS) -> "You need a saw to do that."
            have(p, material) < count -> NO_MATERIALS
            else -> null
        }

    private fun hasAny(p: PlayerRef, objs: IntArray): Boolean = objs.any { p.inv(Invs.INV).total(it) > 0 || p.inv(Invs.WORN).total(it) > 0 }

    private fun have(p: PlayerRef, obj: Int): Long = p.inv(Invs.INV).total(obj) + if (PlankSack.owns(p)) PlankSack.countOf(p, obj) else 0

    /** Takes [n] of [obj] from the backpack, then the plank sack (Wiki "Plank sack": planks are used straight from it). */
    private fun take(p: PlayerRef, obj: Int, n: Int): Boolean {
        val fromInv = minOf(p.inv(Invs.INV).total(obj).toInt(), n)
        val fromSack = n - fromInv
        if (fromSack > 0 && (!PlankSack.owns(p) || fromSack > PlankSack.countOf(p, obj))) return false
        if (fromInv > 0 && !p.transaction("mahogany homes") { remove(Invs.INV, obj, fromInv) }) return false
        if (fromSack > 0) PlankSack.takeOf(p, obj, fromSack)
        return true
    }

    private fun animFor(shape: Int): Int = if (shape <= WALL_DECOR_LAST) Seqs.HUMAN_POH_BUILD_WALL else Seqs.HUMAN_POH_BUILD

    // ---- rewards

    private suspend fun PlayerScript.rewards() {
        val p = player
        var start = 0
        while (true) {
            val page = REWARDS.drop(start).take(PAGE)
            val more = start + PAGE < REWARDS.size
            val labels = page.map { "${it.name} (${it.cost} points)" } + if (more) "More..." else "Cancel"
            val pick = choice("Carpenter points: ${p.saved(POINTS_KEY) ?: 0L}", *labels.toTypedArray())
            when {
                pick == 0 -> return
                pick <= page.size -> return buy(p, page[pick - 1])
                more -> start += PAGE
                else -> return
            }
        }
    }

    private fun buy(p: PlayerRef, reward: Reward) {
        val points = p.saved(POINTS_KEY) ?: 0L
        val owned = p.inv(Invs.INV).total(reward.obj) + p.inv(Invs.BANK).total(reward.obj) + p.inv(Invs.WORN).total(reward.obj)
        when {
            points < reward.cost -> p.message("You don't have enough carpenter points.")
            reward.unique && owned > 0 -> p.message("You can only own one ${reward.name.lowercase()}.")
            !p.transaction("mahogany homes reward") { add(Invs.INV, reward.obj, 1) } -> Unit
            else -> p.setSaved(POINTS_KEY, (points - reward.cost).takeIf { it > 0 })
        }
    }

    /** Wiki "Supply crate (Mahogany Homes)": each material rolls its own quantity out of 144; the items come noted. */
    private fun openCrates(p: PlayerRef, limit: Int) {
        var opened = 0
        while (opened < limit && p.inv(Invs.INV).total(Objs.CONSTRUCTION_SUPPLY_CRATE) > 0) {
            val ok =
                p.transaction("supply crate") {
                    if (!remove(Invs.INV, Objs.CONSTRUCTION_SUPPLY_CRATE, 1)) return@transaction false
                    for (r in crate) {
                        val n = roll(p, r)
                        if (n > 0 && !addOrDrop(Invs.INV, p.noted(r.obj).takeIf { it > 0 } ?: r.obj, n)) return@transaction false
                    }
                    true
                }
            if (!ok) return
            opened++
        }
    }

    private fun roll(p: PlayerRef, r: CrateRoll): Int {
        var pick = p.random(CRATE_DENOMINATOR)
        for ((quantity, weight) in r.weights) {
            if (pick < weight) return quantity
            pick -= weight
        }
        return 0
    }

    // ---- state

    private fun current(p: PlayerRef): MahoganyLayout? {
        val tier = MahoganyTier.entries.getOrNull((p.saved(TIER_KEY) ?: return null).toInt() - 1) ?: return null
        val home = (p.saved(HOME_KEY) ?: return null).toInt()
        return MahoganyData.LAYOUTS[tier]?.getOrNull(home)?.getOrNull((p.saved(LAYOUT_KEY) ?: 0L).toInt())
    }

    private fun slotState(p: PlayerRef, slot: Int): Int = ((p.saved(SLOTS_KEY) ?: 0L) ushr (STATE_BITS * slot)).toInt() and STATE_MASK

    private fun setSlot(p: PlayerRef, slot: Int, state: Int) {
        val old = p.saved(SLOTS_KEY) ?: 0L
        val shift = STATE_BITS * slot
        p.setSaved(SLOTS_KEY, ((old and (STATE_MASK.toLong() shl shift).inv()) or (state.toLong() shl shift)).takeIf { it != 0L })
        p.setVarbit(MahoganyData.FIRST_VARBIT + slot, state)
    }

    /** Mirrors the saved contract into the eight client varbits (login, new contract, hand-in). */
    private fun sync(p: PlayerRef) {
        for (slot in 0 until MahoganyData.SLOTS) p.setVarbit(MahoganyData.FIRST_VARBIT + slot, slotState(p, slot))
        if (current(p) == null) p.setVarbit(Varbits.CON_CONTRACT_DISCUSSED, 0)
    }

    private fun clear(p: PlayerRef) {
        for (key in listOf(TIER_KEY, HOME_KEY, LAYOUT_KEY, SLOTS_KEY)) p.setSaved(key, null)
        sync(p)
    }

    private class CrateRoll(val obj: Int, val name: String, val weights: List<Pair<Int, Int>>)

    private class Reward(val obj: Int, val name: String, val cost: Int, val unique: Boolean = false)

    private companion object {
        /** Cache 2757 npc ops: contractors have 1 Talk-to, 3 Contract, 4 Last-tier contract, 5 Rewards (Amy only). */
        const val OP_TALK = 1
        const val OP_CONTRACT = 3
        const val OP_LAST_TIER = 4
        const val OP_REWARDS = 5

        /** Cache 2757 hotspot children: 2 "Remove" (old piece), 3 "Repair" (broken piece), 4 "Build" (hotspot). */
        const val OP_REMOVE = 2
        const val OP_REPAIR = 3
        const val OP_BUILD = 4

        const val PAGE = 4
        const val NEAR = 3
        const val LEAVE_ALONE = "I'd better leave this alone right now."
        const val NO_MATERIALS = "You don't have the materials to do that."
        const val BOLT = "Bolt of cloth"

        /** Loc shapes up to 8 are walls and wall decor (the client's layer rule), built with the wall animation. */
        const val WALL_DECOR_LAST = 8

        /** Wiki "Mahogany Homes/Mechanics" and "Mahogany Homes": a steel bar repair gives 120 xp. */
        const val BAR_REPAIR_XP = 120.0

        /** Ticks per hotspot action: the Wiki gives none, so a house build's 5 (OPEN-CO12). */
        const val WORK_TICKS = 5
        const val REMOVE_TICKS = 1

        /** Wiki "Mahogany Homes/Mechanics": the contracts completed counter stops at 30,000. */
        const val MAX_COUNT = 30_000L

        /** Run energy is stored in 1/100 percent (Refs.kt `MIN_RUN_ENERGY` = 100 = 1%); the tea restores 100% (Wiki "Mahogany Homes"). */
        const val FULL_RUN_ENERGY = 10_000

        const val TIER_KEY = "mahogany.tier"
        const val HOME_KEY = "mahogany.home"
        const val LAYOUT_KEY = "mahogany.layout"
        const val SLOTS_KEY = "mahogany.slots"
        const val LAST_KEY = "mahogany.last"
        const val JOINED_KEY = "mahogany.joined"
        const val POINTS_KEY = "mahogany.points"
        const val DONE_KEY = "mahogany.done"
        const val STATE_BITS = 4
        const val STATE_MASK = (1 shl STATE_BITS) - 1

        /** A piece that still needs work: broken, old, or an empty hotspot. */
        val PENDING = setOf(MahoganyData.REPAIR, MahoganyData.REPLACE, MahoganyData.BUILD)

        val HAMMERS = intArrayOf(Objs.HAMMER, Objs.IMCANDO_HAMMER, Objs.IMCANDO_HAMMER_OFFHAND)
        val SAWS = intArrayOf(Objs.POH_SAW, Objs.WEARABLE_SAW, Objs.EYEGLO_CRYSTAL_SAW)

        /** Wiki "Mahogany Homes Reward Shop" (Stock): price in carpenter points. */
        val REWARDS =
            listOf(
                Reward(Objs.CONSTRUCTION_SUPPLY_CRATE, "Supply crate", 25),
                Reward(Objs.WEARABLE_SAW, "Amy's saw", 500),
                Reward(Objs.PLANK_SACK, "Plank sack", 350, unique = true),
                Reward(Objs.HOSIDIUS_BLUEPRINTS, "Hosidius blueprints", 2000),
                Reward(Objs.CONSTRUCTION_HAT, "Carpenter's helmet", 400),
                Reward(Objs.CONSTRUCTION_SHIRT, "Carpenter's shirt", 800),
                Reward(Objs.CONSTRUCTION_TROUSERS, "Carpenter's trousers", 600),
                Reward(Objs.CONSTRUCTION_BOOTS, "Carpenter's boots", 200),
            )

        /** Wiki "Supply crate (Mahogany Homes)" drop table: every rarity is out of 144. */
        const val CRATE_DENOMINATOR = 144

        /** Materials with (quantity, weight out of 144); the Wiki lists these rows, the remaining weight gives nothing. */
        val CRATE: List<CrateRoll> =
            listOf(
                CrateRoll(Objs.CLOTH, BOLT, listOf(9 to 5, 10 to 1)),
                CrateRoll(Objs.LIMESTONEBRICK, "Limestone brick", listOf(9 to 5, 10 to 1)),
                CrateRoll(Objs.PLANK_MAHOGANY, "Mahogany plank", listOf(6 to 15, 7 to 3)),
                CrateRoll(Objs.PLANK_OAK, "Oak plank", listOf(28 to 10, 29 to 15, 30 to 5)),
                CrateRoll(Objs.SOFTCLAY, "Soft clay", listOf(45 to 5, 46 to 10, 47 to 10, 48 to 5)),
                CrateRoll(Objs.STEEL_BAR, "Steel bar", listOf(23 to 5, 24 to 20, 25 to 5)),
                CrateRoll(Objs.PLANK_TEAK, "Teak plank", listOf(15 to 20, 16 to 4)),
            )
    }
}
