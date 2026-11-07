@file:Suppress("LongMethod")
// The long function is a Wiki transcript: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Wyson the gardener in Falador Park sells woad leaves, the source of Goblin Diplomacy's blue dye
 * (docs/mechanics/quest-goblin-diplomacy.md GD6). Dialogue: OSRS Wiki "Transcript:Wyson the gardener" (standard dialogue); the
 * shop section of "Wyson the gardener" gives the two prices. The mole-part exchange, holy moleys and clue scroll lines are not
 * implemented (OPEN-QUC4). Spawned by [QuestSpawns].
 */
public class WysonGardener : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.WYSON, OP_TALK) { wyson ->
            player.stopInteraction()
            talk(wyson)
        }
    }

    private suspend fun PlayerScript.talk(wyson: NpcRef) {
        npc(
            wyson,
            "I'm the head gardener around here. If you're looking for woad leaves, or if you need help with owt, I'm yer man.",
        )
        when (
            choice(
                "Select an Option",
                "Yes please, I need woad leaves.",
                "How about ME helping YOU instead?",
                "Sorry, but I'm not interested.",
            )
        ) {
            1 -> {
                me("Yes please, I need woad leaves.")
                woad(wyson)
            }

            2 -> {
                help(wyson)
            }

            3 -> {
                me("Sorry, but I'm not interested.")
                npc(wyson, "Fair enough.")
            }

            else -> {
                Unit
            }
        }
    }

    /** The price haggle: 15 coins buy one leaf, 20 coins two (the shop table of the Wiki page); 5 and 10 are refused. */
    private suspend fun PlayerScript.woad(wyson: NpcRef) {
        npc(wyson, "How much are you willing to pay?")
        val offer = choice("Select an Option", "How about 5 coins?", "How about 10 coins?", "How about 15 coins?", "How about 20 coins?")
        if (offer !in 1..PRICES.size) return
        val price = PRICES[offer - 1]
        me("How about $price coins?")
        when (price) {
            LOW_PRICE, MID_PRICE -> {
                npc(
                    wyson,
                    "No no, that's far too little. Woad leaves are hard to get. I used to have plenty but someone kept " +
                        "stealing them off me.",
                )
            }

            ONE_LEAF_PRICE -> {
                npc(wyson, "Mmmm... okay, that sounds fair.")
                if (buy(price, 1)) {
                    me("Thanks.")
                    npc(wyson, "I'll be around if you have any more gardening needs.")
                }
            }

            else -> {
                npc(wyson, "Okay, that's more than fair.")
                if (buy(price, 2)) {
                    npc(wyson, "Here, have two, you're a generous person.")
                    me("Thanks.")
                }
            }
        }
    }

    /** Trades [coins] for [leaves] in one transaction; false (with the Wiki's line when short of coins) when nothing was traded. */
    private suspend fun PlayerScript.buy(coins: Int, leaves: Int): Boolean {
        val p = player
        if (!p.has(Objs.COINS, coins)) {
            me("I don't have enough coins to buy the leaves. I'll come back later.")
            return false
        }
        val ok = p.transaction("wyson woad leaves") { remove(Invs.INV, Objs.COINS, coins) && add(Invs.INV, Objs.WOADLEAF, leaves) }
        if (!ok) p.message("You don't have enough inventory space.")
        return ok
    }

    private suspend fun PlayerScript.help(wyson: NpcRef) {
        me("How about ME helping YOU instead?")
        npc(
            wyson,
            "That's a nice thing to say. I do need a hand, now you mention it. You see, there's some stupid mole digging up my " +
                "lovely garden.",
        )
        me("A mole? Surely you've dealt with moles in the past?")
        npc(
            wyson,
            "Ah, well this is no ordinary mole! He's a big'un for sure. Ya see... I'm always relied upon to make the most of this " +
                "'ere garden - the faster and bigger I can grow plants the better!",
            "In my quest for perfection I looked into 'Malignius-Mortifer's-Super-Ultra-Flora-Growth-Potion'. It worked well on my " +
                "plants, no doubt about it! But it had the same effect on a nearby mole. Ya can imagine the",
            "havoc he causes to my patches of sunflowers! Why, if any of the other gardeners knew about this mole, I'd be looking " +
                "for a new job in no time!",
        )
        me("I see. What do you need me to do?")
        npc(
            wyson,
            "If ya are willing maybe yer wouldn't mind killing it for me? Take a spade and use it to shake up them mole hills. Be " +
                "careful though, he really is big!",
        )
        me("Is there anything in this for me?")
        npc(
            wyson,
            "Well, if yer gets any mole skin or mole claws off 'un, I'd trade 'em for bird nests if ye brings 'em here to me.",
        )
        me("Right, I'll bear it in mind.")
    }

    private companion object {
        const val OP_TALK = 1

        /** The coin offers of the Wiki dialogue; 15 buys one leaf and 20 two (Wiki "Wyson the gardener" shop table). */
        val PRICES = intArrayOf(5, 10, 15, 20)
        const val LOW_PRICE = 5
        const val MID_PRICE = 10
        const val ONE_LEAF_PRICE = 15
    }
}
