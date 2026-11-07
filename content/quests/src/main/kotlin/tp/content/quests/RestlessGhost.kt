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
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The Restless Ghost (docs/mechanics/quest-the-restless-ghost.md): Father Aereck, Father Urhney's amulet of ghostspeak, the
 * ghost in the Lumbridge graveyard, the skull on the Wizards' Tower basement altar. The quest variable is varp 107: 0 / 1 started
 * / 2 amulet from Urhney / 3 the ghost told the player about its skull / 4 skull taken / 5 finished (cache `endstate`; the
 * stages between are ours, OPEN-QU1). The coffin and altar look is driven by varp 728's bits (varbits 2128-2130, the cache's
 * multi-locs). Dialogue is the Wiki "Transcript:The Restless Ghost" and the NPC transcripts.
 */
public class RestlessGhost : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.FATHER_AERECK, OP_TALK) { aereck ->
            player.stopInteraction()
            aereckTalk(aereck)
        }
        onOpNpc(Npcs.FATHER_URHNEY, OP_TALK) { urhney ->
            player.stopInteraction()
            urhneyTalk(urhney)
        }
        onOpNpc(Npcs.GHOSTX, OP_TALK) { ghost ->
            player.stopInteraction()
            ghostTalk(ghost)
        }
        onOpLoc(COFFIN_CLOSED, OP_FIRST) { coffin ->
            player.stopInteraction()
            openCoffin(coffin)
        }
        for (coffin in OPEN_COFFINS) {
            onOpLoc(coffin, OP_FIRST) {
                player.stopInteraction()
                searchCoffin()
            }
            onOpLoc(coffin, OP_SECOND) { loc ->
                player.stopInteraction()
                closeCoffin(loc)
            }
            onUseOnLoc(Objs.GHOSTSKULL, coffin) {
                player.stopInteraction()
                skullInCoffin()
            }
        }
        onUseOnLoc(Objs.GHOSTSKULL, COFFIN_CLOSED) {
            player.stopInteraction()
            box("Maybe I should open it first.")
        }
        for (altar in ALTARS) {
            onOpLoc(altar, OP_FIRST) {
                player.stopInteraction()
                searchAltar()
            }
        }
        onUseOnNpc(Objs.GHOSTSKULL, Npcs.GHOSTX) { ghost ->
            player.stopInteraction()
            box("I can't give it to him. It goes right through him.")
            npc(ghost, "If you just put it in my coffin that should do the trick...")
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.RESTLESS_GHOST) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        when (stage) {
            0 -> listOf("I can start this quest by speaking to Father Aereck in the Lumbridge church.")
            1 -> listOf("Father Aereck wants a ghost in the graveyard gone. Father Urhney in the swamp, far to the west, may help.")
            2 -> listOf("Father Urhney gave me an amulet of ghostspeak. I should speak to the ghost in the graveyard while wearing it.")
            3 -> listOf("The ghost's skull was stolen by a warlock. I should look for it in the Wizards' Tower.")
            4 -> listOf("I have the ghost's skull. I should put it in the ghost's coffin in the graveyard.")
            else -> listOf("I returned the ghost's skull to its coffin and the ghost can rest in peace.")
        }

    private val PlayerRef.stage: Int get() = Quests.RESTLESS_GHOST.stage(this)

    private val PlayerRef.wearsAmulet: Boolean
        get() = inv(Invs.WORN).obj(AMULET_SLOT) == Objs.AMULET_OF_GHOSTSPEAK

    // ---- Father Aereck

    private suspend fun PlayerScript.aereckTalk(aereck: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(aereck, "Welcome to the church of holy Saradomin.")
                aereckBefore(aereck)
            }

            1 -> {
                npc(aereck, "Have you got rid of the ghost yet?")
                me("I can't find Father Urhney at the moment.")
                npc(
                    aereck,
                    "Well, you can get to the swamp he lives in by going south through the cemetery.",
                    "You'll have to go right into the far western depths of the swamp, near the coastline. That is where his house is.",
                )
            }

            in 2 until FINISHED -> {
                aereckProgress(aereck)
            }

            else -> {
                aereckAfter(aereck)
            }
        }
    }

    private suspend fun PlayerScript.aereckBefore(aereck: NpcRef) {
        while (true) {
            when (choice("Select an option", "Who's Saradomin?", "Nice place you've got here.", "I'm looking for a quest!")) {
                1 -> {
                    me("Who's Saradomin?")
                    npc(
                        aereck,
                        "Surely you have heard of the god, Saradomin?",
                        "He who creates the forces of goodness and purity in this world? I cannot believe your ignorance!",
                        "This is the god with more followers than any other! ...At least in this part of the world.",
                        "He who created this world along with his brothers Guthix and Zamorak?",
                    )
                    if (choice("Select an option", "Oh, THAT Saradomin...", "Oh, sorry. I'm not from this world.") == 1) {
                        me("Oh, THAT Saradomin...")
                        npc(aereck, "There... is only one Saradomin...")
                        me("Yeah... I, uh, thought you said something else.")
                    } else {
                        me("Oh, sorry. I'm not from this world.")
                        npc(
                            aereck,
                            "...",
                            "That's... strange.",
                            "I thought things not from this world were all... You know. Slime and tentacles.",
                        )
                        if (choice(
                                "Select an option",
                                "You don't understand. This is an online game!",
                                "I am - do you like my disguise?",
                            ) ==
                            1
                        ) {
                            me("You don't understand. This is an online game!")
                            npc(aereck, "I... beg your pardon?")
                            me("Never mind.")
                        } else {
                            me("I am - do you like my disguise?")
                            npc(aereck, "Aargh! Avaunt foul creature from another dimension! Avaunt! Begone in the name of Saradomin!")
                            me("Ok, ok, I was only joking...")
                        }
                    }
                    return
                }

                2 -> {
                    me("Nice place you've got here.")
                    npc(aereck, "It is, isn't it? It was built over 230 years ago.")
                    return
                }

                3 -> {
                    return startQuest(aereck)
                }

                else -> {
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.startQuest(aereck: NpcRef) {
        me("I'm looking for a quest.")
        npc(aereck, "That's lucky, I need someone to do a quest for me.")
        if (choice("Start The Restless Ghost quest?", "Yes", "No") != 1) {
            me("Sorry, I don't have time right now.")
            npc(aereck, "Oh well. If you do have some spare time on your hands, come back and talk to me.")
            return
        }
        me("Okay, let me help then.")
        npc(
            aereck,
            "Thank you. The problem is, there is a ghost in the church graveyard. I would like you to get rid of it.",
            "If you need any help, my friend Father Urhney is an expert on ghosts.",
            "I believe he is currently living as a hermit in Lumbridge swamp. He has a little shack in the far west of the swamps.",
            "Exit the graveyard through the south gate to reach the swamp. I'm sure if you told him that I sent you he'd be " +
                "willing to help.",
            "My name is Father Aereck by the way. Pleased to meet you.",
        )
        me("Likewise.")
        Quests.RESTLESS_GHOST.advance(player, 1)
        npc(aereck, "Take care travelling through the swamps, I have heard they can be quite dangerous.")
        me("I will, thanks.")
    }

    /** The "Have you got rid of the ghost yet?" exchange for the later stages (a branch of the one conversation per stage). */
    private suspend fun PlayerScript.aereckProgress(aereck: NpcRef) {
        npc(aereck, "Have you got rid of the ghost yet?")
        when (player.stage) {
            2 -> {
                me("I had a talk with Father Urhney. He has given me this funny amulet to talk to the ghost with.")
                npc(aereck, "I always wondered what that amulet was... Well, I hope it's useful. Tell me when you get rid of the ghost!")
            }

            3 -> {
                me("I've found out that the ghost's corpse has lost its skull. If I can find the skull, the ghost should leave.")
                npc(aereck, "That WOULD explain it.", "Hmmmmm. Well, I haven't seen any skulls.")
                me("Yes, I think a warlock has stolen it.")
                npc(aereck, "I hate warlocks.", "Ah well, good luck!")
            }

            else -> {
                me("I've finally found the ghost's skull!")
                npc(aereck, "Great! Put it in the ghost's coffin and see what happens!")
            }
        }
    }

    private suspend fun PlayerScript.aereckAfter(aereck: NpcRef) {
        npc(aereck, "Welcome to this church of holy Saradomin.")
        // OPEN-QU13: the "Could you tell me more about Saradomin?" branch of the Wiki transcript is not transcribed.
        when (choice("Select an option", "Could you tell me more about prayers?", "Thank you, but I'd better be going.")) {
            1 -> {
                me("Could you tell me about prayers?")
                npc(
                    aereck,
                    "Ah, of course. Saradomin looks kindly upon his most devout followers, and is more than willing to provide " +
                        "them with powerful benefits.",
                )
                me("So prayers are specifically a Saradomin thing?")
                npc(
                    aereck,
                    "Well, I suppose you could pray to other gods... but I don't know why you'd want to. What could any other " +
                        "god possibly offer that Saradomin does not already provide?",
                )
                me("I guess I was just curious. Anyway, what are these benefits that come from prayers?")
                npc(
                    aereck,
                    "Well, an adventurer like you may find them very useful in battle. They can make you stronger, letting you " +
                        "defeat powerful enemies with ease.",
                    "Alternatively, they can offer you protection, stopping you from suffering any harm from certain kinds of attacks.",
                )
                box(
                    "Prayers are powerful passive abilities that you can activate to give you an advantage in combat. You can " +
                        "activate and deactivate prayers from within the Prayer panel.",
                )
                npc(
                    aereck,
                    "Now, you should know that the benefits from praying are not freely given. It requires continued focus and " +
                        "faith to be able to use them.",
                )
                box(
                    "Using prayers will drain your Prayer Points, with stronger ones draining Prayer Points faster than weaker " +
                        "ones. Your current Prayer Points are shown in the orb next to your minimap.",
                )
                me("Is there a good way for me to make sure I don't lose my faith?")
                npc(aereck, "Well, that's what altars like the one in this church are for!")
                box(
                    "Once you've run out of Prayer Points, you'll need to restore them before you can use any prayers again. " +
                        "Prayer Points can be restored at an altar. To find an altar, just look for the relevant icon on your map.",
                )
                me("I see. Is there anything else I should know?")
                npc(aereck, "There is. The greater benefits of praying are only granted to those who show their dedication to Saradomin.")
                me("And how do I do that?")
                npc(aereck, "If you pay your respects to the dead by burying their bones, you will very much gain his favour.")
                box(
                    "Prayer XP is gained by burying bones. The higher your Prayer level, the more Prayer Points you will have " +
                        "available, and the more prayers you'll be able to use.",
                )
                npc(
                    aereck,
                    "And I think that covers pretty much everything. I'm sure you'll still learn an extra thing or two while " +
                        "out on your adventures though.",
                )
                box("To learn more about Prayer, see the guide for it in the Skills panel.")
                me("Thanks!")
            }

            2 -> {
                me("Thank you, but I'd better be going.")
                npc(aereck, "May Saradomin guide you.")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- Father Urhney

    private suspend fun PlayerScript.urhneyTalk(urhney: NpcRef) {
        val p = player
        npc(urhney, "Go away! I'm meditating!")
        val stage = p.stage
        val options =
            when {
                stage == 1 -> {
                    arrayOf(
                        "Well, that's friendly.",
                        "Father Aereck sent me to talk to you.",
                        "I've come to repossess your house.",
                    )
                }

                stage >= 2 -> {
                    arrayOf("Well, that's friendly.", "I've lost the Amulet of Ghostspeak.", "I've come to repossess your house.")
                }

                else -> {
                    arrayOf("Well, that's friendly.", "I've come to repossess your house.")
                }
            }
        val pick = choice("Select an option", *options)
        when {
            pick == 1 -> {
                me("Well, that's friendly.")
                npc(urhney, "I SAID go AWAY.")
                me("Okay, okay... sheesh, what a grouch.")
            }

            pick == 2 && stage == 1 -> {
                sentByAereck(urhney)
            }

            pick == 2 && stage >= 2 -> {
                lostAmulet(urhney)
            }

            pick == 2 || pick == 3 -> {
                repossess(urhney)
            }
        }
    }

    private suspend fun PlayerScript.sentByAereck(urhney: NpcRef) {
        me("Father Aereck sent me to talk to you.")
        npc(urhney, "I suppose I'd better talk to you then. What problems has he got himself into this time?")
        if (choice("Select an option", "He's got a ghost haunting his graveyard.", "You mean he gets himself into lots of problems?") ==
            2
        ) {
            me("You mean he gets himself into lots of problems?")
            npc(
                urhney,
                "Yeah. For example, when we were trainee priests he kept on getting stuck up bell ropes.",
                "Anyway. I don't have time for chitchat. What's his problem THIS time?",
            )
        }
        me("He's got a ghost haunting his graveyard.")
        npc(
            urhney,
            "Oh, the silly fool.",
            "I leave town for just five months, and ALREADY he can't manage.",
            "(sigh)",
            "Well, I can't go back and exorcise it. I vowed not to leave this place. Until I had done a full two years of " +
                "prayer and meditation.",
            "Tell you what I can do though; take this amulet.",
        )
        if (!player.give(Objs.AMULET_OF_GHOSTSPEAK, 1, "restless ghost amulet")) {
            player.message("You don't have enough inventory space.")
            return
        }
        Quests.RESTLESS_GHOST.advance(player, 2)
        itemBox(Objs.AMULET_OF_GHOSTSPEAK, "Father Urhney hands you an amulet.")
        npc(
            urhney,
            "It is an Amulet of Ghostspeak.",
            "So called, because when you wear it you can speak to ghosts. A lot of ghosts are doomed to be ghosts because they " +
                "have left some important task uncompleted.",
            "Maybe if you know what this task is, you can get rid of the ghost. I'm not making any guarantees mind you, but it " +
                "is the best I can do right now.",
        )
        me("Thank you. I'll give it a try!")
    }

    private suspend fun PlayerScript.lostAmulet(urhney: NpcRef) {
        val p = player
        me("I've lost the Amulet of Ghostspeak.")
        box("Father Urhney sighs.")
        when {
            p.has(Objs.AMULET_OF_GHOSTSPEAK) || p.inv(Invs.WORN).total(Objs.AMULET_OF_GHOSTSPEAK) > 0 -> {
                npc(urhney, "What are you talking about? I can see you've got it with you!")
            }

            p.inv(Invs.INV).freeSlots == 0 -> {
                npc(
                    urhney,
                    "How careless can you get? Those things aren't easy to come by you know! Now clear some space in your " +
                        "inventory and I'll give you another one.",
                )
            }

            else -> {
                npc(urhney, "How careless can you get? Those things aren't easy to come by you know! It's a good job I've got a spare.")
                p.give(Objs.AMULET_OF_GHOSTSPEAK, 1, "restless ghost amulet")
                box("Father Urhney hands you an amulet.")
                npc(urhney, "Be more careful this time.")
                me("Okay, I'll try to be.")
            }
        }
    }

    private suspend fun PlayerScript.repossess(urhney: NpcRef) {
        me("I've come to repossess your house.")
        npc(urhney, "Under what grounds???")
        if (choice("Select an option", "Repeated failure on mortgage repayments.", "I don't know. I just wanted this house.") == 1) {
            me("Repeated failure on mortgage repayments.")
            npc(urhney, "What?", "But... I don't have a mortgage! I built this house myself!")
            me("Sorry. I must have got the wrong address. All the houses look the same around here.")
            npc(urhney, "What? What houses? What ARE you talking about???")
            me("Never mind.")
        } else {
            me("I don't know. I just wanted this house...")
            npc(urhney, "Oh... go away and stop wasting my time!")
        }
    }

    // ---- the coffin

    private fun PlayerScript.openCoffin(coffin: LocRef) {
        val p = player
        // Parent loc 15061: its look follows varbit 2129 (coffin_var), 0 = no skull yet.
        p.addLoc(COFFIN_OPEN, coffin.coord, coffin.shape, coffin.angle)
        p.message("You open the coffin.")
    }

    private fun PlayerScript.closeCoffin(coffin: LocRef) {
        player.addLoc(COFFIN_CLOSED, coffin.coord, coffin.shape, coffin.angle)
        player.message("You close the coffin.")
    }

    private suspend fun PlayerScript.searchCoffin() {
        val p = player
        val stage = p.stage
        // The ghost emerges when the coffin is searched (Wiki "The Restless Ghost", walkthrough).
        if (stage in 1 until FINISHED && p.npcsWithin(GHOST_SPAWN, GHOST_RADIUS).none { it.type == Npcs.GHOSTX }) {
            p.spawnNpc(Npcs.GHOSTX, GHOST_SPAWN, 0)
        }
        if (stage >= FINISHED || p.varbit(Varbits.RESTLESS_GHOST_COFFIN_VAR) == 1) {
            box("There's a nice and complete skeleton in here!")
        } else {
            box("There's a skeleton without a skull in here.")
        }
    }

    private suspend fun PlayerScript.skullInCoffin() {
        val p = player
        if (p.stage != 4 || !p.has(Objs.GHOSTSKULL)) return box("There's a skeleton without a skull in here.")
        if (!p.take(Objs.GHOSTSKULL, 1, "restless ghost skull")) return
        p.setVarbit(Varbits.RESTLESS_GHOST_COFFIN_VAR, 1)
        val ghost = p.npcsWithin(GHOST_SPAWN, GHOST_RADIUS).firstOrNull { it.type == Npcs.GHOSTX }
        ghost?.say("Release! Thank you stranger..")
        npcAs(Npcs.GHOSTX, "Restless ghost", "Release! Thank you stranger..")
        ghost?.despawn()
        QuestCompletion.complete(p, Quests.RESTLESS_GHOST, REWARD)
    }

    // ---- the altar

    private suspend fun PlayerScript.searchAltar() {
        val p = player
        val stage = p.stage
        when {
            stage >= FINISHED -> {
                box("Surprisingly, this altar is empty. Probably because you took the skull off it earlier.")
            }

            p.has(Objs.GHOSTSKULL) -> {
                box("You already have the Ghost's skull.")
            }

            stage < 3 -> {
                p.message("Nothing interesting happens.")
            }

            else -> {
                // The skull may be searched for again if it was lost (OPEN-QU11).
                if (!p.give(Objs.GHOSTSKULL, 1, "restless ghost skull")) return p.message("You don't have enough inventory space.")
                Quests.RESTLESS_GHOST.advance(p, 4)
                p.setVarbit(Varbits.RESTLESS_GHOST_ALTAR_VAR, 1)
                if (p.npcsWithin(p.coord, SKELETON_RADIUS).none { it.type == Npcs.SKULL_SKELETON }) {
                    box("The skeleton in the corner suddenly comes to life!")
                    val skeleton = p.spawnNpc(Npcs.SKULL_SKELETON, p.coord.translate(1, 0), 0)
                    skeleton.huntRange = SKELETON_HUNT
                    skeleton.queueHit(SKELETON_LIFETIME, strong = false) { skeleton.despawn() }
                }
            }
        }
    }

    // ---- the ghost

    private suspend fun PlayerScript.ghostTalk(ghost: NpcRef) {
        val p = player
        val stage = p.stage
        me("Hello ghost, how are you?")
        if (!p.wearsAmulet) return ghostWoo(ghost)
        if (stage >= 3) {
            npc(ghost, "How are you doing finding my skull?")
            if (stage == 4) {
                me("I have found it!")
                npc(ghost, "Hurrah! Now I can stop being a ghost! You just need to put it in my coffin there, then I'll be free!")
            } else {
                me("Sorry, I can't find it at the moment.")
                npc(
                    ghost,
                    "Ah well. Keep on looking.",
                    "I'm pretty sure it's somewhere in the tower south-west from here. There's a lot of levels to the tower, " +
                        "though. I suppose it might take a little while to find.",
                )
            }
            return
        }
        npc(ghost, "Not very good actually.")
        me("What's the problem then?")
        npc(ghost, "Did you just understand what I said???")
        when (
            choice(
                "Select an option",
                "Yep, now tell me what the problem is.",
                "No, you sound like you're speaking nonsense to me.",
                "Wow, this amulet works!",
            )
        ) {
            1 -> {
                me("Yep, now tell me what the problem is.")
                npc(ghost, "WOW! This is INCREDIBLE! I didn't expect anyone to ever understand me again!")
                me("Ok, Ok, I can understand you!", "But have you any idea WHY you're doomed to be a ghost?")
                skullStory(ghost)
            }

            2 -> {
                me("No, you sound like you're speaking nonsense to me.")
                npc(ghost, "Oh that's a pity. You got my hopes up there.")
                me("Yeah, it is a pity. Sorry about that.")
                npc(ghost, "Hang on a second... you CAN understand me!")
                if (choice("Select an option", "No I can't.", "Yep, clever aren't I?") == 1) {
                    me("No I can't.")
                    npc(ghost, "Great. The first person I can speak to in ages...and they're a moron.")
                } else {
                    me("Yep, clever aren't I?")
                    npc(ghost, "I'm impressed. You must be very powerful. I don't suppose you can stop me being a ghost?")
                    askWhy(ghost)
                }
            }

            3 -> {
                me("Wow, this amulet works!")
                npc(
                    ghost,
                    "Oh! It's your amulet that's doing it! I did wonder. I don't suppose you can help me? I don't like being a ghost.",
                )
                askWhy(ghost)
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.askWhy(ghost: NpcRef) {
        if (choice("Select an option", "Yes, ok. Do you know WHY you're a ghost?", "No, you're scary!") == 1) {
            me("Yes, ok. Do you know WHY you're a ghost?")
            npc(ghost, "Nope. I just know I can't do much of anything like this!")
            skullStory(ghost)
        } else {
            me("No, you're scary!")
            npc(ghost, "Great.", "The first person I can speak to in ages...", "..and they're an idiot.")
        }
    }

    /** From "Well, to be honest... I'm not sure." to the skull and the tower; the quest moves on. */
    private suspend fun PlayerScript.skullStory(ghost: NpcRef) {
        npc(ghost, "Well, to be honest... I'm not sure.")
        me("I've been told a certain task may need to be completed so you can rest in peace.")
        npc(
            ghost,
            "I should think it is probably because a warlock has come along and stolen my skull. If you look inside my coffin " +
                "there, you'll find my corpse without a head on it.",
        )
        me("Do you know where this warlock might be now?")
        npc(ghost, "I think it was one of the warlocks who lives in the big tower by the sea south-west from here.")
        me("Ok. I will try and get the skull back for you, then you can rest in peace.")
        npc(ghost, "Ooh, thank you. That would be such a great relief!", "It is so dull being a ghost...")
        // OPEN-QU12: the stage only moves once the quest is under way (the Wiki tells no other order).
        if (player.stage >= 1) Quests.RESTLESS_GHOST.advance(player, 3)
    }

    /** The ghost cannot be understood without the amulet (Wiki "Without wearing the Amulet of Ghostspeak"). */
    private suspend fun PlayerScript.ghostWoo(ghost: NpcRef) {
        npc(ghost, "Wooo wooo wooooo!")
        when (
            choice(
                "Select an option",
                "Sorry, I don't speak ghost.",
                "Ooh, THAT'S interesting.",
                "Any hints where I can find some treasure?",
            )
        ) {
            1 -> {
                noSpeak(ghost)
            }

            2 -> {
                me("Ooh, THAT'S interesting.")
                npc(ghost, "Woo wooo. Woooooooooooooooooo!")
                if (choice("Select an option", "Did he really?", "Yeah, that's what I thought.") == 1) {
                    me("Did he really?")
                    npc(ghost, "Woo.")
                    if (choice("Select an option", "My brother had EXACTLY the same problem.", "Goodbye. Thanks for the chat.") == 1) {
                        me("My brother had EXACTLY the same problem.")
                        npc(ghost, "Woo Wooooo!", "Wooooo Woo woo woo!")
                        if (choice("Select an option", "Goodbye. Thanks for the chat.", "You'll have to give me the recipe some time...") ==
                            1
                        ) {
                            goodbye(ghost)
                        } else {
                            me("You'll have to give me the recipe some time...")
                            npc(ghost, "Wooooooo woo woooooooo.")
                            if (choice("Select an option", "Goodbye. Thanks for the chat.", "Hmm... I'm not so sure about that.") ==
                                1
                            ) {
                                goodbye(ghost)
                            } else {
                                notSure(ghost)
                            }
                        }
                    } else {
                        goodbye(ghost)
                    }
                } else {
                    me("Yeah, that's what I thought.")
                    npc(ghost, "Wooo woooooooooooooo...")
                    if (choice("Select an option", "Goodbye. Thanks for the chat.", "Hmm I'm not sure about that.") ==
                        1
                    ) {
                        goodbye(ghost)
                    } else {
                        notSure(ghost)
                    }
                }
            }

            3 -> {
                me("Any hints where I can find some treasure?")
                npc(ghost, "Wooooooo woo! Wooooo woo wooooo woowoowoo woo Woo wooo. Wooooo woo woo? Woooooooooooooooooo!")
                if (choice("Select an option", "Sorry, I don't speak ghost.", "Thank you. You've been very helpful.") == 1) {
                    noSpeak(ghost)
                } else {
                    me("Thank you. You've been very helpful.")
                    npc(ghost, "Wooooooo.")
                }
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.noSpeak(ghost: NpcRef) {
        me("Sorry, I don't speak ghost.")
        npc(ghost, "Woo woo?")
        me("Nope, still don't understand you.")
        npc(ghost, "WOOOOOOOOO!")
        me("Never mind.")
    }

    private suspend fun PlayerScript.goodbye(ghost: NpcRef) {
        me("Goodbye. Thanks for the chat.")
        npc(ghost, "Wooo wooo?")
    }

    private suspend fun PlayerScript.notSure(ghost: NpcRef) {
        me("Hmm... I'm not so sure about that.")
        npc(ghost, "Wooo woo?")
        me("Well, if you INSIST.")
        npc(ghost, "Wooooooooo!")
        me("Ah well, better be off now...")
        npc(ghost, "Woo.")
        me("Bye.")
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val FINISHED = 5

        /** Wear position of the amulet (Wiki "Equipment": head 0, cape 1, neck 2). */
        const val AMULET_SLOT = 2

        /** The coffin in the Lumbridge graveyard (cache loc 2145 "Open") and its open form, multi-loc 15061 (varbit 2129). */
        const val COFFIN_CLOSED = 2145
        const val COFFIN_OPEN = 15061

        /** What the open coffin is clicked as: the multi-loc and its two children (cache locs 15052 / 15053). */
        val OPEN_COFFINS = intArrayOf(COFFIN_OPEN, 15052, 15053)

        /** The Wizards' Tower basement altar: multi-loc 2146 (varbit 2130) and its children 15050 / 15051. */
        val ALTARS = intArrayOf(2146, 15050, 15051)

        /** Where the ghost appears: Wiki "Restless ghost" map 3250,3193, radius 3. */
        val GHOST_SPAWN = CoordGrid(3250, 3193, 0)
        const val GHOST_RADIUS = 6

        /** The skeleton of the altar (level 13): looks for the player within 5 tiles, leaves after 200 ticks (OPEN-QU11). */
        const val SKELETON_HUNT = 5
        const val SKELETON_RADIUS = 6
        const val SKELETON_LIFETIME = 200

        /** Wiki "The Restless Ghost" rewards: 1 quest point, 1,125 Prayer experience (cache `Quest` 120: (5, 11250) tenths), the amulet. */
        val REWARD =
            QuestReward(
                model = Objs.AMULET_OF_GHOSTSPEAK,
                xp = listOf(Stats.PRAYER to 1125.0),
                notes = listOf("Ghostspeak amulet"),
            )
    }
}
