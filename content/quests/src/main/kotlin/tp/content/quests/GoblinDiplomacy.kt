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
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Goblin Diplomacy (docs/mechanics/quest-goblin-diplomacy.md): General Bentnoze and General Wartface want goblin armour in a new
 * colour; the player dyes goblin mail orange, then blue, and finally shows plain (brown) mail. The quest variable (varbit 2378) is
 * 0 / 1 started / 2 orange shown, blue wanted / 3 blue shown, brown wanted / 6 finished (cache `endstate`; the stages between
 * are ours, OPEN-QU1). Aggie's dye service and the dyes live here too (red + yellow dye make orange dye). Dialogue is the Wiki
 * "Transcript:Goblin Diplomacy", "Transcript:General Bentnoze" and "Transcript:Aggie".
 */
public class GoblinDiplomacy : ContentPlugin {
    override fun ContentScope.register() {
        for (general in GENERALS) {
            onOpNpc(general, OP_TALK) {
                player.stopInteraction()
                generalsTalk()
            }
            for (mail in MAILS) {
                onUseOnNpc(mail, general) {
                    player.stopInteraction()
                    showMail(mail)
                }
            }
        }
        onOpNpc(Npcs.CATWALK_GOBLIN_BROWN, OP_TALK) {
            player.stopInteraction()
            grubfootTalk()
        }
        for (crate in CRATES) {
            onOpLoc(crate, OP_SEARCH) {
                player.stopInteraction()
                searchCrate(crate)
            }
        }
        // Dyes: red + yellow make orange; orange and blue dye the plain goblin mail.
        onUseOnItem(Objs.REDDYE, Objs.YELLOWDYE) {
            val ok =
                transaction("goblin diplomacy mix orange dye") {
                    remove(Invs.INV, Objs.REDDYE, 1) && remove(Invs.INV, Objs.YELLOWDYE, 1) && add(Invs.INV, Objs.ORANGEDYE, 1)
                }
            if (ok) message("You mix the red and yellow dyes to make orange dye.")
        }
        onUseOnItem(Objs.ORANGEDYE, Objs.GOBLIN_ARMOUR) { dye(Objs.ORANGEDYE, Objs.GOBLIN_ARMOUR_ORANGE, "orange") }
        onUseOnItem(Objs.BLUEDYE, Objs.GOBLIN_ARMOUR) { dye(Objs.BLUEDYE, Objs.GOBLIN_ARMOUR_DARKBLUE, "blue") }
        for (aggie in intArrayOf(Npcs.AGGIE_1OP, Npcs.AGGIE_2OPS)) {
            onOpNpc(aggie, OP_TALK) {
                player.stopInteraction()
                aggieTalk(it)
            }
        }
        onOpNpc(Npcs.AGGIE_2OPS, OP_DYES) {
            player.stopInteraction()
            npc(it, "What sort of dye would you like? Red, yellow or blue?")
            dyeChoice(it)
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.GOBLIN_DIPLOMACY) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        when (stage) {
            0 -> {
                listOf("I can start this quest by speaking to General Bentnoze or General Wartface in Goblin Village.")
            }

            STARTED -> {
                listOf(
                    "The goblin generals argue about the colour of their armour. They want to see orange goblin mail.",
                    "Goblin mail is in the crates of the village; Aggie in Draynor Village makes dyes.",
                )
            }

            ORANGE_SHOWN -> {
                listOf("The generals did not like orange. They want to see blue goblin mail.")
            }

            BLUE_SHOWN -> {
                listOf("The generals did not like blue either. They want to see plain brown goblin mail.")
            }

            else -> {
                listOf("The generals settled on brown armour and rewarded me.")
            }
        }

    private val PlayerRef.stage: Int get() = Quests.GOBLIN_DIPLOMACY.stage(this)

    // ---- speakers

    private suspend fun PlayerScript.ben(vararg lines: String) = npcAs(Npcs.GENERAL_BENTNOZE_RED, "General Bentnoze", *lines)

    private suspend fun PlayerScript.war(vararg lines: String) = npcAs(Npcs.GENERAL_WARTFACE_GREEN, "General Wartface", *lines)

    private suspend fun PlayerScript.grub(vararg lines: String) = npcAs(Npcs.CATWALK_GOBLIN_BROWN, "Grubfoot", *lines)

    // ---- the generals

    private suspend fun PlayerScript.generalsTalk() {
        val p = player
        // Standard dialogue (Wiki "Transcript:General Bentnoze") once the quest is done; the argument before.
        if (p.stage >= END) return standard()
        when (p.random(RANDOM_DIALOGUES)) {
            0 -> {
                ben("All goblins should wear red armour!")
                war("Not red! Red armour make you look fat.")
                ben("Everything make YOU look fat!")
                war("Shut up!")
                ben("Fatty!")
                war("SHUT UP!")
                ben("Even this human think you look fat! Don't you, human?")
                me("Um...")
                when (choice("Select an option", "Yes, he looks fat.", "No, he doesn't look fat.", "I'll leave you to it.")) {
                    1 -> {
                        me("Yes, he looks fat!")
                        ben("Ha ha! See, fatty? Even human think you fat!")
                        war("Me not care what human think! Human ugly!")
                    }

                    2 -> {
                        me("No, he doesn't look fat.")
                        ben("Shut up human! Wartface fat and human stupid!")
                        war("Shut up Bentnoze!")
                    }

                    else -> {
                        return
                    }
                }
            }

            1 -> {
                war("I tell all goblins in village to wear green armour now!")
                ben("They not listen to you! I already tell them wear red armour!")
                war("They listen to me not you! They know me bigger general!")
                ben("Me bigger general! They listen to me!")
                war("Human! What colour armour they wearing out there?")
                me("Half of them are wearing red and half of them green.")
                war("Shut up human! They wearing green armour really! Human lying because he scared of you!")
                ben("Human scared of me not you? Then you think me bigger general!")
                war("What? Me mean...", "Shut up! Me bigger general!")
            }

            2 -> {
                war("We should wear green armour!")
                ben("Green armour? Are you stupid?")
                war("You stupid! Only stupid goblins think red armour better!")
                ben("No they don't! Me think red armour better!")
                war("That because you stupid!")
                ben("Me not stupid!")
                war("Then why you not like green armour?")
                ben("Because red armour better!")
                war("Only stupid goblins think that! You stupid!")
            }

            3 -> {
                war("Green armour best.")
                ben("No no red every time.")
                war("Go away human, we busy.")
            }

            else -> {
                ben("Red armour best.")
                war("No it has to be green!")
                ben("Go away human, we busy.")
            }
        }
        argueMenu()
    }

    /** The menu every argument ends in (Wiki "Transcript:Goblin Diplomacy"); the answers return to it. */
    private suspend fun PlayerScript.argueMenu() {
        var why = false
        var century = false
        while (true) {
            val options = ArrayList<String>()
            if (!why) options += WHY
            if (why && !century) options += CENTURY
            options += PEACE
            options += PICK
            options += LEAVE
            val pick = options[choice("Select an option", *options.toTypedArray()) - 1]
            when (pick) {
                WHY -> {
                    me(WHY)
                    ben(
                        "We decide to celebrate goblin new century by changing colour of our armour. Brown get boring after a bit. " +
                            "We want change.",
                    )
                    war(
                        "Problem is, we can't agree on new colour. We think maybe we use old goblin tribe colours, but Bentnoze " +
                            "from rubbish Thorobshuun tribe and me from much better Garagorshuun tribe.",
                    )
                    ben("Thorobshuun tribe colour is red! Way better than silly green of Garagorshuun tribe.")
                    war("Shut up!")
                    why = true
                }

                CENTURY -> {
                    me(CENTURY)
                    ben("Goblin century mark year of battle on Plain of Mud. That when Big High War God give goblin commandments.")
                    me("Big High War God?")
                    war("Big High War God is god for goblins. He take us and make us strong, not stupid like humans.")
                    ben("Me bet Big High War God would pick red armour.")
                    war("Shut up! Big High War God would hate you!")
                    century = true
                }

                PEACE -> {
                    me(PEACE)
                    war("Goblins not so good at peace. Maybe peace be okay though, as long as it peace wearing green armour.")
                    ben("But green too much like skin. Nearly make you look naked!")
                }

                PICK -> {
                    me(PICK)
                    war("Yes, as long as you pick green.")
                    ben("No you have to pick red!")
                    if (pickColour()) return
                }

                else -> {
                    me(LEAVE)
                    return
                }
            }
        }
    }

    /** "Do you want me to pick an armour colour for you?"; true when the conversation ended. */
    private suspend fun PlayerScript.pickColour(): Boolean {
        while (true) {
            when (choice("Select an option", "You should wear red.", "You should wear green.", "What about a different colour?", LEAVE)) {
                1 -> {
                    me("You should wear red.")
                    ben("See? Even stupid human think red best. Now we all wear red!")
                    war("Human not know anything! If we wear red then whole village be ugly like YOU!")
                    ben("Go away human. You not helping.")
                    return false
                }

                2 -> {
                    me("You should wear green.")
                    war("Green! We all wear green now, human has decided!")
                    ben("Why we have to do what human say? He not boss of us!")
                    war("No but he agree with me!")
                    ben("That prove you a filthy human-lover!")
                    war("Me hate humans! This human just happen to be right!")
                    ben("Go away human. You not know anything.")
                    return false
                }

                3 -> {
                    me("What about a different colour?")
                    ben("That would mean me wrong... but at least Wartface not right!")
                    war(
                        "Well Bentnoze never been right in his life. Still, maybe new colour good, but will have to see armour " +
                            "before decide.",
                    )
                    ben("Human! You bring us armour in new colour!")
                    return newColour()
                }

                else -> {
                    me(LEAVE)
                    return true
                }
            }
        }
    }

    /** The quest offer after "What about a different colour?" (during the quest the generals repeat their order, OPEN-QUA8). */
    private suspend fun PlayerScript.newColour(): Boolean {
        val p = player
        if (p.stage == 0) {
            if (choice("Start the Goblin Diplomacy quest?", "Yes.", "No.") != 1) {
                me("Actually, I think I'll leave you to it.")
                return true
            }
            me("I can do that.")
            Quests.GOBLIN_DIPLOMACY.advance(p, STARTED)
            war("What colour we try?")
            ben("Orange armour might be good.")
            war("Yep, bring us orange armour.")
            me("How am I meant to get orange armour?")
            ben("Well first you get goblin armour...")
            war("...and then you dye it orange!")
            ben("Even human should be able to work that out!")
        } else {
            val colour =
                if (p.stage == STARTED) {
                    "orange"
                } else if (p.stage == ORANGE_SHOWN) {
                    "blue"
                } else {
                    "brown"
                }
            war("We tell you get $colour armour.")
        }
        while (true) {
            when (choice("Select an option", "Where do I get goblin armour?", "Where do I get dye?", "Okay, I'll be back soon.")) {
                1 -> {
                    me("Where do I get goblin armour?")
                    war("There some spare armour around village somewhere. You can take that.")
                    ben("It in crates somewhere. Can't remember which crates now.")
                }

                2 -> {
                    me("Where do I get dye?")
                    ben("You go north of here into wilderness. There you find many ways to die!")
                    me("No, D-Y-E, not D-I-E.")
                    war("Stupid Bentnoze, you not know how to spell!")
                    ben("Shut up Wartface!")
                    me("Do you know where I can get dye?")
                    ben("Me not know where dye come from.")
                    me("Well where did you get your red and green dye from?")
                    war("Some goblin or other, he steal it. Say he steal it from old witch in Draynor Village.")
                    ben("Maybe you can get more dye from her?")
                }

                else -> {
                    me("Okay, I'll be back soon.")
                    return true
                }
            }
        }
    }

    /** The Wiki's standard dialogue of the generals once the quest is done (one of 5, "Transcript:General Bentnoze"). */
    private suspend fun PlayerScript.standard() {
        when (player.random(STANDARD_DIALOGUES)) {
            0 -> {
                ben("Shut up Grubfoot!")
                grub("But me not say anything!")
                war("Shut up Grubfoot!")
                ben("Shut up Wartface!")
                war("Shut up Bentnoze!")
                grub("Shut up generals!")
                ben("Shut up Grubfoot!")
                war("Shut up Grubfoot!")
                me("Shut up goblins!")
            }

            1 -> {
                ben("What we do now Wartface?")
                war("Me dunno... maybe we change armour colour again.")
                ben("Human, what colour you think best?")
                me("What about brown?")
                war("Yeah brown is nice.")
                ben("Thanks human.")
            }

            2 -> {
                ben("Our soldiers need better weapons. We give them all warhammers!")
                war("No no give them battleaxes!")
                ben("Axes stupid! They things you cut wood with!")
                war("But hammers things you put nails in wood with! At least axes sharp!")
                ben("You not need sharp when you have strong goblin swinging big lump of iron!")
                me("Do you want me to...")
                war("Shut up human, we sort this one out ourselves!")
            }

            3 -> {
                war("Me tell Mudknuckles make worm stew for dinner.")
                ben("Me not like worm stew! Tell him make beetle pie!")
                war("Beetle pie give you wind! You smell bad enough already!")
            }

            else -> {
                war("Now you've solved our argument, we gotta think of something else to do.")
                ben("Yep, we bored now.")
            }
        }
    }

    private suspend fun PlayerScript.grubfootTalk() {
        val p = player
        when (p.stage) {
            ORANGE_SHOWN -> {
                grub("Me not like this orange armour. Make me look like that thing.")
                me("Look like what thing?")
                grub("That fruit thing. The one that orange. What it called?")
                me("An orange?")
                grub("That right. This armour make me look same colour as orange-fruit.")
                war("Shut up Grubfoot!")
            }

            BLUE_SHOWN -> {
                grub("Me not like this blue colour.")
                me("Why not?")
                grub("Me not know. It just make me feel...")
                me("Makes you feel blue?")
                grub("Makes me feel kind of sad.")
                war("Shut up Grubfoot!")
            }

            else -> {
                grub("Grubfoot wear red armour! Grubfoot wear green armour!", "Why they not make up their minds?")
                war("Shut up Grubfoot!")
            }
        }
    }

    /** Showing a goblin mail of colour [mail] to the generals (Wiki "Showing the Generals different goblin armours"). */
    private suspend fun PlayerScript.showMail(mail: Int) {
        val p = player
        val stage = p.stage
        if (stage < STARTED || stage >= END) {
            p.message("Nothing interesting happens.")
            return
        }
        val wanted =
            when (stage) {
                STARTED -> Objs.GOBLIN_ARMOUR_ORANGE
                ORANGE_SHOWN -> Objs.GOBLIN_ARMOUR_DARKBLUE
                else -> Objs.GOBLIN_ARMOUR
            }
        if (mail != wanted) {
            me("What do you think of this colour?")
            ben("That wrong colour.")
            war("We tell you get ${colourOf(wanted)} armour.")
            return
        }
        if (!p.take(mail, 1, "goblin diplomacy mail")) return
        when (stage) {
            STARTED -> {
                me("I have some orange armour here.")
                box("A cutscene begins with the Generals fetching Grubfoot...")
                war("Grubfoot!")
                grub("Yes General Wartface?")
                war("Put on this armour!")
                box("Grubfoot goes into the changing room with orange-painted goblin mail.")
                grub("What do you think?")
                war("No I don't like that much.")
                ben("It clashes with skin colour.")
                war("We need darker colour, like blue.")
                ben("Yeah blue might be good.")
                war("Human! Get us blue armour!")
                Quests.GOBLIN_DIPLOMACY.advance(p, ORANGE_SHOWN)
            }

            ORANGE_SHOWN -> {
                me("I have some blue armour here.")
                box("Another cutscene begins with Grubfoot still wearing the orange armour.")
                war("Grubfoot!")
                grub("Yes General Wartface?")
                war("Put on this armour!")
                box("Grubfoot goes into the changing room, switching his orange armour to blue armour.")
                grub("What do you think?")
                ben("That not right. Not goblin colour at all.")
                war("Goblins wear dark earthy colours like brown.")
                ben("Yeah brown might be good.")
                war("Human! Get us brown armour!")
                me("But I thought brown was the armour you were changing from...", "Never mind, anything is worth a try.")
                Quests.GOBLIN_DIPLOMACY.advance(p, BLUE_SHOWN)
            }

            else -> {
                me("I have some brown armour here.")
                box("A cutscene begins...")
                war("Grubfoot!")
                grub("Yes General Wartface?")
                war("Put on this armour!")
                grub("What do you think?")
                war("That colour quite nice. Me can see myself wearing that.")
                ben("It a deal then. Brown armour it is.")
                war("Thank you for sorting out argument, human. You have reward now.")
                me("Thanks... I think...")
                QuestCompletion.complete(p, Quests.GOBLIN_DIPLOMACY, REWARD)
            }
        }
    }

    private fun colourOf(mail: Int): String =
        when (mail) {
            Objs.GOBLIN_ARMOUR_ORANGE -> "orange"
            Objs.GOBLIN_ARMOUR_DARKBLUE -> "blue"
            else -> "brown"
        }

    private suspend fun PlayerScript.searchCrate(crate: Int) {
        val p = player
        val key = "quests.goblin.crate.$crate"
        if (p.flag(key)) {
            box("You search the crate, but but find nothing of interest.")
            return
        }
        if (!p.giveOrDrop(Objs.GOBLIN_ARMOUR, 1, "goblin diplomacy crate")) return
        p.setFlag(key, true)
        box("You find some goblin mail in the crate.")
    }

    private fun PlayerRef.dye(dye: Int, result: Int, colour: String) {
        val ok =
            transaction("goblin diplomacy dye") {
                remove(Invs.INV, dye, 1) && remove(Invs.INV, Objs.GOBLIN_ARMOUR, 1) &&
                    add(Invs.INV, result, 1)
            }
        if (ok) message("You dye the goblin mail $colour.")
    }

    // ---- Aggie (Wiki "Transcript:Aggie")

    private suspend fun PlayerScript.aggieTalk(aggie: NpcRef) {
        npc(aggie, "What can I help you with?")
        when (
            choice(
                "Select an Option",
                "What could you make for me?",
                "Cool, do you turn people into frogs?",
                "You mad old witch, you can't help me.",
                "Can you make dyes for me please?",
            )
        ) {
            1 -> {
                me("What could you make for me?")
                npc(
                    aggie,
                    "I mostly just make what I find pretty. I sometimes make dye for the women's clothes to brighten the place up. " +
                        "I can make red, yellow and blue dyes. If you'd like some, just bring me the appropriate ingredients.",
                )
                dyeChoice(aggie, withDecline = true)
            }

            2 -> {
                me("Cool, do you turn people into frogs?")
                npc(
                    aggie,
                    "Oh, not for years, but if you meet a talking chicken, you have probably met the professor in the manor north " +
                        "of here. A few years ago it was flying fish. That machine is a menace.",
                )
            }

            3 -> {
                me("You mad old witch, you can't help me.")
                insulted(aggie)
            }

            4 -> {
                me("Can you make dyes for me please?")
                npc(aggie, "What sort of dye would you like? Red, yellow or blue?")
                dyeChoice(aggie)
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.insulted(aggie: NpcRef) {
        val p = player
        val coins = p.inv(Invs.INV).total(Objs.COINS)
        when {
            coins in FINE_LOW_FROM..FINE_LOW_TO -> {
                npc(aggie, "Oh, you like to call a witch names do you?")
                if (p.take(Objs.COINS, FINE_LOW, "aggie fine")) box("Aggie waves her hands about, and you seem to be five coins poorer.")
                npc(aggie, "That's a fine for insulting a witch. You should learn some respect.")
            }

            coins >= FINE_HIGH_FROM -> {
                npc(aggie, "Oh, you like to call a witch names do you?")
                if (p.take(Objs.COINS, FINE_HIGH, "aggie fine")) box("Aggie waves her hands about, and you seem to be 20 coins poorer.")
                npc(aggie, "That's a fine for insulting a witch. You should learn some respect.")
            }

            p.has(Objs.POT_FLOUR) -> {
                npc(aggie, "Oh, you like to call a witch names do you?")
                if (p.take(Objs.POT_FLOUR, 1, "aggie flour")) box("Aggie waves her hands about, and you seem to have a pot of flour less.")
                npc(aggie, "Thank you for your kind present of some flour. I am sure you never meant to insult me.")
            }

            else -> {
                npc(aggie, "You should be careful about insulting a witch. You never know what shape you could wake up in.")
            }
        }
    }

    private class DyeColour(
        val name: String,
        val dye: Int,
        val ingredient: Int,
        val count: Int,
        val needs: String,
        val missing: String,
        val whereOption: String,
        val where: String,
    )

    /** The colour menu of Aggie's dyes; [withDecline] adds the "happy the colour I am" way out of the first conversation. */
    private suspend fun PlayerScript.dyeChoice(aggie: NpcRef, withDecline: Boolean = false) {
        val options =
            arrayOf("What do you need to make red dye?", "What do you need to make yellow dye?", "What do you need to make blue dye?")
        val picked =
            if (withDecline) {
                choice("Select an Option", *options, "No thanks, I am happy the colour I am.")
            } else {
                choice("Select an Option", *options)
            }
        if (picked !in 1..DYES.size) {
            if (picked == DYES.size + 1) {
                me("No thanks, I am happy the colour I am.")
                npc(aggie, "You are easily pleased with yourself then. When you need dyes, come to me.")
            }
            return
        }
        var colour = DYES[picked - 1]
        while (true) {
            me("What do you need to make ${colour.name} dye?")
            npc(aggie, colour.needs)
            val next = DYES.filter { it !== colour }
            when (
                choice(
                    "Select an Option",
                    "Okay, make me some ${colour.name} dye please.",
                    "I don't think I have all the ingredients yet.",
                    "I can do without dye at that price.",
                    colour.whereOption,
                    "What other colours can you make?",
                )
            ) {
                1 -> {
                    me("Okay, make me some ${colour.name} dye please.")
                    makeDye(colour)
                    return
                }

                2 -> {
                    me("I don't think I have all the ingredients yet.")
                    npc(aggie, "You know what you need to get, now come back when you have them. Goodbye for now.")
                    return
                }

                3 -> {
                    me("I can do without dye at that price.")
                    npc(aggie, "That's your choice, but I would think you have killed for less. I can see it in your eyes.")
                    return
                }

                4 -> {
                    me(colour.whereOption)
                    npc(aggie, colour.where)
                    if (choice("Select an Option", "What other colours can you make?", "Thanks") != 1) {
                        me("Thanks.")
                        npc(aggie, "You're welcome!")
                        return
                    }
                    me("What other colours can you make?")
                    npc(aggie, "Red, yellow and blue. Which one would you like?")
                    colour = next[nextPick(next) ?: return]
                }

                OPTION_5 -> {
                    me("What other colours can you make?")
                    npc(aggie, "Red, yellow and blue. Which one would you like?")
                    colour = next[nextPick(next) ?: return]
                }

                else -> {
                    return
                }
            }
        }
    }

    /** The menu of the other two colours; null when closed. */
    private suspend fun PlayerScript.nextPick(others: List<DyeColour>): Int? {
        val pick = choice("Select an Option", *others.map { "What do you need to make ${it.name} dye?" }.toTypedArray())
        return if (pick in 1..others.size) pick - 1 else null
    }

    private suspend fun PlayerScript.makeDye(colour: DyeColour) {
        val p = player
        if (!p.has(colour.ingredient, colour.count)) {
            box(colour.missing)
            return
        }
        // The Wiki prices every dye at 5 coins; its transcript has no line for too few coins, so the message is ours (OPEN-QU4).
        val ok =
            p.transaction("aggie ${colour.name} dye") {
                remove(Invs.INV, Objs.COINS, DYE_PRICE) && remove(Invs.INV, colour.ingredient, colour.count) && add(Invs.INV, colour.dye, 1)
            }
        if (!ok) p.message("You don't have enough coins to pay Aggie.")
    }

    private companion object {
        const val OP_TALK = 1

        /** The fifth entry of a chat menu. */
        const val OPTION_5 = 5
        const val OP_SEARCH = 1

        /** Aggie's cache npc 121 ops: "Talk-to", -, "Dyes". */
        const val OP_DYES = 3

        /** Quest stages (ours, OPEN-QU1) and the cache's `endstate` 6. */
        const val STARTED = 1
        const val ORANGE_SHOWN = 2
        const val BLUE_SHOWN = 3
        const val END = 6

        /** The generals' dialogue count (Wiki: dialogues 1-5; 6-8 need Another Slice of H.A.M.). */
        const val RANDOM_DIALOGUES = 5
        const val STANDARD_DIALOGUES = 5

        /** Wiki "Transcript:Aggie": a dye costs 5 coins; the fine for insulting her is 5 coins at 21-100 coins carried, 20 from 101. */
        const val DYE_PRICE = 5
        const val FINE_LOW_FROM = 21L
        const val FINE_LOW_TO = 100L
        const val FINE_HIGH_FROM = 101L
        const val FINE_LOW = 5
        const val FINE_HIGH = 20

        const val WHY = "Why are you arguing about the colour of your armour?"
        const val CENTURY = "What is the goblin new century?"
        const val PEACE = "Wouldn't you prefer peace?"
        const val PICK = "Do you want me to pick an armour colour for you?"
        const val LEAVE = "I'll leave you to it."

        val GENERALS = intArrayOf(Npcs.GENERAL_BENTNOZE_RED, Npcs.GENERAL_WARTFACE_GREEN)
        val MAILS = intArrayOf(Objs.GOBLIN_ARMOUR, Objs.GOBLIN_ARMOUR_ORANGE, Objs.GOBLIN_ARMOUR_DARKBLUE)
        val CRATES =
            intArrayOf(
                Locs.GOBLIN_OUTPOST_LARGE_CRATE_ARMOUR1,
                Locs.GOBLIN_OUTPOST_LARGE_CRATE_ARMOUR2,
                Locs.GOBLIN_OUTPOST_LARGE_CRATE_ARMOUR3,
            )

        val DYES =
            listOf(
                DyeColour(
                    "red",
                    Objs.REDDYE,
                    Objs.REDBERRIES,
                    3,
                    "3 lots of redberries and 5 coins to you.",
                    "You don't have enough berries to make the red dye.",
                    "Where do I get redberries?",
                    "I pick mine from the woods south of Varrock. The food shop in Port Sarim sometimes has some as well.",
                ),
                DyeColour(
                    "yellow",
                    Objs.YELLOWDYE,
                    Objs.ONION,
                    2,
                    "Yellow is a strange colour to get, comes from onion skins. I need 2 onions and 5 coins to make yellow dye.",
                    "You don't have enough onions to make the yellow dye.",
                    "Where do I get onions?",
                    "There are some onions growing on a farm to the East of here, next to the sheep field.",
                ),
                DyeColour(
                    "blue",
                    Objs.BLUEDYE,
                    Objs.WOADLEAF,
                    2,
                    "2 woad leaves and 5 coins to you.",
                    "You don't have enough woad leaves to make the blue dye.",
                    "Where do I get woad leaves?",
                    "Woad leaves are fairly hard to find. My other customers tell me the chief gardener in Falador grows them.",
                ),
            )

        /** Wiki "Goblin Diplomacy" rewards: 5 quest points, 200 Crafting XP (cache col 33 = (12, 2000 tenths)), a gold bar. */
        val REWARD =
            QuestReward(
                model = Objs.GOBLIN_ARMOUR,
                xp = listOf(Stats.CRAFTING to 200.0),
                items = listOf(Objs.GOLD_BAR to 1),
            )
    }
}
