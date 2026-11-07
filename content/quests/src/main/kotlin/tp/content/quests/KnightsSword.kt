@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * The Knight's Sword (docs/mechanics/quest-the-knights-sword.md): the White Knights' squire lost Sir Vyvin's sword; Reldo points to
 * the Imcando dwarf Thurgo, who wants a redberry pie, the sword's portrait, two iron bars and a blurite ore. The quest variable
 * (varp 122) is 0 / 1 started / 2 Reldo told the player about the dwarves / 3 Thurgo ate the pie / 4 portrait given / 5 sword
 * made / 7 finished (cache `endstate`; the stages between are ours, OPEN-QU1).
 * Blurite ore comes from the Asgarnian Ice Dungeon rocks (content/skills mining). Dialogue is the Wiki
 * "Transcript:The Knight's Sword", "Transcript:Reldo", "Transcript:Thurgo", "Transcript:Squire" and "Transcript:Sir Vyvin".
 */
public class KnightsSword : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.SQUIRE, OP_TALK) { squire ->
            player.stopInteraction()
            squireTalk(squire)
        }
        onOpNpc(Npcs.RELDO_NORMAL, OP_TALK) { reldo ->
            player.stopInteraction()
            reldoTalk(reldo)
        }
        onOpNpc(Npcs.THURGO, OP_TALK) { thurgo ->
            player.stopInteraction()
            thurgoTalk(thurgo)
        }
        onOpNpc(Npcs.SIR_VYVIN, OP_TALK) { vyvin ->
            player.stopInteraction()
            vyvinTalk(vyvin)
        }
        // Sir Vyvin's cupboard (Wiki "The Knight's Sword" > Acquiring the picture): closed 2271 "Open", open 2272 "Search" / "Shut".
        onOpLoc(CUPBOARD_CLOSED, OP_FIRST) { cupboard ->
            player.stopInteraction()
            swap(cupboard, CUPBOARD_OPEN)
        }
        onOpLoc(CUPBOARD_OPEN, OP_FIRST) {
            player.stopInteraction()
            searchCupboard()
        }
        onOpLoc(CUPBOARD_OPEN, OP_SECOND) { cupboard ->
            player.stopInteraction()
            swap(cupboard, CUPBOARD_CLOSED)
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.KNIGHTS_SWORD) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private val PlayerRef.stage: Int get() = Quests.KNIGHTS_SWORD.stage(this)

    private fun journal(stage: Int): List<String> =
        when (stage) {
            0 -> {
                listOf("I can start this quest by speaking to the squire in the courtyard of the White Knights' Castle in Falador.")
            }

            STARTED -> {
                listOf(
                    "Sir Vyvin's squire lost his family sword. Reldo, the Varrock palace librarian, knows about the Imcando dwarves " +
                        "who made it.",
                )
            }

            RELDO_TOLD -> {
                listOf("Reldo said an Imcando dwarf lives on the southern cliffs of Asgarnia and loves redberry pie.")
            }

            PIE_GIVEN -> {
                listOf("Thurgo needs a picture of the sword. The squire says there is a portrait in Sir Vyvin's cupboard.")
            }

            PORTRAIT_GIVEN -> {
                listOf("Thurgo needs a blurite ore and two iron bars. Blurite is found in the Asgarnia Ice Dungeon.")
            }

            SWORD_MADE -> {
                listOf("Thurgo made the sword. I should take it to Sir Vyvin's squire.")
            }

            else -> {
                listOf("I gave the squire a replacement for Sir Vyvin's sword.")
            }
        }

    private fun PlayerRef.hasMaterials(): Boolean = has(Objs.BLURITE_ORE) && has(Objs.IRON_BAR, IRON_BARS)

    // ---- the squire

    private suspend fun PlayerScript.squireTalk(squire: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(squire, "Hello. I am the squire to Sir Vyvin.")
                squireStart(squire)
            }

            STARTED, RELDO_TOLD -> {
                npc(squire, "So how are you doing getting a sword?")
                me("I'm looking for Imcando dwarves to help me.")
                npc(squire, "Please try and find them quickly... I am scared Sir Vyvin will find out!")
            }

            PIE_GIVEN -> {
                npc(squire, "So how are you doing getting a sword?")
                if (!p.flag(FLAG_PORTRAIT_TOLD)) {
                    me("I've found an Imcando dwarf but he needs a picture of the sword before he can make it.")
                    npc(
                        squire,
                        "A picture eh? Hmmm.... The only one I can think of is in a small portrait of Sir Vyvin's father... Sir " +
                            "Vyvin keeps it in a cupboard in his room I think.",
                    )
                    me("Ok, I'll try and get that then.")
                    npc(squire, "Please don't let him catch you! He MUSTN'T know what happened!")
                    p.setFlag(FLAG_PORTRAIT_TOLD, true)
                } else if (p.has(Objs.KNIGHTS_PORTRAIT)) {
                    me("I have the picture. I'll just take it to the dwarf now!")
                    npc(squire, "Please hurry!")
                } else {
                    me("I didn't get the picture yet.")
                    npc(squire, "Please try and get it quickly... I am scared Sir Vyvin will find out!")
                }
            }

            PORTRAIT_GIVEN -> {
                npc(squire, "So how are you doing getting a sword?")
                me("I've found a dwarf who will make the sword. I've just got to find the materials for it now!")
                npc(squire, "Please hurry!")
            }

            SWORD_MADE -> {
                handOver(squire)
            }

            else -> {
                npc(
                    squire,
                    "Hello friend! Many thanks for all of your help! Vyvin never even realised it was a different sword, and I " +
                        "still have my job!",
                )
                me("I'm glad the new sword worked out alright.")
            }
        }
    }

    private suspend fun PlayerScript.handOver(squire: NpcRef) {
        val p = player
        npc(squire, "So how are you doing getting a sword?")
        me("I have retrieved your sword for you.")
        if (p.inv(Invs.WORN).total(Objs.FALADIAN_SWORD) > 0 && !p.has(Objs.FALADIAN_SWORD)) {
            npc(squire, "So can you un-equip it and hand it over to me now please?")
            return
        }
        if (!p.has(Objs.FALADIAN_SWORD)) {
            npc(squire, "So how are you doing getting a sword?")
            return
        }
        npc(squire, "Thank you, thank you, thank you! I was seriously worried I would have to own up to Sir Vyvin!")
        if (!p.take(Objs.FALADIAN_SWORD, 1, "knights sword handed in")) return
        box("You give the sword to the squire.")
        QuestCompletion.complete(p, Quests.KNIGHTS_SWORD, REWARD)
    }

    private suspend fun PlayerScript.squireStart(squire: NpcRef) {
        var firstPass = true
        while (true) {
            val options = ArrayList<String>()
            if (firstPass) options += LIFE
            options += WOULDNT
            val pick = options[choice("Select an Option", *options.toTypedArray()) - 1]
            firstPass = false
            if (pick == WOULDNT) {
                me(WOULDNT)
                npc(squire, "No, sorry, I'm loyal to Sir Vyvin.")
                return
            }
            me(LIFE)
            npc(
                squire,
                "Well, Sir Vyvin is a good guy to work for, however, I'm in a spot of trouble today. I've gone and lost Sir " +
                    "Vyvin's sword!",
            )
            if (squireLost(squire)) return
        }
    }

    /** The "lost the sword" branch; true when the conversation ended. */
    private suspend fun PlayerScript.squireLost(squire: NpcRef): Boolean {
        var asked = false
        while (true) {
            val options = ArrayList<String>()
            if (!asked) options += "Do you know where you lost it?"
            options += "I can make a new sword if you like..."
            options += "Well, the kingdom is fairly abundant with swords..."
            options += "Is he angry?"
            options += "Well, I hope you find it soon."
            when (options[choice("Select an Option", *options.toTypedArray()) - 1]) {
                "Do you know where you lost it?" -> {
                    me("Do you know where you lost it?")
                    npc(squire, "Well now, if I knew THAT it wouldn't be lost, now would it?")
                    me("Well, do you know the VAGUE AREA you lost it in?")
                    npc(
                        squire,
                        "No. I was carrying it for him all the way from where he had it stored in Lumbridge. It must have slipped " +
                            "from my pack during the trip, and you know what people are like these days...",
                        "Someone will have just picked it up and kept it for themselves.",
                    )
                    asked = true
                }

                "Is he angry?" -> {
                    me("Is he angry?")
                    npc(
                        squire,
                        "He doesn't know yet. I was hoping I could think of something to do before he does find out, But I find " +
                            "myself at a loss.",
                    )
                    asked = true
                }

                "Well, the kingdom is fairly abundant with swords..." -> {
                    me("Well, the kingdom is fairly abundant with swords...")
                    npc(squire, "Yes. You can get bronze swords anywhere. But THIS isn't any old sword.")
                    heirloom(squire)
                    return wouldThey(squire)
                }

                "I can make a new sword if you like..." -> {
                    me("I can make a new sword if you like...")
                    npc(squire, "Thanks for the offer. I'd be surprised if you could though.")
                    heirloom(squire)
                    return wouldThey(squire)
                }

                else -> {
                    me("Well, I hope you find it soon.")
                    npc(
                        squire,
                        "Yes, me too. I'm not looking forward to telling Vyvin I've lost it. He's going to want it for the parade next " +
                            "week as well.",
                    )
                    return true
                }
            }
        }
    }

    private suspend fun PlayerScript.heirloom(squire: NpcRef) {
        npc(
            squire,
            "The thing is, this sword is a family heirloom. It has been passed down through Vyvin's family for five generations! " +
                "It was originally made by the Imcando dwarves, who were",
            "a particularly skilled tribe of dwarven smiths. I doubt anyone could make it in the style they do.",
        )
    }

    /** "So would these dwarves make another one?" and the offer of the quest; true when the conversation ended. */
    private suspend fun PlayerScript.wouldThey(squire: NpcRef): Boolean {
        if (choice("Select an Option", "So would these dwarves make another one?", "Well, I hope you find it soon.") != 1) {
            me("Well, I hope you find it soon.")
            npc(
                squire,
                "Yes, me too. I'm not looking forward to telling Vyvin I've lost it. He's going to want it for the parade next week " +
                    "as well.",
            )
            return true
        }
        me("So would these dwarves make another one?")
        npc(
            squire,
            "I'm not a hundred percent sure the Imcando tribe exists anymore. I should think Reldo, the palace librarian in " +
                "Varrock, will know; he has done a lot of research on the races of Gielinor.",
            "I don't suppose you could try and track down the Imcando dwarves for me? I've got so much work to do...",
        )
        // OPEN-QUA12: the Wiki shows this notice "without 10 Mining and while below 20 Combat"; either shortfall shows it here.
        if (player.baseLevel(Stats.MINING) < MINING_LEVEL || player.combatLevel < COMBAT_LEVEL) {
            box(
                "Before starting this quest, be aware that one or more of your skill levels are lower than what is required to " +
                    "fully complete it. Your combat level is also lower than the recommended level of 20.",
            )
        }
        if (choice("Start The Knight's Sword quest?", "Yes.", "No.") != 1) {
            me("No. I've got lots of mining work to do.")
            npc(squire, "Oh man... I'm in such trouble...")
            return true
        }
        me("Ok, I'll give it a go.")
        npc(squire, "Thank you very much! As I say, the best place to start should be with Reldo...")
        Quests.KNIGHTS_SWORD.advance(player, STARTED)
        return true
    }

    // ---- Reldo

    private suspend fun PlayerScript.reldoTalk(reldo: NpcRef) {
        val p = player
        npc(reldo, "Hello there, $name.")
        val options = ArrayList<String>()
        options += QUEST
        if (p.stage in STARTED until PIE_GIVEN) options += IMCANDO
        options += TRADE
        options += GOING
        when (options[choice("Select an Option", *options.toTypedArray()) - 1]) {
            QUEST -> {
                me(QUEST)
                npc(reldo, "Hmm... I don't believe there are any here.", "Let me think actually...")
                npc(reldo, "Ah, yes. I think I have something, if you're definitely interested?")
                if (p.combatLevel < SHIELD_COMBAT_LEVEL) {
                    box("Before starting this quest, be aware that you combat level is lower than the recommended level of 10.")
                }
                if (choice("Start the Shield of Arrav quest?", "Yes.", "No.") == 1) {
                    // OPEN-QUA11: Shield of Arrav is not implemented; the book Reldo mentions does not exist yet.
                    me("Of course.")
                    npc(
                        reldo,
                        "Well, if you look around for book called The Shield of Arrav, you'll find a quest in there.",
                        "I'm not sure where the book is mind you... but I'm sure it's around here somewhere.",
                    )
                    me("Great! Thank you.")
                } else {
                    me("Actually, I don't think I am. Goodbye.")
                }
            }

            IMCANDO -> {
                me(IMCANDO)
                npc(
                    reldo,
                    "The Imcando dwarves, you say? Let's see...",
                    "Ah, yes... For many hundreds of years, they were the world's most skilled smiths. They used secret smithing " +
                        "knowledge passed down from generation to generation.",
                    "Unfortunately, about a century ago, the once thriving group was wiped out during the barbarian invasions of " +
                        "that time.",
                    "I believe a few of them survived, but with the bulk of their population destroyed, their numbers dwindled even " +
                        "further. If any are left now, they'll no doubt keep to themselves.",
                    "However, I have heard tales of their love for redberry pie. Perhaps one of those could convince one to reveal " +
                        "their true heritage.",
                )
                me("So I just go around offering random dwarves redberry pie?")
                npc(
                    reldo,
                    "Well, not totally random. Apparently some of the surviving Imcando made a home for themselves near the cliffs " +
                        "on the Asgarnian southern peninsula. That would be a good place to start.",
                )
                Quests.KNIGHTS_SWORD.advance(p, RELDO_TOLD)
            }

            TRADE -> {
                me(TRADE)
                npc(reldo, "Only knowledge.")
                me("How much do you want for that then?")
                npc(reldo, "No, sorry, that was just my little joke. I'm not the trading type.")
                me("Ah well.")
            }

            GOING -> {
                me(GOING)
                npc(reldo, "Until next time.")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- Thurgo

    private suspend fun PlayerScript.thurgoTalk(thurgo: NpcRef) {
        val p = player
        val stage = p.stage
        val options = ArrayList<String>()
        when {
            stage < PIE_GIVEN -> {
                options += SPECIAL_SWORD
                if (stage >= RELDO_TOLD && p.has(Objs.REDBERRY_PIE)) options += PIE
            }

            stage == PIE_GIVEN -> {
                options += ABOUT_SWORD
                if (p.has(Objs.REDBERRY_PIE)) options += PIE
            }

            stage == PORTRAIT_GIVEN -> {
                options += REPLACEMENT
                if (p.has(Objs.REDBERRY_PIE)) options += PIE
            }

            stage == SWORD_MADE -> {
                options += THANKS
                if (p.has(Objs.REDBERRY_PIE)) options += PIE
            }

            else -> {
                options += ANOTHER
                options += PIE
            }
        }
        options += CAPE
        when (options[choice("Select an option", *options.toTypedArray()) - 1]) {
            SPECIAL_SWORD -> {
                me(SPECIAL_SWORD)
                npc(thurgo, "I don't talk about that sort of thing anymore. I'm getting old.")
                if (stage >= RELDO_TOLD && p.has(Objs.REDBERRY_PIE)) {
                    if (choice("Select an option", PIE, "I'll come back another time.") == 1) return pie(thurgo)
                }
                me("I'll come back another time.")
            }

            PIE -> {
                pie(thurgo)
            }

            ABOUT_SWORD -> {
                me(ABOUT_SWORD)
                npc(thurgo, "Have you got a picture of the sword for me yet?")
                if (!p.has(Objs.KNIGHTS_PORTRAIT)) {
                    me("Sorry, not yet.")
                    npc(thurgo, "Well, come back when you do.")
                    return
                }
                me("I have found a picture of the sword I would like you to make.")
                if (!p.take(Objs.KNIGHTS_PORTRAIT, 1, "knights sword portrait")) return
                box("You give the Portrait to Thurgo. Thurgo studies the portrait.")
                npc(
                    thurgo,
                    "You'll need to get me some stuff to make this. I'll need two iron bars to make the sword, to start with. I'll " +
                        "also need an ore called blurite.",
                    "Blurite is useless for making actual weapons, except crossbows, but I'll need some as decoration for the hilt.",
                    "It is a fairly rare ore. The only place I know to get it is under this cliff here, but it is guarded by a very " +
                        "powerful ice giant.",
                    "Most of the rocks in that cliff are pretty useless, and don't contain much of anything, but there's DEFINITELY " +
                        "some blurite in there.",
                    "You'll need a little bit of mining experience to be able to find it.",
                )
                me("Okay. I'll go and find them then.")
                Quests.KNIGHTS_SWORD.advance(p, PORTRAIT_GIVEN)
            }

            REPLACEMENT -> {
                me(REPLACEMENT)
                npc(thurgo, "How are you doing finding those sword materials?")
                val ore = p.has(Objs.BLURITE_ORE)
                val bars = p.has(Objs.IRON_BAR, IRON_BARS)
                when {
                    ore && bars -> {
                        me("I have them right here.")
                        val done =
                            p.transaction("knights sword materials") {
                                remove(Invs.INV, Objs.BLURITE_ORE, 1) && remove(Invs.INV, Objs.IRON_BAR, IRON_BARS) &&
                                    addOrDrop(Invs.INV, Objs.FALADIAN_SWORD, 1)
                            }
                        if (!done) return
                        box("You give the blurite ore and iron bars to Thurgo. Thurgo makes you a sword.")
                        me("Thank you very much!")
                        npc(thurgo, "Just remember to call in with more pie some time!")
                        Quests.KNIGHTS_SWORD.advance(p, SWORD_MADE)
                    }

                    !ore && !bars -> {
                        me("I don't have any of them yet.")
                        npc(
                            thurgo,
                            "Well, I need a blurite ore and two iron bars. The only place I know to get blurite is under this cliff " +
                                "here, but it is guarded by a very powerful ice giant.",
                        )
                    }

                    bars -> {
                        me("I don't have any blurite ore yet.")
                        npc(
                            thurgo,
                            "Better go get some then, huh? The only place I know to get it is under this cliff here, but it is " +
                                "guarded by a very powerful ice giant.",
                        )
                    }

                    else -> {
                        me("I don't have two iron bars.")
                        npc(thurgo, "Better go get some then, huh?")
                    }
                }
            }

            THANKS -> {
                me(THANKS)
                npc(thurgo, "You're welcome - thanks for the pie!")
            }

            ANOTHER -> {
                me(ANOTHER)
                npc(
                    thurgo,
                    "You want that knight's sword again? I suppose you brought me a lovely pie, so I don't mind. I'll need a blurite " +
                        "ore and two iron bars, like before.",
                )
                if (p.hasMaterials()) {
                    val done =
                        p.transaction("knights sword extra") {
                            remove(Invs.INV, Objs.BLURITE_ORE, 1) && remove(Invs.INV, Objs.IRON_BAR, IRON_BARS) &&
                                addOrDrop(Invs.INV, Objs.FALADIAN_SWORD, 1)
                        }
                    if (done) box("Thurgo makes you another sword.")
                } else {
                    me("Okay, I'll go and find that.")
                }
            }

            CAPE -> {
                me(CAPE)
                npc(
                    thurgo,
                    "It's a Skillcape of Smithing. It shows that I'm a master blacksmith, but that's only to be expected - after all, " +
                        "my ancestors were the greatest blacksmiths in dwarven history.",
                    "If you ever achieve level 99 Smithing you'll be able to wear a cape like this, and receive more experience when " +
                        "smelting gold ore.",
                )
            }

            else -> {
                Unit
            }
        }
    }

    /** "Would you like a redberry pie?": Thurgo eats it; the first time (stage 2) the quest moves on. */
    private suspend fun PlayerScript.pie(thurgo: NpcRef) {
        val p = player
        me(PIE)
        box("You see Thurgo's eyes light up.")
        val first = p.stage == RELDO_TOLD
        val again = p.stage > RELDO_TOLD
        if (again) {
            npc(thurgo, "Another? By Guthix, you are generous! I like you!")
        } else {
            npc(thurgo, "I'd never say no to a redberry pie! We Imcando dwarves love them - they're GREAT!")
        }
        if (!p.take(Objs.REDBERRY_PIE, 1, "knights sword pie")) return
        if (again) {
            box("You watch Thurgo greedily gobble down the pie.")
            npc(thurgo, "<col=0000ff>BUUUUUUUUUUUUUURP</col> Mmmmm... I could eat redberry pies all day!")
            return
        }
        box("You hand over the pie.")
        box("Thurgo eats the pie.")
        box("Thurgo pats his stomach.")
        npc(thurgo, "By Guthix! THAT was good pie! Anyone who makes pie like THAT has got to be alright!")
        if (!first) return
        if (choice("Select an option", "Can you make a special sword for me?", CAPE) == 1) {
            me("Can you make a special sword for me?")
            npc(thurgo, "Well, after bringing me my favorite food I guess I should give it a go. What sort of sword is it?")
            me(
                "I need you to make a sword for one of Falador's knights. He had one which was passed down through five " +
                    "generations, but his squire has lost it.",
                "So we need an identical one to replace it.",
            )
            npc(
                thurgo,
                "A knight's sword eh? Well, I'd need to know exactly how it looked before I could make a new one.",
                "All the Faladian knights used to have swords with unique designs according to their position. Could you bring me " +
                    "a picture or something?",
            )
            me("I'll go and ask his squire and see if I can find one.")
            Quests.KNIGHTS_SWORD.advance(p, PIE_GIVEN)
        }
    }

    // ---- Sir Vyvin

    private suspend fun PlayerScript.vyvinTalk(vyvin: NpcRef) {
        me("Hello.")
        npc(vyvin, "Greetings traveller.")
        when (
            choice(
                "Select an Option",
                "Do you have anything to trade?",
                "Why are there so many knights in this city?",
                "Can I just distract you for a minute?",
            )
        ) {
            1 -> {
                // OPEN-QUA13: the White Knights' ranks and the armoury are not modelled; only the unranked answer exists.
                me("Do you have anything to trade?")
                npc(vyvin, "No, I'm sorry.")
            }

            2 -> {
                me("Why are there so many knights in this city?")
                npc(
                    vyvin,
                    "We are the White Knights of Falador. We are the most powerful order of knights in the land. We are helping " +
                        "the king Vallance rule the kingdom as he is getting old and tired.",
                )
            }

            3 -> {
                me(
                    "Can I just talk to you very slowly for a few minutes, while I distract you, so that my friend over there can " +
                        "do something while you're busy being distracted by me?",
                )
                npc(vyvin, "... ...what?", "I'm... not sure what you're asking me... you want to join the White Knights?")
                me("Nope. I'm just trying to distract you.")
                npc(vyvin, "... ...you are very odd.")
                me("So can I distract you some more?")
                npc(vyvin, "... ...I don't think I want to talk to you anymore.")
                me("Ok. My work here is done. 'Bye!")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- the cupboard

    private fun PlayerScript.swap(cupboard: LocRef, to: Int) {
        player.deleteLoc(cupboard.coord, cupboard.shape, LOC_DURATION)
        player.addLoc(to, cupboard.coord, cupboard.shape, cupboard.angle, LOC_DURATION)
    }

    /**
     * The portrait is found once the squire has told the player about it (Wiki: "You must have spoken to the squire before you may
     * obtain the portrait"). Sir Vyvin catching the player in his room is not modelled (OPEN-QUA9).
     */
    private suspend fun PlayerScript.searchCupboard() {
        val p = player
        if (p.stage != PIE_GIVEN || !p.flag(FLAG_PORTRAIT_TOLD) || p.has(Objs.KNIGHTS_PORTRAIT)) {
            p.message("You search the cupboard but find nothing interesting.")
            return
        }
        if (p.giveOrDrop(Objs.KNIGHTS_PORTRAIT, 1, "knights sword portrait")) box("You find a small portrait in here which you take.")
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1
        const val OP_SECOND = 2

        /** Cache locs 2271 (closed cupboard, op "Open") and 2272 (open: "Search", "Shut") of Sir Vyvin's room. */
        const val CUPBOARD_CLOSED = 2271
        const val CUPBOARD_OPEN = 2272

        /** A changed cupboard reverts after this many ticks (the doors' value, RS Mod `DoorConstants`). */
        const val LOC_DURATION = 500

        /** Quest stages (ours, OPEN-QU1): 1 started, 2 Reldo told, 3 pie eaten, 4 portrait given, 5 sword made; 7 is `endstate`. */
        const val STARTED = 1
        const val RELDO_TOLD = 2
        const val PIE_GIVEN = 3
        const val PORTRAIT_GIVEN = 4
        const val SWORD_MADE = 5

        const val FLAG_PORTRAIT_TOLD = "quests.sword.portrait"

        /** Wiki "The Knight's Sword": two iron bars and one blurite ore for the sword; 10 Mining, combat 20 recommended. */
        const val IRON_BARS = 2
        const val MINING_LEVEL = 10
        const val COMBAT_LEVEL = 20

        /** Wiki "Transcript:Reldo": the Shield of Arrav notice below combat level 10. */
        const val SHIELD_COMBAT_LEVEL = 10

        const val LIFE = "And how is life as a squire?"
        const val WOULDNT = "Wouldn't you prefer to be a squire for me?"
        const val QUEST = "I'm in search of a quest."
        const val IMCANDO = "What do you know about the Imcando dwarves?"
        const val TRADE = "Do you have anything to trade?"
        const val GOING = "I'd better get going."
        const val SPECIAL_SWORD = "Are you an Imcando dwarf? I need a special sword."
        const val PIE = "Would you like a redberry pie?"
        const val ABOUT_SWORD = "About that sword..."
        const val REPLACEMENT = "Can you make that replacement sword now?"
        const val THANKS = "Thanks for making that sword for me!"
        const val ANOTHER = "Can you make me another of Sir Vyvin's swords?"
        const val CAPE = "What is that cape you're wearing?"

        /** Wiki "The Knight's Sword" rewards: 1 quest point, 12,725 Smithing XP (cache col 33 = (13, 127250 tenths)). */
        val REWARD = QuestReward(model = Objs.FALADIAN_SWORD, xp = listOf(Stats.SMITHING to 12725.0))
    }
}
