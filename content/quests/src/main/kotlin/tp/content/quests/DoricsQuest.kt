@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Doric's Quest (docs/mechanics/quest-dorics-quest.md): Doric wants 6 clay, 4 copper ore and 2 iron ore. The quest variable
 * (varp 31) is 0 / 1 started (ours, OPEN-QU1) / 100 finished (cache `endstate`). Dialogue is the Wiki "Transcript:Doric's Quest" and
 * "Transcript:Doric".
 */
public class DoricsQuest : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.DORIC, OP_TALK) { doric ->
            player.stopInteraction()
            when (Quests.DORICS_QUEST.state(player)) {
                QuestState.NOT_STARTED -> {
                    starting(doric)
                }

                QuestState.IN_PROGRESS -> {
                    again(doric)
                }

                QuestState.FINISHED -> {
                    npc(doric, "Hello traveller, how is your metalworking coming along?")
                    me("Not too bad, Doric.")
                    npc(doric, "Good, the love of metal is a thing close to my heart.")
                }
            }
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.DORICS_QUEST) {
                it.lines +=
                    when (it.quest.state(it.player)) {
                        QuestState.NOT_STARTED -> {
                            listOf("I can start this quest by speaking to Doric, in the small house north of Falador.")
                        }

                        QuestState.IN_PROGRESS -> {
                            listOf(
                                "Doric the dwarf wants 6 clay, 4 copper ore and 2 iron ore (not noted) in return for the use of his " +
                                    "anvils.",
                                "He lent me a bronze pickaxe. The ores can be mined in the Dwarven Mine east of his house.",
                            )
                        }

                        QuestState.FINISHED -> {
                            listOf("I brought Doric the ores he needed. He paid me and lets me use his anvils.")
                        }
                    }
            }
        }
    }

    private fun PlayerRef.hasMaterials(): Boolean = has(Objs.CLAY, CLAY) && has(Objs.COPPER_ORE, COPPER) && has(Objs.IRON_ORE, IRON)

    private suspend fun PlayerScript.starting(doric: NpcRef) {
        npc(doric, "Hello traveller, what brings you to my humble smithy?")
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "I wanted to use your anvils.",
                    "I want to use your whetstone.",
                    "Mind your own business, shortstuff!",
                    "I was just checking out the landscape.",
                    "What do you make here?",
                )
            ) {
                1 -> {
                    me("I wanted to use your anvils.")
                    npc(
                        doric,
                        "My anvils get enough work with my own use. I make pickaxes, and it takes a lot of hard work. If you could " +
                            "get me some more materials, then I could let you use them.",
                    )
                    return offer(doric)
                }

                2 -> {
                    me("I wanted to use your whetstone.")
                    npc(
                        doric,
                        "The whetstone is for more advanced smithing, but I could let you use it as well as my anvils if you could " +
                            "get me some more materials.",
                    )
                    return offer(doric)
                }

                3 -> {
                    me("Mind your own business, shortstuff!")
                    npc(
                        doric,
                        "How nice to meet someone with such pleasant manners. Do come again when you need to shout at someone " +
                            "smaller than you!",
                    )
                    return
                }

                4 -> {
                    me("I was just checking out the landscape.")
                    npc(
                        doric,
                        "Hope you like it. I do enjoy the solitude of my little home. If you get time, please say hi to my friends in " +
                            "the Dwarven Mine.",
                    )
                    if (choice("Select an Option", "Dwarven Mine?", "Will do!") == 1) {
                        me("Dwarven Mine?")
                        npc(
                            doric,
                            "Yep, the entrance is in the side of Ice Mountain just to the east of here. They're a friendly bunch. " +
                                "Stop in at Nurmof's store and buy one of my pickaxes!",
                        )
                    } else {
                        me("Will do!")
                    }
                    return
                }

                OPTION_5 -> {
                    me("What do you make here?")
                    npc(doric, "I make pickaxes. I am the best maker of pickaxes in the whole of Gielinor.")
                    me("Do you have any to sell?")
                    npc(doric, "Sorry, but I've got a running order with Nurmof.")
                    if (choice("Select an Option", "Who's Nurmof?", "Ah, fair enough.") == 1) {
                        me("Who's Nurmof?")
                        npc(
                            doric,
                            "Nurmof has a store over in the Dwarven Mine. You can find the entrance on the side of Ice Mountain to " +
                                "the east of here.",
                        )
                    } else {
                        me("Ah, fair enough.")
                    }
                    return
                }

                else -> {
                    return
                }
            }
        }
    }

    /** "Start Doric's Quest?" and what follows. */
    private suspend fun PlayerScript.offer(doric: NpcRef) {
        if (choice("Start Doric's Quest?", "Yes.", "No.") != 1) {
            me("No, hitting rocks is for the boring people, sorry.")
            npc(doric, "That is your choice. Nice to meet you anyway.")
            return
        }
        me("Yes, I will get you the materials.")
        npc(
            doric,
            "Clay is what I use more than anything, to make casts. Could you get me 6 clay, 4 copper ore, and 2 iron ore, please? I " +
                "could pay a little, and let you use my anvils. Take this pickaxe with you just in case you need it.",
        )
        Quests.DORICS_QUEST.advance(player, STARTED)
        player.giveOrDrop(Objs.BRONZE_PICKAXE, 1, "dorics quest pickaxe")
        if (player.hasMaterials()) {
            me(
                "You know, it's funny you should require those exact things!",
            )
            npc(doric, "What do you mean?")
            me(
                "I can usually fit 28 things in my backpack and in a world full of quite literally limitless possibilities, a " +
                    "complete coincidence has occurred!",
            )
            npc(doric, "I don't quite understand what you're saying?")
            me(
                "Well, out of pure coincidence, despite definitely not knowing what you were about to request, I just so happened " +
                    "to have carried those exact items!",
            )
            if (player.inv(Invs.INV).total(Objs.CLAY) == CLAY.toLong() &&
                player.inv(Invs.INV).total(Objs.COPPER_ORE) == COPPER.toLong() &&
                player.inv(Invs.INV).total(Objs.IRON_ORE) == IRON.toLong()
            ) {
                me("In fact, in the exact quantities too!")
            }
            npc(
                doric,
                "Oh my, that is a coincidence! Pass them here, please. I can spare you some coins for your trouble, and please use " +
                    "my anvils any time you want.",
            )
            return finish()
        }
        when (choice("Select an Option", "Where can I find those?", "Certainly, I'll be right back!")) {
            1 -> {
                me("Where can I find those?")
                npc(
                    doric,
                    "You'll be able to find all those ores in the rocks just inside the Dwarven Mine. Head east from here and you'll " +
                        "find the entrance in the side of Ice Mountain.",
                )
                if (player.baseLevel(Stats.MINING) < IRON_MINING_LEVEL) {
                    me("But I'm not a good enough miner to get iron ore.")
                    npc(
                        doric,
                        "Oh well, you could practice mining until you can. Can't beat a bit of mining - it's a useful skill. Failing " +
                            "that, you might be able to find a more experienced adventurer to buy the iron ore off.",
                    )
                }
            }

            2 -> {
                me("Certainly, I'll be right back!")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.again(doric: NpcRef) {
        npc(doric, "Have you got my materials yet, traveller?")
        if (player.hasMaterials()) {
            me("I have everything you need!")
            npc(
                doric,
                "Many thanks! Pass them here, please. I can spare you some coins for your trouble, and please use my anvils any " +
                    "time you want.",
            )
            return finish()
        }
        me("Sorry, I don't have them all yet.")
        npc(doric, "Not to worry, stick at it. Remember, I need 6 clay, 4 copper ore, and 2 iron ore.")
        when (choice("Select an Option", "Where can I find those?", "Certainly, I'll be right back!")) {
            1 -> {
                me("Where can I find those?")
                npc(
                    doric,
                    "You'll be able to find all those ores in the rocks just inside the Dwarven Mine. Head east from here and you'll " +
                        "find the entrance in the side of Ice Mountain.",
                )
                if (player.baseLevel(Stats.MINING) < IRON_MINING_LEVEL) {
                    me("But I'm not a good enough miner to get iron ore.")
                    npc(
                        doric,
                        "Oh well, you could practice mining until you can. Can't beat a bit of mining - it's a useful skill. Failing " +
                            "that, you might be able to find a more experienced adventurer to buy the iron ore off.",
                    )
                }
            }

            2 -> {
                me("Certainly, I'll be right back!")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.finish() {
        val p = player
        val taken =
            p.transaction("dorics quest materials") {
                remove(Invs.INV, Objs.CLAY, CLAY) && remove(Invs.INV, Objs.COPPER_ORE, COPPER) && remove(Invs.INV, Objs.IRON_ORE, IRON)
            }
        if (!taken) return
        itemBox(Objs.COPPER_ORE, "You hand the clay, copper, and iron to Doric.")
        QuestCompletion.complete(p, Quests.DORICS_QUEST, REWARD)
    }

    private companion object {
        const val OP_TALK = 1

        /** The fifth entry of a chat menu. */
        const val OPTION_5 = 5

        /** First stage of the quest variable: started (end state 100 is the cache's `endstate`). */
        const val STARTED = 1

        /** Wiki "Doric's Quest": Doric asks for 6 clay, 4 copper ore and 2 iron ore. */
        const val CLAY = 6
        const val COPPER = 4
        const val IRON = 2

        /** Wiki "Iron rocks": 15 Mining to mine iron ore. */
        const val IRON_MINING_LEVEL = 15

        /**
         * Wiki "Doric's Quest" rewards: 1 quest point, 1,300 Mining XP (cache col 33 = (14, 13000 tenths)), 180 coins; the use of
         * Doric's anvils is not gated (OPEN-QUA2).
         */
        val REWARD =
            QuestReward(
                model = Objs.COINS,
                xp = listOf(Stats.MINING to 1300.0),
                items = listOf(Objs.COINS to 180),
                notes = listOf("Use of Doric's anvils"),
            )
    }
}
