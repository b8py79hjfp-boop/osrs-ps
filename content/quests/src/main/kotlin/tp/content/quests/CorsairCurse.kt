@file:Suppress(
    "LongMethod",
    "LargeClass",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "UnusedParameter",
)
// UnusedParameter: generated dialogue signatures.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * The Corsair Curse (docs/mechanics/quest-the-corsair-curse.md): Captain Tock, Ithoi, Arsen, Colin, Gnocci and Chief Tess. The
 * quest variable is varbit 6071; 0 and 60 are the cache's values and the multi-NPCs fix more (Tock leaves Port Sarim at 6, Ithoi
 * is gone from 52). Ours (OPEN-QU1): 6 started, 10 Ithoi talked to, 20 the three curses are ruled out, 30 the traitor hunt,
 * 40 Ithoi confessed, 50 the lie is proven, 52 Ithoi dead. Which corsair has been investigated is an account flag
 * `quests.cc.*`. Dialogue is the Wiki "Transcript:The Corsair Curse". The boat trips and cutscenes are not staged (OPEN-QUB15).
 */
public class CorsairCurse : ContentPlugin {
    override fun ContentScope.register() {
        for (tock in listOf(Npcs.CORSAIR_CAPTAIN_CROSSROADS, Npcs.CORSAIR_CAPTAIN_1OP, Npcs.CORSAIR_CAPTAIN_2OPS)) {
            onOpNpc(tock, OP_TALK) { npc ->
                player.stopInteraction()
                tockTalk(npc)
            }
        }
        onOpNpc(Npcs.CORSAIR_CAPTAIN_2OPS, OP_TRAVEL) { tock ->
            player.stopInteraction()
            sail(player, tock)
        }
        for (ithoi in listOf(Npcs.CORSCURS_NAVIGATOR_SICK, Npcs.CORSCURS_NAVIGATOR)) {
            onOpNpc(ithoi, OP_TALK) { npc ->
                player.stopInteraction()
                ithoiTalk(npc)
            }
        }
        for (arsen in listOf(Npcs.CORSCURS_THIEF_SICK, Npcs.CORSCURS_THIEF)) {
            onOpNpc(arsen, OP_TALK) { npc ->
                player.stopInteraction()
                arsenTalk(npc)
            }
        }
        for (colin in listOf(Npcs.CORSCURS_CABINBOY_SICK, Npcs.CORSCURS_CABINBOY, Npcs.CORSCURS_CABINBOY_TELESCOPE)) {
            onOpNpc(colin, OP_TALK) { npc ->
                player.stopInteraction()
                colinTalk(npc)
            }
        }
        for (gnocci in listOf(Npcs.CORSCURS_COOK_SICK, Npcs.CORSCURS_COOK)) {
            onOpNpc(gnocci, OP_TALK) { npc ->
                player.stopInteraction()
                gnocciTalk(npc)
            }
        }
        onOpNpc(Npcs.OGRESS_TESS, OP_TALK) { tess ->
            player.stopInteraction()
            tessTalk(tess)
        }
        onOpLoc(Locs.CORSCURS_TELESCOPE, OP_FIRST) {
            player.stopInteraction()
            telescope()
        }
        onUseOnLoc(Objs.TINDERBOX, Locs.CORSCURS_DRIFTWOOD_DRY) {
            player.stopInteraction()
            lightDriftwood()
        }
        events.on<DigEvent> { dig(it) }
        events.on<DeathEvent> { onDeath(it) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.CORSAIR_CURSE) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.CORSAIR_CURSE.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when {
            stage(p) == 0 -> {
                listOf("I can start this quest by speaking to Captain Tock north of Port Sarim.")
            }

            stage(p) < ITHOI -> {
                listOf("Captain Tock's crew is cursed. I should meet him on his ship west of Rimmington and sail to Corsair Cove.")
            }

            stage(p) < RULED_OUT -> {
                listOf(
                    "The corsairs have their own theories about the curse: Arsen (an ogre relic), Colin (a mermaid) and Gnocci (a doll).",
                    "I should look into each of them.",
                )
            }

            stage(p) < PROVEN -> {
                listOf("I ruled out all the corsairs' theories about how they had become cursed. Something else is going on.")
            }

            stage(p) < KILLED -> {
                listOf("Ithoi faked the curse. Captain Tock wants him dealt with.")
            }

            stage(p) < END -> {
                listOf("Ithoi is dead. I should tell Captain Tock.")
            }

            else -> {
                listOf("I uncovered the traitor among the Corsairs of Corsair Cove.")
            }
        }

    /** OPEN-QUB15: the boat trip is a plain move (plugin-api has no boat-travel cutscene hook). */
    private fun sail(p: PlayerRef, tock: NpcRef) {
        p.teleport(if (tock.coord.x > RIMMINGTON_X) COVE_DOCK else RIMMINGTON_DOCK)
    }

    private suspend fun PlayerScript.travelTo(tock: NpcRef, to: CoordGrid) {
        player.teleport(to)
    }

    // ---- Captain Tock

    private suspend fun PlayerScript.tockTalk(tock: NpcRef) {
        val p = player
        val s = stage(p)
        val atRimmington = tock.coord.x > RIMMINGTON_X
        when {
            s == 0 -> {
                tockStart(tock)
            }

            atRimmington && s >= ITHOI -> {
                tockBack(tock)
            }

            s < ITHOI -> {
                tockAgain(tock)
            }

            s < RULED_OUT -> {
                tockInvestigation(tock)
            }

            s < PROVEN -> {
                tockTraitor(tock)
                QuestsB.CORSAIR_CURSE.advance(p, TRAITOR_TALKED)
            }

            s == PROVEN -> {
                tockProven(tock)
            }

            s == KILLED -> {
                tockKilled(tock)
            }

            else -> {
                npc(tock, "Arrr, ye've done well, young one.")
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock". */
    private suspend fun PlayerScript.tockStart(tock: NpcRef) {
        npc(tock, "Ahoy, young lad. Might ye be able to help me, perhaps? I be in a spot of bother.")
        when (
            choice(
                "Select an Option",
                "Who are you?",
                "What kind of help do you need?",
                "No, I'm not available.",
            )
        ) {
            1 -> {
                me("Who are you?")
                npc(
                    tock,
                    "I be the Corsair Captain! Captain of the Corsairs of Corsair Cove! My gallant band of " +
                        "corsairs are famed in song and story among all seafarers.",
                )
                when (
                    choice(
                        "Select an Option",
                        "I've never heard of you.",
                        "That's very interesting; do tell me more.",
                    )
                ) {
                    1 -> {
                        me("I've never heard of you.")
                        npc(
                            tock,
                            "Arrr... well, ye can boast of yer ignorance if ye likes, but it don't make ye sound " +
                                "smarter.",
                        )
                        me("...")
                        npc(
                            tock,
                            "Well, as I were saying, young lad, I be in a spot of bother, and I thought ye might be " +
                                "able to help.",
                        )
                    }

                    2 -> {
                        me("That's very interesting; do tell me more.")
                        npc(
                            tock,
                            "Well, as I were saying, young lad, I be in a spot of bother, and I thought ye might be " +
                                "able to help.",
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("What kind of help do you need?")
                npc(
                    tock,
                    "It be about my crew. They be a lazy bunch at times, but they be stout in the heart and " +
                        "strong in the arm, and they don't deserve what's happened to them...",
                )
                npc(tock, "It be a curse! A curse has been laid upon my crew! That's the only thing it can be.")
                npc(tock, "So I need a young lad such as yerself to find a way to lift the curse. Will ye help me?")
                when (
                    choice(
                        "Select an Option",
                        "Sure, I'll try to help with your curse.",
                        "No, I'm not available.",
                    )
                ) {
                    1 -> {
                        me("Sure, I'll try to help with your curse.")
                        npc(
                            tock,
                            "Arrr! Thanks, young lad. And a worthy lad ye must surely be, brave in the heart and stiff " +
                                "in the spine.",
                        )
                        npc(
                            tock,
                            "Now, I've a ship moored west of Rimmington. Come and meet me on board, and we'll set off " +
                                "to Corsair Cove.",
                        )
                        me("West of Rimmington, you say? Okay, I'll meet you on board your ship there.")
                        box("Captain Tock swaggers off towards Rimmington.")
                        QuestsB.CORSAIR_CURSE.advance(player, STARTED)
                    }

                    2 -> {
                        // ACT: below
                    }

                    else -> {
                        Unit
                    }
                }
            }

            3 -> {
                me("No, I'm not available.")
                npc(tock, "Arrr. Some other time, perhaps...")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock again". */
    private suspend fun PlayerScript.tockAgain(tock: NpcRef) {
        npc(
            tock,
            "Arrr, ye've found my ship - good lad. I used to command a much larger vessel, sailing the " +
                "seas with my crew of Corsairs, but now we be settled in Corsair Cove.",
        )
        npc(
            tock,
            "My Corsairs be a good crew, but this curse has laid them low, all except me. Sickly, they " +
                "be. Too weak to stand. Ye'll see fer yerself soon enough.",
        )
        npc(
            tock,
            "There be four of 'em afflicted by the curse - Gnocci, Ithoi, Arsen and young Colin. All " +
                "taken to their beds, a- mewlin' and a-pukin' like so many babbies.",
        )
        npc(tock, "I've another crewman, Francois, but he be away on a mission. I hope he be spared!")
        npc(
            tock,
            "Now be ye ready to voyage to Corsair Cove to help 'em? I can sail ye back here any time ye " +
                "likes.",
        )
        when (
            choice(
                "Select an Option",
                "Okay, I'm ready go to Corsair Cove.",
                "Not just now, thanks.",
            )
        ) {
            1 -> {
                me("Okay, I'm ready go to Corsair Cove.")
                box("The ship sails to Corsair Cove...")
                travelTo(tock, COVE_DOCK)
            }

            2 -> {
                me("Not just now, thanks.")
                npc(tock, "That's fair enough, young lad, but perhaps ye'll return to help me soon...")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.tockBack(tock: NpcRef) {
        npc(tock, "Arrr, ye've found my ship - good lad. Be ye ready to voyage to Corsair Cove?")
        if (choice("Select an Option", "Okay, I'm ready go to Corsair Cove.", "Not just now, thanks.") == 1) {
            me("Okay, I'm ready go to Corsair Cove.")
            travelTo(tock, COVE_DOCK)
        } else {
            me("Not just now, thanks.")
        }
    }

    /** The cove Tock's talks before the traitor hunt: the crew first (Wiki "Talking to Captain Tock after Ithoi"), then Arsen's relic. */
    private suspend fun PlayerScript.tockInvestigation(tock: NpcRef) {
        val p = player
        if (p.flag(ARSEN_ASKED) && !p.flag(ARSEN_RELIC)) return tockRelic(tock)
        if (!p.flag(TOCK_MET)) {
            p.setFlag(TOCK_MET, true)
            return tockAfterIthoi(tock)
        }
        npc(tock, "Have ye any idea about how to lift the curse?")
        if (choice("Select an Option", "Please take me back to Rimmington.", "I'll talk to you later.") == 1) {
            me("Please take me back to Rimmington.")
            travelTo(tock, RIMMINGTON_DOCK)
        } else {
            me("I'll talk to you later.")
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock after Ithoi". */
    private suspend fun PlayerScript.tockAfterIthoi(tock: NpcRef) {
        npcAs(Npcs.CORSCURS_NAVIGATOR, "Ithoi the Navigator", "Arrr!")
        npc(tock, "Have ye any idea about how to lift the curse?")
        when (
            choice(
                "Select an Option",
                "I've spoken to some of your crew.",
            )
        ) {
            1 -> {
                me("I've spoken to some of your crew.")
            }

            else -> {
                Unit
            }
        }
        me("I've spoken to some of your crew.")
        npc(
            tock,
            "Arrr! Make sure ye speaks to 'em all. They'll be in their huts, over to the west. Four of " +
                "them, there are - Gnocci, Ithoi, Arsen and young Colin.",
        )
        npc(tock, "Have ye any idea about how to lift the curse?")
        when (
            choice(
                "Select an Option",
                "Please take me back to Rimmington.",
                "I'll talk to you later.",
            )
        ) {
            1 -> {
                me("Please take me back to Rimmington.")
                travelTo(tock, RIMMINGTON_DOCK)
            }

            2 -> {
                me("I'll talk to you later.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock@2". */
    private suspend fun PlayerScript.tockRelic(tock: NpcRef) {
        npc(tock, "Have ye any idea about how to lift the curse?")
        when (
            choice(
                "Select an Option",
                "Arsen says he gave you a sacred ogre relic.",
                "Please take me back to Rimmington.",
                "I'll talk to you later.",
            )
        ) {
            1 -> {
                me("Arsen says he gave you a sacred ogre relic.")
                npc(
                    tock,
                    "Arrr, that he did. I'd sent him to explore the caves west of the cove, an' he brings me " +
                        "back a bone lump, saying he stole it from the ogre chief.",
                )
                me(
                    "He thinks the ogres have cursed you all because he stole one of their sacred relics, and " +
                        "he wants me to take it back to them now.",
                )
                npc(
                    tock,
                    "Arrr, I've no use fer the silly thing. If it'll get the curse lifted, ye can take it right " +
                        "to that ogre chief an' give it back.",
                )
                itemBox(Objs.CORSCURS_RELIC, "Captain Tock gives you the loot that Arsen stole from the ogre chief.")
                player.giveOrDrop(Objs.CORSCURS_RELIC, 1, "corsair curse relic")
                player.setFlag(ARSEN_RELIC, true)
            }

            2 -> {
                me("Please take me back to Rimmington.")
                travelTo(tock, RIMMINGTON_DOCK)
            }

            3 -> {
                me("I'll talk to you later.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock@3". */
    private suspend fun PlayerScript.tockTraitor(tock: NpcRef) {
        npc(tock, "Have ye any idea about how to lift the curse?")
        when (
            choice(
                "Select an Option",
                "I've ruled out all the Corsairs' theories...",
                "Please take me back to Rimmington.",
                "I'll talk to you later.",
            )
        ) {
            1 -> {
                me("I've ruled out all the Corsairs' theories...")
                me("Cabin-boy Colin thought he'd upset a mermaid, but there wasn't a mermaid, just a wet ogre.")
                me(
                    "Arsen the Thief thought he'd upset the ogre shamans by stealing a sacred ogre relic, but " +
                        "it was just a tooth-pick, and the ogres didn't care.",
                )
                me(
                    "Ithoi the Navigator said there were lots of things that could bring a curse on a crew, and " +
                        "this could be any of them.",
                )
                me(
                    "And Gnocci the Cook thought he'd awoken a demon in a possessed doll, but it wasn't " +
                        "possessed at all. It was clockwork.",
                )
                npc(
                    tock,
                    "Arrr, any sufficiently advanced technology be indistinguishable from demonic possession, " +
                        "that it be.",
                )
                when (
                    choice(
                        "Select an Option",
                        "So what do I do now?",
                        "Can I give up and go home?",
                    )
                ) {
                    1 -> {
                        me("So what do I do now?")
                        npc(
                            tock,
                            "Arrr, I don't rightly know. It began when I'd gone away for a parlay with Ithoi; we came " +
                                "back ter find the crew all a-pukin' up their dinner. Then Ithoi felt ill too, though he'd " +
                                "not eaten with 'em, so it can't be the food.",
                        )
                        me(
                            "I see. You're saying it began after they'd all eaten, but Ithoi felt ill even though he'd " +
                                "not had any of the food.",
                        )
                        npc(tock, "Arrr, that be right.")
                        me("That meal must have been interesting...")
                    }

                    2 -> {
                        me("Can I give up and go home?")
                        npc(
                            tock,
                            "If ye wants a ride back to Rimmington, I'll take ye there any time, but I do hope ye can " +
                                "help my poor crew.",
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Please take me back to Rimmington.")
            }

            3 -> {
                me("I'll talk to you later.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock after proving Ithoi poisoned the crew". */
    private suspend fun PlayerScript.tockProven(tock: NpcRef) {
        npc(tock, "So what's happening now?")
        when (
            choice(
                "Select an Option",
                "I've seen Ithoi running around. He's not sick at all.",
                "Please take me back to Rimmington.",
                "I'll talk to you later.",
            )
        ) {
            1 -> {
                me("I've seen Ithoi running around. He's not sick at all.")
                npc(
                    tock,
                    "Arrr! So he was never sick at all! An' here's me been feeling sorry for him. A trickster " +
                        "he be indeed! Kill him fer me.",
                )
                when (
                    choice(
                        "Select an Option",
                        "Why don't you kill him yourself?",
                        "I don't believe in capital punishment.",
                        "I'll be back.",
                    )
                ) {
                    1 -> {
                        me("Why don't you kill him yourself?")
                        npc(
                            tock,
                            "A captain shouldn't leave his ship in a time of crisis. Rule of the sea, that is. Even if " +
                                "the crisis ain't exactly on the ship.",
                        )
                    }

                    2 -> {
                        me("I don't believe in capital punishment.")
                        npc(tock, "I do.")
                    }

                    3 -> {
                        me("I'll be back.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Please take me back to Rimmington.")
            }

            3 -> {
                me("I'll talk to you later.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Captain Tock after killing Ithoi". */
    private suspend fun PlayerScript.tockKilled(tock: NpcRef) {
        npc(tock, "So what's happening now?")
        when (
            choice(
                "Select an Option",
                "I've killed Ithoi for poisoning your crew.",
                "Please take me back to Rimmington.",
                "I'll talk to you later.",
            )
        ) {
            1 -> {
                me("I've killed Ithoi for poisoning your crew.")
                npc(
                    tock,
                    "Thank ye fer dealing with that serpent. I suppose the rest of my crew will feel better now " +
                        "ye've proven there be no curse on them.",
                )
                npc(tock, "Most of them deleterious effects probably be psychosomatic anyway, arrr!")
                npc(
                    tock,
                    "Anyways, ye've been a mighty help, young lad. Ye be brave in the heart, strong in the arm " +
                        "and full in the bowels, an' I be grateful to ye.",
                )
                npc(
                    tock,
                    "Tell ye what, I'll have a word with Yusuf, in the bank. Ye've earned the right to use his " +
                        "services. An' thanks again, very much.",
                )
                // {{Qact|Congratulations! Quest complete!}}
            }

            2 -> {
                me("Please take me back to Rimmington.")
            }

            3 -> {
                me("I'll talk to you later.")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- Ithoi

    private suspend fun PlayerScript.ithoiTalk(ithoi: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            s < STARTED -> {
                npc(ithoi, "Arrr, we be cursed, we be CURSED! It be a curse upon us!")
            }

            s < ITHOI -> {
                ithoiFirst(ithoi)
            }

            s < RULED_OUT -> {
                ithoiInvestigation(ithoi)
            }

            s < TRAITOR_TALKED -> {
                ithoiEarly(ithoi)
            }

            s < CONFESSED -> {
                ithoiAccuse(ithoi)
                QuestsB.CORSAIR_CURSE.advance(p, CONFESSED)
            }

            s < PROVEN -> {
                ithoiLie(ithoi)
            }

            else -> {
                // The fight (OPEN-QUB15: no instance): the sick navigator turns into the combat variant.
                ithoi.transform(Npcs.CORSCURS_NAVIGATOR_COMBAT, restoreStats = false)
                ithoi.huntRange = FIGHT_HUNT
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Ithoi". */
    private suspend fun PlayerScript.ithoiFirst(ithoi: NpcRef) {
        npc(ithoi, "Arrr, we be cursed, we be CURSED! It be a curse upon us!")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "I think I'll leave you to get on with it.",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(ithoi, "Aye? D'ye think so? Really? Am I not a picture of health an' beauty?")
                npc(ithoi, "YES - WE'VE BEEN CURSED!")
                npc(ithoi, "I've sailed the seas all my life. I sailed the seas as a man, like I sailed 'em as a boy.")
                npc(
                    ithoi,
                    "I even sailed the seas during that tricky phase in between, when ye'r not quite a man yet, " +
                        "but ye'r not really a boy anymore. But I don't want to talk about that bit.",
                )
                me("Um...")
                npc(
                    ithoi,
                    "The point I'm makin' is that I know the seas like I know the palm of my hand. I've been to " +
                        "strange lands an' seen things that'll make yer juices turn in yer veins.",
                )
                npc(
                    ithoi,
                    "I've seen dancing fish-women, monkey-kings, huge men in turbans from the far West selling " +
                        "treasure maps - you name it, an' I've seen it.",
                )
                npc(
                    ithoi,
                    "There be more things in the world than ye dreams of in yer fancy book-learnin'! An' most " +
                        "of 'em be hostile. Could be any number of things brought this curse upon us. Demons, evil " +
                        "spirits, anything!",
                )
                me("Okay, it doesn't sound like you know what's behind the curse. Thanks.")
                QuestsB.CORSAIR_CURSE.advance(player, ITHOI)
                npc(ithoi, "Arrr!")
            }

            2 -> {
                me("I think I'll leave you to get on with it.")
                npc(ithoi, "... arrr.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Ithoi during the investigation of the "curses"". */
    private suspend fun PlayerScript.ithoiInvestigation(ithoi: NpcRef) {
        npc(ithoi, "Ye be back? Tell me, do those scurvy Corsairs take the curse seriously?")
        me("I've been hearing about what might have caused it...")
        me("Colin thought he upset a mermaid, and she might have cursed you all in revenge.")
        npc(ithoi, "Arrr! That'll teach him to meddle with such folk. Colin should stick to what he's good at.")
        me(
            "Gnocci fished a doll out of the water, and he thought it might have been possessed by an " +
                "evil spirit.",
        )
        npc(
            ithoi,
            "Arrr chuckie chuckie chuckie! That'll teach him to play with dolls at his age. Dolls can " +
                "be frightening.",
        )
        me(
            "Arsen stole what he thought was a sacred relic from the ogre cave, and he said their " +
                "shamans might have cursed you in revenge.",
        )
        npc(ithoi, "Arrr! Ogre women be powerful, an' Arsen be a fool.")
        me("I'll carry on trying to solve the curse.")
        npc(ithoi, "Arrr! Off ye goes...")
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Ithoi before talking to everyone else". */
    private suspend fun PlayerScript.ithoiEarly(ithoi: NpcRef) {
        npc(ithoi, "Ye be back? Have ye been investigatin' the curse?")
        when (
            choice(
                "Select an Option",
                "I hear it happened straight after dinner.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("I hear it happened straight after dinner.")
                npc(
                    ithoi,
                    "Arrr! That be wrong! I were away for a parlay with Captain Tock, an' I didn't eat that " +
                        "dinner at all. The Captain can tell ye so himself.",
                )
                npc(ithoi, "So it must be a curse. A CURSE!")
                me("Okay, thanks.")
            }

            2 -> {
                me("I'm still looking into it.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Ithoi after talking to everyone else". */
    private suspend fun PlayerScript.ithoiAccuse(ithoi: NpcRef) {
        npc(ithoi, "Ye be back? Have ye been investigatin' the curse?")
        when (
            choice(
                "Select an Option",
                "I hear you cooked the meal they ate before getting sick.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("I hear you cooked the meal they ate before getting sick.")
                npc(
                    ithoi,
                    "Arrr? And what if I did? Can't an old sailor help his crew without interfering young " +
                        "land-lubbers making accusations?",
                )
                when (
                    choice(
                        "Select an Option",
                        "Maybe because the Captain's thinking of firing you.",
                        "Maybe you're just a nasty person.",
                        "I don't know your reason.",
                    )
                ) {
                    1 -> {
                        me(
                            "Maybe because the Captain's thinking of firing you - Arsen heard it from his brother. The " +
                                "Corsairs don't need a navigator anymore.",
                        )
                        npc(
                            ithoi,
                            "Arrr! Too clever ye be... I heard the Captain might kick me off the crew, so I poisoned " +
                                "the dinner, an' acted sick too. Now they think they're cursed, so they'll want me around, " +
                                "wi' my knowledge o' such things!",
                        )
                        npc(ithoi, "But they all think I'm sick, so they'll never believe I'm behind it. Hahaha!")
                        me("We'll see what they have to say about that!")
                    }

                    2 -> {
                        me("Maybe you're just a nasty person.")
                        npc(
                            ithoi,
                            "Bah! Ye can't even think of a reason why I'd want to poison the crew, so don't bother me " +
                                "with yer accusations.",
                        )
                    }

                    3 -> {
                        me("I don't know your reason.")
                        npc(
                            ithoi,
                            "Bah! Ye can't even think of a reason why I'd want to poison the crew, so don't bother me " +
                                "with yer accusations.",
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("I'm still looking into it.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Ithoi again". */
    private suspend fun PlayerScript.ithoiLie(ithoi: NpcRef) {
        npc(ithoi, "What d'ye want now?")
        when (
            choice(
                "Select an Option",
                "I know you've faked the curse.",
                "Um... nothing, really.",
            )
        ) {
            1 -> {
                me("I know you've faked the curse.")
                npc(
                    ithoi,
                    "The Corsairs all think I'm sick too, even though I never ate the food. An' ye'll never " +
                        "make me prove that I'm healthy - I ain't gettin' up! So they'll keep believing in the " +
                        "curse.",
                )
                when (
                    choice(
                        "Select an Option",
                        "I bet I can prove you're well enough to get up.",
                        "I'll be back.",
                    )
                ) {
                    1 -> {
                        me("I bet I can prove you're well enough to get up.")
                        npc(
                            ithoi,
                            "Ye'll never make me get up! I ain't afraid of cold, nor of wet, nor of anythin' ye can do " +
                                "in here.",
                        )
                    }

                    2 -> {
                        me("I'll be back.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Um... nothing, really.")
            }

            else -> {
                Unit
            }
        }
    }

    private fun onDeath(e: DeathEvent) {
        val npc = e.entity as? NpcRef ?: return
        val killer = e.lootFor ?: return
        if (npc.type == Npcs.CORSCURS_NAVIGATOR_COMBAT && stage(killer) in PROVEN until KILLED) {
            QuestsB.CORSAIR_CURSE.advance(killer, KILLED)
        }
    }

    // ---- the crew

    private suspend fun PlayerScript.arsenTalk(arsen: NpcRef) {
        val p = player
        when {
            stage(p) < ITHOI -> arsenBefore(arsen)
            stage(p) >= RULED_OUT -> arsenLater(arsen)
            p.flag(ARSEN_DONE) -> npc(arsen, "Urgh!")
            p.flag(ARSEN_TESS) -> arsenReturn(arsen)
            p.flag(ARSEN_ASKED) -> arsenAgain(arsen)
            else -> arsenCurse(arsen)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Arsen before talking to Ithoi". */
    private suspend fun PlayerScript.arsenBefore(arsen: NpcRef) {
        npc(arsen, "Urgh!")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "Urgh to you too, with candy sprinkles on.",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(
                    arsen,
                    "I think so. That navigator, Ithoi, always warned us that this kind of thing could happen, " +
                        "but I didn't think it could possibly be real. But now this has happened, and I think it's " +
                        "my fault.",
                )
                npc(
                    arsen,
                    "You should maybe see what Ithoi thinks. He's been cursed too, so I guess he'll be in his " +
                        "hut, off to the south-west, near the shore.",
                )
            }

            2 -> {
                me("Urgh to you too, with candy sprinkles on.")
                npc(arsen, "A sympathetic soul you are. I'm in real pain here.")
                me("Yes, I hear you've been cursed.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Arsen". */
    private suspend fun PlayerScript.arsenCurse(arsen: NpcRef) {
        npc(arsen, "Urgh!")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "Urgh to you too, with candy sprinkles on.",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(arsen, "I think so. That navigator, Ithoi, always warned us that this kind of thing could happen.")
                me(
                    "I've met him. He definitely thinks it's a curse, but wasn't able to say exactly what " +
                        "caused it.",
                )
                npc(arsen, "I'm afraid it may be all my fault.")
                me("Oh?")
                npc(
                    arsen,
                    "Captain Tock had me exploring some caves, west of the cove. I'm a thief, see, like my " +
                        "brother Francois, and the Captain thought I could sneak past the monsters without being " +
                        "noticed.",
                )
                npc(arsen, "Well, I climbed down the vines, and found a big cave filled with terrifying ogres!")
                npc(
                    arsen,
                    "I'd hoped I could use my amazing Thieving skills to avoid them seeing me, but I was out of " +
                        "luck. By the time I'd finished clambering down the ladder, they'd spotted me.",
                )
                me("So you were trying to explore that ogre cave, and the ogres saw you?")
                npc(
                    arsen,
                    "Yes. I quickly grabbed some loot, so I wouldn't have to face the Captain empty-handed, and " +
                        "fled up the ladder before the ogres could kill me!",
                )
                npc(
                    arsen,
                    "But a couple of days later we all fell ill. I think what I stole must have been some kind " +
                        "of sacred relic, and the shamans in the cave have cursed us for stealing it.",
                )
                npc(
                    arsen,
                    "Maybe you could get the relic back from the Captain, and return it to the ogres? And tell " +
                        "them I'm very sorry. Then perhaps they'll lift the curse.",
                )
                player.setFlag(ARSEN_ASKED, true)
                me("I see.")
            }

            2 -> {
                me("Urgh to you too, with candy sprinkles on.")
                npc(arsen, "A sympathetic soul you are. I'm in real pain here.")
                me("Yes, I hear you've been cursed.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Arsen again". */
    private suspend fun PlayerScript.arsenAgain(arsen: NpcRef) {
        npc(
            arsen,
            "Have you got that relic back from Captain Tock? Maybe if you return it to the ogres, " +
                "they'll lift the curse.",
        )
        if (!player.flag(ARSEN_RELIC)) {
            me("Nope, I've not done that.")
            npc(arsen, "Oh dear.")
            return
        }
        if (player.flag(ARSEN_RELIC)) {
            npc(arsen, "Please take it back to the ogres for me, and tell them I'm sorry.")
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Returning to Arsen". */
    private suspend fun PlayerScript.arsenReturn(arsen: NpcRef) {
        npc(arsen, "Have you returned the sacred relic to the ogres? Will they forgive me and lift the curse?")
        me("Their chief, Tess, says that wasn't a sacred relic at all. It was her tooth-pick.")
        npc(arsen, "Are you telling me the ogres cursed us all because I stole their chief's tooth-pick?")
        me(
            "She didn't care - she's got lots of tooth-picks, and she just thought you were some kind " +
                "of weirdo when you stole one. They never cursed you at all.",
        )
        npc(
            arsen,
            "Oh. That's a relief, I guess. Um... thank you for your help. But I do wonder where the " +
                "curse really came from.",
        )
        me("I'm not done investigating yet.")
        player.setFlag(ARSEN_DONE, true)
        checkRuledOut()
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Arsen@2". */
    private suspend fun PlayerScript.arsenTraitor(arsen: NpcRef) {
        npc(arsen, "Do you know how we all got cursed?")
        when (
            choice(
                "Select an Option",
                "I hear Ithoi cooked the meal you ate that night.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("I hear Ithoi cooked the meal you ate that night.")
                npc(
                    arsen,
                    "I suppose Ithoi's trying to be extra helpful at the moment. Don't tell anyone, but Ithoi " +
                        "might not be in our crew much longer.",
                )
                npc(
                    arsen,
                    "My brother, Francois, says the Captain will make him second-in-command if he completes his " +
                        "mission. That's Ithoi's rank, but we don't need a navigator now we've settled here, and he " +
                        "can't do much else.",
                )
                when (
                    choice(
                        "Select an Option",
                        "What is the mission Francois is doing?",
                        "Okay, thanks.",
                    )
                ) {
                    1 -> {
                        me("What is the mission Francois is doing?")
                        npc(arsen, "That's a secret.")
                        me("Okay, thanks.")
                    }

                    2 -> {
                        me("Okay, thanks.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("I'm still looking into it.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.arsenLater(arsen: NpcRef) {
        val p = player
        when {
            stage(p) >= PROVEN -> arsenProven(arsen)
            stage(p) >= CONFESSED -> arsenProven(arsen)
            else -> arsenTraitor(arsen)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Arsen after proving Ithoi poisoned the crew". */
    private suspend fun PlayerScript.arsenProven(arsen: NpcRef) {
        npc(arsen, "What's happening now?")
        when (
            choice(
                "Select an Option",
                "Ithoi's faked the curse. I've seen him running around, healthy.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                npc(arsen, "Give him what he deserves!")
            }

            2 -> {
            }

            else -> {
                Unit
            }
        }
    }

    /** Once Arsen, Colin and Gnocci are cleared the curse hunt moves to the traitor (stage [RULED_OUT]). */
    private fun PlayerScript.checkRuledOut() {
        val p = player
        if (p.flag(ARSEN_DONE) && p.flag(COLIN_DONE) && p.flag(GNOCCI_DONE)) QuestsB.CORSAIR_CURSE.advance(p, RULED_OUT)
    }

    private suspend fun PlayerScript.colinTalk(colin: NpcRef) {
        val p = player
        when {
            stage(p) < ITHOI -> colinBefore(colin)
            stage(p) >= PROVEN -> colinProven(colin)
            stage(p) >= RULED_OUT -> colinTraitor(colin)
            p.flag(COLIN_DONE) -> npc(colin, "I'm sorry! Tell her I'm sorry!")
            p.flag(COLIN_SCOPE) -> colinReturn(colin)
            p.flag(COLIN_ASKED) -> colinAgain(colin)
            else -> colinCurse(colin)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Colin before talking to Ithoi". */
    private suspend fun PlayerScript.colinBefore(colin: NpcRef) {
        npc(colin, "I'm sorry! Tell her I'm sorry! Then maybe she'll make the pain stop...")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "Perhaps you can tell me about it another time.",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(
                    colin,
                    "Yes, that's what it must be. Ithoi, the navigator, always told us to beware of curses. You " +
                        "should talk to him - he really knows about these things.",
                )
                npc(colin, "Ithoi lives in the hut to the south-west, nearest to the sea.")
            }

            2 -> {
                me("Perhaps you can tell me about it another time.")
                npc(colin, "I'll be here. I'm not going anywhere while I feel so sick.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Colin". */
    private suspend fun PlayerScript.colinCurse(colin: NpcRef) {
        npc(colin, "I'm sorry! Tell her I'm sorry! Then maybe she'll make the pain stop...")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "Perhaps you can tell me about it another time.",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(colin, "Yes, that's what it must be. Ithoi, the navigator, always told us to beware of curses.")
                me(
                    "I've met him. He definitely thinks it's a curse, but wasn't able to say exactly what " +
                        "caused it.",
                )
                npc(
                    colin,
                    "This is all my fault. But I didn't mean to upset her. I just thought we could be friends. " +
                        "Now I must have upset her, and she's cursed us all!",
                )
                npc(
                    colin,
                    "Ithoi always told us that bad things happen to sailors who get involved with mermaids, but " +
                        "I didn't believe him. I should have listened! But I'll tell you what happened...",
                )
                npc(colin, "I was using Ithoi's telescope...")
                player.setFlag(COLIN_ASKED, true)
                // ACT: A flashback to a few nights ago...
                npc(colin, "Such a beautiful night!")
                npc(colin, "What lands lie beyond the dim horizon, I wonder...")
                // ACT: Colin uses the telescope at Ithoi's hut.
                npc(colin, "Eh?")
                npc(colin, "What was that? I'd better have another look.")
                // ACT: Colin uses the telescope again.
                npc(colin, "Is that what I think it is?")
                npc(colin, "It's so dark out there, but I think... ... yes, I'm quite sure...")
                npc(colin, "... yes, it's a mermaid! She's beautiful!")
                npc(colin, "Hello?")
                npc(colin, "Can you hear me?")
                npc(colin, "You're the most lovely person I've ever seen!")
                // ACT: Colin uses the telescope again.
                npc(colin, "I can see you moving your arms, but it's very dark. Are you waving to me?")
                npc(colin, "{{Colour|#0000ff|*kiss*}}")
                npc(colin, "Hello?")
                // ACT: Colin uses the telescope again.
                npc(colin, "Oh, no, I think she's gone away. Did I put her off?")
                npc(
                    colin,
                    "Maybe she doesn't like strangers shouting about her appearance and blowing kisses at her. " +
                        "That might have been rude of me.",
                )
                npc(colin, "I hope she's not angry. Ithoi says mermaids can be dangerous.")
                // ACT: Cutscene ends.
                npc(
                    colin,
                    "Perhaps you can find the mermaid and tell her I'm very sorry I was rude to her. Then maybe " +
                        "she'll lift the curse.",
                )
            }

            2 -> {
                me("Perhaps you can tell me about it another time.")
                npc(colin, "I'll be here. I'm not going anywhere while I feel so sick.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Colin again". */
    private suspend fun PlayerScript.colinAgain(colin: NpcRef) {
        npc(colin, "Have you found the mermaid yet? Will she forgive me?")
        when (
            choice(
                "Select an Option",
                "Where did you find this mermaid?",
                "Nope, I've not seen any mermaids round here.",
            )
        ) {
            1 -> {
                me("Where did you find this mermaid?")
                npc(
                    colin,
                    "I was using Ithoi's telescope to look out at the sea, imagining the strange lands and " +
                        "fantastic creatures Ithoi's always telling us he's seen, and I saw her.",
                )
            }

            2 -> {
                me("Nope, I've not seen any mermaids round here.")
                npc(colin, "Oh dear, oh dear. Maybe we'll be cursed forever!")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Returning to Colin". */
    private suspend fun PlayerScript.colinReturn(colin: NpcRef) {
        npc(colin, "Have you found the mermaid yet? Will she forgive me?")
        me("I've looked through that telescope myself. That wasn't a mermaid. That was a wet ogre.")
        if (!player.questDone("Big Chompy Bird Hunting")) {
            me("He's definitely not a mermaid.")
            // ACT: below
        }
        if (player.questDone("Big Chompy Bird Hunting")) {
            me(
                "I've met that ogre before. He lives with his dad in the Feldip Hills. He's definitely not " +
                    "a mermaid.",
            )
            npc(
                colin,
                "Oh. Oh, I see. Okay... I guess that means I didn't upset a mermaid after all. So the curse " +
                    "isn't my fault! Thank you!",
            )
            me("How are you feeling?")
            npc(
                colin,
                "I still feel awful. But I'm glad you've proven it wasn't my fault that we got cursed. I " +
                    "hope you can work out what brought it on us.",
            )
            me("I'll be on the case.")
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Colin@2". */
    private suspend fun PlayerScript.colinTraitor(colin: NpcRef) {
        npc(colin, "Have you found out how we all got cursed?")
        when (
            choice(
                "Select an Option",
                "I hear it happened straight after dinner.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("I hear it happened straight after dinner.")
                npc(
                    colin,
                    "That's right. Gnocci had cooked us some swordfish. It didn't taste as good as his usual " +
                        "cooking, but I didn't complain. The Captain doesn't like complainers.",
                )
                npc(
                    colin,
                    "Then we all started feeling sick and clammy. That was when the Captain and Ithoi came back " +
                        "from their parlay, and Ithoi looked sick too.",
                )
                me("Okay, thanks.")
            }

            2 -> {
                me("I'm still looking into it.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Colin after proving Ithoi poisoned the crew". */
    private suspend fun PlayerScript.colinProven(colin: NpcRef) {
        npc(colin, "So what's going on now?")
        when (
            choice(
                "Select an Option",
                "Ithoi's faked the curse. I've seen him running around, healthy.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                npc(
                    colin,
                    "It sounds like there isn't a curse after all! Maybe I'll recover soon from whatever he did " +
                        "to me.",
                )
            }

            2 -> {
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.telescope() {
        val p = player
        if (stage(p) < ITHOI ||
            !p.flag(COLIN_ASKED)
        ) {
            return itemBox(Objs.CORSCURS_RELIC, "You look through the telescope but see only the sea.")
        }
        // Wiki "Using Ithoi's telescope": the mermaid is an ogre named Bugs.
        if (p.flag(COLIN_SCOPE)) telescopeAgain() else telescopeFirst()
        p.setFlag(COLIN_SCOPE, true)
    }

    /** Wiki "Transcript:The Corsair Curse", section "Using Ithoi's telescope". */
    private suspend fun PlayerScript.telescopeFirst(unused: Int = 0) {
        me("Let's see if I can find that mermaid he saw...")
        // ACT: An ogre approaches the telescope's view.
        if (!player.questDone("Big Chompy Bird Hunting")) {
            me("That doesn't look anything like a mermaid. That's an ogre!")
            return
        }
        if (player.questDone("Big Chompy Bird Hunting")) {
            me(
                "That doesn't look anything like a mermaid. That's Bugs, the son of that chompy hunter, " +
                    "Rantz!",
            )
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Using Ithoi's telescope again". */
    private suspend fun PlayerScript.telescopeAgain(unused: Int = 0) {
        me("I've seen enough of that ogre already. Colin might be relieved to hear it wasn't a mermaid.")
    }

    private suspend fun PlayerScript.gnocciTalk(gnocci: NpcRef) {
        val p = player
        when {
            stage(p) < ITHOI -> gnocciBefore(gnocci)
            stage(p) >= PROVEN -> gnocciProven(gnocci)
            stage(p) >= RULED_OUT -> gnocciTraitor(gnocci)
            p.flag(GNOCCI_DONE) -> npc(gnocci, "My stomach!")
            p.flag(GNOCCI_DUG) -> gnocciReturn(gnocci)
            p.flag(GNOCCI_ASKED) -> gnocciAgain(gnocci)
            else -> gnocciCurse(gnocci)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Gnocci before talking to Ithoi". */
    private suspend fun PlayerScript.gnocciBefore(gnocci: NpcRef) {
        npc(gnocci, "My stomach!")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "My lower intestine!",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(
                    gnocci,
                    "Yes, I think She did this to us. I should have left Her alone. Ithoi told us to beware of " +
                        "this kind of thing.",
                )
                npc(gnocci, "Ithoi's been cursed too. You can probably find him in his hut, south-west from here.")
            }

            2 -> {
                me("My lower intestine!")
                npc(gnocci, "... look, I'm in a lot of pain here. We're not playing Name Your Favourite Body Part.")
                me("Yes, I hear you've been cursed.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Gnocci". */
    private suspend fun PlayerScript.gnocciCurse(gnocci: NpcRef) {
        npc(gnocci, "My stomach!")
        when (
            choice(
                "Select an Option",
                "I hear you've been cursed.",
                "My lower intestine!",
            )
        ) {
            1 -> {
                me("I hear you've been cursed.")
                npc(
                    gnocci,
                    "Yes, I think She did this to us. I should have left Her alone. Ithoi told us to beware of " +
                        "this kind of thing.",
                )
                me(
                    "I've met him. He definitely thinks it's a curse, but wasn't able to say exactly what " +
                        "caused it.",
                )
                npc(gnocci, "It's all my fault, I know it is. I've awoken Her, and now She's done this to us!")
                me("Who are you talking about?")
                npc(gnocci, "I'll explain... See, I'm the cook, and I have to find the food as well as cooking it...")
                npc(gnocci, "I was fishing...")
                // ACT: A flashback to a few days ago...
                npc(gnocci, "Let's see what I can find for the boys tonight.")
                npc(gnocci, "C'mon little lobsters, come to Gnocci!")
                npc(gnocci, "Obby-lobby obby-lobby oi oi oi!")
                me("'obby-lobby' - what?")
                npc(gnocci, "It's a traditional lobster-fishing chant handed down by my ancestors. Don't interrupt.")
                npc(
                    gnocci,
                    "C'mon little lobsters. We don't want to keep the Captain waiting! He'll get angry with me " +
                        "again.",
                )
                npc(gnocci, "Ooh, what's this? This isn't a lobster.")
                npc(gnocci, "Looks more like a child's toy. But - no, it can't be...")
                npc(gnocci, "Is it moving?")
                npc(gnocci, "She's moving! SHE'S POSSESSED! HELP!")
                // ACT: the doll flashback (cutscene, OPEN-QUB15)
                npc(
                    gnocci,
                    "I buried Her in the sand, by that tree, to the west. But I still feel Her eyes staring " +
                        "into my soul. Please find Her, and kill the demon inside!",
                )
            }

            2 -> {
                me("My lower intestine!")
                npc(gnocci, "... look, I'm in a lot of pain here. We're not playing Name Your Favourite Body Part.")
                me("Yes, I hear you've been cursed.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Gnocci again". */
    private suspend fun PlayerScript.gnocciAgain(gnocci: NpcRef) {
        npc(gnocci, "Have you tried to destroy Her yet? Her eyes, those terrible eyes!")
        when (
            choice(
                "Select an Option",
                "Where did you say this happened?",
                "No, I've not.",
            )
        ) {
            1 -> {
                me("Where did you say this happened?")
                npc(
                    gnocci,
                    "On the beach, to the west of the cove. Near a big tree. I buried Her in the sand, but I " +
                        "can still feel her eyes staring into my soul. Please find Her, and kill the demon inside!",
                )
            }

            2 -> {
                me("No, I've not.")
                npc(gnocci, "Oh dear, I feel terrible. Please help us soon.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Returning to Gnocci". */
    private suspend fun PlayerScript.gnocciReturn(gnocci: NpcRef) {
        npc(gnocci, "Have you tried to destroy Her yet? Her eyes, those terrible eyes!")
        me(
            "Well, sort of. That doll wasn't possessed at all. It was a clockwork toy. They're supposed " +
                "to move.",
        )
        npc(
            gnocci,
            "Clockwork? Oh, that would explain how it moved. Ahem. Well, I suppose the curse didn't " +
                "come from Her.",
        )
        npc(gnocci, "I hope you can find the real cause soon.")
        me("Still trying.")
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Gnocchi". */
    private suspend fun PlayerScript.gnocciTraitor(gnocci: NpcRef) {
        npc(gnocci, "Have you solved the curse yet?")
        when (
            choice(
                "Select an Option",
                "I hear it happened straight after dinner.",
            )
        ) {
            1 -> {
                me("I hear it happened straight after dinner.")
                npc(
                    gnocci,
                    "Hey, don't go blaming the cook just because the curse began after dinner. I didn't even " +
                        "cook that meal. I'd been too scared of the possessed doll...",
                )
                me("You mean the clockwork doll?")
                npc(gnocci, "It's an easy mistake to make!")
                npc(
                    gnocci,
                    "Anyway I didn't dare try to fish after that, and Ithoi volunteered to do all the fishing " +
                        "and cooking instead, before he went off for his parlay with Captain Tock.",
                )
                npc(gnocci, "Anyway, Ithoi got sick without even eating it, so it can't be the food.")
                return
            }

            else -> {
                Unit
            }
        }
        when (
            choice(
                "Select an Option",
                "I hear the Captain's thinking of firing Ithoi.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("I hear the Captain's thinking of firing Ithoi.")
                npc(gnocci, "I don't see why that's got anything to do with the curse.")
                me("Perhaps not.")
            }

            2 -> {
                me("I'm still looking into it.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Gnocci after proving Ithoi poisoned the crew". */
    private suspend fun PlayerScript.gnocciProven(gnocci: NpcRef) {
        npc(gnocci, "Can you tell me what's going on now?")
        when (
            choice(
                "Select an Option",
                "Ithoi's faked the curse. I've seen him running around, healthy.",
                "I'm still looking into it.",
            )
        ) {
            1 -> {
                me("Ithoi's faked the curse. I've seen him running around, healthy.")
                npc(gnocci, "Make sure he gets what's coming to him. No-one poisons my crew!")
            }

            2 -> {
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Digging for Her": the doll lies by the lump of sand west of Ithoi's hut (OPEN-QUB15: tile from the Wiki map). */
    private fun dig(e: DigEvent) {
        val p = e.player
        if (!p.flag(GNOCCI_ASKED) || p.flag(GNOCCI_DUG) || e.at.chebyshev(DOLL_SPOT) > DOLL_RADIUS) return
        e.handled = true
        p.startScript {
            digDoll()
            p.setFlag(GNOCCI_DUG, true)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Digging for Her". */
    private suspend fun PlayerScript.digDoll(unused: Int = 0) {
        me("Okay, he said he buried the possessed doll in the sand...")
        when (
            choice(
                "Select an Option",
                "Search for the possessed doll and face the consequences.",
                "Leave the thing alone.",
            )
        ) {
            1 -> {
                if (player.baseLevel(tp.plugin.Stats.CRAFTING) < CRAFTING_DOLL) {
                    me("That's not possessed. That sounds more like clockwork.")
                    return
                }
                if (player.baseLevel(tp.plugin.Stats.CRAFTING) >= CRAFTING_DOLL) {
                    me(
                        "That's not possessed. That sounds more like clockwork. In fact I've got the Crafting level " +
                            "to make those.",
                    )
                }
            }

            2 -> {
                me("Actually, it sounds scary.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.tessTalk(tess: NpcRef) {
        val p = player
        when {
            !p.flag(ARSEN_ASKED) -> npc(tess, "What you want, skinny creature?")
            p.flag(ARSEN_RELIC) -> tessAfter(tess)
            else -> tessBefore(tess)
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Chief Tess before talking to Captain Tock". */
    private suspend fun PlayerScript.tessBefore(tess: NpcRef) {
        npc(tess, "What you want, skinny creature?")
        when (
            choice(
                "Select an Option",
                "A man called Arsen told me he'd visited your cave.",
                "What is this place?",
                "I'll leave you alone, sorry.",
            )
        ) {
            1 -> {
                me("A man called Arsen told me he'd visited your cave.")
                npc(tess, "Yah, skinny man climb down vine the other day. Little man, big idiot! Hahahaha...")
                me("He says he's very sorry...")
                npc(tess, "He very silly!")
                me("Perhaps I should come back another time.")
            }

            2 -> {
                me("What is this place?")
            }

            3 -> {
                me("I'll leave you alone, sorry.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:The Corsair Curse", section "Talking to Chief Tess after talking to Captain Tock". */
    private suspend fun PlayerScript.tessAfter(tess: NpcRef) {
        npc(tess, "What you want, skinny creature?")
        when (
            choice(
                "Select an Option",
                "I've come to return what Arsen stole.",
                "What is this place?",
                "I'll leave you alone, sorry.",
            )
        ) {
            1 -> {
                me("I've come to return what Arsen stole.")
                npc(tess, "Arsen says he's very sorry for stealing from you. Here it is...")
                itemBox(Objs.CORSCURS_RELIC, "You present Tess with Arsen's loot.")
                player.take(Objs.CORSCURS_RELIC, 1, "corsair curse relic")
                player.setFlag(ARSEN_TESS, true)
                npc(
                    tess,
                    "Yah, dat little man is big idiot. Climbed down vine, grabbed tooth-pick off Tess and ran " +
                        "away like scared kebbit.",
                )
                me(
                    "You're saying this is a tooth-pick? Not a sacred relic of the ogres? Did your shamans " +
                        "curse the Corsairs for stealing it?",
                )
                npc(
                    tess,
                    "Dis is tooth-pick. Tess got plenty more. If skinny idiot man want Tess' old tooth-pick, " +
                        "maybe he sick in his head. But Tess not having shamans curse anyone.",
                )
                me(
                    "Oh. Well, I guess Arsen will be pleased to hear he didn't bring the curse upon the " +
                        "Corsairs.",
                )
            }

            2 -> {
                me("What is this place?")
            }

            3 -> {
                me("I'll leave you alone, sorry.")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- the driftwood

    private suspend fun PlayerScript.lightDriftwood() {
        val p = player
        if (stage(p) < CONFESSED) return box("You decide not to burn the driftwood yet.")
        // OPEN-QUB15: the burning-driftwood loc state and Ithoi running out (cutscene) are not modelled; the lie is proven at once.
        QuestsB.CORSAIR_CURSE.advance(p, PROVEN)
        box("Ithoi runs out of his hut and douses the flames with magic. He is not so sick after all.")
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1

        /** Op 3 "Travel" of the Rimmington / cove captain (cache npc 7958). */
        const val OP_TRAVEL = 3

        /** Quest variable stages (OPEN-QU1): 0 and 60 are the cache's, 6 and 52 come from the multi-NPCs 7959 and 7962. */
        const val STARTED = 6
        const val ITHOI = 10
        const val RULED_OUT = 20
        const val TRAITOR_TALKED = 30
        const val CONFESSED = 40
        const val PROVEN = 50
        const val KILLED = 52
        const val END = 60

        const val TOCK_MET = "quests.cc.tock_met"
        const val ARSEN_ASKED = "quests.cc.arsen_asked"
        const val ARSEN_RELIC = "quests.cc.arsen_relic"
        const val ARSEN_TESS = "quests.cc.arsen_tess"
        const val ARSEN_DONE = "quests.cc.arsen_done"
        const val COLIN_ASKED = "quests.cc.colin_asked"
        const val COLIN_SCOPE = "quests.cc.colin_scope"
        const val COLIN_DONE = "quests.cc.colin_done"
        const val GNOCCI_ASKED = "quests.cc.gnocci_asked"
        const val GNOCCI_DUG = "quests.cc.gnocci_dug"
        const val GNOCCI_DONE = "quests.cc.gnocci_done"

        /** The x of the Rimmington dock (Wiki Captain Tock pins 2910,3226) against the cove (2574,2835). */
        const val RIMMINGTON_X = 2800
        val RIMMINGTON_DOCK = CoordGrid(2910, 3226, 0)

        /** OPEN-QUB15: the cove dock, the Wiki's Tock-on-ship pin (2574,2835). */
        val COVE_DOCK = CoordGrid(2574, 2835, 0)

        /** OPEN-QUB15: the doll's sand by the fishing spot west of Ithoi's hut (Wiki map 2531,2833), dug within this radius. */
        val DOLL_SPOT = CoordGrid(2524, 2833, 0)
        const val DOLL_RADIUS = 3
        const val FIGHT_HUNT = 8

        /** Wiki "Digging for Her": level 18 Crafting knows how to make a toy doll. */
        const val CRAFTING_DOLL = 18

        /** Wiki "The Corsair Curse" rewards: 2 quest points (cache), access to the Corsair Cove bank (Yusuf). */
        val REWARD = QuestReward(model = Objs.CORSCURS_RELIC, notes = listOf("Access to Corsair Cove"))
    }
}
