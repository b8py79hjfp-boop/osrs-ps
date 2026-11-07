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
 * Imp Catcher (docs/mechanics/quest-imp-catcher.md): Wizard Mizgog wants his four beads back from the imps. The quest variable
 * (varp 160) is 0 / 1 started / 2 finished (cache `endstate`); the same varp makes the multi-NPC 5005 show 7746 (before) or 7747
 * (after, with "Purchase Amulet"). The beads come from imp drops (content/drops). Dialogue is the Wiki "Transcript:Imp Catcher"
 * and "Transcript:Wizard Mizgog".
 */
public class ImpCatcher : ContentPlugin {
    override fun ContentScope.register() {
        for (type in intArrayOf(Npcs.WIZARD_MIZGOG_QUEST, Npcs.WIZARD_MIZGOG_POST)) {
            onOpNpc(type, OP_TALK) { mizgog ->
                player.stopInteraction()
                when (Quests.IMP_CATCHER.state(player)) {
                    QuestState.NOT_STARTED -> starting(mizgog)
                    QuestState.IN_PROGRESS -> again(mizgog)
                    QuestState.FINISHED -> after(mizgog)
                }
            }
        }
        onOpNpc(Npcs.WIZARD_MIZGOG_POST, OP_PURCHASE) { mizgog ->
            player.stopInteraction()
            amulet(mizgog)
        }
        onOpNpc(Npcs.WIZARD_GRAYZAG, OP_TALK) { grayzag ->
            player.stopInteraction()
            npc(grayzag, "You're a fool, $name. Do you really think you'll find four imps out of thousands? Good luck. Ha!")
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.IMP_CATCHER) {
                it.lines +=
                    when (it.quest.state(it.player)) {
                        QuestState.NOT_STARTED -> {
                            listOf("I can start this quest by speaking to Wizard Mizgog on the top floor of the Wizards' Tower.")
                        }

                        QuestState.IN_PROGRESS -> {
                            listOf(
                                "Wizard Mizgog's four beads were stolen by imps. I need a black, a yellow, a red and a white bead.",
                                "Imps drop them when killed.",
                            )
                        }

                        QuestState.FINISHED -> {
                            listOf("I returned the four beads to Wizard Mizgog and he gave me an amulet of accuracy.")
                        }
                    }
            }
        }
    }

    private fun hasAllBeads(p: PlayerRef): Boolean = BEADS.all { p.has(it) }

    private suspend fun PlayerScript.starting(mizgog: NpcRef) {
        me("Give me a quest!")
        npc(mizgog, "Give me a quest what?")
        when (
            choice(
                "Select an Option",
                "Give me a quest please.",
                "Give me a quest or else!",
                "Just stop messing around and give me a quest!",
            )
        ) {
            1 -> {
                me("Give me a quest please.")
                npc(
                    mizgog,
                    "Well seeing as you asked nicely... I could do with some help.",
                    "The wizard Grayzag next door decided he didn't like me so he enlisted an army of hundreds of imps.",
                    "These imps stole all sorts of my things. Most of these things I don't really care about, just eggs and balls " +
                        "of string and things.",
                    "But they stole my four magical beads. There was a red one, a yellow one, a black one, and a white one.",
                    "These imps have now spread out all over the kingdom. Could you get my beads back for me?",
                )
                if (choice("Start the Imp Catcher quest?", "Yes.", "No.") != 1) {
                    me("I've better things to do than chase imps.")
                    npc(
                        mizgog,
                        "Well if you're not interested in the quests I have to give you, don't waste my time by asking me for them.",
                    )
                    return
                }
                Quests.IMP_CATCHER.advance(player, STARTED)
                if (hasAllBeads(player)) {
                    me(
                        "Well this is a surprising turn of events!",
                    )
                    npc(mizgog, "What?")
                    me("Well I just so happen to have all of those beads on me!")
                    npc(mizgog, "Are you saying that you stole my beads all this time and I've been blaming these imps!?")
                    me("No, not at all! I just found them throughout my travels and presumed somebody would need them at some point.")
                    npc(mizgog, "Bah! Fine.")
                    finish(mizgog)
                    return
                }
                me("I'll try.")
                npc(mizgog, "That's great, thank you.")
            }

            2 -> {
                me("Give me a quest or else!")
                npc(mizgog, "Or else what? You'll attack me?", "Hahaha!")
            }

            3 -> {
                me("Just stop messing around and give me a quest!")
                npc(mizgog, "Ah now you're assuming I have one to give.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.again(mizgog: NpcRef) {
        npc(mizgog, "So how are you doing finding my beads?")
        val p = player
        when {
            hasAllBeads(p) -> {
                me("I've got all four beads. It was hard work I can tell you.")
                finish(mizgog)
            }

            BEADS.none { p.has(it) } -> {
                me("I've not found any yet.")
                npc(
                    mizgog,
                    "Well get on with it. I've lost a white bead, a red bead, a black bead, and a yellow bead. Go kill some imps!",
                )
            }

            else -> {
                me("I have found some of your beads.")
                npc(
                    mizgog,
                    "Come back when you have them all. The colour of the four beads that I need are red, yellow, black, and white. " +
                        "Go chase some imps!",
                )
            }
        }
    }

    private suspend fun PlayerScript.finish(mizgog: NpcRef) {
        npc(
            mizgog,
            "Give them here and I'll check that they really are MY beads, before I give you your reward. You'll like it, it's an " +
                "amulet of accuracy.",
        )
        val p = player
        if (!p.transaction("imp catcher beads") { BEADS.all { remove(Invs.INV, it, 1) } }) return
        box("You give four coloured beads to Wizard Mizgog.")
        box("Wizard Mizgog places the four beads on the table and casts a spell on them.")
        box("The wizard hands you an amulet.")
        QuestCompletion.complete(p, Quests.IMP_CATCHER, REWARD)
    }

    private suspend fun PlayerScript.after(mizgog: NpcRef) {
        when (
            choice(
                "Select an Option",
                "Got any more quests?",
                "Do you know any interesting spells you could teach me?",
                "Have you got another one of those fancy schmancy amulets?",
            )
        ) {
            1 -> {
                me("Got any more quests?")
                npc(mizgog, "No, everything is good with the world today.")
            }

            2 -> {
                me("Do you know any interesting spells you could teach me?")
                npc(mizgog, "I don't think so, the type of magic I study involves years of meditation and research.")
            }

            3 -> {
                me("Have you got another one of those fancy schmancy amulets?")
                npc(
                    mizgog,
                    "I have a few spare. I'd like one of each coloured bead again in return, though! Black, white, yellow and red.",
                )
                if (!hasAllBeads(player)) {
                    me("I don't have them all on me at the moment. I'll come back when I have them!")
                    npc(mizgog, "Very well. See you soon!")
                    return
                }
                if (choice("Select an Option", "I have them with me!", "Maybe later.") != 1) {
                    me("Maybe later.")
                    npc(mizgog, "Perhaps some other time, then.")
                    return
                }
                me("I have them with me! Here you go.")
                swap(mizgog)
            }

            else -> {
                Unit
            }
        }
    }

    /** The "Purchase Amulet" op of the post-quest Mizgog (OPEN-QUA1: the op's own lines are not published). */
    private suspend fun PlayerScript.amulet(mizgog: NpcRef) {
        if (!hasAllBeads(player)) {
            npc(
                mizgog,
                "You don't seem to have one of each coloured bead on you at the moment. Return when you have a black, white, yellow " +
                    "and red in your backpack!",
            )
            return
        }
        swap(mizgog)
    }

    /** One bead of each colour for another amulet of accuracy (Wiki "Transcript:Wizard Mizgog" after the quest). */
    private suspend fun PlayerScript.swap(mizgog: NpcRef) {
        val p = player
        val done =
            p.transaction("imp catcher amulet") {
                BEADS.all { remove(Invs.INV, it, 1) } && addOrDrop(Invs.INV, Objs.AMULET_OF_ACCURACY, 1)
            }
        if (!done) return
        itemBox(Objs.AMULET_OF_ACCURACY, "Mizgog removes the beads from your backpack and gives you another Amulet of accuracy.")
        me("Thanks, Mizgog!", "What are you going to do with all of these extra beads, anyway?")
        npc(mizgog, "You don't want to know. Take care!")
    }

    private companion object {
        const val OP_TALK = 1

        /** Op 3 of the post-quest Mizgog (cache npc 7747 ops: "Talk-to", -, "Purchase Amulet"). */
        const val OP_PURCHASE = 3

        /** First stage of the quest variable: started (end state 2 is the cache's `endstate`). */
        const val STARTED = 1

        val BEADS = intArrayOf(Objs.BLACK_BEAD, Objs.YELLOW_BEAD, Objs.RED_BEAD, Objs.WHITE_BEAD)

        /** Wiki "Imp Catcher" rewards: 1 quest point, 875 Magic XP (cache col 33 = (6, 8750 tenths)), an amulet of accuracy. */
        val REWARD =
            QuestReward(
                model = Objs.AMULET_OF_ACCURACY,
                xp = listOf(Stats.MAGIC to 875.0),
                items = listOf(Objs.AMULET_OF_ACCURACY to 1),
            )
    }
}
