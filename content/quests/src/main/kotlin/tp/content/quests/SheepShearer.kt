@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Sheep Shearer (docs/mechanics/quest-sheep-shearer.md): Fred the Farmer wants 20 balls of wool. The quest variable (varp 179)
 * is 0 until started, 1 once started and grows by one for each ball handed in; 21 is the cache's end state. Dialogue is the Wiki
 * "Transcript:Sheep Shearer" and "Transcript:Fred the Farmer".
 */
public class SheepShearer : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.FRED_THE_FARMER, OP_TALK) { fred ->
            player.stopInteraction()
            when (Quests.SHEEP_SHEARER.state(player)) {
                QuestState.NOT_STARTED -> starting(fred)
                QuestState.IN_PROGRESS -> started(fred)
                QuestState.FINISHED -> after(fred)
            }
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.SHEEP_SHEARER) {
                it.lines +=
                    when (it.quest.state(it.player)) {
                        QuestState.NOT_STARTED -> {
                            listOf("I can start this quest by speaking to Fred the Farmer, north of Lumbridge Castle.")
                        }

                        QuestState.IN_PROGRESS -> {
                            listOf(
                                "Fred the Farmer wants 20 balls of wool. I shear sheep with shears and spin the wool on a " +
                                    "spinning wheel.",
                                "I still need to bring him ${remaining(it.quest.stage(it.player))} more balls of wool.",
                            )
                        }

                        QuestState.FINISHED -> {
                            listOf("I brought Fred the Farmer 20 balls of wool and he paid me.")
                        }
                    }
            }
        }
    }

    private fun remaining(stage: Int): Int = GOAL - (stage - STARTED)

    private suspend fun PlayerScript.starting(fred: NpcRef) {
        npc(
            fred,
            "What are you doing on my land? You're not the one who keeps leaving all my gates open and letting out all my " +
                "sheep, are you?",
        )
        when (choice("Select an option", "I'm looking for a quest.", "I'm looking for something to kill.", "I'm lost.")) {
            1 -> quest(fred)
            2 -> killing(fred)
            3 -> lost(fred)
            else -> Unit
        }
    }

    private suspend fun PlayerScript.quest(fred: NpcRef) {
        me("I'm looking for a quest.")
        npc(
            fred,
            "You're after a quest, you say? Actually, I could do with a bit of help.",
            "My sheep are getting mighty woolly. I'd be much obliged if you could shear them. And while you're at it, spin the " +
                "wool for me too.",
            "Yes, that's it. Bring me 20 balls of wool. And I'm sure I could sort out some sort of payment. Of course, there's " +
                "the small matter of The Thing.",
        )
        if (player.has(Objs.BALL_OF_WOOL, GOAL)) {
            me("In fact Fred, funnily enough, I actually have 20 balls of wool already on me.")
            npc(fred, "Have you been shearing my sheep without permission!?")
            me("No! Well, maybe... They just looked a little wooly! Surely you like a shave once in a while, too?")
            npc(fred, "It's rude to shave another person without permission - don't be coming at me with them shears!")
            me("I'm sorry, I'll ask permission next time.")
            npc(fred, "I guess no real 'arm was done. Hand the balls over and we can put this whole thing behind us.")
            player.take(Objs.BALL_OF_WOOL, GOAL, "sheep shearer balls of wool")
            finish()
            return
        }
        me("What do you mean, The Thing?")
        npc(
            fred,
            "Well now, no one has ever seen The Thing. That's why we call it The Thing, 'cos we don't know what it is.",
            "Some say it's a black hearted shapeshifter, hungering for the souls of hard working decent folk like me. Others " +
                "say it's just a sheep.",
            "Well I don't have all day to stand around and gossip. Are you going to shear my sheep or what!",
        )
        if (choice("Start the Sheep Shearer quest?", "Yes.", "No.") != 1) {
            me("No, I'll give it a miss.")
            npc(fred, "Suit yourself.")
            return
        }
        me("Yes, okay. I can do that.")
        Quests.SHEEP_SHEARER.advance(player, STARTED)
        npc(fred, "Good! Now one more thing, do you actually know how to shear a sheep?")
        me("Err. No, I don't know actually.")
        shearHelp(fred)
        npc(fred, "Do you know how to spin wool?")
        me("I don't know how to spin wool, sorry.")
        npc(fred, "Don't worry, it's quite simple!")
        spinHelp(fred)
    }

    /** How to shear: with or without shears (they are handed out on the first explanation). */
    private suspend fun PlayerScript.shearHelp(fred: NpcRef) {
        if (!player.has(Objs.SHEARS)) {
            npc(fred, "Well, first things first, you need a pair of shears. I've got some here you can use.")
            if (player.giveOrDrop(Objs.SHEARS, 1, "sheep shearer shears")) itemBox(Objs.SHEARS, "Fred gives you a set of sharp shears.")
            npc(fred, "You just need to go and use them on the sheep out in my field.")
            me("Sounds easy!")
            npc(
                fred,
                "That's what they all say!",
                "Some of the sheep don't like it too much... Persistence is the key.",
                "Once you've collected some wool you can spin it into balls.",
            )
        } else {
            npc(
                fred,
                "Well, you're half way there already! You have a set of shears in your inventory. Just use those on a Sheep to shear it.",
            )
            me("That's all I have to do?")
            npc(fred, "Well once you've collected some wool you'll need to spin it into balls.")
        }
    }

    private suspend fun PlayerScript.spinHelp(fred: NpcRef) {
        npc(
            fred,
            "The nearest Spinning Wheel can be found on the first floor of Lumbridge Castle.",
            "To get to Lumbridge Castle just follow the road east.",
        )
        box("This icon denotes a Spinning Wheel on the world map.")
        me("Thank you!")
    }

    private suspend fun PlayerScript.killing(fred: NpcRef) {
        me("I'm looking for something to kill.")
        npc(fred, "What, on my land? Leave my livestock alone you scoundrel!")
    }

    private suspend fun PlayerScript.lost(fred: NpcRef) {
        me("I'm lost.")
        npc(fred, "How can you be lost? Just follow the road east and south. You'll end up in Lumbridge fairly quickly.")
    }

    private suspend fun PlayerScript.started(fred: NpcRef) {
        val p = player
        npc(fred, "What are you doing on my land?")
        if (p.flag(QuestKeys.SAW_THE_THING)) {
            when (choice("Select an option", "I need to talk to you about shearing these sheep!", "Fred! Fred! I've seen The Thing!")) {
                2 -> return theThing(fred)
                1 -> me("I need to talk to you about shearing these sheep!")
                else -> return
            }
        } else {
            me("I need to talk to you about shearing these sheep!")
        }
        npc(fred, "Oh. How are you doing getting those balls of wool?")
        val have = p.inv(tp.symbols.Invs.INV).total(Objs.BALL_OF_WOOL).toInt()
        val left = remaining(Quests.SHEEP_SHEARER.stage(p))
        if (have > 0) {
            me("I have some.")
            npc(fred, "Give 'em here then.")
            val given = minOf(have, left)
            if (!p.take(Objs.BALL_OF_WOOL, given, "sheep shearer balls of wool")) return
            if (given == left) {
                me("That's the last of them.")
                npc(fred, "I guess I'd better pay you then.")
                finish()
                return
            }
            Quests.SHEEP_SHEARER.advance(p, Quests.SHEEP_SHEARER.stage(p) + given)
            box("You give Fred $given ${if (given == 1) "ball" else "balls"} of wool.")
            me("That's all I've got so far.")
            npc(fred, "I need ${left - given} more before I can pay you.")
            me("Ok I'll work on it.")
            return
        }
        me("How many more do I need to give you?")
        npc(fred, "You need to collect $left more balls of wool.")
        if (p.has(Objs.WOOL)) {
            me("I've got some wool. I've not managed to make it into a ball though.")
            npc(
                fred,
                "Well go find a spinning wheel then. You can find one on the first floor of Lumbridge Castle, just walk east on " +
                    "the road " +
                    "outside my house and you'll find Lumbridge.",
            )
            return
        }
        me("I haven't got any at the moment.")
        npc(fred, "Ah well at least you haven't been eaten. You know what you're doing, right?")
        when (choice("Select an option", "How do I shear sheep, again?", "Remind me how to spin wool.", "Yeah, I think so.")) {
            1 -> {
                me("How do I shear sheep, again?")
                shearHelp(fred)
                npc(fred, "Do you know how to spin wool?")
                if (choice("Select an option", "Yes, I know how to spin wool.", "I don't know how to spin wool, sorry.") == 1) {
                    me("Yes, I know how to spin wool.")
                    npc(fred, "Great!")
                } else {
                    me("I don't know how to spin wool, sorry.")
                    spinHelp(fred)
                }
            }

            2 -> {
                me("Remind me how to spin wool.")
                spinHelp(fred)
            }

            3 -> {
                me("Yeah, I think so.")
                npc(fred, "You can get to it, then!")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.after(fred: NpcRef) {
        npc(fred, "What are you doing on my land?")
        val options =
            if (player.flag(
                    QuestKeys.SAW_THE_THING,
                )
            ) {
                arrayOf(
                    "I'm looking for something to kill.",
                    "I'm lost.",
                    "Fred! Fred! I've seen The Thing!",
                )
            } else {
                arrayOf("I'm looking for something to kill.", "I'm lost.")
            }
        when (choice("Select an option", *options)) {
            1 -> killing(fred)
            2 -> lost(fred)
            3 -> theThing(fred)
            else -> Unit
        }
    }

    private suspend fun PlayerScript.theThing(fred: NpcRef) {
        me("Fred! Fred! I've seen The Thing!")
        npc(fred, "You ... you actually saw it?", "Run for the hills! $name grab as many chickens as you can! We have to ...")
        me("Fred!")
        npc(fred, "... flee! Oh, woe is me! The shapeshifter is coming! We're all ...")
        me("FRED!")
        npc(fred, "... doomed. What!")
        me("It's not a shapeshifter or any other kind of monster!")
        npc(fred, "Well then what is it?")
        me("Well ... it's just two Penguins; Penguins disguised as a sheep.")
        npc(fred, "...", "Have you been out in the sun too long?")
    }

    private fun PlayerScript.finish() {
        QuestCompletion.complete(player, Quests.SHEEP_SHEARER, REWARD)
    }

    private companion object {
        const val OP_TALK = 1

        /** Balls of wool Fred asks for (Wiki "Sheep Shearer"). */
        const val GOAL = 20

        /** Variable value of a started quest; each ball handed in adds one (end state 21 = 1 + [GOAL], cache `endstate`). */
        const val STARTED = 1

        /** Wiki "Sheep Shearer" rewards: 1 quest point, 150 Crafting experience, 60 coins. */
        val REWARD =
            QuestReward(
                model = Objs.BALL_OF_WOOL,
                xp = listOf(Stats.CRAFTING to 150.0),
                items = listOf(Objs.COINS to 60),
            )
    }
}
