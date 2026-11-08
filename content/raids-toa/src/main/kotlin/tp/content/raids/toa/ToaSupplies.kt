package tp.content.raids.toa

import tp.content.raids.RaidRun
import tp.content.raids.Raids
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.WorldRef
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The seven supply potions of the Helpful Spirit (toa.md TA52-TA59): the dose items from one dose left upwards, the cache
 * op text that uses them (cache iops: Drink / Apply / Crack / Crush) and a lower-case name for the messages.
 */
internal enum class ToaSupply(val label: String, val verb: String, private val byDose: IntArray) {
    /** TA57: nectar (4 doses), `TOA_SUPPLY_HEAL_*`. */
    NECTAR("nectar", "Drink", intArrayOf(Objs.TOA_SUPPLY_HEAL_1, Objs.TOA_SUPPLY_HEAL_2, Objs.TOA_SUPPLY_HEAL_3, Objs.TOA_SUPPLY_HEAL_4)),

    /** TA59: tears of Elidinis (4 doses), `TOA_SUPPLY_PRAYER_*`. */
    TEARS(
        "tears of Elidinis",
        "Drink",
        intArrayOf(Objs.TOA_SUPPLY_PRAYER_1, Objs.TOA_SUPPLY_PRAYER_2, Objs.TOA_SUPPLY_PRAYER_3, Objs.TOA_SUPPLY_PRAYER_4),
    ),

    /** TA56: ambrosia (2 doses), `TOA_SUPPLY_PANICHEAL_*`. */
    AMBROSIA("ambrosia", "Drink", intArrayOf(Objs.TOA_SUPPLY_PANICHEAL_1, Objs.TOA_SUPPLY_PANICHEAL_2)),

    /** TA58: silk dressing (2 uses), `TOA_SUPPLY_HEAL_OVERTIME_*`. */
    SILK("silk dressing", "Apply", intArrayOf(Objs.TOA_SUPPLY_HEAL_OVERTIME_1, Objs.TOA_SUPPLY_HEAL_OVERTIME_2)),

    /** TA58: blessed crystal scarab (2 uses), `TOA_SUPPLY_PRAYER_OVERTIME_*`. */
    SCARAB("blessed crystal scarab", "Crack", intArrayOf(Objs.TOA_SUPPLY_PRAYER_OVERTIME_1, Objs.TOA_SUPPLY_PRAYER_OVERTIME_2)),

    /** TA59: liquid adrenaline (2 doses), `TOA_SUPPLY_ENERGY_*`. */
    ADRENALINE("liquid adrenaline", "Drink", intArrayOf(Objs.TOA_SUPPLY_ENERGY_1, Objs.TOA_SUPPLY_ENERGY_2)),

    /** TA59: smelling salts (2 uses), `TOA_SUPPLY_STATS_*`. */
    SALTS("smelling salts", "Crush", intArrayOf(Objs.TOA_SUPPLY_STATS_1, Objs.TOA_SUPPLY_STATS_2)),
    ;

    val maxDoses: Int get() = byDose.size

    /** The item with every dose left. */
    val full: Int get() = byDose.last()

    fun item(dose: Int): Int = byDose[dose - 1]

    companion object {
        private val DECODE: Map<Int, Pair<ToaSupply, Int>> =
            entries.flatMap { s -> (1..s.maxDoses).map { s.item(it) to (s to it) } }.toMap()

        /** The supply and the doses left of [obj], or null when it is not a supply potion. */
        fun of(obj: Int): Pair<ToaSupply, Int>? = DECODE[obj]

        val ITEMS: Set<Int> get() = DECODE.keys
    }
}

/** What one Helpful Spirit visit has done for a run: the spirit, the bundles rolled per player and who claimed. */
internal class ToaSupplyState {
    /** 1 or 2 while a spirit stands in the Nexus (TA51), else 0. */
    var visit = 0

    /** The highest visit already offered; a Nexus re-entry never offers it twice. */
    var offered = 0
    var spirit: NpcRef? = null

    /** Lower-case player name to the three bundles (Life, Chaos, Power) rolled for this visit: obj to count. */
    val rolls = HashMap<String, Array<List<Pair<Int, Int>>>>()

    /** Lower-case names that took a bundle this visit. */
    val claimed = HashSet<String>()
}

/**
 * The Helpful Spirit (toa.md TA51-TA54; client-contract 2.8 and 8): the spirit stands in the run's Nexus after the second
 * and the fourth path, a "Claim" opens interface 777 whose three bundles (invs 807-809, sent before the interface opens)
 * are rolled once per player and visit (`TOA_MIDRAIDLOOT_ROLLED`), and one choice moves its items into the supplies bag
 * (inventory 810, [ToaSupplyBag]) and sets `TOA_MIDRAIDLOOT_CLAIMED`. The potions' effects are [ToaConsumables], the
 * restores and honey locusts [ToaRestores].
 */
internal object ToaSupplies {
    /** Items removed when a player leaves the raid (`RaidDefinition.raidOnlyItems`): the bag, the potions, the locust. */
    val RAID_ONLY: Set<Int> = ToaSupply.ITEMS + Objs.TOA_MIDRAIDLOOT_BAG + Objs.TOA_HONEY_LOCUST

    /** TA51: the first offering follows the second finished path, the second the fourth. */
    private const val FIRST_VISIT_PATHS = 2
    private const val SECOND_VISIT_PATHS = 4

    /**
     * OPEN-TA37: TA51 / lobby-rewards TL13 pin the spirit at (3547, 5157), but that tile and its four neighbours are all
     * blocked in the cache collision (cache-dump `collision 3547 5157 0`), so nobody could reach him. He stands one tile
     * south-west on the free tile (3546, 5156), reached from the south, beside the Deposit pot's pin (3546, 5154).
     */
    val SPIRIT_AT = CoordGrid(3546, 5156)

    /** The three bundles, in interface order (777:6 / 9 / 12, client-contract 2.8). */
    private const val BUNDLES = 3
    private const val BUNDLE_SLOTS = 7

    /** TA52 Life: nectar and tears "are given in a set of 5" (OPEN-TA24: five potions each). */
    private const val LIFE_SET = 5
    private val DRESSINGS = 1..3
    private val SCARABS = 1..3
    private val LIFE_AMBROSIA = 1..2

    /** TA52 Life with On a Diet and no reduction: the dressings are replaced by "two scarabs and ambrosia" (OPEN-TA24: +2, +1). */
    private const val DIET_EXTRA_SCARABS = 2
    private const val DIET_EXTRA_AMBROSIA = 1

    /** TA52 Chaos ranges. */
    private val CHAOS_NECTAR = 1..8
    private val CHAOS_TEARS = 0..6
    private val CHAOS_SALTS = 0..2

    /** TA52 Power: 2 smelling salts and 1 liquid adrenaline. */
    private const val POWER_SALTS = 2
    private const val POWER_ADRENALINE = 1

    private const val PERCENT = 100

    // OPEN-TA38: none of these lines was captured.
    private const val ALREADY_CLAIMED = "You have already claimed your supplies."
    private const val NO_SPACE = "You don't have enough inventory space."
    private const val CLAIMED_MESSAGE = "You take the supplies."
    private const val FORFEIT = "If you go on now, you will forfeit the supplies the Helpful Spirit offers."

    /** The spirit, if this run has one in the Nexus (for the claim check). */
    private fun isSpirit(run: RaidRun, st: ToaState, npc: NpcRef): Boolean =
        st.supplies.visit > 0 && st.supplies.spirit == npc && npc[SharedAttrs.RAID_RUN] == run.id.value

    // ---- the visits (TA51)

    /** The Nexus started: the spirit appears after the second and the fourth finished path (once each). */
    fun nexusEntered(run: RaidRun, members: List<PlayerRef>) {
        val st = ToaFlow.state(run) ?: return
        val sup = st.supplies
        val visit =
            when (ToaPath.entries.count { st.pathDone(it) }) {
                FIRST_VISIT_PATHS -> 1
                SECOND_VISIT_PATHS -> 2
                else -> 0
            }
        if (visit == 0 || visit <= sup.offered) return
        val any = members.firstOrNull() ?: return
        sup.offered = visit
        sup.visit = visit
        sup.rolls.clear()
        sup.claimed.clear()
        sup.spirit = Raids.spawn(any, run, Npcs.TOA_MIDRAIDLOOT_TRADER, SPIRIT_AT)
        for (p in members) resetVarbits(p)
    }

    /** A room other than the Nexus started: the spirit leaves, what was not claimed is forfeited (TA51). */
    fun nexusLeft(run: RaidRun, members: List<PlayerRef>) {
        val sup = ToaFlow.state(run)?.supplies ?: return
        if (sup.visit == 0) return
        sup.spirit?.despawn()
        sup.spirit = null
        sup.visit = 0
        sup.rolls.clear()
        for (p in members) {
            p.closeIfOpen(Interfaces.TOA_MIDRAID_LOOT)
            resetVarbits(p)
        }
    }

    private fun PlayerRef.closeIfOpen(iface: Int) {
        if (isOpen(iface)) closeModal()
    }

    fun resetVarbits(p: PlayerRef) {
        if (p.varbit(Varbits.TOA_MIDRAIDLOOT_ROLLED) != 0) p.setVarbit(Varbits.TOA_MIDRAIDLOOT_ROLLED, 0)
        if (p.varbit(Varbits.TOA_MIDRAIDLOOT_CLAIMED) != 0) p.setVarbit(Varbits.TOA_MIDRAIDLOOT_CLAIMED, 0)
    }

    /** TA51: "trying to enter the next area without collecting warns the player that the items will be forfeited". */
    fun warnForfeit(player: PlayerRef, run: RaidRun) {
        val sup = ToaFlow.state(run)?.supplies ?: return
        if (sup.visit > 0 && player.username.lowercase() !in sup.claimed) player.message(FORFEIT)
    }

    // ---- the claim (TA52, client-contract 2.8)

    /** "Claim" on the spirit: rolls this player's bundles once, shows them and opens 777. Hostile input: run, spirit, reach. */
    fun claim(player: PlayerRef, npc: NpcRef) {
        val run = ToaFlow.runOf(player) ?: return
        val st = ToaFlow.state(run) ?: return
        if (!isSpirit(run, st, npc) || run.member(player.username)?.inside != true || player.distanceTo(npc) > REACH) return
        val sup = st.supplies
        val key = player.username.lowercase()
        if (key in sup.claimed) return player.message(ALREADY_CLAIMED)
        val bundles =
            sup.rolls.getOrPut(key) {
                player.setVarbit(Varbits.TOA_MIDRAIDLOOT_ROLLED, 1)
                roll(run, st, player)
            }
        // Client-contract 2.8: 777 has no inv-transmit hook, the three invs are filled before it opens.
        for (i in 0 until BUNDLES) {
            val objs = IntArray(BUNDLE_SLOTS) { EMPTY }
            val counts = IntArray(BUNDLE_SLOTS)
            bundles[i].forEachIndexed { slot, (obj, count) ->
                objs[slot] = obj
                counts[slot] = count
            }
            player.showInv(Invs.TOA_MIDRAIDLOOT_BUNDLE1 + i, objs, counts)
        }
        player.openModal(Components.Toplevel.MAINMODAL, Interfaces.TOA_MIDRAID_LOOT)
    }

    /** IF_BUTTON1 on 777:6 / 9 / 12 = bundle [index] 0 / 1 / 2 (client-contract 2.8). */
    fun choose(player: PlayerRef, index: Int) {
        if (!player.isOpen(Interfaces.TOA_MIDRAID_LOOT)) return
        val run = ToaFlow.runOf(player) ?: return
        val st = ToaFlow.state(run) ?: return
        val sup = st.supplies
        val key = player.username.lowercase()
        if (sup.visit == 0 || run.member(player.username)?.inside != true || key in sup.claimed) return
        val items = sup.rolls[key]?.getOrNull(index) ?: return
        if (!give(player, items) { sup.claimed += key }) return
        player.setVarbit(Varbits.TOA_MIDRAIDLOOT_CLAIMED, 1)
        player.transmitInv(Invs.TOA_MIDRAIDLOOT_BAG)
        player.closeModal()
        player.message(CLAIMED_MESSAGE)
    }

    /**
     * Moves [items] into the player's bag (a new one when they hold none) in one transaction; [committed] runs with it.
     * Returns false, with a message, when the backpack has no room for the bag.
     */
    private fun give(player: PlayerRef, items: List<Pair<Int, Int>>, committed: () -> Unit): Boolean {
        val inv = player.inv(Invs.INV)
        val hasBag = inv.total(Objs.TOA_MIDRAIDLOOT_BAG) > 0L
        if (!hasBag && inv.freeSlots == 0) {
            player.message(NO_SPACE)
            return false
        }
        val ok =
            player.transaction("toa supplies claim") {
                if (!hasBag) {
                    // A bag destroyed with supplies in it leaves them behind; the new bag starts empty.
                    val bag = player.inv(Invs.TOA_MIDRAIDLOOT_BAG)
                    for (slot in 0 until bag.size) {
                        if (bag.obj(slot) != EMPTY &&
                            !removeAt(Invs.TOA_MIDRAIDLOOT_BAG, slot, bag.obj(slot), bag.count(slot))
                        ) {
                            return@transaction false
                        }
                    }
                    if (!add(Invs.INV, Objs.TOA_MIDRAIDLOOT_BAG, 1)) return@transaction false
                }
                // OPEN-TA39: a bag of 28 slots can fill up (two offerings of 18 potions); the surplus goes to the backpack.
                var room = player.inv(Invs.TOA_MIDRAIDLOOT_BAG).freeSlots
                for ((obj, count) in items) {
                    val toBag = minOf(count, room)
                    room -= toBag
                    if (toBag > 0 && !add(Invs.TOA_MIDRAIDLOOT_BAG, obj, toBag)) return@transaction false
                    if (count > toBag && !addOrDrop(Invs.INV, obj, count - toBag)) return@transaction false
                }
                onCommit(committed)
                true
            }
        if (!ok) player.message(NO_SPACE)
        return ok
    }

    // ---- the rolls (TA52, TA53, TA13)

    /**
     * This player's three bundles for the visit. Quantities are rolled uniformly inside their ranges (OPEN-TA24), the
     * rare Chaos extras with [ToaConfig.chaosRareChancePercent], then scaled by the Helpful Spirit invocation (TA13) and
     * On a Diet drops the dressings (TA15).
     */
    private fun roll(run: RaidRun, st: ToaState, player: PlayerRef): Array<List<Pair<Int, Int>>> {
        val percent = ToaInvocations.activeValue(player, st.words, ToaInvocations.CAT_HELPFUL_SPIRIT)
        val diet = ToaConsumables.ON_A_DIET in st.modifierNames
        val rng = run.rng

        fun range(r: IntRange, label: String): Int = r.first + rng.next(r.last - r.first + 1, "toa supply $label")

        fun rare(label: String): Int = if (rng.next(PERCENT, "toa supply $label") < ToaConfig.chaosRareChancePercent) 1 else 0

        fun bundle(vararg parts: Pair<ToaSupply, Int>): List<Pair<Int, Int>> =
            parts.map { (supply, q) -> supply to scale(q, percent, run) }.filter { it.second > 0 }.map { (supply, q) -> supply.full to q }

        val dressings = if (diet) 0 else range(DRESSINGS, "dressing")
        var scarabs = range(SCARABS, "scarab")
        var ambrosia = range(LIFE_AMBROSIA, "ambrosia")
        if (diet && percent == null) {
            scarabs += DIET_EXTRA_SCARABS
            ambrosia += DIET_EXTRA_AMBROSIA
        }
        val life =
            bundle(
                ToaSupply.NECTAR to LIFE_SET,
                ToaSupply.TEARS to LIFE_SET,
                ToaSupply.SILK to dressings,
                ToaSupply.SCARAB to scarabs,
                ToaSupply.AMBROSIA to ambrosia,
            )
        val chaos =
            bundle(
                ToaSupply.NECTAR to range(CHAOS_NECTAR, "chaos nectar"),
                ToaSupply.TEARS to range(CHAOS_TEARS, "chaos tears"),
                ToaSupply.SALTS to range(CHAOS_SALTS, "chaos salts"),
                ToaSupply.AMBROSIA to rare("chaos ambrosia"),
                ToaSupply.ADRENALINE to rare("chaos adrenaline"),
            )
        val power = bundle(ToaSupply.SALTS to POWER_SALTS, ToaSupply.ADRENALINE to POWER_ADRENALINE)
        return arrayOf(life, chaos, power)
    }

    /**
     * TA13 / TA53: [quantity] scaled to [percent] (66 / 33 / 10, null = unreduced) with a minimum of one of a supply that
     * is offered. OPEN-TA25: floor, plus a round-up with probability equal to the fraction
     * ([ToaConfig.supplyStochasticRounding]), which lets 2 salts at 66% stay 2 or become 1 as TI9 describes.
     */
    private fun scale(quantity: Int, percent: Int?, run: RaidRun): Int {
        if (quantity <= 0 || percent == null) return quantity
        val scaled = quantity * percent
        var n = scaled / PERCENT
        val fraction = scaled % PERCENT
        if (ToaConfig.supplyStochasticRounding && fraction > 0 && run.rng.next(PERCENT, "toa supply rounding") < fraction) n++
        return maxOf(1, n)
    }

    /** The spirit's "Claim" reach: next to him (the op is routed to him already; hostile input is bounded anyway). */
    private const val REACH = 1
    private const val EMPTY = -1
}

/** The Helpful Spirit, the interface buttons, and the member clean-up; the other supply modules register themselves here. */
public class ToaSuppliesPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart { world = this }
        onOpNpc(Npcs.TOA_MIDRAIDLOOT_TRADER, OP1) { npc -> ToaSupplies.claim(player, npc) }
        onButton(Components.Toa_Midraid_Loot.SELECT_BUTTON_1) { ToaSupplies.choose(this, 0) }
        onButton(Components.Toa_Midraid_Loot.SELECT_BUTTON_2) { ToaSupplies.choose(this, 1) }
        onButton(Components.Toa_Midraid_Loot.SELECT_BUTTON_3) { ToaSupplies.choose(this, 2) }
        ToaSupplyBag.register(this)
        ToaConsumables.register(this)
        ToaRestores.register(this) { world }
        events.on<tp.content.raids.RaidEvent.RoomStarted> { e ->
            val w = world
            if (e.run.raid.id != ToaRaid.ID || w == null) return@on
            val members = ToaRestores.present(w, e.run)
            if (e.room.id == ToaRaid.NEXUS) ToaSupplies.nexusEntered(e.run, members) else ToaSupplies.nexusLeft(e.run, members)
        }
    }

    private companion object {
        const val OP1 = 1
    }
}
