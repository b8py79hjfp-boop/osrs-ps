@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Witch's Potion (docs/mechanics/quest-witchs-potion.md): Hetty in Rimmington wants an eye of newt, a rat's tail, an onion and
 * burnt meat. The quest variable (varp 67) is 0 / 1 started / 2 ingredients handed in, the potion waits / 3 finished (cache
 * `endstate`; stage 2 is the cache-free "drink from the cauldron" step, ours, OPEN-QU1). Rats drop the tail only during the quest.
 * Dialogue is the Wiki "Transcript:Witch's Potion".
 */
public class WitchsPotion : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.HETTY, OP_TALK) { hetty ->
            player.stopInteraction()
            when (Quests.WITCHS_POTION.stage(player)) {
                0 -> {
                    starting(hetty)
                }

                STARTED -> {
                    collecting(hetty)
                }

                POTION_READY -> {
                    npc(hetty, "Well are you going to drink the potion or not?")
                    me("Yes, I will.")
                }

                else -> {
                    after(hetty)
                }
            }
        }
        onOpLoc(Locs.HETTYCAULDRON, OP_DRINK) {
            player.stopInteraction()
            drink()
        }
        // The Wiki's Rat page: "Rat's tails is only dropped during Witch's Potion" (always, 1). content/drops attaches the row to
        // the Stronghold of Security dungeon rats instead (OPEN-QUA4), so the plugin drops it for the quest's rats.
        events.on<DeathEvent> { e ->
            val npc = e.entity as? NpcRef ?: return@on
            val killer = e.lootFor ?: return@on
            if (npc.type !in RATS || Quests.WITCHS_POTION.state(killer) != QuestState.IN_PROGRESS) return@on
            killer.transaction("witchs potion rat tail") { createOnGround(Objs.RATS_TAIL, 1, npc.coord, killer.username) }
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.WITCHS_POTION) {
                it.lines +=
                    when (it.quest.stage(it.player)) {
                        0 -> {
                            listOf("I can start this quest by speaking to Hetty in her house in Rimmington.")
                        }

                        STARTED -> {
                            listOf(
                                "Hetty is making a potion and needs an eye of newt, a rat's tail, an onion and a piece of burnt meat.",
                                "A rat's tail only drops from a rat once the quest is started.",
                            )
                        }

                        POTION_READY -> {
                            listOf("Hetty put the ingredients in her cauldron. I should drink from it.")
                        }

                        else -> {
                            listOf("I drank from Hetty's cauldron and feel imbued with power.")
                        }
                    }
            }
        }
    }

    private suspend fun PlayerScript.starting(hetty: NpcRef) {
        npc(hetty, "What could you want with an old woman like me?")
        while (true) {
            when (choice("Select an Option", "I am in search of a quest.", "I've heard that you are a witch.")) {
                1 -> {
                    return quest(hetty)
                }

                2 -> {
                    me("I've heard that you are a witch.")
                    npc(
                        hetty,
                        "Yes it does seem to be getting fairly common knowledge.",
                        "I fear I may be getting a visit from the witch hunters of Falador before long.",
                    )
                    if (choice("Select an Option", "I am in search of a quest.", "Goodbye.") == 1) return quest(hetty)
                    me("Goodbye.")
                    return
                }

                else -> {
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.quest(hetty: NpcRef) {
        me("I am in search of a quest.")
        npc(
            hetty,
            "Hmmm... Maybe I can think of something for you.",
            "Would you like to become more proficient in the dark arts?",
        )
        if (choice("Start the Witch's Potion quest?", "Yes.", "No.") != 1) {
            me("No, I have my principles and honour.")
            npc(hetty, "Suit yourself, but you're missing out.")
            return
        }
        Quests.WITCHS_POTION.advance(player, STARTED)
        me("Yes, help me become one with my darker side.")
        npc(
            hetty,
            "Okay, I'm going to make a potion to help bring out your darker self.",
            "You will need certain ingredients.",
        )
        me("What do I need?")
        npc(hetty, "You need an eye of newt, a rat's tail, an onion... Oh and a piece of burnt meat.")
        me("Great, I'll go and get them.")
    }

    /** Talking to Hetty during the quest: "So what have you found so far?" and the ingredients handed in. */
    private suspend fun PlayerScript.collecting(hetty: NpcRef) {
        val p = player
        me("I've been looking for those ingredients.")
        npc(hetty, "So what have you found so far?")
        val have = INGREDIENTS.map { p.has(it.obj) }
        when {
            have.all { it } -> {
                me("In fact I have everything!")
                npc(hetty, "Excellent, can I have them then?")
                val taken = p.transaction("witchs potion ingredients") { INGREDIENTS.all { remove(Invs.INV, it.obj, 1) } }
                if (!taken) return
                Quests.WITCHS_POTION.advance(p, POTION_READY)
                box(
                    "You pass the ingredients to Hetty and she puts them all into her cauldron. Hetty closes her eyes and begins to " +
                        "chant. The cauldron bubbles mysteriously.",
                )
                me("Well, is it ready?")
                npc(hetty, "Ok, now drink from the cauldron.")
            }

            have.none { it } -> {
                me("I'm afraid I don't have any of them yet.")
                npc(
                    hetty,
                    "Well I can't make the potion without them! Remember... You need an eye of newt, a rat's tail, an onion, and a " +
                        "piece of burnt meat. Off you go dear!",
                )
            }

            else -> {
                val parts = INGREDIENTS.mapIndexed { i, ing -> if (have[i]) ing.have else ing.lack }
                me(parts.dropLast(1).joinToString(", ") + ", and " + parts.last() + ".")
                npc(hetty, "Great, but I'll need the other ingredients as well.")
            }
        }
    }

    /** The post-quest Hetty: OPEN-QUA3, the Wiki has no post-quest transcript, so only the witch talk remains. */
    private suspend fun PlayerScript.after(hetty: NpcRef) {
        npc(hetty, "What could you want with an old woman like me?")
        me("I've heard that you are a witch.")
        npc(
            hetty,
            "Yes it does seem to be getting fairly common knowledge.",
            "I fear I may be getting a visit from the witch hunters of Falador before long.",
        )
    }

    private suspend fun PlayerScript.drink() {
        val p = player
        if (Quests.WITCHS_POTION.stage(p) != POTION_READY) {
            me("As nice as that looks, I think I'll give it a miss for now.")
            return
        }
        box("You drink from the cauldron. It tastes horrible! You feel yourself imbued with power.")
        QuestCompletion.complete(p, Quests.WITCHS_POTION, REWARD)
    }

    private class Ingredient(val obj: Int, val have: String, val lack: String)

    private companion object {
        const val OP_TALK = 1

        /** Op 1 "Drink-from" of the cauldron (cache loc 2024 `hettycauldron`). */
        const val OP_DRINK = 1

        /** Quest stages: 1 started, 2 ingredients handed in (ours, OPEN-QU1); 3 is the cache's `endstate`. */
        const val STARTED = 1
        const val POTION_READY = 2

        /** Wiki "Rat" (level 1 versions, Regular and Stronghold of Security): the rats of the Witch's Potion tail. */
        val RATS = setOf(Npcs.RAT, Npcs.RAT_INDOORS, Npcs.RAT_LOWWANDER, Npcs.SOS_WAR_RAT, Npcs.SOS_FAM_RAT)

        val INGREDIENTS =
            listOf(
                Ingredient(Objs.RATS_TAIL, "I have the rat's tail (ewww)", "I don't have a rat's tail"),
                Ingredient(Objs.BURNT_MEAT, "I have the burnt meat", "I don't have any burnt meat"),
                Ingredient(Objs.ONION, "I have an onion", "I don't have an onion"),
                Ingredient(Objs.EYE_OF_NEWT, "I have the eye of newt, yum!", "I don't have an eye of newt"),
            )

        /** Wiki "Witch's Potion" rewards: 1 quest point, 325 Magic XP (cache col 33 = (6, 3250 tenths)). */
        val REWARD = QuestReward(model = Objs.EYE_OF_NEWT, xp = listOf(Stats.MAGIC to 325.0))
    }
}
