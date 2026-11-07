@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.TimerId
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Temple of the Eye (docs/mechanics/quest-temple-of-the-eye.md): Wizard Persten's eye amulet leads through the Mage
 * of Zamorak, the
 * Dark Mage of the Abyss, Archmage Sedridor and Traiborn's apprentices to the temple of the Guardians of the Rift,
 * where a tutorial
 * round of the minigame (`content/skills` `GuardiansOfTheRift`, tutorial floor) closes the rift. The quest variable
 * is varbit 13738
 * (end 130); the cache's multi-NPCs show Persten in Al Kharid up to 60, the apprentices in the tower from 70 and
 * Persten there from
 * 85 to 90. Dialogue is the Wiki "Transcript:Temple of the Eye".
 */
public class TempleOfTheEye : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.TOTE_PERSTEN_ALKHARID_PARENT, OP_TALK) {
            player.stopInteraction()
            perstenAlKharid(it)
        }
        onOpNpc(Npcs.TOTE_PERSTEN_TOWER_PARENT, OP_TALK) {
            player.stopInteraction()
            readyConversation(it)
        }
        onOpNpc(Npcs.TOTE_PERSTEN_TEMPLE, OP_TALK) {
            player.stopInteraction()
            perstenTemple(it)
        }
        onOpNpc(Npcs.TEA_SELLER, OP_TALK) {
            player.stopInteraction()
            teaSellerTalk(it)
        }
        onOpNpc(Npcs.RCU_ZAMMY_MAGE2, OP_TALK) {
            player.stopInteraction()
            darkMageEntry(it)
        }
        onUseOnNpc(Objs.TOTE_AMULET, Npcs.RCU_ZAMMY_MAGE2) {
            player.stopInteraction()
            if (Tote.stage(player) in Tote.APPRAISED..Tote.TEA_DRUNK) darkMageAmulet(it) else player.message("Nothing interesting happens.")
        }
        onUseOnNpc(Objs.TOTE_AMULET, Npcs.RCU_ZAMMY_MAGE1_EDGE) {
            player.stopInteraction()
            mageAmulet(it)
        }
        onUseOnNpc(Objs.TOTE_CUP_OF_TEA_STRONG, Npcs.RCU_ZAMMY_MAGE1_EDGE) {
            player.stopInteraction()
            mageAmulet(it)
        }
        for ((parent, who) in listOf(
            Npcs.TOTE_TAMARA_WIZARD_TOWER_PARENT to "Tamara",
            Npcs.TOTE_CORDELIA_WIZARD_TOWER_PARENT to "Cordelia",
            Npcs.TOTE_FELIX_WIZARD_TOWER_PARENT to "Felix",
        )) {
            onOpNpc(parent, OP_TALK) {
                player.stopInteraction()
                towerApprentice(it, who)
            }
        }
        for ((type, who) in listOf(
            Npcs.TOTE_TAMARA_TEMPLE to "Tamara",
            Npcs.TOTE_CORDELIA_TEMPLE to "Cordelia",
            Npcs.TOTE_FELIX_TEMPLE to "Felix",
        )) {
            onOpNpc(type, OP_TALK) {
                player.stopInteraction()
                templeApprentice(it, who)
            }
        }
        for ((i, energy) in ENERGY_LOCS.withIndex()) onOpLoc(energy, OP_TALK) { AbyssEnergy.touch(player, i) }
        onWorldStart {
            // Wiki maps: Persten (Al Kharid) 3284,3231 r=4, Tea Seller 3270,3413 r=4; the tower pair stands beside
            // Sedridor and the
            // apprentices
            // beside Traiborn (OPEN-TE3: no map for them; the Wiki lists the tower, floor 1).
            spawnNpc(Npcs.TOTE_PERSTEN_ALKHARID_PARENT, CoordGrid(PERSTEN_X, PERSTEN_Z, 0), PERSTEN_WANDER)
            spawnNpc(Npcs.TOTE_PERSTEN_TOWER_PARENT, CoordGrid(SEDRIDOR_X + 2, SEDRIDOR_Z + 1, 0), 0)
            spawnNpc(Npcs.TEA_SELLER, CoordGrid(TEA_X, TEA_Z, 0), TEA_WANDER)
            spawnNpc(Npcs.TOTE_TAMARA_WIZARD_TOWER_PARENT, CoordGrid(TOWER_X - 2, TOWER_Z, 1), 2)
            spawnNpc(Npcs.TOTE_CORDELIA_WIZARD_TOWER_PARENT, CoordGrid(TOWER_X + 1, TOWER_Z + 3, 1), 4)
            spawnNpc(Npcs.TOTE_FELIX_WIZARD_TOWER_PARENT, CoordGrid(TOWER_X, TOWER_Z, 1), 2)
        }
        onLogin { timer(POLL, POLL_TICKS, soft = true) { poll(this) } }
        events.on<JournalRequest> {
            if (it.quest == Quests.TEMPLE_OF_THE_EYE) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        listOf(
            JOURNAL[
                JOURNAL_STAGES.indexOfFirst { stage < it }.let {
                    if (it <
                        0
                    ) {
                        JOURNAL.size - 1
                    } else {
                        it
                    }
                },
            ],
        )

    // ---- Wizard Persten (Al Kharid)

    private suspend fun PlayerScript.perstenAlKharid(per: NpcRef) {
        val p = player
        when (val stage = Tote.stage(p)) {
            0 -> if (p.varbit(Varbits.TOTE_PRE_STORY) == 1) perstenReturn(per) else perstenIntro(per)
            in Tote.GOT_AMULET until Tote.INCANTATION -> perstenAmulet(per, stage)
            Tote.INCANTATION -> perstenIncantation(per)
            else -> perstenTeleport(per)
        }
    }

    private suspend fun PlayerScript.perstenIntro(per: NpcRef) {
        val p = player
        say(per, "Hello!", "> Hey there.")
        var asked = false
        while (true) {
            val labels =
                if (asked) {
                    arrayOf(
                        "So what are you doing in Al Kharid?",
                        "See you around.",
                    )
                } else {
                    arrayOf("Who are you?", "What's a wizard doing in Al Kharid?", "See you around.")
                }
            val pick = choice("Select an option", *labels)
            if (!asked && pick == 1) {
                say(
                    per,
                    "> Who are you?",
                    "What? You haven't heard of me? The great Mia Persten of the Order of Wizards? The leading " +
                        "researcher in " +
                        "teleportation magic? The creator of the Home Teleport spell?",
                    "> Oh, I'm sorry. I didn't mean to offend you. I'm $name.",
                    "No offence taken! My research tends to take me far from the Wizards' Tower. I've been all " +
                        "across Gielinor and even " +
                        "to some other planes of existence!",
                    "> Other planes of existence? Sounds exciting!",
                    "Exciting and terrifying! Brilliant, right?",
                )
                asked = true
                continue
            }
            if (pick == labels.size) return me("See you around.")
            if (asked) {
                say(
                    per,
                    "> So what are you doing in Al Kharid?",
                    "Should I not be in Al Kharid?",
                    "> Er... good question. I guess I just haven't seen a wizard around here before.",
                )
            } else {
                say(
                    per,
                    "> What's a wizard doing in Al Kharid?",
                    "Why shouldn't a wizard be in Al Kharid?",
                    "> Er... good question. I guess I just haven't seen a wizard around here before.",
                    "Well for a start, I'm not just any wizard. The name's Mia Persten. I'm the leading " +
                        "researcher in teleportation " +
                        "magic for the Order of Wizards.",
                    "> Pleased to meet you. I'm $name.",
                )
            }
            break
        }
        say(
            per,
            "Well, maybe you should start expecting the unexpected! That way you can spend less time worrying " +
                "about why I'm here, and " +
                "more time asking life's real questions.",
            "> Right... and what kind of questions are you referring to?",
            "Well, that's the thing. It's different for everyone! Why do trees grow from stumps substantially " +
                "faster than from seeds? " +
                "Where do meteorites come from? Why do we have to hold our arms out to teleport successfully?",
            "> I don't know. What are the answers?",
            "I don't know either!",
            "> So you're in Al Kharid to work out how trees grow?",
            "No, silly! I came here to buy something.",
            "> Something?",
        )
        if (p.baseLevel(Stats.RUNECRAFT) < REQUIRED_RUNECRAFT) {
            return say(
                per,
                "That's right! Something! Tell me, what do you know of runecrafting?",
                "> Not a lot really.",
                "A shame. Unfortunately, this something requires some understanding in that area.",
                "You need level 10 Runecraft to start this quest.",
            )
        }
        say(per, "That's right! Something! Tell me, what do you know of the Abyss?")
        if (!Quests.ENTER_THE_ABYSS.atLeast(p, AbyssOrb.STAGE_DONE)) {
            return say(
                per,
                "> Not a lot really.",
                "A shame. Unfortunately, this something requires somewhat of an abyssal understanding.",
                "You need to have completed Enter the Abyss to start this quest.",
            )
        }
        say(
            per,
            "> The Abyss? I know all about the Abyss.",
            "Really? It's quite unusual for an adventurer like yourself to have knowledge in that area.",
            "> Well I recently helped a group of Zamorakian wizards gain access to the Rune Essence Mine. In " +
                "return, they gave me " +
                "access to the Abyss.",
            "Zamorakian wizards? You do realise that the Z.M.I. are enemies to the Order of Wizards, right?",
            "> Maybe I shouldn't have mentioned that...",
            "Oh, don't worry about it. The Order of Wizards may be a Saradominist group, but I've never " +
                "personally had much time for " +
                "gods. I prefer knowledge to religion.",
            "Still, it's maybe best you don't tell anyone else. I can't imagine my fellow wizards will be happy " +
                "to know that you gave " +
                "away details on their secret mine.",
            "Enough about them though. You've just shared quite the revelation with me! Is it really true that " +
                "the Z.M.I. have found a " +
                "way to access the Abyss?",
            "> That's right.",
            "Well in that case, I think you might be the perfect person to aid me in a little task.",
            "> What kind of task?",
            "Well I can't go telling you that until you agree, can I? Wouldn't want you sharing my secrets.",
            "> You're not giving me much to go on here.",
            "There'll be quite the reward for you at the end!",
        )
        offer(per)
    }

    private suspend fun PlayerScript.perstenReturn(per: NpcRef) {
        npc(per, "Hello again! Have you changed your mind about helping me with that little task?")
        offer(per)
    }

    private suspend fun PlayerScript.offer(per: NpcRef) {
        if (choice("Select an option", "Yes.", "No.") != 1) {
            player.setVarbit(Varbits.TOTE_PRE_STORY, 1)
            return say(per, "> I'd rather not get involved.", "That's a shame. Well, you just let me know if you reconsider.")
        }
        say(
            per,
            "> Alright, what do you need from me?",
            "You'll help? Great! Well first, a question for you: you say the Z.M.I. have gained access to the " +
                "Abyss? By any chance, are " +
                "they using it to gain quick access to the runic altars for runecrafting purposes?",
            "> Yes, but how did you...",
            "I knew it! I've been wondering for some time how they've been creating so many runes. I'd never " +
                "considered that they'd " +
                "somehow gained access to the Abyss, but I now realise it was the obvious explanation.",
            "> So is that what you're after? You want access to the Abyss as well?",
            "Well, yes, but actually, no. It seems we've jumped ahead a bit. Let's go back to why I'm here.",
            "> The 'something' you bought?",
            "The 'something' I bought! Here, take a look.",
        )
        box("Persten shows you an amulet.")
        say(
            per,
            "A foolish archaeologist by the name of Asgarnia Smith recently found this washed up near the Ruins " +
                "of Uzer. Despite his " +
                "apparent archaeological credentials, he dismissed it as junk.",
            "Luckily, I was able to buy it from him before he disposed of it. Can you believe he thought the 100 " +
                "coins I paid was too " +
                "much? If only he knew the true value of something like this...",
            "> Why is it so valuable?",
            "You see that eye on it? That is the symbol of both the Order of Wizards and our home, the Wizards' " +
                "Tower. Thing is, that's " +
                "only the start.",
            "From what I can tell, this amulet is thousands of years old, but our order was only formed at the " +
                "dawn of the Fifth Age. " +
                "That wasn't even two hundred years ago!",
            "> But how...",
            "Hang on! I'm not done yet. I also inspected the amulet for magical energies and found something " +
                "fascinating! I recognised " +
                "two distinct magical signatures.",
            "The first is the same magic found in rune essence. That's already pretty exciting, but it's the " +
                "second that will blow your " +
                "mind!",
            "> What was it?",
            "I'm getting to that! My, you do like interrupting, don't you? Anyway, here's the big reveal for you: " +
                "the second signature " +
                "was energy from the Abyss!",
            "> So you have an amulet with the symbol of your order on it, but it predates your order by thousands " +
                "of years? It also " +
                "turned up miles from where your order is based, and is filled with energy from a " +
                "dimension that until recently, no one had " +
                "even been to?",
            "Now you see why this is so exciting!",
            "> So what do you need from me?",
            "I need to find out where this amulet came from. It should be possible to trace it using the abyssal " +
                "energy it still " +
                "contains. This is where you ask 'What's the catch?'",
            "> *sigh* What's the catch?",
            "I'm glad you asked, $name! Unfortunately, the spell needed to trace the amulet will only work " +
                "properly from inside the Abyss.",
            "> Oh I see where this is going. You don't have access to the Abyss, but I do.",
            "Precisely! While I don't care about the religious squabbling between the Z.M.I. and my order, I " +
                "still can't see them " +
                "wanting to work with me any time soon.",
            "> So I need to take this amulet to the Abyss and work out where it came from?",
            "Correct! I imagine you'll need the help of your Z.M.I. friends. Just maybe don't tell them who gave " +
                "it to you.",
            "> I'll visit the Mage of Zamorak in Varrock. He should be able to help me.",
            "Return to me once you are done. Oh, and don't forget the amulet!",
        )
        giveAmulet()
    }

    private suspend fun PlayerScript.giveAmulet() {
        val p = player
        if (p.give(Objs.TOTE_AMULET, 1, "tote amulet")) {
            Tote.set(p, Tote.GOT_AMULET)
            p.setVarbit(Varbits.TOTE_RECEIVED_AMULET_BEFORE, 1)
            itemBox(Objs.TOTE_AMULET, "Persten hands you the amulet.")
        } else {
            itemBox(Objs.TOTE_AMULET, "Persten tries to hand you the amulet, but you don't have enough room to take it.")
        }
    }

    private suspend fun PlayerScript.perstenAmulet(per: NpcRef, stage: Int) {
        val p = player
        if (stage == Tote.GOT_AMULET && !p.has(Objs.TOTE_AMULET) && p.varbit(Varbits.TOTE_RECEIVED_AMULET_BEFORE) == 0) {
            npc(per, "Hello again! Here, take this amulet to your Z.M.I. friend in Varrock. Hopefully he can tell us where it came from.")
            return giveAmulet()
        }
        npc(per, "Hello again! Any luck with that amulet?")
        if (!p.has(Objs.TOTE_AMULET) && stage < Tote.WASHED) {
            say(
                per,
                "> Sorry, but I lost it.",
                "You lost a one of a kind ancient artefact?",
                "> Yes...",
                "Well, good job I found it again. Here you go. Take it to your Z.M.I. friend in " +
                    "Varrock. Hopefully he can tell us where it came from.",
            )
            if (p.give(Objs.TOTE_AMULET, 1, "tote amulet")) itemBox(Objs.TOTE_AMULET, "Persten hands you an amulet.")
        } else {
            say(per, "> Not yet.", "You should take it to your Z.M.I. friend in Varrock. Hopefully he can tell us where it came from.")
        }
    }

    private suspend fun PlayerScript.perstenIncantation(per: NpcRef) {
        val p = player
        npc(per, "Hello again! Any luck with that amulet?")
        if (!p.has(Objs.TOTE_INCANTATION)) {
            return say(
                per,
                "> Yes! But I seem to have lost the incantation.",
                "No matter, I can give you a copy of mine. Try not to lose it this time!",
            ).also {
                if (p.give(Objs.TOTE_INCANTATION, 1, "tote incantation")) box("Persten hands you a copy of the incantation.")
            }
        }
        say(per, "> Yes! I have an incantation that could lead us to wherever it came from.", "Brilliant! Let's have a look.")
        box("You show the incantation to Persten and she quickly makes a copy for herself.")
        say(
            per,
            "Oh, fascinating! Hmm... But that would mean...",
            "> That would mean what?",
            "Shhh! I'm thinking.",
            "> ...",
            "Yes... Two... no three more! Silly me! Counted myself didn't I.",
            "Well it should be fine. I can't see them saying no. After all, it's their symbol as well...",
            "> What are you on about?",
            "Oh yes, you're here! Brilliant! You'll be pleased to know that I've worked out what we must do next.",
            "> And that is?",
            "Patience, young adventurer. Before we get to that, let me share with you my theory on where this " +
                "amulet came from.",
            "While you were gone, I was thinking about why the amulet would have magical energy from both rune " +
                "essence and the Abyss. " +
                "All of a sudden, it hit me!",
            "> Ouch?",
            "Quite the opposite! The source of this amulet must be an ancient teleportation matrix to the runic " +
                "altars, just like the " +
                "one the Z.M.I. have in the Abyss!",
            "This is good news, in case you were wondering! Access to a teleportation matrix like that will be " +
                "life changing when it " +
                "comes to my work!",
            "Of course, the rest of my order will be very happy as well. It'll completely remove the advantage " +
                "the Z.M.I. currently " +
                "have. Now there's a balance that Guthix himself would love!",
        )
        if (p.has(Objs.TOTE_AMULET)) {
            say(per, "Ah, but this talk of the Z.M.I. has reminded me of something. Could I have that amulet back?", "> Here you go.")
            p.take(Objs.TOTE_AMULET, 1, "tote amulet")
            box("You give the amulet back to Persten.")
        } else {
            say(
                per,
                "Ah, but this talk of the Z.M.I. has reminded me of something. You lost that amulet " +
                    "didn't you?",
                "> Maybe...",
                "Don't worry, I found it for you. I'll keep hold of it for now.",
            )
        }
        box("Persten casts a spell on the amulet.")
        say(
            per,
            "Ah, yes. Well, that's not surprising.",
            "> What is it?",
            "Your Z.M.I. friends cast a transmission spell on the amulet. Had we taken it with us, it would have " +
                "transmitted all sorts " +
                "of information back to them about the origin.",
            "It's not a problem. I suspected they might do something like this. The good news is that this means " +
                "they have no intention " +
                "of using that incantation themselves.",
            "Anyway, I've modified their spell to send false information to them. They'll be none the wiser.",
            "> I thought you didn't care about religion? Why don't you want the Z.M.I. getting that information?",
            "Oh, my friend, you misunderstand. I have no issues with the Z.M.I. having the information, but I " +
                "don't want them getting " +
                "it before I've even had a chance to determine what it is!",
            "> I see. So what now?",
            "Yes, back to business! The incantation is quite complex, so we will need the help of three other " +
                "wizards to perform it. " +
                "I'm afraid I'm not including you in that, although you are very talented!",
            "> Thanks, I think... so where do we get these other wizards?",
            "From the Wizards' Tower of course! My fellow wizards won't be able to resist getting their hands on " +
                "a teleportation " +
                "matrix, especially one that may be connected to the history of our order!",
            "Now, you head to the Wizards' Tower and show that incantation to Sedridor. I have a couple of things " +
                "to prepare first, but " +
                "I will join you there soon.",
            "Oh, and whatever you do, don't mention the Z.M.I. while you're there.",
            "> Alright, I'll see you soon.",
        )
        Tote.set(p, Tote.SHOWN_PERSTEN)
        perstenTeleport(per, afterStory = true)
    }

    private suspend fun PlayerScript.perstenTeleport(per: NpcRef, afterStory: Boolean = false) {
        val p = player
        val stage = Tote.stage(p)
        if (!afterStory) {
            if (stage < Tote.SHOWN_PERSTEN) return
            npc(per, "Hello again! Did you take that incantation to Sedridor?")
            if (!p.has(Objs.TOTE_INCANTATION) && stage == Tote.SHOWN_PERSTEN) {
                say(per, "> I may have lost it.", "No matter, I can give you a copy of mine. Try not to lose it this time!")
                if (p.give(Objs.TOTE_INCANTATION, 1, "tote incantation")) box("Persten hands you a copy of the incantation.")
            } else {
                say(per, "> Not yet.", "Well hurry up! This could be the biggest discovery of our lifetimes!")
            }
        }
        if (p.varbit(Varbits.TOTE_TOWER_TELEPORT_USED) == 1) return
        say(per, "Hey, if you want, I can teleport you to the Wizards' Tower. Consider it a one-time gift from me.")
        if (choice("Teleport to the Wizards' Tower? You can only use this teleport once.", "Yes.", "No.") == 1) {
            per.say("Saris! Val! Curak! Belito!")
            p.setVarbit(Varbits.TOTE_TOWER_TELEPORT_USED, 1)
            p.teleport(landing(p, Tote.TOWER_ARRIVAL))
        } else {
            say(per, "> I think I'll walk.", "Well let me know if you change your mind.")
        }
    }

    // ---- Archmage Sedridor, Traiborn and the apprentices

    private suspend fun PlayerScript.darkMageEntry(dark: NpcRef) {
        val p = player
        if (templeDarkMageSolved(p)) return darkMageIncantation(dark)
        if (Tote.stage(p) in Tote.GOT_AMULET until Tote.END) darkMageTalk(dark) else darkMageDefault(dark)
    }

    private suspend fun PlayerScript.darkMageDefault(dark: NpcRef) {
        me("Hello there.")
        npc(dark, "Quiet! You must not break my concentration!")
    }

    private suspend fun PlayerScript.towerApprentice(a: NpcRef, who: String) {
        val p = player
        val stage = Tote.stage(p)
        val seen = SPOKEN.getValue(who)
        if (stage >= Tote.RIDDLE_SOLVED) return npc(a, READY.getValue(who))
        if (!p.flag(TRAIBORN_ASKED)) return say(a, *BUSY.getValue(who).toTypedArray())
        if (p.varbit(seen) == 0) {
            when (who) {
                "Cordelia" -> {
                    say(
                        a,
                        "Thingummywut. Thin... gummy... what?",
                        "> Traiborn said you're trying to solve a puzzle? Maybe I could help?",
                        "Why? Do you think I need your help to solve this? I'll prove I'm smarter than these two!",
                        "> I'm actually looking to recruit some talented wizards to help uncover a long-lost " +
                            "teleportation matrix. Traiborn " +
                            "said that he wouldn't give his permission for you to join me until the puzzle is " +
                            "solved, so I thought I'd lend a " +
                            "hand.",
                        "Very well, you appear to be telling the truth, and I'd rather be uncovering a teleportation " +
                            "matrix than doing " +
                            "this. Here it is.",
                    )
                }

                "Felix" -> {
                    say(
                        a,
                        "So, if that's six and this is eight, then if we multiply... No, divide? No, that's not it " +
                            "either.",
                        "> Sounds like you're trying to solve a puzzle. Perhaps I could help?",
                        "Oh, hey there! Yes, I'm trying to solve a puzzle set by the world-renowned Traiborn himself! " +
                            "It truly is an honour " +
                            "to have someone like him take the time to teach an up-and-coming wizard such as myself.",
                        "> I'm currently helping a wizard called Persten. She's tasked me with recruiting some " +
                            "wizards to help her uncover " +
                            "a teleportation matrix. Would you like to join us?",
                        "You've met Persten? She studied directly under Traiborn for multiple years! I'd love to meet " +
                            "her, but she's hard " +
                            "to track down. Could you introduce us?",
                        "> Sure, I can do that. We need to solve this puzzle first though. Perhaps I could help you " +
                            "solve it?",
                        "I'm not sure. I wouldn't want Traiborn to think poorly of me if I've cheated and asked for " +
                            "help... but I do want " +
                            "to work with Persten... Okay, I'll show you the puzzle.",
                    )
                }

                else -> {
                    say(
                        a,
                        "Air and earth? What about water and fire? Is this an elemental problem, or are they " +
                            "completely irrelevant?",
                        "> What appears to be the problem?",
                        "Yes, I just can't wrap my head around it. It feels like there are too many unknown " +
                            "variables. Besides, how does " +
                            "all this relate to a thingummywut in the first place? Whatever that actually is.",
                        "> Perhaps I could take a look and help you out? I'm here to recruit the three of you to help " +
                            "uncover an old " +
                            "teleportation matrix.",
                        "Ooh! Did you say a teleportation matrix? Teleportation magic is my specialty! I'd love to " +
                            "help! Sure, I'll show " +
                            "you the puzzle. Just please don't tell the others you helped me.",
                    )
                }
            }
            p.setVarbit(seen, 1)
        } else {
            npc(a, "Here it is.")
        }
        val (first, second) = Tote.RIDDLES.getValue(who)
        box("$first<br><br>$second<br><br>What is a thingummywut?")
    }

    internal companion object {
        const val OP_TALK = 1
        const val REQUIRED_RUNECRAFT = 10

        /**
         * Journal lines (OPEN-QU6: wording is ours): the line shown while the stage is below the same index of
         * [JOURNAL_STAGES].
         */
        val JOURNAL_STAGES = intArrayOf(1, 20, 40, 50, 70, 85, 91, 100, 120, 130)
        val JOURNAL =
            listOf(
                "I can start this quest by speaking to Wizard Persten north of Al Kharid. I need 10 Runecraft and " +
                    "Enter the Abyss.",
                "Persten gave me an eye amulet. I should show it to the Mage of Zamorak in the chaos temple in " +
                    "Varrock.",
                "The Mage of Zamorak wants a bucket of water and a strong cup of tea (the Tea Seller in Varrock) " +
                    "before he sends me to " +
                    "the Abyss.",
                "The Dark Mage in the Abyss has cast a tracing spell. I must work out the order of the runic energy.",
                "I have an abyssal incantation. Persten wants me to show it to Archmage Sedridor in the Wizards' " +
                    "Tower basement.",
                "Sedridor is checking the incantation. Traiborn's apprentices can help if I solve his " +
                    "thingummywut riddle.",
                "Everyone is ready. I should speak to Sedridor or Persten in the basement to start the incantation.",
                "I am in the Temple of the Eye. I should speak to the apprentices and to Persten.",
                "Persten has fallen into the rift. I must help the Great Guardian close it with cells, rune " +
                    "guardians and runic energy.",
                "The rift is closed for now. I should tell Archmage Sedridor in the Wizards' Tower basement.",
                "I helped close the rift to the Scar. Traiborn's apprentices stayed to help the Guardians of the " +
                    "Rift.",
            )
        const val PERSTEN_X = 3284
        const val PERSTEN_Z = 3231
        const val PERSTEN_WANDER = 4
        const val TEA_X = 3270
        const val TEA_Z = 3413
        const val TEA_WANDER = 4
        const val SEDRIDOR_X = 3103
        const val SEDRIDOR_Z = 9571
        const val TOWER_X = 3110
        const val TOWER_Z = 3160
        const val POLL_TICKS = 2
        const val TRAIBORN_ASKED = "quests.tote.traiborn"
        val POLL = TimerId("tote.poll")

        /** What the apprentices say once Traiborn has released them (Wiki "After solving the riddle"). */
        val READY =
            mapOf(
                "Felix" to "I think I'm ready to meet Persten! You should go to the basement and let Sedridor know we're all ready.",
                "Cordelia" to
                    "Traiborn says I'm allowed to help you with this teleportation matrix. " +
                    "You should speak to Sedridor in the basement.",
                "Tamara" to
                    "Traiborn says I'm allowed to help you now. Let's find this teleportation matrix! " +
                    "I think you need to speak to Sedridor in the basement first though.",
            )

        /**
         * What they say while Traiborn's puzzle is unsolved and the player has not asked him yet (Wiki "Talking to
         * the apprentices before
         * speaking to Traiborn").
         */
        val BUSY =
            mapOf(
                "Cordelia" to
                    listOf(
                        "Thingummywut. Thin... gummy... what?",
                        "> What are you talking about?",
                        "I'm a little busy trying to solve that mad wizard's puzzle. If you have any questions, " +
                            "ask him instead.",
                    ),
                "Felix" to
                    listOf(
                        "So, if that's six and this is eight, then if we multiply... No, divide? No, that's not " +
                            "it either.",
                        "> What are you talking about?",
                        "Oh, didn't see you there! I'm a little busy trying to solve a puzzle set by the great " +
                            "Traiborn. " +
                            "If you have any questions, could you ask him instead?",
                    ),
                "Tamara" to
                    listOf(
                        "Air and earth? What about water and fire? Is this an elemental problem, or are they " +
                            "completely irrelevant?",
                        "> What are you talking about?",
                        "Oh, didn't see you there. I'm a little busy trying to solve the puzzle Traiborn gave us. " +
                            "If you have any questions, you should ask him instead.",
                    ),
            )

        val SPOKEN =
            mapOf(
                "Tamara" to Varbits.TOTE_SPOKEN_TO_TAMARA,
                "Felix" to Varbits.TOTE_SPOKEN_TO_FELIX,
                "Cordelia" to Varbits.TOTE_SPOKEN_TO_CORDELIA,
            )

        /**
         * Cache locs `tote_abyssal_energy_*` (the multi-loc parents placed in the Abyss' centre), in
         * [AbyssEnergy.VARBITS] order.
         */
        val ENERGY_LOCS =
            intArrayOf(
                Locs.TOTE_ABYSSAL_ENERGY_EARTH,
                Locs.TOTE_ABYSSAL_ENERGY_COSMIC,
                Locs.TOTE_ABYSSAL_ENERGY_DEATH,
                Locs.TOTE_ABYSSAL_ENERGY_NATURE,
                Locs.TOTE_ABYSSAL_ENERGY_LAW,
                Locs.TOTE_ABYSSAL_ENERGY_FIRE,
            )

        /** Extra options of the Mage of Zamorak's menu ([EnterTheAbyss]). */
        fun mageOptions(p: PlayerRef): List<Pair<String, suspend PlayerScript.(NpcRef) -> Unit>> = templeMageOptions(p)
    }
}
