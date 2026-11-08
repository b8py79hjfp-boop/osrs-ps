package tp.content.raids.toa

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.InvView
import tp.plugin.OpenBankRequest
import tp.plugin.OpenCollectionBox
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Objs
import tp.symbols.Varps

/**
 * The lobby's furniture beyond the raid flow (lobby-rewards.md TL4-TL9, TR16, TR29; toa.md TA45, TA68): the Bank Camel,
 * the Sack, the crate, Icthlarin's shroud chest, the Item Retrieval chest for the reclaim inventory
 * `Invs.TOA_RECLAIM`, the Rewards Niche for unclaimed loot, and the Cache of runes. The pickaxe cavity shares its storage
 * with Het's statues and belongs to the Het puzzle (not here).
 */
internal object ToaLobbyNpcs {
    // ---- Sack (TL5)

    /** TL5: the find chance is 0.4% at Thieving 1 and 3.9% at 99, in hundred-thousandths; linear between (OPEN-TL5). */
    private const val SACK_AT_1 = 400
    private const val SACK_AT_99 = 3_900
    private const val SACK_ROLL = 100_000
    private const val THIEVING_MAX_STEPS = 98

    private const val SACK_FAIL = "You go to search the sack, but the bank camel glares at you menacingly and spits in your direction."

    /** TL5: the camel's line when a camulet is worn. */
    private const val SACK_FAIL_CAMULET =
        "Hey, get your hands off my food! Unless you'd like me to start eating the contents of your bank instead!"

    /** OPEN-TL5: the success line is not captured. */
    private const val SACK_FOUND = "You find something in the sack."

    fun searchSack(p: PlayerRef) {
        val level = p.level(Stats.THIEVING).coerceIn(1, THIEVING_MAX_STEPS + 1)
        val chance = SACK_AT_1 + (level - 1) * (SACK_AT_99 - SACK_AT_1) / THIEVING_MAX_STEPS
        // "Always fails with a full inventory" (Mod Ash, 31 Aug 2022).
        if (p.inv(Invs.INV).freeSlots == 0 || p.random(SACK_ROLL) >= chance) {
            p.message(if (p.inv(Invs.WORN).total(Objs.CAMULET) > 0) SACK_FAIL_CAMULET else SACK_FAIL)
            return
        }
        // A needle or a desert-grown grain, equal chances.
        val item = if (p.random(2) == 0) Objs.NEEDLE else Objs.TOA_GRAIN
        if (p.transaction("toa sack") { add(Invs.INV, item) }) p.message(SACK_FOUND)
    }

    // ---- Icthlarin's shroud (TA68)

    /** TA68: completions for tiers 1-5. */
    private val SHROUD_AT = intArrayOf(100, 500, 1_000, 1_500, 2_000)
    private val SHROUDS =
        intArrayOf(
            Objs.ICTHLARINS_SHROUD_1,
            Objs.ICTHLARINS_SHROUD_2,
            Objs.ICTHLARINS_SHROUD_3,
            Objs.ICTHLARINS_SHROUD_4,
            Objs.ICTHLARINS_SHROUD_5,
        )

    // OPEN-TA34: the chest's lines are not captured.
    private const val SHROUD_NOT_ENOUGH = "You have not completed enough raids to earn a shroud."
    private const val SHROUD_HAVE = "You already have the shroud you have earned."
    private const val SHROUD_GIVEN = "You take the shroud from the chest."
    private const val SHROUD_NO_SPACE = "You don't have enough inventory space."

    fun searchCapeChest(p: PlayerRef) {
        var kc = p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT) + p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_EXPERT)
        if (ToaConfig.shroudCountsEntry) kc += p.varp(Varps.TOTAL_COMPLETED_TOMBSOFAMASCUT_ENTRY)
        val tier = SHROUD_AT.indexOfLast { kc >= it }
        if (tier < 0) {
            p.message(SHROUD_NOT_ENOUGH)
            return
        }
        // Tier 5 also gives the hood; an owned shroud of this tier or a higher one is enough (OPEN-TA34: lower ones stay).
        val wanted = ArrayList<Int>(2)
        if (SHROUDS.drop(tier).none { owns(p, it) }) wanted += SHROUDS[tier]
        if (tier == SHROUDS.lastIndex && !owns(p, Objs.ICTHLARINS_HOOD)) wanted += Objs.ICTHLARINS_HOOD
        if (wanted.isEmpty()) {
            p.message(SHROUD_HAVE)
            return
        }
        val ok = p.transaction("toa shroud") { wanted.all { add(Invs.INV, it) } }
        p.message(if (ok) SHROUD_GIVEN else SHROUD_NO_SPACE)
    }

    private fun owns(p: PlayerRef, obj: Int): Boolean =
        p.inv(Invs.INV).total(obj) > 0 || p.inv(Invs.WORN).total(obj) > 0 || p.inv(Invs.BANK).total(obj) > 0

    // ---- Item Retrieval chest (TA45)

    private const val RECLAIM_EMPTY = "There is nothing for you to reclaim."
    private const val RECLAIMED = "You reclaim your items."
    private const val RECLAIM_FAILED = "You need the fee in coins and enough inventory space to reclaim your items."

    /** TA45 / OPEN-TA21: the fee is a share of the stored items' Grand Exchange value, capped at 500,000 coins. */
    fun fee(p: PlayerRef, store: InvView): Int {
        var value = 0L
        for (slot in 0 until store.size) {
            if (store.obj(slot) >=
                0
            ) {
                value += p.objValue(p.unnoted(store.obj(slot))).toLong() * store.count(slot)
            }
        }
        return minOf(ToaConfig.reclaimFeeCap.toLong(), value * ToaConfig.reclaimFeePercent / PERCENT).toInt()
    }

    private const val PERCENT = 100L

    fun reclaim(p: PlayerRef) {
        val store = p.inv(Invs.TOA_RECLAIM, stackAll = true)
        val slots = (0 until store.size).filter { store.obj(it) >= 0 }
        if (slots.isEmpty()) {
            p.message(RECLAIM_EMPTY)
            return
        }
        // Commands brief §6 `::raid waivefee`: one fee-free reclaim, used up when it succeeds.
        val waived =
            tp.content.raids.Raids
                .feeWaived(p)
        val fee = if (waived) 0 else fee(p, store)
        val ok =
            p.transaction("toa reclaim") {
                (fee <= 0 || remove(Invs.INV, Objs.COINS, fee)) &&
                    slots.all { move(Invs.TOA_RECLAIM, it, store.obj(it), store.count(it), Invs.INV) }
            }
        if (ok && waived) {
            tp.content.raids.Raids
                .useFeeWaiver(p)
        }
        p.message(if (ok) RECLAIMED else RECLAIM_FAILED)
    }

    // ---- Cache of runes (TR16)

    /** TR16: one rune type (blood, soul or death, equally likely) in three quantity bands with weights 6 / 3 / 1 out of 10. */
    private val RUNES = intArrayOf(Objs.BLOODRUNE, Objs.SOULRUNE, Objs.DEATHRUNE)
    private const val BAND_TOTAL = 10
    private const val BAND_LOW_WEIGHT = 6
    private const val BAND_MID_WEIGHT = 3
    private const val LOW_MIN = 500
    private const val LOW_MAX = 1_000
    private const val MID_MIN = 1_000
    private const val MID_MAX = 1_500
    private const val HIGH_QUANTITY = 3_000

    /** Quantity inside a band is uniform (assumed, TR16). */
    fun cacheQuantity(band: Int, offset: (Int) -> Int): Int =
        when {
            band < BAND_LOW_WEIGHT -> LOW_MIN + offset(LOW_MAX - LOW_MIN + 1)
            band < BAND_LOW_WEIGHT + BAND_MID_WEIGHT -> MID_MIN + offset(MID_MAX - MID_MIN + 1)
            else -> HIGH_QUANTITY
        }

    fun openCache(p: PlayerRef, slot: Int, obj: Int) {
        val inv = p.inv(Invs.INV)
        if (slot !in 0 until inv.size || inv.obj(slot) != obj || obj != Objs.TOA_RUNE_CACHE) return
        val rune = RUNES[p.random(RUNES.size)]
        val quantity = cacheQuantity(p.random(BAND_TOTAL)) { p.random(it) }
        p.transaction("toa rune cache") { removeAt(Invs.INV, slot, obj, 1) && add(Invs.INV, rune, quantity) }
    }
}

/** The lobby's locs and the Cache of runes. */
public class ToaLobbyPlugin : ContentPlugin {
    override fun ContentScope.register() {
        // TL4: the camel's "Bank" and "Collect" (cache ops 1 and 3); the named variant and its parent resolve to the same.
        for (id in CAMEL) {
            onOpLoc(id, OP1) { player.events.publish(OpenBankRequest(player)) }
            onOpLoc(id, OP3) { player.events.publish(OpenCollectionBox(player)) }
        }
        onOpLoc(Locs.TOA_SACK_FULL, OP1) { ToaLobbyNpcs.searchSack(player) }
        // TL9 / OPEN-TL9: the crate's contents are not stated on the wiki.
        for (id in intArrayOf(Locs.TOA_LOBBY_LAMP_CRATE, Locs.TOA_LOBBY_LAMP_CRATE_OP)) {
            onOpLoc(id, OP1) { player.message("You search the crate but find nothing of interest.") }
        }
        onOpLoc(Locs.TOA_LOBBY_CAPE_CHEST, OP1) { ToaLobbyNpcs.searchCapeChest(player) }
        onOpLoc(Locs.TOA_LOBBY_GRAVESTONE_CHEST, OP1) { ToaLobbyNpcs.reclaim(player) }
        // TL8: the Rewards Niche and the False Door both say "Claim" (the multi's parent and its two forms).
        for (id in intArrayOf(Locs.TOA_REWARDS_CHEST_LOBBY, Locs.TOA_REWARDS_CHEST_LOBBY_OPEN, Locs.TOA_REWARDS_CHEST_LOBBY_CLOSED)) {
            onOpLoc(id, OP1) { ToaChestUi.claim(player) }
        }
        onItemOp(Objs.TOA_RUNE_CACHE, "Open") { click -> ToaLobbyNpcs.openCache(this, click.slot, click.obj) }
    }

    private companion object {
        const val OP1 = 1
        const val OP3 = 3
        val CAMEL = intArrayOf(Locs.TOA_BANK_CAMEL, Locs.TOA_BANK_CAMEL_NAMED, Locs.TOA_BANK_CAMEL_PARENT)
    }
}
