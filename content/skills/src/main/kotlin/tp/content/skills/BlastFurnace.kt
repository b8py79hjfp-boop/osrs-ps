package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.BankPinRequest
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.Teleported
import tp.plugin.TimerId
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.plugin.commands.perk
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.util.DonatorPerk

/**
 * The Blast Furnace (docs/mechanics/blast-furnace.md): ore on the conveyor belt, the melting pot, the bar dispenser, the
 * coffer and the foreman's fee, the pedals (run energy), the stove and pump, and the cogs and drive belt. The furnace's
 * contents are per player, as on the real game (varbits 941..959). This world is a themed Blast Furnace world: the dwarves
 * run the machinery while the player's coffer holds coins; with an empty coffer the player pedals the belt by hand.
 */
public class BlastFurnace : ContentPlugin {
    override fun ContentScope.register() {
        onLogin {
            syncCoffer(this)
            if (BlastFurnaceData.inside(coord)) enter(this)
        }
        onLogout { leave(this) }
        events.on<Teleported> { e -> if (BlastFurnaceData.inside(e.to)) enter(e.player) else leave(e.player) }
        // Wiki "Bank PIN": adding ore and using the coffer ask for the bank PIN (bank-pin.md BP2).
        onOpLoc(Locs.BLAST_FURNACE_CONVEYER_BELT_CLICKABLE, OP_FIRST) { player.events.publish(BankPinRequest(player) { putOre(this) }) }
        onOpLoc(Locs.BLAST_FURNACE_CHIMNEY, OP_FIRST) { checkPot(player) }
        for (dispenser in BlastFurnaceData.DISPENSERS) {
            onOpLoc(dispenser, OP_FIRST) { take(player) }
            onOpLoc(dispenser, OP_SECOND) { checkBars(player) }
            onUseOnLoc(Objs.BUCKET_WATER, dispenser) { cool(player) }
        }
        onOpLoc(Locs.BLAST_FURNACE_SINK, OP_FIRST) { fillBucket(player) }
        onOpLoc(Locs.BLAST_FURNACE_PEDALS, OP_FIRST) { pedal() }
        onOpLoc(Locs.BLAST_FURNACE_COKE, OP_FIRST) { collectCoke(player) }
        for (stove in BlastFurnaceData.STOVES) onOpLoc(stove, OP_FIRST) { refuel(player) }
        onOpLoc(Locs.BLAST_FURNACE_PUMP, OP_FIRST) { pump() }
        onOpLoc(Locs.BLAST_FURNACE_CONVEYER_COGS2_BROKEN, OP_FIRST) { repair(player, COGS) }
        onOpLoc(Locs.BLAST_FURNACE_CONVEYER_COGS_BROKEN, OP_FIRST) { repair(player, BELT) }
        onOpLoc(Locs.BLAST_FURNACE_AUTOMATA_COFFER_EMPTY, OP_FIRST) {
            player.events.publish(BankPinRequest(player) { weakQueue(0) { coffer(player) } })
        }
        onOpLoc(Locs.BLAST_FURNACE_AUTOMATA_COFFER_FULL, OP_FIRST) {
            player.events.publish(BankPinRequest(player) { weakQueue(0) { coffer(player) } })
        }
        onOpNpc(Npcs.BLAST_FURNACE_INSTRUCTOR, OP_FIRST) { npc -> foreman(npc) }
        onOpNpc(Npcs.BLAST_FURNACE_INSTRUCTOR, OP_THIRD) { payForeman(player) }
    }

    // ---- state (per player, saved as plain counts)

    private fun count(p: PlayerRef, key: String): Int = (p.saved(key) ?: 0L).toInt()

    private fun set(p: PlayerRef, key: String, value: Int) = p.setSaved(key, value.toLong().takeIf { it > 0 })

    private fun belt(i: Int) = "bf.belt.$i"

    private fun pot(i: Int) = "bf.pot.$i"

    private fun bars(bar: Int) = "bf.bar.$bar"

    private fun cofferCoins(p: PlayerRef): Int = count(p, COFFER_KEY)

    private fun syncCoffer(p: PlayerRef) {
        val coins = cofferCoins(p)
        p.setVarbit(Varbits.BLAST_FURNACE_COFFER, coins)
        p.setVarbit(Varbits.BLAST_FURNACE_COINSINCOFFER, if (coins > 0) 1 else 0)
    }

    private fun totalBars(p: PlayerRef): Int = BlastFurnaceData.BARS.sumOf { count(p, bars(it.bar)) }

    private fun syncDispenser(p: PlayerRef) {
        val hot = p.tick < (p[HOT_UNTIL] ?: 0L)
        p.setVarbit(
            Varbits.BLAST_FURNACE_BARS_HOT,
            if (totalBars(p) == 0) {
                0
            } else if (hot) {
                2
            } else {
                3
            },
        )
    }

    // ---- inside: the per-tick drive (fee, belt delivery)

    private fun enter(p: PlayerRef) {
        if (p.hasTimer(TICK_TIMER)) return
        syncCoffer(p)
        syncDispenser(p)
        p.timer(TICK_TIMER, 1, soft = true) { tick() }
    }

    private fun leave(p: PlayerRef) {
        if (p.hasTimer(TICK_TIMER)) p.stopTimer(TICK_TIMER)
    }

    private fun PlayerRef.tick() {
        if (!BlastFurnaceData.inside(coord)) return leave(this)
        val coins = cofferCoins(this)
        if (coins > 0) {
            val left = (coins - BlastFurnaceData.COFFER_COINS_PER_TICK).coerceAtLeast(0)
            set(this, COFFER_KEY, left)
            if (left == 0) {
                message("The dwarves stop work: the coffer is empty.")
                syncCoffer(this)
            } else if (tick % COFFER_SYNC_TICKS == 0L) {
                syncCoffer(this)
            }
        }
        val at = this[DELIVER_AT]
        if (at != null && tick >= at && powered(this)) deliver(this)
        if (totalBars(this) > 0 && tick == (this[HOT_UNTIL] ?: 0L)) syncDispenser(this)
    }

    /** The belt runs when the dwarves are paid, or while a player pedals it and nothing is broken (Wiki "Conveyor belt"). */
    private fun powered(p: PlayerRef): Boolean {
        if (cofferCoins(p) > 0) return true
        val broken = p.worldAttr(BROKEN)
        if (broken != null && (broken[COGS] || broken[BELT])) return false
        return p.tick <= (p[PEDAL_UNTIL] ?: -1L)
    }

    // ---- the foreman

    private fun hasAccess(p: PlayerRef): Boolean =
        p.baseLevel(Stats.SMITHING) >= BlastFurnaceData.FREE_SMITHING_LEVEL || (p.saved(PAID_KEY) ?: 0L) > p.epochMinutes

    private fun noAccess(p: PlayerRef) = p.message("You must ask the foreman's permission before using the blast furnace.")

    private suspend fun PlayerScript.foreman(npc: NpcRef) {
        val p = player
        if (p.baseLevel(Stats.SMITHING) >= BlastFurnaceData.FREE_SMITHING_LEVEL) {
            return chatNpc(npc, "You have the Smithing level to use the Blast Furnace for free. Mind the machinery.")
        }
        chatNpc(
            npc,
            "You need 60 Smithing to use the Blast Furnace, or ${BlastFurnaceData.FOREMAN_FEE} coins for " +
                "${BlastFurnaceData.FOREMAN_MINUTES} minutes.",
        )
        if (choice("Pay the fee?", "Pay ${BlastFurnaceData.FOREMAN_FEE} coins", "No thanks") == 1) payForeman(p)
    }

    private fun payForeman(p: PlayerRef) {
        if (p.baseLevel(Stats.SMITHING) >= BlastFurnaceData.FREE_SMITHING_LEVEL) return p.message("You can use the blast furnace for free.")
        if (!p.transaction("blast furnace fee") { remove(Invs.INV, Objs.COINS, BlastFurnaceData.FOREMAN_FEE) }) {
            return p.message("You need ${BlastFurnaceData.FOREMAN_FEE} coins to pay the foreman.")
        }
        p.setSaved(PAID_KEY, p.epochMinutes + BlastFurnaceData.FOREMAN_MINUTES)
        p.message("You pay the foreman and may use the blast furnace for ${BlastFurnaceData.FOREMAN_MINUTES} minutes.")
    }

    // ---- the coffer

    private suspend fun PlayerScript.coffer(p: PlayerRef) {
        enter(p)
        when (choice("Coffer: ${cofferCoins(p)} coins", "Deposit coins", "Withdraw coins")) {
            1 -> p.countDialog("Deposit how many coins?") { n -> deposit(this, n) }
            2 -> p.countDialog("Withdraw how many coins?") { n -> withdraw(this, n) }
            else -> Unit
        }
    }

    private fun deposit(p: PlayerRef, requested: Int) {
        val n = minOf(requested, p.inv(Invs.INV).total(Objs.COINS).toInt(), BlastFurnaceData.COFFER_MAX - cofferCoins(p))
        if (n <= 0 || !p.transaction("blast furnace coffer") { remove(Invs.INV, Objs.COINS, n) }) return
        set(p, COFFER_KEY, cofferCoins(p) + n)
        syncCoffer(p)
    }

    private fun withdraw(p: PlayerRef, requested: Int) {
        val n = minOf(requested, cofferCoins(p))
        if (n <= 0 || !p.transaction("blast furnace coffer") { add(Invs.INV, Objs.COINS, n) }) return
        set(p, COFFER_KEY, cofferCoins(p) - n)
        syncCoffer(p)
    }

    // ---- the belt and the melting pot

    private fun putOre(p: PlayerRef) {
        enter(p)
        if (!hasAccess(p)) return noAccess(p)
        val moves = planMoves(p)
        if (moves.all { it == 0 }) return p.message("You have no ore to put on the conveyor belt.")
        val inv = p.inv(Invs.INV)
        val bagCoal = minOf((moves[COAL_INDEX] - inv.total(Objs.COAL).toInt()).coerceAtLeast(0), Bags.coal(p))
        val ok =
            p.transaction("blast furnace belt") {
                BlastFurnaceData.ORES.withIndex().all { (i, ore) ->
                    val fromInv = if (i == COAL_INDEX) moves[i] - bagCoal else moves[i]
                    fromInv <= 0 || remove(Invs.INV, ore, fromInv)
                }
            }
        if (!ok) return
        if (bagCoal > 0) set(p, Bags.COAL_KEY, Bags.coal(p) - bagCoal)
        for (i in moves.indices) if (moves[i] > 0) set(p, belt(i), count(p, belt(i)) + moves[i])
        p.anim(Seqs.HUMAN_FURNACE)
        if (p[DELIVER_AT] == null) p[DELIVER_AT] = p.tick + BELT_TICKS
    }

    private fun isPrimary(i: Int): Boolean = i != COAL_INDEX && i != TIN_INDEX

    /** How much of each ore (in [BlastFurnaceData.ORES] order) goes on the belt: what the player holds, within the limits (BF3). */
    private fun planMoves(p: PlayerRef): IntArray {
        val inv = p.inv(Invs.INV)
        val level = p.level(Stats.SMITHING)
        val inSystem =
            BlastFurnaceData.ORES.indices
                .filter(::isPrimary)
                .sumOf { count(p, belt(it)) + count(p, pot(it)) }
        var primaryRoom = BlastFurnaceData.PRIMARY_MAX - inSystem - totalBars(p)
        val moves = IntArray(BlastFurnaceData.ORES.size)
        for ((i, ore) in BlastFurnaceData.ORES.withIndex()) {
            val have = inv.total(ore).toInt() + if (i == COAL_INDEX) Bags.coal(p) else 0
            if (have == 0) continue
            if (i != COAL_INDEX && level < BlastFurnaceData.oreLevel(ore)) {
                p.message("You need a Smithing level of ${BlastFurnaceData.oreLevel(ore)} to smelt ${p.objName(ore).lowercase()}.")
                continue
            }
            val room = if (isPrimary(i)) primaryRoom else BlastFurnaceData.COAL_MAX - count(p, belt(i)) - count(p, pot(i))
            moves[i] = minOf(have, room.coerceAtLeast(0))
            if (!isPrimary(i)) continue
            primaryRoom -= moves[i]
            if (moves[i] < have) p.message("You should collect your bars before making any more.")
        }
        return moves
    }

    /** The belt reaches the pot: the ores join what is there and the pot smelts at once. */
    private fun deliver(p: PlayerRef) {
        p[DELIVER_AT] = null
        for (i in BlastFurnaceData.ORES.indices) {
            val n = count(p, belt(i))
            if (n == 0) continue
            set(p, belt(i), 0)
            set(p, pot(i), count(p, pot(i)) + n)
        }
        melt(p)
    }

    /**
     * Smelts what the pot holds, in the order of [BlastFurnaceData.BARS]: iron ore with coal in the pot becomes steel, the rest
     * of it iron (Wiki "Blast Furnace" notes); ores that need more coal than there is wait in the pot.
     */
    private fun melt(p: PlayerRef) {
        val level = p.level(Stats.SMITHING)
        var made = 0
        for (r in BlastFurnaceData.BARS) {
            if (level < r.level) continue
            val primary = BlastFurnaceData.ORES.indexOf(r.primary)
            val partner = if (r.partner >= 0) BlastFurnaceData.ORES.indexOf(r.partner) else -1
            var n = count(p, pot(primary))
            if (partner >= 0) n = minOf(n, count(p, pot(partner)))
            if (r.coal > 0) n = minOf(n, count(p, pot(COAL_INDEX)) / r.coal)
            if (n <= 0) continue
            set(p, pot(primary), count(p, pot(primary)) - n)
            if (partner >= 0) set(p, pot(partner), count(p, pot(partner)) - n)
            if (r.coal > 0) set(p, pot(COAL_INDEX), count(p, pot(COAL_INDEX)) - n * r.coal)
            set(p, bars(r.bar), count(p, bars(r.bar)) + n)
            made += n
        }
        if (made == 0) return
        p[HOT_UNTIL] = p.tick + BARS_COOL_TICKS
        p[COOLED] = false
        syncDispenser(p)
    }

    private fun checkPot(p: PlayerRef) {
        val parts =
            BlastFurnaceData.ORES.indices.mapNotNull { i ->
                val n = count(p, pot(i)) + count(p, belt(i))
                if (n > 0) "$n ${p.objName(BlastFurnaceData.ORES[i]).lowercase()}" else null
            }
        p.message(if (parts.isEmpty()) "The melting pot is empty." else "The melting pot holds: ${parts.joinToString(", ")}.")
    }

    // ---- the dispenser

    private fun checkBars(p: PlayerRef) {
        val parts =
            BlastFurnaceData.BARS.mapNotNull { r ->
                count(p, bars(r.bar)).takeIf { it > 0 }?.let { "$it ${p.objName(r.bar).lowercase()}" }
            }
        p.message(if (parts.isEmpty()) "The dispenser holds no bars." else "The dispenser holds: ${parts.joinToString(", ")}.")
    }

    private fun worn(p: PlayerRef, slot: Int): Int = p.inv(Invs.WORN).obj(slot)

    private fun cool(p: PlayerRef) {
        if (totalBars(p) == 0) return p.message("There are no bars to cool.")
        val inv = p.inv(Invs.INV)
        val slot =
            (0 until inv.size).firstOrNull { inv.obj(it) == Objs.BUCKET_WATER }
                ?: return p.message("You need a bucket of water to cool the bars.")
        if (!p.transaction("blast furnace bucket") { replaceAt(Invs.INV, slot, Objs.BUCKET_WATER, Objs.BUCKET_EMPTY) }) return
        p[HOT_UNTIL] = 0L
        p[COOLED] = true
        syncDispenser(p)
        p.message("You pour the water over the bars to cool them.")
    }

    private fun fillBucket(p: PlayerRef) {
        val inv = p.inv(Invs.INV)
        val slot =
            (0 until inv.size).firstOrNull { inv.obj(it) == Objs.BUCKET_EMPTY } ?: return p.message("You need an empty bucket to fill.")
        p.transaction("blast furnace sink") { replaceAt(Invs.INV, slot, Objs.BUCKET_EMPTY, Objs.BUCKET_WATER) }
    }

    /** Take: the bars leave the dispenser once cool (or with ice gloves on) and the Smithing xp comes with them (Wiki "Blast Furnace"). */
    private fun take(p: PlayerRef) {
        enter(p)
        if (!hasAccess(p)) return noAccess(p)
        if (totalBars(p) == 0) return p.message("There are no bars in the dispenser.")
        val hot = p.tick < (p[HOT_UNTIL] ?: 0L)
        val gloves = worn(p, GLOVES_SLOT).let { it == Objs.ICE_GLOVES || it == Objs.SMITHING_UNIFORM_GLOVES_ICE }
        if (hot && !gloves) return p.message("The bars are still molten. Cool them with a bucket of water, or wear ice gloves.")
        val gauntlets = worn(p, GLOVES_SLOT) == Objs.GAUNTLETS_OF_GOLDSMITHING
        var xp = 0.0
        val noteBars = p.perk(DonatorPerk.RECEIVE_NOTED_BARS_WHEN_SMELTING_IN_THE_BLAST_FURNACE) == 1
        for (r in BlastFurnaceData.BARS) {
            // D-093 donator perk (perks.tsv row "Receive noted bars when smelting in the Blast Furnace"): noted bars stack in one slot.
            val note = if (noteBars) p.noted(r.bar) else -1
            val n = if (note >= 0) count(p, bars(r.bar)) else minOf(count(p, bars(r.bar)), p.inv(Invs.INV).freeSlots)
            if (n <= 0 || !p.transaction("blast furnace bars") { add(Invs.INV, if (note >= 0) note else r.bar, n) }) continue
            set(p, bars(r.bar), count(p, bars(r.bar)) - n)
            xp += n * if (r.bar == Objs.GOLD_BAR && gauntlets) BlastFurnaceData.GOLDSMITH_GOLD_XP else r.xp
        }
        if (xp > 0) p.addXp(Stats.SMITHING, xp)
        syncDispenser(p)
    }

    // ---- pedals, stove, pump, cogs

    private suspend fun PlayerScript.pedal() {
        val p = player
        enter(p)
        if (!hasAccess(p)) return noAccess(p)
        if (p.baseLevel(Stats.AGILITY) < BlastFurnaceData.PEDAL_AGILITY_LEVEL) {
            return p.message("You need an Agility level of ${BlastFurnaceData.PEDAL_AGILITY_LEVEL} to pedal the conveyor belt.")
        }
        val broken = p.worldAttr(BROKEN)
        if (broken != null && (broken[COGS] || broken[BELT])) return p.message("The conveyor belt is broken; it needs repairing.")
        if (p.runEnergy < BlastFurnaceData.PEDAL_ENERGY) return p.message("You are too tired to pedal.")
        keepInteraction()
        p.runEnergy = p.runEnergy - BlastFurnaceData.PEDAL_ENERGY
        p.addXp(Stats.AGILITY, 1.0)
        p[PEDAL_UNTIL] = p.tick + 1
        if (p.random(BREAK_ONE_IN) == 0) breakPart(p)
    }

    /** OPEN-BF4: the Wiki gives no break chance for the cogs and the drive belt; one in [BREAK_ONE_IN] per pedalled tick is assumed. */
    private fun breakPart(p: PlayerRef) {
        val broken = p.worldAttr(BROKEN) ?: BooleanArray(2).also { p.setWorldAttr(BROKEN, it) }
        val part = p.random(2)
        broken[part] = true
        if (part == COGS) {
            p.addLoc(Locs.BLAST_FURNACE_CONVEYER_COGS2_BROKEN, BlastFurnaceData.COGS_AT, BlastFurnaceData.PART_SHAPE, 0)
            p.message("The cogs break!")
        } else {
            p.addLoc(Locs.BLAST_FURNACE_CONVEYER_COGS_BROKEN, BlastFurnaceData.BELT_AT, BlastFurnaceData.PART_SHAPE, 0)
            p.message("The drive belt breaks!")
        }
    }

    private fun repair(p: PlayerRef, part: Int) {
        val inv = p.inv(Invs.INV)
        if (inv.total(Objs.HAMMER) == 0L && inv.total(Objs.IMCANDO_HAMMER) == 0L) return p.message("You need a hammer to repair this.")
        val broken = p.worldAttr(BROKEN) ?: return
        if (!broken[part]) return
        broken[part] = false
        p.revertLoc(if (part == COGS) BlastFurnaceData.COGS_AT else BlastFurnaceData.BELT_AT, BlastFurnaceData.PART_SHAPE)
        p.addXp(Stats.CRAFTING, BlastFurnaceData.REPAIR_XP)
    }

    private fun collectCoke(p: PlayerRef) {
        enter(p)
        if (p.inv(Invs.INV).total(Objs.SPADE) == 0L) return p.message("You need a spade to collect coke.")
        if (p.inv(Invs.INV).total(Objs.BLAST_FURNACE_COKE_SPADE) > 0) return p.message("You already have a spadeful of coke.")
        p.transaction("blast furnace coke") { add(Invs.INV, Objs.BLAST_FURNACE_COKE_SPADE, 1) }
    }

    private fun refuel(p: PlayerRef) {
        enter(p)
        val stove = p.worldAttr(STOVE) ?: IntArray(1).also { p.setWorldAttr(STOVE, it) }
        if (stove[0] >= BlastFurnaceData.STOVE_FULL) return p.message("The stove is full.")
        if (!p.transaction("blast furnace stove") { remove(Invs.INV, Objs.BLAST_FURNACE_COKE_SPADE, 1) }) {
            return p.message("You need a spadeful of coke to refuel the stove.")
        }
        stove[0]++
        p.addXp(Stats.FIREMAKING, BlastFurnaceData.STOVE_XP)
    }

    /** Pump: 2 Strength xp per tick while the stove holds coke (Wiki "Pump (Blast Furnace)"); the stove's burn rate is OPEN-BF6. */
    private suspend fun PlayerScript.pump() {
        val p = player
        enter(p)
        val stove = p.worldAttr(STOVE)
        if (stove == null || stove[0] <= 0) return p.message("The stove needs coke before the pump does anything.")
        if (p.baseLevel(Stats.STRENGTH) < BlastFurnaceData.PEDAL_AGILITY_LEVEL) {
            return p.message("You need a Strength level of ${BlastFurnaceData.PEDAL_AGILITY_LEVEL} to operate the pump.")
        }
        keepInteraction()
        p.addXp(Stats.STRENGTH, PUMP_XP)
    }

    private companion object {
        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val OP_THIRD = 3
        val TICK_TIMER = TimerId("blast_furnace")
        const val COFFER_KEY = "blastfurnace.coffer"
        const val PAID_KEY = "blastfurnace.paid"

        val DELIVER_AT = AttrKey<Long>("bf.deliver")
        val HOT_UNTIL = AttrKey<Long>("bf.hot")
        val COOLED = AttrKey<Boolean>("bf.cooled")
        val PEDAL_UNTIL = AttrKey<Long>("bf.pedal")
        val BROKEN = AttrKey<BooleanArray>("bf.broken")
        val STOVE = AttrKey<IntArray>("bf.stove")

        const val COGS = 0
        const val BELT = 1

        /** Positions in [BlastFurnaceData.ORES]. */
        const val COAL_INDEX = 0
        const val TIN_INDEX = 2

        /** Worn-inventory slot of the gloves (cache wear position 9). */
        const val GLOVES_SLOT = 9

        /** Wiki "Pump (Blast Furnace)": 2 Strength xp per tick. */
        const val PUMP_XP = 2.0

        /** The coffer varbits are refreshed every this many ticks while it drains (avoids a packet per tick). */
        const val COFFER_SYNC_TICKS = 10L

        /** OPEN-BF1: no source for the belt's travel time to the pot; 4 ticks is assumed. */
        const val BELT_TICKS = 4

        /** OPEN-BF3: no source for how long fresh bars stay hot; 50 ticks (30 s) is assumed. */
        const val BARS_COOL_TICKS = 50

        /** OPEN-BF4: assumed break chance per pedalled tick. */
        const val BREAK_ONE_IN = 400
    }
}
