@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Ernest the Chicken (docs/mechanics/quest-ernest-the-chicken.md): Veronica sends the player into Draynor Manor, where Professor
 * Oddenstein needs a pressure gauge, a rubber tube and an oil can. The quest variable (varp 32) is 0 / 1 started / 2 the professor
 * explained / 3 finished (cache `endstate`; stage 2 is ours, OPEN-QU1). The basement lever puzzle that guards the oil can is not
 * implemented (OPEN-QUA1). Dialogue is the Wiki "Transcript:Ernest the Chicken", "Transcript:Veronica" and
 * "Transcript:Professor Oddenstein".
 */
public class ErnestTheChicken : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.VERONICA, OP_TALK) { veronica ->
            player.stopInteraction()
            veronicaTalk(veronica)
        }
        onOpNpc(Npcs.PROFESSOR_ODDENSTEIN, OP_TALK) { professor ->
            player.stopInteraction()
            professorTalk(professor)
        }
        // The fish food and the poison make poisoned fish food; the fountain's piranhas guard the pressure gauge.
        onUseOnItem(Objs.POISON, Objs.FISH_FOOD) {
            val ok =
                transaction("ernest poisoned fish food") {
                    remove(Invs.INV, Objs.POISON, 1) && remove(Invs.INV, Objs.FISH_FOOD, 1) && add(Invs.INV, Objs.POISONED_FISH_FOOD, 1)
                }
            if (ok) message("You poison the fish food.")
        }
        onUseOnLoc(Objs.POISONED_FISH_FOOD, Locs.HAUNTEDFOUNTAIN) {
            player.stopInteraction()
            if (player.take(Objs.POISONED_FISH_FOOD, 1, "ernest poisoned fountain")) {
                player.message("You pour the poisoned fish food into the fountain.")
                player.message("The piranhas start eating the food...")
                player.message("... then die and float to the surface.")
                player.setFlag(FLAG_POISONED, true)
            }
        }
        onUseOnLoc(Objs.FISH_FOOD, Locs.HAUNTEDFOUNTAIN) {
            player.stopInteraction()
            if (player.take(Objs.FISH_FOOD, 1, "ernest fed fountain")) {
                player.message("You pour the fish food into the fountain.")
                player.message("The piranhas start eating the food...")
                player.message("Now they seem hungrier than ever!")
            }
        }
        onOpLoc(Locs.HAUNTEDFOUNTAIN, OP_SEARCH) {
            player.stopInteraction()
            searchFountain()
        }
        onUseOnLoc(Objs.SPADE, Locs.HAUNTEDCOMPOSTHEAP) {
            player.stopInteraction()
            digCompost()
        }
        onOpLoc(Locs.CLOSET_DOOR, OP_OPEN) { door ->
            player.stopInteraction()
            closetDoor(door)
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.ERNEST_THE_CHICKEN) it.lines += journal(it.player)
        }
    }

    private val PlayerRef.stage: Int get() = Quests.ERNEST_THE_CHICKEN.stage(this)

    private fun journal(p: PlayerRef): List<String> =
        when (p.stage) {
            0 -> {
                listOf("I can start this quest by speaking to Veronica, outside Draynor Manor.")
            }

            1 -> {
                listOf(
                    "Veronica's fiance Ernest went into Draynor Manor an hour ago and has not come out. I should look for him.",
                    "Professor Oddenstein lives at the top of the manor.",
                )
            }

            2 -> {
                listOf(
                    "Ernest was turned into a chicken. Professor Oddenstein needs a pressure gauge, a rubber tube and an oil can " +
                        "to change him back.",
                    "The poison and the fish food are in the manor; the gauge is in the fountain, the key to the tube's room in the " +
                        "compost heap.",
                )
            }

            else -> {
                listOf("I helped Professor Oddenstein turn Ernest back into a human.")
            }
        }

    // ---- Veronica

    private suspend fun PlayerScript.veronicaTalk(veronica: NpcRef) {
        when (player.stage) {
            0 -> {
                npc(veronica, "Can you please help me? I'm in a terrible spot of trouble.")
                when (choice("Select an Option", "Aha, sounds like a quest. I'll help.", "No, I'm looking for something to kill.")) {
                    1 -> {
                        me("Aha, sounds like a quest. I'll help.")
                        npc(
                            veronica,
                            "Yes yes, I suppose it is a quest. My fiance Ernest and I came upon this house.",
                            "Seeing as we were a little lost Ernest decided to go in and ask for directions.",
                            "That was an hour ago. That house looks spooky, can you go and see if you can find him for me?",
                        )
                        me("Ok, I'll see what I can do.")
                        npc(veronica, "Thank you, thank you. I'm very grateful.")
                        Quests.ERNEST_THE_CHICKEN.advance(player, STARTED)
                    }

                    2 -> {
                        me("No, I'm looking for something to kill.")
                        npc(veronica, "Oooh, you violent person you.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            STARTED -> {
                npc(veronica, "Have you found my sweetheart yet?")
                me("No, not yet.")
            }

            EXPLAINED -> {
                npc(veronica, "Have you found my sweetheart yet?")
                me("Yes, he's a chicken.")
                npc(veronica, "I know he's not exactly brave but I think you're being a bit harsh.")
                me("No no, he's been turned into an actual chicken by a mad scientist.")
                npc(veronica, "Eeeeeek!", "My poor darling, why must these things happen to us.")
                me("Well I'm doing my best to turn him back.")
                npc(veronica, "Well be quick, I'm sure being a chicken can't be good for him.")
            }

            else -> {
                npc(veronica, "Thank you for rescuing Ernest.")
                me("Where is he now?")
                npc(veronica, "Oh he went off to talk to some green warty guy. I'm sure he'll be back soon.")
            }
        }
    }

    // ---- Professor Oddenstein

    private suspend fun PlayerScript.professorTalk(professor: NpcRef) {
        val p = player
        when (p.stage) {
            STARTED -> {
                npc(professor, "Be careful in here, there's lots of dangerous equipment.")
                when (
                    choice(
                        "Select an Option",
                        "I'm looking for a guy called Ernest.",
                        "What does this machine do?",
                        "Is this your house?",
                    )
                ) {
                    1 -> {
                        me("I'm looking for a guy called Ernest.")
                        npc(
                            professor,
                            "Ah Ernest, top notch bloke. He's helping me with my experiments.",
                        )
                        me("So you know where he is then?")
                        npc(professor, "He's that chicken over there.")
                        me("Ernest is a chicken...? Are you sure?")
                        npc(
                            professor,
                            "Oh, he isn't normally a chicken, or at least he wasn't until he helped me test my pouletmorph machine.",
                            "It was originally going to be called a transmutation machine. But after testing pouletmorph seems more " +
                                "appropriate.",
                        )
                        if (choice(
                                "Select an Option",
                                "I'm glad Veronica didn't actually get engaged to a chicken.",
                                "Change him back this instant!",
                            ) ==
                            1
                        ) {
                            me("I'm glad Veronica didn't actually get engaged to a chicken.")
                            npc(professor, "Who's Veronica?")
                            me("Ernest's fiancee. She probably doesn't want to marry a chicken.")
                            npc(professor, "Ooh I dunno. She could have free eggs for breakfast.")
                            me("I think you'd better change him back.")
                        } else {
                            me("Change him back this instant!")
                        }
                        npc(
                            professor,
                            "Umm... It's not so easy...",
                            "My machine is broken, and the house gremlins have run off with some vital bits.",
                        )
                        me("Well I can look for them.")
                        npc(
                            professor,
                            "That would be a help. They'll be somewhere in the manor house or its grounds, the gremlins never get " +
                                "further than the entrance gate.",
                            "I'm missing the pressure gauge and a rubber tube. They've also taken my oil can, which I'm going to need " +
                                "to get this thing started again.",
                        )
                        Quests.ERNEST_THE_CHICKEN.advance(p, EXPLAINED)
                    }

                    2 -> {
                        machine(professor)
                    }

                    3 -> {
                        house(professor)
                    }

                    else -> {
                        Unit
                    }
                }
            }

            EXPLAINED -> {
                returning(professor)
            }

            0 -> {
                npc(professor, "Be careful in here, there's lots of dangerous equipment.")
                when (choice("Select an Option", "What does this machine do?", "Is this your house?")) {
                    1 -> machine(professor)
                    2 -> house(professor)
                    else -> Unit
                }
            }

            else -> {
                when (choice("Select an Option", "What does the portal machine do?", "Is this your house?")) {
                    1 -> {
                        me("What does the portal machine do?")
                        npc(
                            professor,
                            "Ah, an interesting question! I believe it should create a rift in the very fabric of space! Opening a " +
                                "portal to some, hitherto undiscovered plane that exists parallel to our own.",
                        )
                        me("Huh?")
                        npc(professor, "It's a teleport machine.")
                    }

                    2 -> {
                        house(professor)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    private suspend fun PlayerScript.machine(professor: NpcRef) {
        me("What does this machine do?")
        npc(
            professor,
            "Nothing at the moment... It's broken. It's meant to be a transmutation machine.",
            "It has also spent time as a time travel machine, and a dramatic lightning generator, and a thing for generating monsters.",
        )
    }

    private suspend fun PlayerScript.house(professor: NpcRef) {
        me("Is this your house?")
        npc(professor, "No, I'm just one of the tenants. It belongs to the count who lives in the basement.")
    }

    /** "Have you found anything yet?" with the parts the player carries (Wiki "Returning the parts"). */
    private suspend fun PlayerScript.returning(professor: NpcRef) {
        val p = player
        npc(professor, "Have you found anything yet?")
        val tube = p.has(Objs.RUBBER_TUBE)
        val gauge = p.has(Objs.PRESSURE_GAUGE)
        val can = p.has(Objs.OIL_CAN)
        when {
            tube && gauge && can -> {
                me("I have everything!")
                npc(professor, "Give 'em here then.")
                val taken =
                    p.transaction("ernest parts") {
                        remove(Invs.INV, Objs.RUBBER_TUBE, 1) && remove(Invs.INV, Objs.PRESSURE_GAUGE, 1) &&
                            remove(Invs.INV, Objs.OIL_CAN, 1)
                    }
                if (!taken) return
                box("You give the rubber tube, a pressure gauge,")
                box("and a can of oil to the professor.")
                npc(professor, "Let's get this fixed then.")
                box("Oddenstein starts up the machine.")
                box("The machine hums and shakes.")
                box("The machine turns Ernest back into human form.")
                // The Wiki gives "Thank you sir / Thank you m'lady / Thank you" by gender; the plugin API has none, so the neutral one.
                npcAs(Npcs.ERNEST, "Ernest", "Thank you. It was dreadfully irritating being a chicken. How can I ever thank you?")
                me("Well a cash reward is always nice...")
                npcAs(Npcs.ERNEST, "Ernest", "Of course, of course.")
                QuestCompletion.complete(p, Quests.ERNEST_THE_CHICKEN, REWARD)
            }

            !tube && !gauge && !can -> {
                me("I'm afraid I don't have any of them yet!")
                npc(professor, "I need a rubber tube, a pressure gauge and a can of oil. Then your friend can stop being a chicken.")
            }

            tube && !gauge && !can -> {
                partial(
                    professor,
                    "I've only got the rubber tube so far.",
                    "It's a good start, but I'll need my pressure gauge and oil can too.",
                )
            }

            !tube && gauge && !can -> {
                partial(
                    professor,
                    "I've only got the pressure gauge so far.",
                    "It's a good start, but I'll need my oil can and rubber tube too.",
                )
            }

            !tube && !gauge -> {
                partial(
                    professor,
                    "I've only got the oil can so far.",
                    "It's a good start, but I'll need my pressure gauge and rubber tube too.",
                )
            }

            tube && gauge -> {
                partial(professor, "I've got the rubber tube and the pressure gauge.", "That's good, but I still need my oil can.", "it")
            }

            tube -> {
                partial(professor, "I've got the oil can and the rubber tube.", "That's good, but I still need my pressure gauge.", "it")
            }

            else -> {
                partial(professor, "I've got the oil can and the pressure gauge.", "That's good, but I still need my rubber tube.", "it")
            }
        }
    }

    private suspend fun PlayerScript.partial(professor: NpcRef, said: String, reply: String, pronoun: String = "them") {
        me(said)
        npc(professor, reply)
        me("Ok, I'll go and look for $pronoun.")
    }

    // ---- the manor

    private suspend fun PlayerScript.searchFountain() {
        val p = player
        if (p.has(Objs.PRESSURE_GAUGE) || p.flag(FLAG_GAUGE_TAKEN)) {
            me("It's full of dead fish!")
            return
        }
        me("There seems to be a pressure gauge in here...")
        if (!p.flag(FLAG_POISONED)) {
            box("Something in the water bites you.")
            p.say("Ow!")
            me("... and a lot of piranhas! I can't get the gauge out.")
            return
        }
        me("... and a lot of dead fish.")
        if (p.giveOrDrop(Objs.PRESSURE_GAUGE, 1, "ernest pressure gauge")) {
            p.setFlag(FLAG_GAUGE_TAKEN, true)
            box("You get the pressure gauge from the fountain.")
        }
    }

    private suspend fun PlayerScript.digCompost() {
        val p = player
        // Wiki "Key (Ernest the Chicken)": found with a spade on the compost heap during the quest.
        if (p.stage < STARTED || p.has(Objs.CLOSET_KEY)) {
            p.message("Nothing interesting happens.")
            return
        }
        p.message("You dig through the compost...")
        if (p.giveOrDrop(Objs.CLOSET_KEY, 1, "ernest closet key")) p.message("... and find a small key.")
    }

    /**
     * The closet door of the rubber tube's room: the key opens it. The door's open model is not paired in `doors.tsv`, so the
     * player is moved through to the other side of the wall ([stepAcross], OPEN-QUA6); the "locked" wording is ours (OPEN-QU4).
     */
    private fun PlayerScript.closetDoor(door: LocRef) {
        val p = player
        if (!p.has(Objs.CLOSET_KEY)) {
            p.message("The door is locked.")
            return
        }
        p.message("You unlock the door.")
        p.stepAcross(door)
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_SEARCH = 1
        const val OP_OPEN = 1

        /** Quest stages: 1 started, 2 the professor explained (ours, OPEN-QU1); 3 is the cache's `endstate`. */
        const val STARTED = 1
        const val EXPLAINED = 2

        /** Account flags: the piranhas are dead (the fountain stays poisoned), the gauge was taken. */
        const val FLAG_POISONED = "quests.ernest.poisoned"
        const val FLAG_GAUGE_TAKEN = "quests.ernest.gauge"

        /** Wiki "Ernest the Chicken" rewards: 4 quest points, 300 coins. The 3 November 2026 antique lamp is OPEN-QUA5. */
        val REWARD = QuestReward(model = Objs.COINS, items = listOf(Objs.COINS to 300))
    }
}
