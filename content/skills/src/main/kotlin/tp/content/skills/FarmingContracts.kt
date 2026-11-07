package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Farming contracts and seed packs (docs/mechanics/farming.md FC1-FC6; Wiki "Farming contract", "Seed pack"): Guildmaster Jane gives
 * a crop to grow in the Farming Guild, equally likely among the tasks the player's Farming level has unlocked (boosts count) and never
 * the one before; the contract is done when the health of such a crop in a guild patch is checked, or, for a crop with no health
 * check, when its patch is fully harvested. The reward is a seed pack of the task's tier, opened from the backpack for seeds rolled
 * by the tier's table.
 *
 * Saved: the task index + 1 (0 none), its state (1 active, 2 done), the completed count, the previous task's crop and a queue of the
 * tiers of the packs handed out (a pack item carries no data of its own, OPEN-FA11).
 */
internal class FarmingContracts(private val patches: FarmingPatches, private val chains: SaplingTable) {
    private val tasks: List<ContractTask> = ContractTable.load()
    private val packs: SeedPackTable = SeedPackTable.load()
    private val crops get() = patches.crops

    /** The crop a task's seed grows into (a tree seed through its sapling). */
    private fun cropOf(seed: Int): Crop? = crops.bySeed[seed] ?: chains.bySeed[seed]?.let { crops.bySeed[it.sapling] }

    fun ContentScope.registerContracts() {
        for (jane in intArrayOf(Npcs.FARMING_GUILD_MASTER_1OP, Npcs.FARMING_GUILD_MASTER_2OP)) {
            onOpNpc(jane, OP_TALK) { talk(it) }
        }
        onOpNpc(Npcs.FARMING_GUILD_MASTER_2OP, OP_CONTRACT) { contractOption(it) }
        onItemOp(Objs.SEEDBOX, "Take-all") { open(this, it) }
        onItemOp(Objs.SEEDBOX, "Take") { open(this, it) }
    }

    // ---- progress

    /** A crop's health was checked in [patch]: completes a contract for it (Wiki: "For types of crops with health checking"). */
    fun onChecked(p: PlayerRef, patch: Patch, crop: Crop) {
        complete(p, patch, crop)
    }

    /** A crop in [patch] was fully harvested: completes a contract for a crop with no health check. */
    fun onHarvested(p: PlayerRef, patch: Patch, crop: Crop) {
        if (!crop.type.check) complete(p, patch, crop)
    }

    private fun complete(p: PlayerRef, patch: Patch, crop: Crop) {
        if (!patch.guild || p.saved(STATE) != STATE_ACTIVE) return
        val task = tasks.getOrNull((p.saved(TASK) ?: 0L).toInt() - 1) ?: return
        if (cropOf(task.seed)?.key != crop.key) return
        p.setSaved(STATE, STATE_DONE)
        p.message("You have completed your Farming Guild contract. Talk to Guildmaster Jane for your reward.")
    }

    // ---- Jane

    private suspend fun PlayerScript.talk(jane: NpcRef) {
        val state = player.saved(STATE)
        if (state == STATE_DONE) return reward(jane)
        when (
            choice(
                "Select an Option",
                "Do you have a contract for me?",
                "Do you have anything easier?",
                "How many contracts have I done?",
                "I have a spirit seed to exchange.",
            )
        ) {
            1 -> contract(jane)
            2 -> easier(jane)
            3 -> chatNpc(jane, "You have completed ${player.saved(COUNT) ?: 0} contracts.")
            4 -> exchangeSpirit(jane)
            else -> Unit
        }
    }

    private suspend fun PlayerScript.contractOption(jane: NpcRef) {
        if (player.saved(STATE) == STATE_DONE) reward(jane) else contract(jane)
    }

    private suspend fun PlayerScript.contract(jane: NpcRef) {
        val p = player
        if (p.saved(STATE) == STATE_ACTIVE) return remind(jane)
        val level = p.level(Stats.FARMING)
        if (level < MIN_LEVEL) return chatNpc(jane, "You need a Farming level of $MIN_LEVEL for me to give you a contract.")
        val last = p.saved(LAST)?.toInt() ?: -1
        val eligible = tasks.indices.filter { tasks[it].unlockLevel <= level && tasks[it].seed != last }
        if (eligible.isEmpty()) return chatNpc(jane, "I have nothing for you right now.")
        assign(p, eligible[p.random(eligible.size)])
        remind(jane)
    }

    private suspend fun PlayerScript.remind(jane: NpcRef) {
        val task = tasks[(player.saved(TASK) ?: 1L).toInt() - 1]
        val crop = cropOf(task.seed)
        chatNpc(
            jane,
            "Please grow ${crop?.name ?: "that"} in the guild, on a ${task.patch.lowercase().replace(
                '_',
                ' ',
            )} patch, and come back to me.",
        )
    }

    /** Wiki "Farming contract#Cancelling": "Do you have anything easier?" swaps the contract for a lower difficulty; easy ones stay. */
    private suspend fun PlayerScript.easier(jane: NpcRef) {
        val p = player
        if (p.saved(STATE) != STATE_ACTIVE) return chatNpc(jane, "You haven't got a contract to swap.")
        val current = tasks[(p.saved(TASK) ?: 1L).toInt() - 1]
        val lower = DIFFICULTIES.indexOf(current.difficulty) - 1
        if (lower < 0) return chatNpc(jane, "That is as easy as they get.")
        val level = p.level(Stats.FARMING)
        val eligible = tasks.indices.filter { DIFFICULTIES.indexOf(tasks[it].difficulty) <= lower && tasks[it].unlockLevel <= level }
        if (eligible.isEmpty()) return chatNpc(jane, "I have nothing easier for you.")
        assign(p, eligible[p.random(eligible.size)])
        remind(jane)
    }

    private fun assign(p: PlayerRef, index: Int) {
        p.setSaved(TASK, (index + 1).toLong())
        p.setSaved(STATE, STATE_ACTIVE)
        p.setSaved(LAST, tasks[index].seed.toLong())
    }

    private suspend fun PlayerScript.reward(jane: NpcRef) {
        val p = player
        val task = tasks.getOrNull((p.saved(TASK) ?: 0L).toInt() - 1) ?: return
        if (!p.transaction("farming-contract-reward") { addOrDrop(Invs.INV, Objs.SEEDBOX, 1) }) return
        pushPack(p, task.tier)
        p.setSaved(STATE, null)
        p.setSaved(COUNT, (p.saved(COUNT) ?: 0L) + 1)
        chatNpc(jane, "Well done. Here is a seed pack for your trouble.")
    }

    /** Wiki "Seed pack": Jane exchanges a spirit seed, seedling or sapling for a tier 5 seed pack. */
    private suspend fun PlayerScript.exchangeSpirit(jane: NpcRef) {
        val p = player
        val chain = chains.chains.firstOrNull { it.key == SPIRIT } ?: return
        val item = intArrayOf(chain.seed, chain.seedling, chain.wet, chain.sapling).firstOrNull { p.inv(Invs.INV).total(it) > 0 }
        if (item == null) {
            chatPlayer("I have a spirit seed to exchange.")
            return chatNpc(jane, "You don't have anything I'd take for a seed pack.")
        }
        if (!p.transaction("farming-spirit-exchange") { remove(Invs.INV, item, 1) && addOrDrop(Invs.INV, Objs.SEEDBOX, 1) }) return
        pushPack(p, SPIRIT_PACK_TIER)
        chatNpc(jane, "Here is a seed pack in exchange.")
    }

    // ---- seed packs

    private fun pushPack(p: PlayerRef, tier: Int) {
        var queue = p.saved(QUEUE) ?: 0L
        var place = 1L
        while (queue / place > 0 && place < QUEUE_LIMIT) place *= TIER_BASE
        queue += tier * place
        p.setSaved(QUEUE, queue)
    }

    /** The tier of the oldest pack handed out, or [UNTRACKED_TIER] if the queue is empty (a pack from elsewhere, OPEN-FA11). */
    private fun popPack(p: PlayerRef): Int {
        val queue = p.saved(QUEUE) ?: 0L
        if (queue == 0L) return UNTRACKED_TIER
        p.setSaved(QUEUE, queue / TIER_BASE)
        return (queue % TIER_BASE).toInt()
    }

    /** "Take" / "Take-all": the pack's seeds, rolled now, go to the backpack (the rest to the floor; the take-one menu is OPEN-FA11). */
    private fun open(p: PlayerRef, click: ButtonClick) {
        if (p.inv(Invs.INV).obj(click.slot) != Objs.SEEDBOX) return
        val tier = popPack(p)
        val rolls = packs.rolls[tier] ?: return
        val seeds = rollSeeds(p, rolls)
        p.transaction("farming-seed-pack") {
            removeAt(Invs.INV, click.slot, Objs.SEEDBOX, 1) && seeds.all { (seed, count) -> addOrDrop(Invs.INV, seed, count) }
        }
    }

    /**
     * Wiki "Seed pack#Rewards": the numbers of medium and high rolls are random within the tier's ranges and the rest of its rolls are
     * low ones; each roll takes a seed from that table by weight and a quantity in its range.
     */
    private fun rollSeeds(p: PlayerRef, r: IntArray): Map<Int, Int> {
        val medium = r[MED_MIN] + p.random(r[MED_MAX] - r[MED_MIN] + 1)
        val high =
            if (r[HIGH_ONE_IN] > 1) {
                if (p.random(r[HIGH_ONE_IN]) == 0) r[HIGH_MAX] else r[HIGH_MIN]
            } else {
                r[HIGH_MIN] + p.random(r[HIGH_MAX] - r[HIGH_MIN] + 1)
            }
        val low = r[TOTAL] - medium - high
        val result = LinkedHashMap<Int, Int>()
        repeat(low) { roll(p, "LOW", result) }
        repeat(medium) { roll(p, "MEDIUM", result) }
        repeat(high) { roll(p, "HIGH", result) }
        return result
    }

    private fun roll(p: PlayerRef, table: String, into: MutableMap<Int, Int>) {
        val rows = packs.drops[table] ?: return
        var pick = p.random(rows.sumOf { it[DROP_WEIGHT] })
        for (row in rows) {
            pick -= row[DROP_WEIGHT]
            if (pick < 0) {
                into.merge(row[DROP_SEED], row[DROP_MIN] + p.random(row[DROP_MAX] - row[DROP_MIN] + 1), Int::plus)
                return
            }
        }
    }

    private companion object {
        /** Cache npc ops of Guildmaster Jane: "Talk-to" and (npc 8587) "Contract". */
        const val OP_TALK = 1
        const val OP_CONTRACT = 3

        /** Wiki "Farming contract": the easy tasks need the guild's entry level, 45 Farming. */
        const val MIN_LEVEL = 45

        val DIFFICULTIES: List<String> = listOf("EASY", "MEDIUM", "HARD")

        /** Wiki "Seed pack": a spirit seed, seedling or sapling is worth a tier 5 pack. */
        const val SPIRIT_PACK_TIER = 5
        const val SPIRIT = "SPIRIT_TREE"

        /** OPEN-FA11: a pack whose tier is not recorded counts as a tier 3 pack (what Farmer Gricoller sells). */
        const val UNTRACKED_TIER = 3

        const val TASK = "farming.contract"
        const val STATE = "farming.contract.state"
        const val COUNT = "farming.contract.count"
        const val LAST = "farming.contract.last"
        const val QUEUE = "farming.contract.packs"
        const val STATE_ACTIVE = 1L
        const val STATE_DONE = 2L

        /** Pack tiers are base-8 digits of one Long (tiers 1..5), the oldest in the lowest digit. */
        const val TIER_BASE = 8L
        const val QUEUE_LIMIT = 1L shl 60

        // columns of a farming-seedpacks.tsv ROLLS row (after the tier) and DROP row (after the table)
        const val MED_MIN = 2
        const val MED_MAX = 3
        const val HIGH_MIN = 4
        const val HIGH_MAX = 5
        const val HIGH_ONE_IN = 6
        const val TOTAL = 7
        const val DROP_SEED = 0
        const val DROP_MIN = 1
        const val DROP_MAX = 2
        const val DROP_WEIGHT = 3
    }
}
