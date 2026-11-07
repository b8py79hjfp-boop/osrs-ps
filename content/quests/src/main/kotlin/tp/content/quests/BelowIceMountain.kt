@file:Suppress(
    "LongMethod",
    "LargeClass",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "MagicNumber",
    "UnusedParameter",
)
// MagicNumber: menu option numbers of the transcripts; UnusedParameter: generated dialogue signatures.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Below Ice Mountain (docs/mechanics/quest-below-ice-mountain.md): Willow's crew (Checkal, Burntof, Marley), Atlas' training,
 * the Blue Moon Inn cook's steak sandwich and the Ancient Guardian at the ruins of Camdozaal. The quest variable is varbit
 * 12063; 0 and 120 are the cache's values, ours (OPEN-QU1): 10 started, 30 the guardian awake, 40 the guardian dead. Who has been
 * recruited and Atlas' lesson are account flags `quests.bim.*`. Dialogue is the Wiki "Transcript:Below Ice Mountain". The cutscenes
 * (training, unblocking the entrance, the rock paper scissors game's room) are not staged (OPEN-QUB14): their dialogue plays in place.
 */
public class BelowIceMountain : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.BIM_WILLOW, OP_TALK) { willow ->
            player.stopInteraction()
            willowTalk(willow)
        }
        onOpNpc(Npcs.BIM_CHECKAL, OP_TALK) { checkal ->
            player.stopInteraction()
            checkalTalk(checkal)
        }
        onOpNpc(Npcs.BIM_ATLAS, OP_TALK) { atlas ->
            player.stopInteraction()
            atlasTalk(atlas)
        }
        for (burntof in listOf(Npcs.BIM_BURNTOF, Npcs.BIM_BURNTOF_NODRINK)) {
            onOpNpc(burntof, OP_TALK) { npc ->
                player.stopInteraction()
                burntofTalk(npc)
            }
        }
        onOpNpc(Npcs.BIM_MARLEY, OP_TALK) { marley ->
            player.stopInteraction()
            marleyTalk(marley)
        }
        onOpNpc(Npcs.FAI_VARROCK_BLUEMOON_CHEF, OP_TALK) { cook ->
            player.stopInteraction()
            cookTalk(cook)
        }
        for (ramarno in listOf(Npcs.CAMZODAAL_RAMARNO, Npcs.CAMZODAAL_RAMARNO_ENTRANCE)) {
            onOpNpc(ramarno, OP_TALK) { npc ->
                player.stopInteraction()
                if (player.flag(RAMARNO_PENDING)) {
                    player.setFlag(RAMARNO_PENDING, false)
                    ramarnoAfterQuest()
                } else {
                    npc(npc, "Bless my beard...")
                }
            }
        }
        onUseOnItem(Objs.BREAD, Objs.KNIFE) { startScript { makeSandwich() } }
        onModalClosed(Interfaces.QUESTSCROLL) {
            if (flag(RAMARNO_PENDING)) {
                setFlag(RAMARNO_PENDING, false)
                startScript { ramarnoAfterQuest() }
            }
        }
        events.on<DeathEvent> { onDeath(it) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.BELOW_ICE_MOUNTAIN) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.BELOW_ICE_MOUNTAIN.stage(p)

    private fun crew(p: PlayerRef): Int = listOf(CHECKAL, BURNTOF, MARLEY).count { p.flag(it) }

    private fun journal(p: PlayerRef): List<String> =
        when {
            stage(p) == 0 -> {
                listOf("I can start this quest by speaking to Willow, south of Ice Mountain.")
            }

            stage(p) < ARRIVED -> {
                listOf(
                    "Willow wants her old crew back: Burntof (Rising Sun Inn, Falador), Checkal (Barbarian Village) and Marley " +
                        "(Edgeville ruins).",
                    "${crew(p)} of the 3 are recruited.",
                )
            }

            stage(p) < DEAD -> {
                listOf("The Ancient Guardian awoke in the ruins of Camdozaal. I must defeat it.")
            }

            else -> {
                listOf("I defeated the Ancient Guardian and found Ramarno in the ruins.")
            }
        }

    // ---- Willow

    private suspend fun PlayerScript.willowTalk(willow: NpcRef) {
        val p = player
        when {
            stage(p) == 0 -> {
                willowIntro(willow)
            }

            crew(p) < CREW -> {
                willowAgain(willow)
            }

            stage(p) < ARRIVED -> {
                willowRuins(willow)
            }

            else -> {
                npc(willow, "Holy Zamorak! What is that thing?")
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Willow". */
    private suspend fun PlayerScript.willowIntro(willow: NpcRef) {
        me("Hello there. What are you doing all the way out here?")
        npc(willow, "Just... looking around.")
        me("Oh, did you lose something?")
        npc(willow, "No... I found something. A door.")
        me("A door? I see doors all the time. What makes this one so special?")
        npc(willow, "It's buried beneath a tonne of rocks. Could be the entrance to some ancient ruins...")
        me("Wow! Ancient ruins? Are you an archaeologist or something?")
        npc(willow, "Archaeologist? Me? Hmm...")
        npc(
            willow,
            "Of course I am! I'm the greatest archaeologist of our time. The name's Willow! Have you " +
                "not heard of me?",
        )
        me("I'm afraid I haven't. Are you looking for a way into the ruins?")
        npc(willow, "You bet I am, but I don't think there's any other way in.")
        me("Oh dear. Do you have another plan to get in?")
        npc(
            willow,
            "Potentially, but I won't be able to do it on my own. Nor can I leave the area. If I'm not " +
                "here to guard the entrance, some other archaeologist might come along and steal the glory " +
                "from me!",
        )
        npc(willow, "Maybe you could give me a hand? There could be untold riches inside the ruins!")
        when (
            choice(
                "Select an Option",
                "Yes.",
                "No.",
            )
        ) {
            1 -> {
                QuestsB.BELOW_ICE_MOUNTAIN.advance(player, STARTED)
                me(
                    "A chance to uncover ancient ruins doesn't happen every day! Of course I'll help! I'm $name " +
                        "by the way. How do you plan to get in?",
                )
                npc(willow, "The entrance is covered in rocks and rubble - I don't think mining them will work.")
                npc(
                    willow,
                    "We'll need something stronger. Who knows what could be inside the ruins. There could be " +
                        "traps, or even monsters!",
                )
                npc(willow, "No, I think we'll need to get my old crew...")
                npc(
                    willow,
                    "...I mean, excavation team together. But you'll need to convince them to help us. We " +
                        "didn't part on the best of terms.",
                )
                me("What happened?")
                npc(willow, "We had a falling out after a, uhm... dig, went wrong.")
                me("Where can I find your old team, and how will they help?")
                npc(willow, "To get into the ruins we'll need to blow up all the rocks blocking the entrance.")
                me(
                    "That doesn't sound too safe. What if we damage the ruins? Surely it's better to go slower " +
                        "and preserve as much as we can?",
                )
                npc(
                    willow,
                    "No! We need to get in there as soon as possible! Burntof the dwarf will do it. He's a " +
                        "master with explosives.",
                )
                npc(
                    willow,
                    "From what I can remember, he's a fan of his drink. His favourite pub being the Rising Sun " +
                        "Inn, in Falador - I'd look there first.",
                )
                npc(
                    willow,
                    "After we've blown the rocks away, we'll need someone to move all the rubble. Someone " +
                        "strong.",
                )
                me("I could do it! I'm quite strong myself.")
                npc(
                    willow,
                    "Ha ha ha! I don't think so. We need a real [strongman/strongwoman] - someone with muscles. " +
                        "An old friend of mine, Checkal, should be able to do all the heavy lifting.",
                )
                me("Oh okay... Where can I find him?")
                npc(
                    willow,
                    "Hmm... We had quite a rough falling out when we last spoke, but I heard he's hanging " +
                        "around Barbarian Village.",
                )
                npc(
                    willow,
                    "Lastly, if my hunch is correct, this place will be covered in traps. We'll need my pal " +
                        "Marley to disarm them. He's the best thief I know. Been doing it for years.",
                )
                npc(
                    willow,
                    "I have no idea where he is these days. But his cousin Charlie in Varrock may know. You " +
                        "should talk to him. He's normally somewhere near the Blue Moon Inn.",
                )
                me(
                    "You have a rather interesting group of friends... but if they get us into the ruins, I'm " +
                        "all for it.",
                )
                npc(
                    willow,
                    "Good luck. Once you've recruited one of the team, I'll meet them at the entrance. It's on " +
                        "the west side of the mountain.",
                )
            }

            2 -> {
                me("Seems like you're in a bit of a pickle. Have fun with that.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Willow again". */
    private suspend fun PlayerScript.willowAgain(willow: NpcRef) {
        npc(willow, "Back again? What can I help you with?")
        when (
            choice(
                "Select an Option",
                "Where can I find Marley?",
                "Where can I find Checkal?",
                "Where can I find Burntof?",
                "Where can I find the entrance to the ruins?",
                "Nothing, thanks.",
            )
        ) {
            1 -> {
                me("Where can I find Marley?")
                npc(
                    willow,
                    "Marley is an elusive one, to be honest. I have no idea where to find him. You should try " +
                        "talk to his cousin Charlie near the Blue Moon Inn in Varrock. Maybe he will know.",
                )
                npc(willow, "Need help finding anyone else?")
                // ACT: previous
            }

            2 -> {
                me("Where can I find Checkal?")
                npc(
                    willow,
                    "Last I heard Checkal was hanging around Barbarian Village. Maybe you should head there to " +
                        "find him?",
                )
                npc(willow, "Need help finding anyone else?")
                // ACT: previous
            }

            3 -> {
                me("Where can I find Burntof?")
                npc(willow, "Burntof likes to hang out at the Rising Sun Inn located in Falador.")
                npc(willow, "Need help finding anyone else?")
                // ACT: previous
            }

            4 -> {
                me("Where can I find the entrance to the ruins?")
                npc(
                    willow,
                    "On the western side of Ice Mountain. There's no way up on this side so you'll need to head " +
                        "east of here first and go around the dwarf colony to get there.",
                )
                me("Okay.")
                // ACT: previous
            }

            5 -> {
                me("Nothing, thanks.")
                npc(willow, "Hurry up! It's not exactly exciting standing out here all day.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Willow@2". */
    private suspend fun PlayerScript.willowRuins(willow: NpcRef) {
        me("That's everyone gathered. Interesting company for an archaeologist to keep...")
        npc(willow, "*Shrugs* How many archaeologists do you actually know?")
        me("Well I-")
        npc(
            willow,
            "Exactly. So who are you to judge? Anyway, we're all here and ready to break in... Um, I " +
                "mean excavate... the entrance.",
        )
        npc(willow, "Are you ready? Who knows what's inside the ruins?")
        when (
            choice(
                "Select an Option",
                "Yes.",
                "No.",
            )
        ) {
            1 -> {
                me("I'm ready as I'll ever be. Let's head in.")
                // cutscene (OPEN-QUB14)
                npc(willow, "Right, that's everyone gathered. Thank you, $name, for all your help.")
                me("Oh it was no pro...")
                npc(willow, "Okay everyone, we've wasted enough time. Let's crack this door open. Burntof, you're up!")
                npc(willow, "Try not to blow the entire mountain up this time...")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Yesh ma'am, let'sh get this rock out the way.")
                // fade (OPEN-QUB14)
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Jusht got to plashe thish here...")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "... little bit of dyn-*hic*-amite here...")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "... and there we go!")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Now we light the fushe!")
                // fade (OPEN-QUB14)
                // cutscene (OPEN-QUB14)
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "What are you doin' standin' there?!")
                me("What do you mean?")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Everyone'sh ran for cover lad. You'd best do the shame.")
                me("Wait, what?!")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Clock'sh ticking lad!")
                // fade (OPEN-QUB14)
                npcAs(Npcs.BIM_MARLEY, "Marley", "Bloomin' 'eck, Burntof!")
                npcAs(Npcs.BIM_CHECKAL, "Checkal", "That was a tad too explosive I think.")
                me("My ears are still ringing!")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Quit yer moanin', it did the job didnae it?")
                npc(willow, "He's right, the rock's been broken up. Checkal, it's your turn.")
                npcAs(Npcs.BIM_CHECKAL, "Checkal", "Of course ma'am, I will get to work.")
                // fade (OPEN-QUB14)
                // cutscene (OPEN-QUB14)
                // fade (OPEN-QUB14)
                npcAs(Npcs.BIM_CHECKAL, "Checkal", "The work is done, Willow. My apologies if it took too long.")
                npc(willow, "Don't be sorry, Checkal. Gotta put those muscles to use somehow.")
                npc(willow, "Alright Marley, in you go. Let us know if you find anything.")
                npcAs(Npcs.BIM_MARLEY, "Marley", "Right, in I go. Wish me luck $name.")
                me("Try not to die...")
                // cutscene (OPEN-QUB14)
                npcAs(Npcs.BIM_MARLEY, "Marley", "Right, let's see what hidden surprises you have in store for me.")
                npcAs(Npcs.BIM_MARLEY, "Marley", "Hmm...")
                // cutscene (OPEN-QUB14)
                npcAs(Npcs.BIM_MARLEY, "Marley", "That would have been nasty.")
                npcAs(Npcs.BIM_MARLEY, "Marley", "Is that it? I thought this place would be better protected.")
                // cutscene (OPEN-QUB14)
                npcAs(Npcs.BIM_MARLEY, "Marley", "Now to disarm it...")
                npcAs(Npcs.BIM_MARLEY, "Marley", "Maybe this will work?")
                npcAs(Npcs.BIM_MARLEY, "Marley", "That got it!")
                npcAs(Npcs.BIM_MARLEY, "Marley", "Alright guys! It's safe to come in! I think...")
                // fade (OPEN-QUB14)
                me("Wow, this place is amazing. I can't believe this was hidden away.")
                me("I wonder how long it's been left untouched for.")
                npc(willow, "You're impressed? There's nothing here but dust and fallen rocks.")
                // overhead: Yaaa*hic*aawn
                me(
                    "You don't find it exciting? As an archaeologist, I thought you would be thrilled at this " +
                        "discovery?",
                )
                npc(
                    willow,
                    "Thrilled? What's there to be excited about? I don't see any gold, diamonds, rare gems or " +
                        "piles of lost treasure!",
                )
                // overhead: Hmm...
                me(
                    "It's not about the money though, right? We've potentially discovered the long lost ruins " +
                        "of an ancient civilisation!",
                )
                npc(willow, "Of course it's about the money. It's always been about money, you fool.")
                me("I don't understand...")
                npc(
                    willow,
                    "We're not a team of archaeologist, you idiot. We're grave robbers looking for their next " +
                        "score.",
                )
                // overhead: Theshe markings look familiar...
                me(
                    "So this entire time, you've just been using me to help break into these ruins in hopes of " +
                        "finding someone else's treasures.",
                )
                npc(
                    willow,
                    "Yep, and you were surprisingly easy to convince. I'll admit, you have been a tremendous " +
                        "help, $name.",
                )
                npc(willow, "But your usefulness has come to an end I'm afraid. I can't simply let you leave here.")
                me("Why not?")
                npc(
                    willow,
                    "I can't risk you running to the authorities and telling them about our little gang. We're " +
                        "wanted in quite a few large cities across Gielinor.",
                )
                // overhead: I've sheen thish before...
                npc(
                    willow,
                    "I'm afraid this is the end of the road for you, $name. This ruin is about to become your " +
                        "tomb.",
                )
                // cutscene (OPEN-QUB14)
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "*hic* Woops...")
                npc(willow, "What was that noise?")
                npcAs(Npcs.BIM_BURNTOF, "Burntof", "Uh... it wasn't me!")
                // cutscene (OPEN-QUB14)
                npc(willow, "Whats happening?!")
                // cutscene (OPEN-QUB14)
                npc(willow, "It feels like the quaking has finally stopped...")
                // cutscene (OPEN-QUB14)
                npc(willow, "Holy Zamorak! What is that thing?! Checkal, take it down!")
                npcAs(Npcs.BIM_CHECKAL, "Checkal", "I'm a muscle man, not a fighter.")
                npcAs(Npcs.BIM_MARLEY, "Marley", "I think we should get out of here, Willow!")
                npc(willow, "You're right, Marley. Burntof, we're leaving!")
                // cutscene (OPEN-QUB14)
                me("I can't believe they ran so quickly. I suppose I should take care of this thing.")
                awakenGuardian(willow)
            }

            2 -> {
                me("I need a little bit more time to prepare.")
                npc(willow, "Hurry up then! We've waited long enough.")
            }

            else -> {
                Unit
            }
        }
    }

    /** The cutscene ends with the Ancient Guardian awake; the crew flees (OPEN-QUB14: no arena, it appears beside the player). */
    private fun PlayerScript.awakenGuardian(willow: NpcRef) {
        val p = player
        if (!QuestsB.BELOW_ICE_MOUNTAIN.advance(p, ARRIVED)) return
        val guardian = p.spawnNpc(Npcs.BIM_GOLEM_BOSS, p.coord.translate(GUARDIAN_OFFSET, 0), 0)
        guardian.huntRange = GUARDIAN_HUNT
    }

    private fun onDeath(e: DeathEvent) {
        val npc = e.entity as? NpcRef ?: return
        val killer = e.lootFor ?: return
        if (npc.type != Npcs.BIM_GOLEM_BOSS || stage(killer) != ARRIVED) return
        QuestsB.BELOW_ICE_MOUNTAIN.advance(killer, DEAD)
        killer.startScript { finishingUp() }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Finishing up". */
    private suspend fun PlayerScript.finishingUp(unused: Int = 0) {
        me(
            "That monster showed up at the perfect time. I would have been toast if Willow and her gang " +
                "had their chance to attack.",
        )
        me("Speaking of which, maybe they left something behind I can salvage some kind of reward from.")
        me("Looks like Willow left a bag behind. Maybe there's something in it I could take.")
        // searching the bag
        player.giveOrDrop(Objs.COINS, BAG_COINS, "willow's bag")
        me("Those will do nicely!")
        // doors open
        me("What was that noise? Please don't be another rock monster...")
        me("What the!")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "Bless my beard...")
        player.setFlag(RAMARNO_PENDING, true)
        QuestCompletion.complete(player, QuestsB.BELOW_ICE_MOUNTAIN, REWARD)
    }

    // ---- Checkal and Atlas

    private suspend fun PlayerScript.checkalTalk(checkal: NpcRef) {
        val p = player
        when {
            p.flag(CHECKAL) -> checkalIce(checkal)
            p.flag(TRAINED) -> checkalDone(checkal)
            p.flag(CHECKAL_ASKED) -> checkalAgain(checkal)
            stage(p) == 0 -> npc(checkal, "That would be me. What can I do for you?")
            else -> checkalIntro(checkal)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Checkal". */
    private suspend fun PlayerScript.checkalIntro(checkal: NpcRef) {
        me("Hello there. I'm $name and I'm looking for a man named Checkal.")
        npc(checkal, "That would be me [sir/miss]. What can I do for you?")
        me("I was told to come find you. Willow has found a new digsi...")
        npc(checkal, "Stop right there [sir/miss]. You can tell Willow she can do the heavy lifting herself.")
        me("And why is that?")
        npc(
            checkal,
            "Willow does not appreciate the amount of work that goes into building my physique. I would " +
                "not help her lift a shrimp.",
        )
        npc(checkal, "I take great pride in these muscles and it hurts me to the core to hear such insults!")
        me(
            "But we need your help! She's found a new archaeological digsite and needs you to help " +
                "clear the entrance.",
        )
        npc(checkal, "Archaeological digsite? Why is Willow interested in starting a digsite?")
        me("She said there could be some ancient ruins, even an untouched hoard of gold and treasure!")
        npc(checkal, "Ah that makes sense. *Sighs* You poor lad...")
        me("What do you mean?")
        npc(checkal, "Nothing. Look, I will help you. But first you must prove your strength to me.")
        if (player.baseLevel(Stats.STRENGTH) >= MAX_LEVEL) {
            me("Well Checkal, I don't mean to brag, but there are few people in Gielinor stronger than me.")
            npc(checkal, "Hmm... I will see about that.")
            npc(checkal, "Yes... you do have tremendous muscle mass.")
            npc(checkal, "Your legs are strong as steel, too.")
            npc(checkal, "Well $name, I have to say you are the definition of strength.")
            me("I do try.")
            npc(
                checkal,
                "But a [strongman/strongwoman] doesn't stop when they reach the top. They always push " +
                    "themselves.",
            )
        }
        if (player.baseLevel(Stats.STRENGTH) < MAX_LEVEL) {
            me("Well that shouldn't be too hard. I'm pretty strong.")
            npc(checkal, "That is true. But are you strong in both body and mind?")
            me("Uhm... yes?")
            npc(
                checkal,
                "To be a true [strongman/strongwoman], you do not just need big muscles. No, you must have " +
                    "self control and an unrelenting desire to push yourself to become stronger.",
            )
            me("Oh, I'm not sure about that...")
        }
        npc(
            checkal,
            "I would ask you to train with my old friend and trainer, Atlas, one of the strongest " +
                "barbarians I know. You can find him in the hall north of here.",
        )
        npc(checkal, "If you can survive his rigorous training program, I shall offer you my help.")
        me("How do you know he'll train me?")
        npc(checkal, "Tell him I sent you and he will train you.")
        player.setFlag(CHECKAL_ASKED, true)
        me("Alright then, I'll go speak to Atlas. Farewell.")
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Checkal again". */
    private suspend fun PlayerScript.checkalAgain(checkal: NpcRef) {
        me("I seem to have forgotten where Atlas is. Can you remind me?")
        npc(checkal, "Atlas can be found in the hall to the north of us.")
        me("Thanks.")
        npc(checkal, "No problem at all.")
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Checkal after completing Atlas' training". */
    private suspend fun PlayerScript.checkalDone(checkal: NpcRef) {
        npc(checkal, "So have you completed your training session with Atlas?")
        me("Just about, but it was a real struggle.")
        npc(checkal, "It always is, but I can see you are standing taller and looking stronger already.")
        npc(checkal, "Now, show me your muscles and I will judge if you're ready to receive my help.")
        // ACT: Show your strength by flexing your muscles.
        npc(
            checkal,
            "My goodness, those muscles are exquisite. I will definitely help a [man/lady] who shows " +
                "that much dedication to their physique.",
        )
        player.setFlag(CHECKAL, true)
        me("Fantastic! Willow said she'd meet you by the entrance, on the west side of Ice Mountain.")
        npc(checkal, "Thank you my friend, I will meet you there.")
        if (crew(player) + 1 < CREW) {
            me("What an exhausting workout. I hope the others aren't as much effort to recruit.")
        }
        if (crew(player) + 1 >= CREW) {
            me(
                "Well, that's everyone. I guess I should head off the entrance on the west side of Ice " +
                    "Mountain.{{fact}}",
            )
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Checkal at Ice Mountain". */
    private suspend fun PlayerScript.checkalIce(checkal: NpcRef) {
        me("Do you think you'll be able to handle all these rocks?")
        npc(checkal, "Should not be a problem. This challenge is easy for a [strongman/strongwoman].")
        me("I wish I could help...")
        npc(
            checkal,
            "Do not worry my friend. If you stay dedicated and keep working everyday, you'll be able to " +
                "achieve anything you dream of.",
        )
        me("Thanks Checkal, I will.")
        npc(checkal, "One last thing - be careful when we go inside. You never know what will happen.")
    }

    private suspend fun PlayerScript.atlasTalk(atlas: NpcRef) {
        val p = player
        when {
            !p.flag(CHECKAL_ASKED) -> npc(atlas, "What do you want, little one?")
            p.flag(TRAINED) -> atlasAgain(atlas)
            else -> atlasTraining(atlas)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Atlas". */
    private suspend fun PlayerScript.atlasTraining(atlas: NpcRef) {
        npc(atlas, "What do you want, little man?")
        me(
            "I've just come to talk to you. Checkal said you could train me. He said you're the " +
                "strongest barbarian he knows.",
        )
        npc(atlas, "Oh did he now? What makes you think I will train you?")
        me("I... Um...")
        npc(atlas, "Come on then! Out with it already!")
        me("Well uh, he said he wouldn't...")
        npc(atlas, "I'm just pulling your leg! Any friend of Checkal is a friend of mine.")
        me("Please don't scare me like that.")
        npc(
            atlas,
            "It's a force of habit. It comes with the barbarian lifestyle. But to apologise, I'll give " +
                "you a free lesson!",
        )
        me("Oh really? How much would this usually cost?")
        npc(atlas, "Around 25,000 coins per session.")
        me("That's a lot of money!")
        npc(atlas, "No price is too much for a good workout. Are you ready to start your training session?")
        when (
            choice(
                "Select an Option",
                "Yes.",
                "No.",
            )
        ) {
            1 -> {
                // ACT: Training session begins.
                npc(atlas, "If you want to become a [strongman/strongwoman], you need to get into shape!")
                npc(atlas, "It's not all about strength! You need to get some cardio in, too.")
                npc(atlas, "You need to be tough to be a [strongman/strongwoman]! Show me what you've got! Come on!")
                npc(atlas, "Every [strongman/strongwoman] was once a beginner who refused to give up.")
                npc(atlas, "You need to push yourself!")
                npc(atlas, "Always strive to be the best that you can be! That is the [strongman/strongwoman] way.")
                npc(
                    atlas,
                    "I'm very proud of you. You've shown grit, determination and an iron will. You are a true " +
                        "[strongman/strongwoman] at heart.",
                )
                me("That was exhausting... I've never worked so hard in my life!")
                npc(atlas, "And for that, I am proud. Now, show us what you're made of and flex those muscles!")
                // ACT: You have unlocked the flex emote!
                player.setFlag(TRAINED, true)
                // ACT: Training session ends.
                npc(atlas, "My work is done. Return to Checkal and show him your new-found muscles.")
            }

            2 -> {
                me("Not right now, I am a bit busy.")
                npc(atlas, "No problem. Come back when you are ready.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "After first training session". */
    private suspend fun PlayerScript.atlasAgain(atlas: NpcRef) {
        npc(atlas, "Well done, little one. You've proven your worth.")
        me("Can I do it again?")
        npc(atlas, "What? Why?")
        me("It was fun, I'd like to do it again.")
        npc(
            atlas,
            "You're the first person to ask me that $name. I suppose I could train you again, but I " +
                "will have to charge you the full 25,000 coins this time.",
        )
        when (
            choice(
                "Select an Option",
                "Yes",
                "No",
            )
        ) {
            1 -> {
                me("Let's do this, I'm ready.")
                if (player.take(Objs.COINS, TRAINING_FEE, "atlas training")) player.message("Atlas puts you through his workout again.")
                if (player.has(Objs.COINS, TRAINING_FEE)) {
                    npc(atlas, "It's not going to be any easier my friend!")
                    // ACT: Training session restarts. See above for dialogue.
                }
                if (!player.has(Objs.COINS, TRAINING_FEE)) {
                    npc(atlas, "Hold it there little one, you don't have enough money to pay for the lesson.")
                    me("Oh, sorry I was too excited...")
                    npc(atlas, "No worries friend, come back when you can afford the session.")
                    return
                }
            }

            2 -> {
                return
            }

            else -> {
                Unit
            }
        }
        me("Thanks for that workout. I can see why you normally charge people so much.")
    }

    // ---- Burntof

    private fun hasDrink(p: PlayerRef): Boolean = p.hasAny(Objs.BEER, Objs.ASGARNIAN_ALE, Objs.DWARVEN_STOUT, Objs.WIZARDS_MIND_BOMB)

    private suspend fun PlayerScript.burntofTalk(burntof: NpcRef) {
        val p = player
        when {
            p.flag(BURNTOF) -> burntofIce(burntof)
            p.flag(BURNTOF_ASKED) -> burntofAgain(burntof)
            stage(p) == 0 -> npc(burntof, "Buy ush a drink.")
            else -> burntofIntro(burntof)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Burntof". */
    private suspend fun PlayerScript.burntofIntro(burntof: NpcRef) {
        npc(burntof, "Buy ush a drink.")
        me("Are you Burntof?")
        player.setFlag(BURNTOF_ASKED, true)
        npc(burntof, "I might be, I might not. Buy ush a drink and find out *hic*.")
        if (!hasDrink(player)) {
            me("Fine... I'll get you a beer.")
            npc(burntof, "Thanksh a bunch.")
        }
        if (hasDrink(player)) {
            me("It just so happens I've got a drink you can have.")
            npc(burntof, "Giv' it 'ere then.")
            // ACT: You give Burntof the drink.
            giveDrink()
            npc(burntof, "That'sh good shtuff.")
            me("So, are you Burntof?")
            player.setFlag(BURNTOF_ASKED, true)
            npc(burntof, "Yesh I am *hic*. What can thish dwarf do for you?")
            me("Willow sent me. She needs your help with a job.")
            npc(burntof, "Willow? Now that'sh a name I drink to forget!")
            me("What do you mean?")
            npc(
                burntof,
                "Willow alwaysh insishted on changing me waysh. I knows explosivesh. Just because I lost a " +
                    "couple of me fingersh a few yearsh back doesn't mean I'll do it again *hic*.",
            )
            npc(
                burntof,
                "I knew the exhact amount of dyn*hic*amite needed to blow that rich fool's shafe. We woulda " +
                    "been shet for life!",
            )
            me("I thought you were a team of archaeologists?")
            npc(burntof, "Archaeologishts? Shure, if that's what you want to call ush?!")
            me(
                "Willow sent me to get you. She's found a new digsite and needs your expert knowledge on " +
                    "explosives.",
            )
            npc(burntof, "Hmmm... *hic*")
            npc(
                burntof,
                "Tell you what. If you beat me in a friendly game of rock paper s*hic*cissorsh, I'll join " +
                    "the team.",
            )
            npc(burntof, "*Whispers* We'll have the game upshtairs away from everyone.")
            burntofGame(burntof)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Burntof again before he gets a beer". */
    private suspend fun PlayerScript.burntofAgain(burntof: NpcRef) {
        npc(burntof, "Where'sh my beer?")
        if (!hasDrink(player)) {
            me("I'll get right on it.")
        }
        if (hasDrink(player)) {
            me("Here you go.")
            // ACT: You give Burntof the drink.
            giveDrink()
            if (player.has(Objs.DWARVEN_STOUT)) {
                npc(burntof, "Ah! A lad of fhine taste *hic*. I love Dwarven shtout!")
                me("So, are you Burntof?")
            }
            if (player.has(Objs.ASGARNIAN_ALE) && !player.has(Objs.DWARVEN_STOUT)) {
                npc(burntof, "That'sh good shtuff.")
                me("So, are you Burntof?")
            }
            if (player.has(Objs.WIZARDS_MIND_BOMB) && !player.has(Objs.DWARVEN_STOUT) && !player.has(Objs.ASGARNIAN_ALE)) {
                npc(burntof, "No messhing around with you! Only the strong stuff, I like it!")
                me("So, are you Burntof?")
            }
        }
    }

    private fun PlayerScript.giveDrink() {
        val p = player
        val drink = listOf(Objs.DWARVEN_STOUT, Objs.ASGARNIAN_ALE, Objs.WIZARDS_MIND_BOMB, Objs.BEER).firstOrNull { p.has(it) } ?: return
        p.take(drink, 1, "burntof drink")
        p.setFlag(BURNTOF_DRINK, true)
    }

    /** Wiki "Rock, paper, scissors": Burntof is too drunk to play properly and the player wins (OPEN-QUB14: no room, no cutscene). */
    private suspend fun PlayerScript.burntofGame(burntof: NpcRef) {
        npc(
            burntof,
            "The rulesh are ash followsh. We both pick a shign to show and then shay 'rock, paper, shcissors.' After schissors, we " +
                "show " +
                "our shigns",
            "Thish ish a besht of three game. Nowsh, pick what you are going to shhhow.",
        )
        val signs = listOf("Rock.", "Paper.", "Scissors.")
        var pick = signs[choice("Round 1: What sign will you show?", *signs.toTypedArray()) - 1]
        npc(burntof, "Looksh like you win the firsht round.")
        me("But...? I put out ${pick.dropLast(1).lowercase()}?")
        npc(burntof, "Yesh, and I put out *hic* ${loses(pick)}... I think.")
        me("Okay then... I guess the first round goes to me.")
        pick = signs[choice("Round 2: What sign will you show?", *signs.toTypedArray()) - 1]
        me("I won! That's two rounds to me, so you have to help.")
        npc(burntof, "Hooold it right there *hic* bud, I thinksh I won that round...")
        me("But ${pick.dropLast(1).lowercase()} beats ${loses(pick)}?")
        npc(burntof, "Yesh, that is correct, but you chose ${beatenBy(pick)}. So I win.")
        me("Whatever, let's just play the last round.")
        pick = signs[choice("Round 3: What sign will you show?", *signs.toTypedArray()) - 1]
        npc(burntof, "Well, it looksh like I win, sho I don't have to help you out.")
        me("Are you okay Burntof? I clearly beat you. Check my hand again.")
        npc(
            burntof,
            "Merlinsh beard! You did. A deal'sh a deal, I'll help you out.",
            "But I think I'll need one more drink before I hitsh the road.",
        )
        player.setFlag(BURNTOF, true)
        me(if (crew(player) < CREW) "That was interesting, but that's Burntof recruited. On to the next person." else RECRUITED_ALL)
    }

    /** The sign [sign] beats. */
    private fun loses(sign: String): String =
        when (sign) {
            "Rock." -> "scissors"
            "Paper." -> "rock"
            else -> "paper"
        }

    /** The sign that beats [sign]. */
    private fun beatenBy(sign: String): String =
        when (sign) {
            "Rock." -> "paper"
            "Paper." -> "scissors"
            else -> "rock"
        }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Burntof at Ice Mountain". */
    private suspend fun PlayerScript.burntofIce(burntof: NpcRef) {
        me("You managed to make your way over to the ruins then?")
        npc(burntof, "Yush, I did, why's that shuch a *hic* shurprise?")
        me("I thought you would pass out on your way.")
        npc(burntof, "Dwarshves have a very good shense of direction and we can *hic* hold our beer very well.")
        me("Are you sure you'll be able to handle the dynamite in your current state?")
        npc(burntof, "Hah! I could blow thish rubble away with one hand and me eyesh closed.")
    }

    // ---- Marley, Charlie and the cook

    private suspend fun PlayerScript.marleyTalk(marley: NpcRef) {
        val p = player
        when {
            p.flag(MARLEY) -> marleyIce(marley)
            p.flag(MARLEY_ASKED) && p.has(STEAK_SANDWICH) -> marleyReturn(marley)
            p.flag(MARLEY_ASKED) -> marleyAgain(marley)
            stage(p) == 0 -> npc(marley, "I could be. Depends on who's asking?")
            else -> marleyIntro(marley)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Marley". */
    private suspend fun PlayerScript.marleyIntro(marley: NpcRef) {
        me("Hey there. Would you happen to be Marley?")
        npc(
            marley,
            "I could be. Depends on who's asking? If you're with the Varrock City Guard, I had nothing " +
                "to do with the robbery at the museum. I don't even know what I'd do with some Wizard " +
                "robes.",
        )
        me("Uhm, no? What robbery? Wizard robes?")
        npc(marley, "Robbery? Sorry [sir/miss], first I've heard of a robbery.")
        me("Look, I'm not here with the city guard, Willow sent me to find you. She needs your help.")
        npc(marley, "Willow? Hmm... I'm afraid I don't recall a Willow.")
        me("Are you sure? She said you were old pals.")
        npc(marley, "Nope, I'm sure. Only Willow I know of is a type of tree.")
        npc(marley, "But, then again, my memory is a bit hazy, due to how hungry I am.")
        me("You're too hungry... to remember your friend?")
        npc(marley, "Yep... been a while since I had a nice hot meal.")
        me("*Sighs*... would you like me to get you something to eat, to help jog your memory?")
        npc(marley, "Oh what a kind offer! I couldn't say no.")
        me("Fine, I'll be back in a...")
        npc(
            marley,
            "Woah woah woah, hold your horses there friend! I can't just eat anything. I have very " +
                "specific dietary requirements.",
        )
        me("...")
        npc(
            marley,
            "Don't be like that! I don't want nothing fancy, just a Steak sandwich from the Blue Moon " +
                "Inn, in Varrock.",
        )
        me("Why don't you go and get one then?")
        npc(
            marley,
            "Slight problem there, friend. I've been barred and the chef won't make me another one. " +
                "Something about stealing from punters. I can't remember...",
        )
        me("*Sighs*... Fine. If I go get you this sandwich, will you help Willow?")
        npc(marley, "Of course! Oh, by the way, don't bother with the bartender. Just talk to the chef. Trust me.")
        me("Alright. I'll be back momentarily with your sandwich.")
        player.setFlag(MARLEY_ASKED, true)
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Marley again". */
    private suspend fun PlayerScript.marleyAgain(marley: NpcRef) {
        npc(marley, "Have you got the sandwich yet?")
        me("I'm still working on it. Where did you say you got them from?")
        npc(marley, "The Blue Moon Inn, in Varrock. They have the best cook around!")
        me("I'll go speak to them, then.")
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Returning to Marley". */
    private suspend fun PlayerScript.marleyReturn(marley: NpcRef) {
        me("Hey Marley. I've managed to get your Steak sandwich.")
        npc(marley, "Ah, just in time for my afternoon snack. Pass it over.")
        // ACT: You give the steak sandwich to Marley.
        player.take(STEAK_SANDWICH, 1, "marley sandwich")
        player.setFlag(MARLEY, true)
        npc(marley, "Aaah... not quite as good as the usual, but it hit the spot.")
        me("Are you finally done?")
        npc(marley, "Yes [sir/miss] I am. I'll honour my side of the deal and head off to find Willow.")
        me("Finally! You can find her on the west side of Ice Mountain.")
        npc(marley, "Excellent! I'll be on my way then.")
        if (crew(player) + 1 < CREW) {
            me(
                "Marley is certainly an interesting character. Hopefully the next team member is more " +
                    "respectable.",
            )
        }
        if (crew(player) + 1 >= CREW) {
            me(
                "Well, that's everyone. I guess I should head off the entrance on the west side of Ice " +
                    "Mountain.",
            )
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Marley at Ice Mountain". */
    private suspend fun PlayerScript.marleyIce(marley: NpcRef) {
        me("Hello again Marley.")
        npc(marley, "Ah, my new favourite chef. Have you got another sandwich for me?")
        me("No!")
        npc(marley, "Woah! Alright there, calm down. Was only a joke.")
        me("I don't find being your errand boy very funny.")
        npc(marley, "You need to lighten up a little, my friend.")
    }

    private suspend fun PlayerScript.cookTalk(cook: NpcRef) {
        val p = player
        when {
            !p.flag(MARLEY_ASKED) -> npc(cook, "What do you want? I'm busy!")
            p.flag(COOK_TOLD) && p.has(STEAK_SANDWICH) -> cookMade(cook)
            p.flag(COOK_TOLD) -> cookAgain(cook)
            else -> cookRecipe(cook)
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to the Cook". */
    private suspend fun PlayerScript.cookRecipe(cook: NpcRef) {
        npc(cook, "What do you want? I'm busy!")
        when (
            choice(
                "Select an Option",
                "I was wondering if you'd be able to make me a Steak sandwich?",
                "Can you sell me any food?",
                "Can you give me any free food?",
                "I don't want anything from this horrible kitchen.",
            )
        ) {
            1 -> {
                me("I was wondering if you'd be able to make me a Steak sandwich?")
                npc(
                    cook,
                    "Hmm... we don't get many food orders in 'ere. Last person to order one of them was barred " +
                        "for thievin'.",
                )
                me("Oh... uhm... no. It's for me?")
                npc(cook, "Really?")
                me("No... it's for Marley...")
                npc(cook, "I knew it! I'm not making another meal for that thievin' git! Clear off.")
                me("At least tell me why you hate him so much?")
                npc(
                    cook,
                    "I know what it's like, not knowin' where your next meals going to come from, so I let him " +
                        "eat 'ere for free.",
                )
                npc(
                    cook,
                    "The minute I turn my back, he's caught red-handed trying to pickpocket one of our patrons. " +
                        "We gave him the boot there 'n' then.",
                )
                me(
                    "Well, fair enough, but it's just one sandwich. He's not even in Varrock anymore. He's " +
                        "moved off to Edgeville.",
                )
                npc(cook, "No chance mate, it's the principle. I won't make food for such a low class criminal...")
                me("Please, I really need Marley's help and he'll only offer it if I get him a Steak sandwich.")
                npc(cook, "Well, I suppose if I give you the recipe, you can make it yourself.")
                me("Great! I can't imagine it's that hard to make.")
                npc(cook, "Watch your mouth! Making a Steak sandwich is a delicate art!")
                me("Sorry! Of course it is. What's the recipe then?")
                npc(
                    cook,
                    "To start, you need to get some prime beef from a cow. The fields around Lumbridge are a " +
                        "great source. Cook it up - medium rare is perfect. Don't cook it for too long or you'll " +
                        "burn it...",
                )
                npc(
                    cook,
                    "Then, you take some bread. I recommend making it fresh, but to each their own. Slice it in " +
                        "half and slide the beautifully cooked beef between the two halves...",
                )
                npc(cook, "And hey presto, you've got yourself a delicious, perfectly made Steak sandwich.")
                me("Wow... is that it? Some beef between a loaf of bread?")
                npc(cook, "It's harder to make than it sounds! Now get out before I whack you one for being so rude.")
                player.setFlag(COOK_TOLD, true)
            }

            2 -> {
                me("Can you sell me any food?")
            }

            3 -> {
                me("Can you give me any free food?")
            }

            4 -> {
                me("I don't want anything from this horrible kitchen.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to the cook again". */
    private suspend fun PlayerScript.cookAgain(cook: NpcRef) {
        npc(cook, "What do you want? I'm busy!")
        when (
            choice(
                "Select an Option",
                "I forgot the recipe to make a Steak sandwich.",
                "Can you sell me any food?",
                "Can you give me any free food?",
                "I don't want anything from this horrible kitchen.",
            )
        ) {
            1 -> {
                me("I forgot the recipe to make a Steak sandwich.")
                npc(
                    cook,
                    "For Saradomin's sake! Take some bread, cut it in half and stick some cooked Steak inside, " +
                        "done.",
                )
                me("Oh yeah, thank you!")
            }

            2 -> {
                me("Can you sell me any food?")
            }

            3 -> {
                me("Can you give me any free food?")
            }

            4 -> {
                me("I don't want anything from this horrible kitchen.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to the cook after managing to make yourself a Steak sandwich". */
    private suspend fun PlayerScript.cookMade(cook: NpcRef) {
        npc(cook, "What do you want? I'm busy!")
        when (
            choice(
                "Select an Option",
                "I've managed to make myself a Steak sandwich.",
                "Can you sell me any food?",
                "Can you give me any free food?",
                "I don't want anything from this horrible kitchen.",
            )
        ) {
            1 -> {
                me("I've managed to make myself a Steak sandwich.")
                npc(cook, "Let me take a look at it.")
                // You show your Steak sandwich to the cook.
                npc(cook, "Ha ha ha, you call this a sandwich!?")
                me("Well, I tried my best...")
                npc(cook, "I'm sure you did. It'll be enough for Marley, anyway.")
            }

            2 -> {
                me("Can you sell me any food?")
            }

            3 -> {
                me("Can you give me any free food?")
            }

            4 -> {
                me("I don't want anything from this horrible kitchen.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Steak sandwich": a knife on bread with cooked meat in the backpack, once the cook has told the recipe. */
    private suspend fun PlayerScript.makeSandwich() {
        val p = player
        if (!p.flag(COOK_TOLD) || !p.has(Objs.COOKED_MEAT)) return p.message("Nothing interesting happens.")
        // OPEN-QUB14: the messages of making the sandwich are not published.
        if (p.transaction("steak sandwich") {
                remove(Invs.INV, Objs.BREAD, 1) && remove(Invs.INV, Objs.COOKED_MEAT, 1) && add(Invs.INV, STEAK_SANDWICH, 1)
            }
        ) {
            p.message("You make a steak sandwich.")
        }
    }

    // ---- Ramarno

    /** Wiki "Transcript:Below Ice Mountain", section "Ramarno". */
    private suspend fun PlayerScript.ramarnoAfterQuest(unused: Int = 0) {
        me("Wow. I honestly didn't expect to see a dwarf in these ruins.")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "I can't believe it...")
        me("Yeah, exactly.")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "It's been so long since I've heard another person's voice.")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "How did you find me?")
        me("To be honest, it was all a bit of an accident.")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "What do you mean?")
        me(
            "There was a gang of... well, I thought were archaeologists, but it turned out they were " +
                "grave robbers.",
        )
        me(
            "I helped them clear the entrance to these ruins and we worked our way inside, only to wake " +
                "up a monster! They ran off and left me to defeat it.",
        )
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "That wasn't a monster...")
        me("Well it looked menacing enough!")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "That was one of the guardians of this once great city.")
        me("City?")
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "Yes! Camdozaal, home of the Imcando Dwarves.")
        npcAs(
            Npcs.CAMZODAAL_RAMARNO_ENTRANCE,
            "Ramarno",
            "These halls once bustled with life, but that was a long time ago. I'm the only one left " +
                "now. The name's Ramarno.",
        )
        me(
            "Well Ramarno, I'm $name, and you have me at a loss for words! I'd have never even imagined " +
                "there would be a whole city down here.",
        )
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "Not much of a city anymore, I suppose.")
        me(
            "I guess I didn't help with that. Sorry about breaking into your home and killing your " +
                "guardian.",
        )
        npcAs(
            Npcs.CAMZODAAL_RAMARNO_ENTRANCE,
            "Ramarno",
            "We have plenty more. No real harm done. That said, I do think I might have a way for you " +
                "to make up for it. There'll likely be something in it for you as well.",
        )
        npcAs(Npcs.CAMZODAAL_RAMARNO_ENTRANCE, "Ramarno", "My workshop is just north of here. Meet me there and we'll talk further.")
        // ACT: Ramarno departs for his workshop.
    }

    private companion object {
        const val OP_TALK = 1

        /** Steak sandwich obj (Wiki "Steak sandwich": id 25631). */
        const val STEAK_SANDWICH = 25631

        /** Quest variable stages (OPEN-QU1): 0 and 120 are the cache's `unstarted` / `endstate`. */
        const val STARTED = 10
        const val ARRIVED = 30
        const val DEAD = 40

        const val CHECKAL = "quests.bim.checkal"
        const val BURNTOF = "quests.bim.burntof"
        const val MARLEY = "quests.bim.marley"
        const val CHECKAL_ASKED = "quests.bim.checkal_asked"
        const val BURNTOF_ASKED = "quests.bim.burntof_asked"
        const val BURNTOF_DRINK = "quests.bim.burntof_drink"
        const val MARLEY_ASKED = "quests.bim.marley_asked"
        const val COOK_TOLD = "quests.bim.cook_told"
        const val TRAINED = "quests.bim.trained"
        const val RAMARNO_PENDING = "quests.bim.ramarno_pending"

        /** Wiki: three crew members to recruit. */
        const val CREW = 3
        const val MAX_LEVEL = 99
        const val RECRUITED_ALL = "Well, that's everyone. I guess I should head off the entrance on the west side of Ice Mountain."

        /** Wiki "Talking to Atlas": 25,000 coins per further session. */
        const val TRAINING_FEE = 25_000

        /** OPEN-QUB14: the guardian appears this many tiles east of the player and hunts within [GUARDIAN_HUNT]. */
        const val GUARDIAN_OFFSET = 2
        const val GUARDIAN_HUNT = 10

        /** Wiki "Finishing up": Willow's bag holds a pile of coins (the picture is the 1000 coins stack). */
        const val BAG_COINS = 1_000

        /** Wiki "Below Ice Mountain" rewards: 1 quest point (cache), 2,000 coins, 1,200 Mining experience, the Flex emote (OPEN-QUB14). */
        val REWARD =
            QuestReward(
                model = STEAK_SANDWICH,
                xp = listOf(Stats.MINING to 1_200.0),
                items = listOf(Objs.COINS to 2_000),
                notes = listOf("Access to the Ruins of Camdozaal", "The ability to make a steak sandwich"),
            )
    }
}
