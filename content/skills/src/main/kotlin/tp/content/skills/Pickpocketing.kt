package tp.content.skills

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.SpotAnims

/**
 * Pickpocketing (docs/mechanics/thieving.md). One "Pickpocket" op script per click: the roll is made on the arrival
 * tick ([PlayerRef.statRandom] with the NPC's Wiki rates), then the player is delayed so the next attempt can start two
 * ticks later (TH2). A failed roll makes the NPC strike the player for the sourced damage and stuns them (TH3). Coin
 * pouches stack to [ThievingBonuses.pouchLimit] and are opened with "Open" / "Open-all" (TH4).
 */
public class Pickpocketing : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpcOption("Pickpocket") { npc -> pickpocket(npc) }
        for (pouch in ThievingData.POUCHES.keys) {
            onItemOp(pouch, "Open-all") { click -> openPouches(click, all = true) }
            onItemOp(pouch, "Open") { click -> openPouches(click, all = false) }
        }
    }

    private suspend fun PlayerScript.pickpocket(npc: NpcRef) {
        val p = player
        Blackjack.targetOf(npc.type)?.let { return blackjackPickpocket(npc, it) }
        val target = pickable(npc)
        if (target == null) {
            p.message("Nothing interesting happens.")
            return
        }
        val name = npc.displayName.lowercase()
        if (p.level(Stats.THIEVING) < target.level) {
            p.message("You need a Thieving level of ${target.level} to pickpocket the $name.")
            return
        }
        val pouch = target.always.firstOrNull { it.coins != null } ?: target.main.firstOrNull { it.coins != null }
        if (pouch != null && p.inv(Invs.INV).total(pouch.obj) >= ThievingBonuses.pouchLimit(p)) {
            p.message("You need to open your coin pouches before you can pickpocket again.")
            return
        }
        // One attempt per click: the delay below would otherwise leave the interaction running and repeat the op.
        p.stopInteraction()
        p.face(npc)
        p.anim(Seqs.HUMAN_PICKPOCKET)
        p.message("You attempt to pick the $name's pocket.")
        val rate = ThievingBonuses.pickpocketRate(p, target.rate, target.glovesRate)
        if (p.statRandom(Stats.THIEVING, rate.low, rate.high)) {
            if (steal(p, target, name)) delay(PICKPOCKET_TICKS)
        } else if (ThievingBonuses.stunAvoided(p)) {
            // SV1 / TH10: Shadow Veil or the dodgy necklace spares the stun and the damage; the attempt still fails.
            p.message("You fail to pick the $name's pocket.")
            delay(PICKPOCKET_TICKS)
        } else {
            fail(p, npc, target, name)
            delay(PICKPOCKET_TICKS + STUN_TICKS)
        }
    }

    /** The Wiki page's data for [npc], by cache name (or by id range for the NPCs the Wiki lumps together). */
    private fun pickable(npc: NpcRef): Pickable? {
        val page =
            when (npc.type) {
                in DESERT_BANDITS -> "Bandit (Bandit Camp)"
                in ELVES -> "Elf (Thieving)"
                in VYRES -> "Vyre (Thieving)"
                else -> NAME_PAGES[npc.displayName.lowercase()]
            }
        return ThievingData.PICKPOCKETS[page]
    }

    /** The successful steal: loot, then xp. False (nothing given) if the loot does not fit. */
    private fun steal(p: PlayerRef, target: Pickable, name: String): Boolean {
        val rows = ArrayList<LootRow>(target.always)
        rollMain(p, target)?.let { rows += it }
        val items = ArrayList<Pair<Int, Int>>(rows.size * 2)
        for (row in rows) items += row.obj to row.min + p.random(row.max - row.min + 1)
        if (ThievingBonuses.rogueDoubles(p)) {
            // TH9: double loot, but a coin pouch becomes one pouch plus the coins it holds (Wiki "Rogue equipment"). Xp is unchanged.
            for ((i, row) in rows.withIndex()) {
                val coins = row.coins
                if (coins != null) {
                    items += Objs.COINS to coins.first + p.random(coins.last - coins.first + 1)
                } else {
                    items[i] = row.obj to items[i].second * 2
                }
            }
        }
        val stored = p.transaction("pickpocket") { items.all { (obj, amount) -> add(Invs.INV, obj, amount) } }
        if (!stored) {
            p.message("You don't have enough inventory space.")
            return false
        }
        p.message("You pick the $name's pocket.")
        p.addXp(Stats.THIEVING, target.xp)
        return true
    }

    /** One roll of the mutually exclusive main table: the row whose slice of [0, 1) the roll falls in, or none. */
    private fun rollMain(p: PlayerRef, target: Pickable): LootRow? {
        val roll = p.random(ROLL_RESOLUTION).toDouble() / ROLL_RESOLUTION
        var sum = 0.0
        for (row in target.main) {
            sum += row.chance
            if (roll < sum) return row
        }
        return null
    }

    private fun fail(p: PlayerRef, npc: NpcRef, target: Pickable, name: String) {
        p.message("You fail to pick the $name's pocket.")
        npc.face(p)
        val damage = target.damage.first + p.random(target.damage.last - target.damage.first + 1)
        val request = DamageRequest(p, npc, damage)
        p.events.publish(request)
        if (!request.handled) applyNonLethal(p, npc, damage)
        p.anim(Seqs.STUNNED_THIEVING)
        p.spotAnim(SpotAnims.STUNNED_THIEVING)
    }

    /** Fallback while no combat module answers [DamageRequest] (OPEN-TH2): the damage is dealt but never kills. */
    private fun applyNonLethal(p: PlayerRef, npc: NpcRef, damage: Int) {
        val dealt = minOf(damage, p.hitpoints - 1).coerceAtLeast(0)
        p.hitpoints -= dealt
        p.hitsplat(npc, dealt, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_LIT, HITMARK_DAMAGE_TINT)
    }

    /** "Open" opens one pouch, "Open-all" the whole stack: each pouch holds its Wiki coin amount (a fresh roll for ranges). */
    private fun PlayerRef.openPouches(click: ButtonClick, all: Boolean) {
        val p = this
        val contents = ThievingData.POUCHES[click.obj] ?: return
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val count = if (all) inv.count(click.slot) else 1
        var coins = 0L
        repeat(count) { coins += contents.first + p.random(contents.last - contents.first + 1) }
        val opened =
            coins <= Int.MAX_VALUE &&
                p.transaction("coin pouch") {
                    removeAt(Invs.INV, click.slot, click.obj, count) && add(Invs.INV, Objs.COINS, coins.toInt())
                }
        if (!opened) p.message("You don't have enough inventory space.")
    }

    private companion object {
        /** Ticks between pickpockets (Wiki "Pickpocketing": "NPCs may be pickpocketed every two ticks"). */
        const val PICKPOCKET_TICKS = 2

        /** Extra ticks a failed pickpocket costs (Wiki "Pickpocketing": further pickpocketing is blocked for eight ticks). */
        const val STUN_TICKS = 8

        /** Resolution of the loot roll: 2^24 slices, finer than the smallest Wiki rarity (50/463599). */
        const val ROLL_RESOLUTION = 1 shl 24

        /** Hitmark config ids of a regular hit (same as the combat module's: lit for the victim, tint for others). */
        const val HITMARK_DAMAGE_LIT = 16
        const val HITMARK_DAMAGE_TINT = 17

        /** Bandit Camp bandits ("Bandit" npcs 690 and 695 spawn there, npc-spawns.tsv); the Wiki page "Bandit (Bandit Camp)". */
        val DESERT_BANDITS = setOf(690, 695)

        /** Elves (cache npcs 9055..9117 carry the "Pickpocket" op: Lletya / Prifddinas). */
        val ELVES = 9055..9117

        /** Darkmeyer vyres (cache npcs 9688..9713). */
        val VYRES = 9688..9713

        /** NPC cache name (lower case) -> Wiki page of its pickpocket data. */
        val NAME_PAGES =
            mapOf(
                "man" to "Man",
                "woman" to "Woman",
                "farmer" to "Farmer",
                "h.a.m. member" to "H.A.M. Member",
                "warrior" to "Warrior (Thieving)",
                "al kharid warrior" to "Al Kharid warrior",
                "rogue" to "Rogue",
                "master farmer" to "Master Farmer",
                "guard" to "Guard",
                "knight of ardougne" to "Knight of Ardougne",
                "knight of varlamore" to "Knight of Ardougne",
                "watchman" to "Watchman",
                "paladin" to "Paladin",
                "gnome" to "Gnome",
                "gnome child" to "Gnome",
                "gnome woman" to "Gnome",
                "hero" to "Hero",
                "tzhaar-hur" to "TzHaar-Hur",
            )
    }
}
