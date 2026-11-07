@file:Suppress(
    "LongMethod",
    "LargeClass",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "MagicNumber",
    "ComplexCondition",
)
// MagicNumber: menu option numbers of the transcripts.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Prince Ali Rescue (docs/mechanics/quest-prince-ali-rescue.md): Chancellor Hassan, Osman, Leela, Ned, Aggie, Lady Keli,
 * Joe and Prince Ali. The quest variable is varp 273; 0 and 110 are the cache's values and the multi-NPCs of Keli, Joe and the
 * two Princes switch on it (Keli is gone above 40, the jailed Prince above 90, the Prince at the palace shows from 100), which
 * fixes where the stages lie. Ours (OPEN-QU1): 10 started, 20 Osman, 30 Leela's plan, 32 everything gathered, 34 Joe talked
 * to, 40 Joe drunk, 50 Keli tied up, 60 cell unlocked, 100 Prince Ali escaped. Dialogue is the Wiki "Transcript:Prince Ali Rescue".
 */
public class PrinceAliRescue : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.HASSAN, OP_TALK) { hassan ->
            player.stopInteraction()
            hassanTalk(hassan)
        }
        onOpNpc(Npcs.OSMAN, OP_TALK) { osman ->
            player.stopInteraction()
            osmanTalk(osman)
        }
        onOpNpc(Npcs.LEELA, OP_TALK) { leela ->
            player.stopInteraction()
            leelaTalk(leela)
        }
        // Aggie's Talk-to is Goblin Diplomacy's handler; it hands over to [aggiePrinceAli] while this quest is under way.
        onOpNpc(NEW_KELI, OP_TALK) { keli ->
            player.stopInteraction()
            keliTalk(keli)
        }
        onUseOnNpc(Objs.ROPE, NEW_KELI) { keli ->
            player.stopInteraction()
            if (ready(player)) tieKeli(keli) else keliNotReady()
        }
        onOpNpc(NEW_JOE, OP_TALK) { joe ->
            player.stopInteraction()
            joeTalk(joe)
        }
        onOpNpc(NEW_PRINCE_PRISON, OP_TALK) { prince ->
            player.stopInteraction()
            princeTalk(prince)
        }
        onOpNpc(NEW_PRINCE_PALACE, OP_TALK) { prince ->
            player.stopInteraction()
            npc(prince, "Good to see you, friend. I am forever in your debt for what you did, as is all of Al Kharid.")
            npc(
                prince,
                "I know my father is very grateful as well. If he were in better health, he would have thanked you personally. " +
                    "However, at his age, it is best that he spends his time resting.",
            )
            me("I'm just happy to have helped. All the best.")
        }
        onUseOnLoc(Objs.PRINCESKEY, Locs.ELFDOOR) { door ->
            player.stopInteraction()
            unlock(door)
        }
        for (furnace in FURNACES) {
            onUseOnLoc(Objs.KEYPRINT, furnace) {
                player.stopInteraction()
                castKey()
            }
            onUseOnLoc(Objs.BRONZE_BAR, furnace) {
                player.stopInteraction()
                castKey()
            }
        }
        onUseOnItem(Objs.YELLOWDYE, Objs.PLAINWIG) {
            dyeWig(this)
        }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.PRINCE_ALI_RESCUE) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.PRINCE_ALI_RESCUE.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when {
            stage(p) == 0 -> {
                listOf("I can start this quest by speaking to Chancellor Hassan in the Al Kharid palace.")
            }

            stage(p) < OSMAN -> {
                listOf("Chancellor Hassan asked me to speak to Osman, the Spymaster, outside the palace.")
            }

            stage(p) < LEELA -> {
                listOf("Prince Ali has been kidnapped. Osman sent me to talk to his daughter Leela in Draynor Village.")
            }

            stage(p) < DRUNK -> {
                listOf(
                    "I need a key to the Prince's cell, a disguise to make him look like Lady Keli, and a rope, then I must deal " +
                        "with the guard Joe.",
                )
            }

            stage(p) < KELI_TIED -> {
                listOf("Joe is out of the way. I should tie up Lady Keli with a rope.")
            }

            stage(p) < FREED -> {
                listOf("I should unlock the cell with the key and give Prince Ali his disguise.")
            }

            stage(p) < END -> {
                listOf("Prince Ali escaped. I should tell Chancellor Hassan.")
            }

            else -> {
                listOf("I rescued Prince Ali of Al Kharid.")
            }
        }

    private fun ready(p: PlayerRef): Boolean = stage(p) >= DRUNK && stage(p) < KELI_TIED && p.has(Objs.ROPE) && disguise(p)

    private fun disguise(p: PlayerRef): Boolean = p.has(Objs.BLONDWIG) && p.has(Objs.PINK_SKIRT) && p.has(Objs.SKINPASTE)

    // ---- Chancellor Hassan

    private suspend fun PlayerScript.hassanTalk(hassan: NpcRef) {
        val p = player
        when {
            stage(p) == 0 -> hassanStart(hassan)
            stage(p) == STARTED -> hassanBefore(hassan)
            stage(p) < FREED -> hassanAfter(hassan)
            stage(p) < END -> hassanReward(hassan)
            else -> npc(hassan, "Greetings! I am Hassan, Chancellor to the Emir of Al Kharid.")
        }
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Chancellor Hassan". */
    private suspend fun PlayerScript.hassanStart(hassan: NpcRef) {
        npc(hassan, "Greetings! I am Hassan, Chancellor to the Emir of Al Kharid.")
        when (
            choice(
                "Select an Option",
                "Is there anything I can help you with?",
                "It's just too hot here. How can you stand it?",
                "Do you mind if I just kill your warriors?",
                "I'd better be off.",
            )
        ) {
            1 -> {
                me("Is there anything I can help you with?")
                npc(
                    hassan,
                    "Well... we do currently have a very urgent issue we need to resolve, and I suppose you " +
                        "look like someone who knows how to get a job done. Are you definitely interested in " +
                        "helping?",
                )
                when (
                    choice(
                        "Start the Prince Ali Rescue quest?",
                        "Yes.",
                        "No.",
                    )
                ) {
                    1 -> {
                        QuestsB.PRINCE_ALI_RESCUE.advance(player, STARTED)
                        me("Of course.")
                        npc(
                            hassan,
                            "You'll find our Spymaster, Osman, just outside the palace. Go to him and tell him I sent " +
                                "you. He will fill you in on the details of our problem.",
                        )
                        me("Alright, I'll get to it.")
                    }

                    2 -> {
                        me("Actually, I've changed my mind.")
                        npc(hassan, "I see. Well you know where to find me if you do wish to help us.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("It's just too hot here. How can you stand it?")
            }

            3 -> {
                me("Do you mind if I just kill your warriors?")
            }

            4 -> {
                me("I'd better be off.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to him again before meeting Osman". */
    private suspend fun PlayerScript.hassanBefore(hassan: NpcRef) {
        npc(hassan, "Hello again. Have you spoken to Osman yet? He should be just outside the palace.")
        me("Not yet. I'll go and see him.")
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to him after meeting Osman". */
    private suspend fun PlayerScript.hassanAfter(hassan: NpcRef) {
        npc(
            hassan,
            "Hello again. I hear you have agreed to help rescue Prince Ali. On behalf of the Emir, I " +
                "will have a reward ready for you upon your success.",
        )
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Chancellor Hassan@2". */
    private suspend fun PlayerScript.hassanReward(hassan: NpcRef) {
        npc(
            hassan,
            "Prince Ali is home safe. You have the eternal gratitude of the Emir for rescuing his son. " +
                "Please, take this payment as a thank you.",
        )
        QuestCompletion.complete(player, QuestsB.PRINCE_ALI_RESCUE, REWARD)
    }

    // ---- Osman

    private suspend fun PlayerScript.osmanTalk(osman: NpcRef) {
        val p = player
        when {
            stage(p) < OSMAN || stage(p) == STARTED -> {
                osmanIntro(osman)
                osmanQuestions(osman, first = true)
            }

            stage(p) < FREED -> {
                osmanAgain(osman)
                osmanQuestions(osman, first = false)
            }

            else -> {
                npc(osman, "Prince Ali is safe once more. Chancellor Hassan has your payment.")
            }
        }
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Osman". */
    private suspend fun PlayerScript.osmanIntro(osman: NpcRef) {
        me(
            "Osman? I was told by the Chancellor to come and speak to you. Apparently you have an issue " +
                "that I can help with.",
        )
        QuestsB.PRINCE_ALI_RESCUE.advance(player, OSMAN)
        npc(
            osman,
            "That would be an apt description. However, I find you to be an interesting choice by the " +
                "Chancellor. Why has he chosen to trust you with this task over one of our own?",
        )
        me("Er... I just asked if he needed help and he said yes.")
        npc(
            osman,
            "That man is far too trusting at a time when we must take extra care. However, if he has " +
                "made his decision, I will not question it. Still, you should know that I will be keeping a " +
                "close eye on you.",
        )
        me("Fair enough. So what is this issue?")
        npc(
            osman,
            "Prince Ali, heir to the Emir of Al Kharid, has been taken. My spies have already " +
                "discovered where he is being held, but we need someone to make the rescue.",
        )
        me("Well I'm sure I can manage that. How do I go about rescuing him?")
        npc(
            osman,
            "The Prince has been taken by a group of bandits led by the self-proclaimed 'Lady' Keli. " +
                "They are holding him in the abandoned jail just east of Draynor Village.",
        )
        npc(
            osman,
            "According to our information, Keli is the only one able to freely move around the area. " +
                "For you to get the Prince out, you will need to disguise him as her. She will of course " +
                "need dealing with first.",
        )
        me("Why can't I just go in and kill her and her bandits?")
        npc(
            osman,
            "And endanger the life of the Prince in the process? No. There will be no unnecessary " +
                "risks. We need to do this with as little bloodshed as possible.",
        )
        me("You make a fair point. Do you know what Keli looks like?")
        npc(
            osman,
            "She has blonde hair and wears pink clothes. My daughter, Leela, one of my spies, is " +
                "currently in Draynor Village keeping an eye on the jail. I'm sure she can help you with " +
                "the specifics.",
        )
        npc(
            osman,
            "Before you go, there is one more thing. You'll need a key to get the Prince out of his " +
                "cell. Keli has the only one. You could steal it, but that seems like a risk we should " +
                "avoid.",
        )
        me("But if she has the only copy, and I can't just steal it, how do I get the key?")
        npc(
            osman,
            "If you bring me an imprint of the key along with a bronze bar, I can show you how to make " +
                "a copy. You should be able to make an imprint of the key using some soft clay.",
        )
        npc(
            osman,
            "Of course, you'll need to find a way to get Keli to show you the key without causing " +
                "suspicion. I'm sure Leela can help you with that.",
        )
        me("Sounds like I should head on over to Draynor Village and see Leela then.")
        npc(osman, "Indeed. Do you have any further questions before you go?")
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Osman again". */
    private suspend fun PlayerScript.osmanAgain(osman: NpcRef) {
        npc(osman, "You again. How are things going in Draynor?")
        if (player.has(Objs.KEYPRINT) && !player.has(Objs.BRONZE_BAR)) {
            me("I have an imprint of the key.")
            npc(osman, "Good. Bring me a bronze bar, and I'll get a copy made.")
        }
        if (player.has(Objs.KEYPRINT) && player.has(Objs.BRONZE_BAR)) {
            me("I have an imprint of the key.")
            itemBox(Objs.KEYPRINT, "You give Osman the imprint along with a bronze bar.")
            player.take(Objs.KEYPRINT, 1, "prince ali key print")
            player.take(Objs.BRONZE_BAR, 1, "prince ali bronze bar")
            player.setFlag(OSMAN_KEY, true)
            npc(osman, "I'll use this to have a copy of the key made. I'll send it to Leela once it's ready.")
        }
        if (!player.has(Objs.KEYPRINT)) {
            me("I'm still working on gathering all the items I need.")
            npc(osman, "Well if you need help, just talk to Leela.")
        }
    }

    /** The questions of Osman's menu; the first visit follows [osmanIntro], later ones [osmanAgain]. */
    private suspend fun PlayerScript.osmanQuestions(osman: NpcRef, first: Boolean) {
        val prompt = if (first) "Select an Option" else "Select an Option"
        while (true) {
            val last = if (first) "No. I think I know everything I need to." else "I'll get going."
            when (choice(prompt, "Do you know why they've taken the Prince?", "Where abouts in Draynor is Leela?", last)) {
                1 -> {
                    me("Do you know why they've taken the Prince?")
                    npc(osman, "No, but we have our theories.")
                    me("Care to share them?")
                    npc(osman, "No. You have not yet proven yourself enough to be trusted with that information.")
                    npc(osman, "Now, do you have any further questions?")
                }

                2 -> {
                    me("Where abouts in Draynor is Leela?")
                    npc(osman, "She will be somewhere near the abandoned jail that the bandits are using. It's just east of the village.")
                    npc(osman, "Do you have any further questions?")
                }

                else -> {
                    me(last)
                    if (first) npc(osman, "Then you should get going.")
                    return
                }
            }
        }
    }

    // ---- Leela

    private suspend fun PlayerScript.leelaTalk(leela: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            s < LEELA -> {
                leelaIntro(leela)
                leelaQuestions(leela, first = true)
            }

            s == LEELA && allItems(p) -> {
                leelaItems(leela)
            }

            s == LEELA -> {
                leelaAgain(leela)
            }

            s < DRUNK -> {
                leelaGuard(leela)
            }

            s == DRUNK -> {
                leelaDrunk(leela)
            }

            s < FREED -> {
                npc(leela, "You're back. Do you have everything needed yet?")
            }

            else -> {
                npc(
                    leela,
                    "You did it! Prince Ali is now safe again. You should head back to Al Kharid. I expect you will be well " +
                        "rewarded for your work.",
                )
            }
        }
    }

    /** Wiki quest page "Needed": key, wig dyed yellow, pink skirt, paste, rope and three beers. */
    private fun allItems(p: PlayerRef): Boolean =
        (p.has(Objs.PRINCESKEY) || p.flag(OSMAN_KEY)) && disguise(p) && p.has(Objs.ROPE) && p.has(Objs.BEER, BEERS)

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Leela". */
    private suspend fun PlayerScript.leelaIntro(leela: NpcRef) {
        me("You must be Leela. Your father sent me to help rescue Prince Ali.")
        QuestsB.PRINCE_ALI_RESCUE.advance(player, LEELA)
        npc(leela, "Yes, he sent word ahead that you'd be coming. Are you aware of the plan?")
        me(
            "I need to obtain a copy of the key to the Prince's cell, create a disguise for him that " +
                "makes him look like Keli and then break him out of the jail.",
        )
        npc(
            leela,
            "I'd say that's a good summary. Now, do you have any questions for me about any part of the " +
                "plan?",
        )
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Leela after having all the required items". */
    private suspend fun PlayerScript.leelaItems(leela: NpcRef) {
        npc(leela, "You're back. Do you have everything needed yet?")
        me("I do indeed.")
        QuestsB.PRINCE_ALI_RESCUE.advance(player, EQUIPPED)
        leelaKey()
        npc(
            leela,
            "Good work. Now, before breaking the Prince out, you'll need to find a way to deal with his " +
                "personal guard. He's talkative, so try to find a weakness. Remember, we don't want any " +
                "unneeded violence.",
        )
        me("Alright. I'll go have a chat to this guard.")
    }

    /** The key Osman had made arrives with Leela once everything else is gathered (Wiki "Talking to Osman again"). */
    private fun PlayerScript.leelaKey() {
        val p = player
        if (!p.has(Objs.PRINCESKEY) && p.flag(OSMAN_KEY)) p.giveOrDrop(Objs.PRINCESKEY, 1, "prince ali key")
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Leela once Joe is drunk". */
    private suspend fun PlayerScript.leelaDrunk(leela: NpcRef) {
        npc(leela, "You're back. How are things going with that guard?")
        me("He's been dealt with.")
        npc(
            leela,
            "Great! I think that means we're ready. Go in and use some rope to tie Keli up. Once she's " +
                "dealt with, use the key to free the Prince. Don't forget to give him his disguise so the " +
                "guards outside don't spot him.",
        )
    }

    private suspend fun PlayerScript.leelaQuestions(leela: NpcRef, first: Boolean = false) {
        val last = if (first) "Don't think so. I'll go and prepare." else "Not yet. I'll go and prepare."
        while (true) {
            when (choice("Select an Option", "Any ideas for the key?", "Any ideas for the disguise?", last)) {
                1 -> {
                    leelaKeyIdeas(leela)
                }

                2 -> {
                    me("Any ideas for the disguise?")
                    npc(
                        leela,
                        "To make the Prince look like Keli, you'll need a blonde wig and a pink skirt. You'll also want some skin " +
                            "paste " +
                            "to hide the black eye he got when they captured him.",
                        "There's an old sailor in the village who makes rope. Perhaps he could make you a wig. Don't forget to dye it " +
                            "once you have one.",
                        "For the skin paste, there's a local witch who's apparently an expert on all sorts of potions. I'm sure she " +
                            "could " +
                            "make you some. I hear she also sells dye, if you need some for the wig.",
                        "Finally there's the skirt. I imagine you could just buy one from any clothes shop. Thessalia's Fine Clothes " +
                            "in " +
                            "Varrock is probably the closest.",
                    )
                }

                else -> {
                    me(last)
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.leelaKeyIdeas(leela: NpcRef) {
        me("Any ideas for the key?")
        npc(
            leela,
            "Keli keeps it on her at all times, on a chain around her neck. If you can convince her to show it to you, you might be " +
                "able to use some soft clay to take an imprint.",
        )
        if (!player.has(Objs.KEYPRINT)) {
            me("That doesn't sound easy.")
            npc(
                leela,
                "My suggestion is that you pretend to be interested in joining her bandits. From there, you should be able to steer " +
                    "the conversation towards the key.",
                "Once you have the imprint, take it to my father along with a bronze bar. He'll then be able to make us a copy.",
            )
        } else {
            me("I already have the imprint!")
            npc(leela, "Then you should take it to my father along with a bronze bar. He'll then be able to make us a copy.")
        }
        npc(leela, "Do you have any other questions about the plan?")
    }

    /** "Talking to Leela again" (the plan is known, the items are not all there) and the lost key (Wiki, 15 coins). */
    private suspend fun PlayerScript.leelaAgain(leela: NpcRef) {
        npc(leela, "You're back. Do you have everything needed yet?")
        leelaQuestions(leela)
    }

    private suspend fun PlayerScript.leelaGuard(leela: NpcRef) {
        val p = player
        if (!p.has(Objs.PRINCESKEY)) {
            npc(leela, "You're back. How are things going?")
            me("I'm afraid I lost that key you gave me.")
            npc(leela, "Well that was foolish. I can sort you out with another, but it will cost you 15 coins.")
            if (!p.has(Objs.COINS, KEY_FEE)) {
                me("I haven't got that much.")
                npc(leela, "Then come back to me when you do.")
                return
            }
            me("Here, I have 15 coins.")
            p.take(Objs.COINS, KEY_FEE, "prince ali replacement key")
            p.giveOrDrop(Objs.PRINCESKEY, 1, "prince ali key")
            itemBox(Objs.PRINCESKEY, "Leela gives you a key.")
            npc(leela, "Now, how are things going with that guard?")
            me("I haven't spoken to him yet.")
            npc(leela, "Well you'd better get on it then. We need him out of the way.")
            return
        }
        npc(leela, "You're back. How are things going with that guard?")
        var attacked = false
        while (true) {
            when (
                choice(
                    "Select an Option",
                    if (attacked) "I might be able to get him drunk." else "I could attack him.",
                    if (attacked) "Maybe I could bribe him to leave." else "I might be able to get him drunk.",
                    if (attacked) "I'll keep thinking." else "Maybe I could bribe him to leave.",
                    "I'm not sure yet.",
                )
            ) {
                1 -> {
                    if (!attacked) {
                        me("I could attack him.")
                        npc(leela, "I don't think that's a good idea. Any violence could put the Prince at risk.")
                        attacked = true
                    } else {
                        drunkIdea(leela)
                        return
                    }
                }

                2 -> {
                    if (!attacked) {
                        drunkIdea(leela)
                        return
                    }
                    bribeIdea(leela)
                }

                3 -> {
                    if (!attacked) {
                        bribeIdea(leela)
                    } else {
                        me("I'll keep thinking.")
                        return
                    }
                }

                else -> {
                    me("I'm not sure yet.")
                    npc(leela, "You should try talking to him. He might give away some sort of weakness.")
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.drunkIdea(leela: NpcRef) {
        me("I might be able to get him drunk.")
        npc(leela, "Yes, that could work. I'd imagine three beers would do it. Why don't you give it a try?")
    }

    private suspend fun PlayerScript.bribeIdea(leela: NpcRef) {
        me("Maybe I could bribe him to leave.")
        npc(
            leela,
            "It would take a lot of gold to convince him to betray Keli. She's not known to be kind to those she believes to be " +
                "traitors. Perhaps there's something else you could try.",
        )
    }

    // ---- Ned (the wig; shared with Dragon Slayer I, see DragonSlayer.nedTalk) and Aggie

    // Ned's wig menu lives in DragonSlayer (Ned has one handler for both quests).

    internal suspend fun PlayerScript.aggieTalk(aggie: NpcRef) {
        npc(aggie, "What can I help you with?")
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "What could you make for me?",
                    "Can you make skin paste?",
                    "Cool, do you turn people into frogs?",
                    "You mad old witch, you can't help me.",
                    "Can you make dyes for me please?",
                )
            ) {
                2 -> {
                    pasteTalk(aggie)
                    return
                }

                5 -> {
                    dyeTalk(aggie)
                    return
                }

                1 -> {
                    me("What could you make for me?")
                    npc(
                        aggie,
                        "I mostly just make what I find pretty. I sometimes make dye for the women's clothes to brighten the " +
                            "place up.",
                    )
                    npc(aggie, "I can make red, yellow and blue dyes. If you'd like some, just bring me the appropriate ingredients.")
                }

                3 -> {
                    me("Cool, do you turn people into frogs?")
                    npc(aggie, "Oh, not for years, but if you meet a talking chicken, you have probably met the professor in the Manor.")
                }

                else -> {
                    me("You mad old witch, you can't help me.")
                    npc(aggie, "Heh, heh, heh.")
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.pasteTalk(aggie: NpcRef) {
        val p = player
        me("Can you make skin paste?")
        val water = p.has(Objs.BUCKET_WATER) || p.has(Objs.JUG_WATER)
        if (!(p.has(Objs.ASHES) && p.has(Objs.POT_FLOUR) && water && p.has(Objs.REDBERRIES))) {
            npc(
                aggie,
                "Why, it's one of my most popular potions! Lots of people around here like to pretty their faces up a bit. I can " +
                    "make it for you if you get me what's needed.",
            )
            me("What do you need?")
            npc(
                aggie,
                "Well dearie, you need a base for the paste. That's a mix of ash, flour and water. Then you need redberries to " +
                    "colour it as you want. Bring me those four items and I will make you some.",
            )
            return
        }
        npc(aggie, "Yes I can. I see you already have the ingredients. Would you like me to mix some for you now?")
        if (choice(
                "Select an Option",
                "Yes please. Mix me some skin paste.",
                "No thank you. I don't need any skin paste right " +
                    "now.",
            ) != 1
        ) {
            me("No thank you. I don't need any skin paste right now.")
            npc(aggie, "Okay dearie, that's always your choice.")
            return
        }
        me("Yes please. Mix me some skin paste.")
        npc(aggie, "That should be simple. Hand the things to Aggie then.")
        val done =
            p.transaction("prince ali skin paste") {
                remove(INV, Objs.ASHES, 1) &&
                    remove(INV, Objs.POT_FLOUR, 1) &&
                    remove(INV, Objs.REDBERRIES, 1) &&
                    // OPEN-QUB6: the Wiki does not say whether the empty bucket or jug comes back; the water is consumed with its vessel.
                    (remove(INV, Objs.BUCKET_WATER, 1) || remove(INV, Objs.JUG_WATER, 1)) &&
                    add(INV, Objs.SKINPASTE, 1)
            }
        if (!done) return
        itemBox(
            Objs.REDBERRIES,
            "You hand the ash, flour, water and redberries to Aggie. She tips the ingredients into a cauldron and " +
                "mutters some words.",
        )
        npc(aggie, "Tourniquet, Fenderbaum, Tottenham, Marshmallow, Marblearch.")
        itemBox(Objs.SKINPASTE, "Aggie hands you the skin paste.")
        npc(aggie, "There you go dearie. That will make you look good at the Varrock dances.")
    }

    private suspend fun PlayerScript.dyeTalk(aggie: NpcRef) {
        val p = player
        me("Can you make dyes for me please?")
        npc(aggie, "What sort of dye would you like? Red, yellow or blue?")
        // OPEN-QUB6: only the yellow dye of this quest is made; the red and blue dyes of the NPC transcript are not implemented.
        if (choice("Select an Option", "What do you need to make yellow dye?", "I can do without dye at that price.") != 1) {
            return me("I can do without dye at that price.")
        }
        me("What do you need to make yellow dye?")
        npc(aggie, "Yellow is a strange colour to get, comes from onion skins. I need 2 onions and 5 coins to make yellow dye.")
        if (choice("Select an Option", "Okay, make me some yellow dye please.", "I can do without dye at that price.") != 1) {
            return me("I can do without dye at that price.")
        }
        me("Okay, make me some yellow dye please.")
        if (!p.has(Objs.ONION, ONIONS) || !p.has(Objs.COINS, DYE_FEE)) {
            return box("You don't have enough onions to make the yellow dye.")
        }
        p.transaction("yellow dye") {
            remove(INV, Objs.ONION, ONIONS) && remove(INV, Objs.COINS, DYE_FEE) && add(INV, Objs.YELLOWDYE, 1)
        }
    }

    private fun dyeWig(p: PlayerRef) {
        // OPEN-QUB6: no message is published for dyeing the wig.
        p.transaction("dye wig") { remove(INV, Objs.YELLOWDYE, 1) && remove(INV, Objs.PLAINWIG, 1) && add(INV, Objs.BLONDWIG, 1) }
    }

    /** Wiki quest page: the key print and a bronze bar at any furnace make the bronze key (change of 14 January 2026). */
    private fun PlayerScript.castKey() {
        val p = player
        if (!p.has(Objs.KEYPRINT) || !p.has(Objs.BRONZE_BAR)) return p.message("Nothing interesting happens.")
        // OPEN-QUB6: no message is published for making the key.
        p.transaction("prince ali bronze key") {
            remove(INV, Objs.KEYPRINT, 1) && remove(INV, Objs.BRONZE_BAR, 1) &&
                add(INV, Objs.PRINCESKEY, 1)
        }
    }

    // ---- Lady Keli

    private suspend fun PlayerScript.keliTalk(keli: NpcRef) {
        val p = player
        if (ready(p)) return tieKeli(keli)
        if (p.flag(KELI_ASKED)) {
            me("Hello again!")
            npc(keli, "Oh, it's you. What do you want?")
            me("I was hoping we could talk more about your prisoner.")
            npc(
                keli,
                "Until you join us, I can say little. What I can tell you is that he is very valuable. If all goes well, he will " +
                    "make us very rich.",
            )
            return keliPrisoner(keli)
        }
        me("Are you the famous Lady Keli? Leader of the toughest gang of bandits around?")
        npc(keli, "Yes, I am Keli. You've heard of me then?")
        val katrine = QuestsB.SHIELD_OF_ARRAV.state(p) == QuestState.FINISHED
        val famous = "Heard of you? You're famous in Gielinor!"
        val tougher = "I've heard a little, but I think Katrine is tougher."
        val rumours = "I've heard rumours that you kill people."
        val options =
            buildList {
                add(famous)
                if (katrine) add(tougher)
                add(rumours)
                add("No, I've never really heard of you.")
            }
        val pick = options[choice("Select an Option", *options.toTypedArray()) - 1]
        when (pick) {
            tougher -> {
                keliKatrine(keli)
            }

            famous -> {
                keliFamous(keli)
            }

            rumours -> {
                me("I've heard rumours that you kill people.")
                npc(keli, "There's always someone ready to spread rumours. I hear all sort of ridiculous things these days.")
                keliTail(keli)
            }

            else -> {
                me("No, I've never really heard of you.")
                npc(keli, "You must be new around here then. Everyone knows of Lady Keli and her prowess with a sword.")
                if (choice(
                        "Select an Option",
                        "No, still doesn't ring a bell.",
                        "Actually, I have heard of you. You're famous in " +
                            "Gielinor!",
                    ) == 2
                ) {
                    keliFamous(keli)
                } else {
                    me("No, still doesn't ring a bell.")
                    npc(
                        keli,
                        "Well, you know of me now. You should also know that I will wring your neck if you don't show some " +
                            "respect.",
                    )
                    if (choice(
                            "Select an Option",
                            "I don't show respect to killers and " +
                                "hoodlums.",
                            "What's your latest plan then?",
                        ) == 1
                    ) {
                        me("I don't show respect to killers and hoodlums.")
                        npc(
                            keli,
                            "You should, you really should. I am wealthy enough to place a bounty on your head, or I could just remove " +
                                "your head myself. Luckily, I am too busy to deal with the likes of you, so clear off!",
                        )
                    } else {
                        keliPlan(keli)
                    }
                }
            }
        }
    }

    private suspend fun PlayerScript.keliKatrine(keli: NpcRef) {
        me("I've heard a little, but I think Katrine is tougher.")
        // OPEN-QUB6: the Wiki leaves this answer untranscribed ({{tmissing}}); the answer of the other Katrine option is reused.
        npc(
            keli,
            "Well you can think that all you like! Those cowards dare not leave Varrock. Out here, I'm toughest. You can tell them " +
                "that! Now get out of my sight, before I call my guards.",
        )
    }

    private suspend fun PlayerScript.keliFamous(keli: NpcRef) {
        me("Heard of you? You're famous in Gielinor!")
        npc(
            keli,
            "That's very kind of you to say. Reputations are not easily earned. I have managed to succeed where many fail.",
        )
        val katrine = QuestsB.SHIELD_OF_ARRAV.state(player) == QuestState.FINISHED
        val tougher = "I think Katrine is still tougher."
        val plan = "What's your latest plan then?"
        val trained = "You must have trained a lot for this work."
        val options =
            buildList {
                if (katrine) add(tougher)
                add(plan)
                add(trained)
                add("I shouldn't disturb someone as tough as you.")
            }
        when (options[choice("Select an Option", *options.toTypedArray()) - 1]) {
            tougher -> {
                me(tougher)
                npc(
                    keli,
                    "Well you can think that all you like! Those cowards dare not leave Varrock. Out here, I'm toughest. You can tell " +
                        "them that! Now get out of my sight, before I call my guards.",
                )
            }

            plan -> {
                keliPlan(keli)
            }

            trained -> {
                keliTrained(keli)
            }

            else -> {
                keliDisturb(keli)
            }
        }
    }

    /** After a remark of Keli's: the plan, her training or leaving (the Wiki's "What's your latest plan then?" menu). */
    private suspend fun PlayerScript.keliTail(keli: NpcRef) {
        when (
            choice(
                "Select an Option",
                "What's your latest plan then?",
                "You must have trained a lot for this work.",
                "I shouldn't disturb " +
                    "someone as tough as you.",
            )
        ) {
            1 -> keliPlan(keli)
            2 -> keliTrained(keli)
            else -> keliDisturb(keli)
        }
    }

    private suspend fun PlayerScript.keliTrained(keli: NpcRef) {
        me("You must have trained a lot for this work.")
        npc(keli, "I have used a sword since I was a girl. My first kill was before I was even six years old.")
        if (choice("Select an Option", "What's your latest plan then?", "I shouldn't disturb someone as tough as you.") == 1) {
            keliPlan(keli)
        } else {
            keliDisturb(keli)
        }
    }

    private suspend fun PlayerScript.keliDisturb(keli: NpcRef) {
        me("I shouldn't disturb someone as tough as you.")
        npc(keli, "Yes, I am very busy. Goodbye.")
    }

    private suspend fun PlayerScript.keliPlan(keli: NpcRef) {
        me("What's your latest plan then?")
        npc(keli, "Why do you want to know?")
        me("Well I was actually hoping to join your group.")
        npc(keli, "Join us? Interesting... I suppose you do look the type.")
        npc(
            keli,
            "You'll of course need to properly prove yourself before we let you join us. However, what I can tell you is that we " +
                "currently have a very valuable prisoner. If all goes well, he will make us very rich.",
        )
        player.setFlag(KELI_ASKED, true)
        keliPrisoner(keli)
    }

    private suspend fun PlayerScript.keliPrisoner(keli: NpcRef) {
        when (
            choice(
                "Select an Option",
                "Ah, I see. You must have been very skillful.",
                "How do you know someone won't try to free him?",
                "Well good luck with it.",
            )
        ) {
            1 -> {
                me("Ah, I see. You must have been very skillful.")
                npc(
                    keli,
                    "To catch him? Oh yes. We had to grab him without his bodyguards noticing. It was a stroke of genius. Mostly my " +
                        "doing, of course.",
                )
                if (choice("Select an Option", "How do you know someone won't try to free him?", "Well good luck with it.") == 1) {
                    keliFree(keli)
                } else {
                    me("Well good luck with it.")
                }
            }

            2 -> {
                keliFree(keli)
            }

            else -> {
                me("Well good luck with it.")
            }
        }
    }

    private suspend fun PlayerScript.keliFree(keli: NpcRef) {
        val p = player
        me("How do you know someone won't try to free him?")
        npc(
            keli,
            "There is no way to release him. The only key to his cell is on a chain around my neck, and the locksmith who made it " +
                "died very suddenly.",
            "There isn't another key like this in the world.",
        )
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "Could I see the key please?",
                    "That is a good way to keep secrets.",
                    "Well I'll be off. Good luck.",
                )
            ) {
                1 -> {
                    me("Could I see the key please?")
                    npc(keli, "Why?")
                    me("It will be something I can tell my grandchildren, when you are even more famous than you are now.")
                    npc(keli, "Well I suppose there's no harm in letting you see it. After all, you have no hope of stealing it.")
                    itemBox(Objs.PRINCESKEY, "Keli shows you a small key on a strong looking chain.")
                    if (!p.has(Objs.SOFTCLAY) || p.has(Objs.KEYPRINT)) {
                        me("Thank you. I'd better go now.")
                        return
                    }
                    if (choice("Select an Option", "Could I touch the key for a moment please?", "Thank you. I'd better go now.") == 1) {
                        me("Could I touch the key for a moment please?")
                        npc(keli, "Well... only for a moment then.")
                        if (p.transaction("prince ali key print") { remove(INV, Objs.SOFTCLAY, 1) && add(INV, Objs.KEYPRINT, 1) }) {
                            itemBox(Objs.KEYPRINT, "As you touch the key, you take an imprint of it using your soft clay.")
                        }
                        me("Thank you so much! You are too kind.")
                        npc(keli, "You are welcome, but run along now. I will need some time to consider your request to join us.")
                    } else {
                        me("Thank you. I'd better go now.")
                    }
                    return
                }

                2 -> {
                    me("That is a good way to keep secrets.")
                    npc(keli, "It is the best way I know. Dead men tell no tales.")
                }

                else -> {
                    me("Well I'll be off. Good luck.")
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.keliNotReady() {
        box("You cannot tie Keli up until you have all equipment and disabled the guard!")
    }

    private suspend fun PlayerScript.tieKeli(keli: NpcRef) {
        val p = player
        if (p.inv(Invs.INV).total(Objs.ROPE) < 1) return keliNotReady()
        me("Hello! I'm here to tie you up!")
        npc(keli, "What?")
        box("You overpower Keli, tie her up, and put her in a cupboard.")
        p.take(Objs.ROPE, 1, "prince ali rope")
        QuestsB.PRINCE_ALI_RESCUE.advance(p, KELI_TIED)
    }

    // ---- Joe

    private suspend fun PlayerScript.joeTalk(joe: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            s < EQUIPPED -> {
                me("Hi. Who are you guarding here?")
                npc(joe, "Can't say. It's all very secret. You should get out of here. I am not supposed to talk while I guard.")
            }

            s < DRUNK -> {
                QuestsB.PRINCE_ALI_RESCUE.advance(p, JOE_TALKED)
                me("Hi there.")
                npc(joe, "What do you want?")
                joeMenu(joe, JOE_MAIN)
            }

            s < FREED -> {
                npc(joe, "Halt! Who goes there?")
                me("Hello friend. I'm just here to rescue the Prince, if thats okay?")
                npc(joe, "Thatsh a funny joke. You are lucky I'm shober. Go in peace, friend.")
            }

            else -> {
                npc(joe, "Did yoush say something about shome Prince?")
                me("No.")
                npc(joe, "Oh... okay.")
            }
        }
    }

    /** Joe's menus: [node] is the menu the Wiki shows after the last answer (main, life, boy, chill, wages). */
    private suspend fun PlayerScript.joeMenu(joe: NpcRef, start: Int) {
        var node = start
        while (true) {
            val beer = "I have some beer here. Fancy one?"
            val life = "Tell me about the life of a guard."
            val boy = "What did you want to be when you were a boy?"
            val go = "I'd better go."
            val chill = "Hey, chill out. I won't cause you trouble."
            val wages = "So what do you buy with your great wages?"
            val money = "Would you be interested in making a little more money?"
            val options =
                when (node) {
                    JOE_MAIN -> listOf(beer, life, boy, go)
                    JOE_LIFE -> listOf(boy, go)
                    JOE_BOY -> listOf(chill, life, go)
                    JOE_CHILL -> listOf(wages, life, money, go)
                    else -> listOf(beer, life, boy, go)
                }
            // OPEN-QUB6: the beer is offered only while the player carries a beer.
            val shown = options.filter { it != beer || player.has(Objs.BEER) }
            val picked = shown[choice("Select an Option", *shown.toTypedArray()) - 1]
            when (picked) {
                beer -> {
                    if (joeBeer(joe)) return
                }

                life -> {
                    me(life)
                    npc(joe, "Well, the hours are good, but most of those hours are a drag.")
                    npc(
                        joe,
                        "Sometimes I wonder if I should have spent more time learning when I was a young boy. Maybe I wouldn't be " +
                            "here now, scared of Keli.",
                    )
                    node = JOE_LIFE
                }

                boy -> {
                    me(boy)
                    npc(joe, "Well, I loved to sit by the lake, with my toes in the water. I'd shoot the fish with my bow and arrow.")
                    me("That's a strange hobby for a boy.")
                    npc(joe, "It kept us from goblin hunting, which was what most boys did.")
                    npc(joe, "Hang on... Why do you ask? What do you want?")
                    node = JOE_BOY
                }

                chill -> {
                    me(chill)
                    npc(joe, "Sorry, it's hard to relax when I'm on duty. Stress of the job, and all.")
                    me("So why do you do it?")
                    npc(joe, "There's good money in it, and some of the shouting I rather like.")
                    npc(joe, "RESISTANCE IS USELESS!")
                    node = JOE_CHILL
                }

                wages -> {
                    me(wages)
                    npc(
                        joe,
                        "Really, after working here, there's only time for a drink or three. All us guards go to the same pub and " +
                            "drink ourselves stupid.",
                    )
                    npc(joe, "It's what I enjoy these days. I can't resist the sight of a really cold beer.")
                    node = JOE_MAIN
                }

                money -> {
                    me(money)
                    npc(
                        joe,
                        "What? Are you trying to bribe me? I may not be a great guard, but I am loyal. How dare you try to bribe " +
                            "me!",
                    )
                    me("No, no, you've got the wrong idea, totally. I just wondered if you wanted some part-time bodyguard work.")
                    npc(joe, "Oh... sorry. No, I don't need money. As long as you were not offering me a bribe.")
                }

                else -> {
                    me(go)
                    if (node == JOE_MAIN) {
                        npc(
                            joe,
                            "Thanks, I appreciate that. Talking on duty can be punished by having your mouth stitched up. These are " +
                                "tough " +
                                "people, make no mistake.",
                        )
                    }
                    return
                }
            }
        }
    }

    /** The beers (Wiki "Talking to Joe"): one is drunk at once, two more make him drunk. True when the talk ends. */
    private suspend fun PlayerScript.joeBeer(joe: NpcRef): Boolean {
        val p = player
        me("I have some beer here. Fancy one?")
        npc(joe, "Ah, that would be lovely. Only one though, just to wet my throat.")
        me("Of course. It must be tough being here without a drink.")
        if (!p.take(Objs.BEER, 1, "prince ali beer")) return true
        itemBox(Objs.BEER, "You hand a beer to the guard. He drinks it in seconds.")
        npc(joe, "That was perfect! I can't thank you enough.")
        me("How are you? Still okay? Not too drunk?")
        if (!p.has(Objs.BEER, BEERS - 1)) {
            npc(
                joe,
                "No, I don't get drunk from only one drink. I reckon I'd need at least two more for that. Still, thanks for the " +
                    "beer.",
            )
            return true
        }
        me("Would you care for another beer, my friend?")
        npc(joe, "I'd better not. I don't want to be drunk on duty.")
        me("Here, just keep these for later. I hate to see a thirsty guard.")
        if (!p.take(Objs.BEER, BEERS - 1, "prince ali beers")) return true
        itemBox(Objs.BEER, "You hand two more beers to the guard. He takes a sip of one, and then he quickly drinks them both.")
        npc(joe, "Franksh! That wash jusht what I need to shtay on guard. No more beersh, I don't want to get drunk.")
        QuestsB.PRINCE_ALI_RESCUE.advance(p, DRUNK)
        return true
    }

    // ---- the cell and Prince Ali

    private suspend fun PlayerScript.unlock(door: tp.plugin.LocRef) {
        val p = player
        if (stage(p) < KELI_TIED) return box("You'll need to deal with Lady Keli before freeing the Prince.")
        p.message("You unlock the door.")
        QuestsB.PRINCE_ALI_RESCUE.advance(p, CELL)
        p.passDoor(door)
    }

    private suspend fun PlayerScript.princeTalk(prince: NpcRef) {
        val p = player
        me("Prince Ali? I'm here to rescue you.")
        npc(prince, "Oh thank goodness! What's your plan?")
        if (stage(p) < CELL || !disguise(p)) {
            me(
                "I've already dealt with Lady Keli and the guard. I'm going to get you a disguise so the guards outside don't spot " +
                    "you leaving. I'll be back once I have it.",
            )
            return
        }
        me("Take this disguise. You can use it to get past the guards outside.")
        npc(prince, "Thank you, my friend. I must leave you now, but my father will pay you well for this.")
        p.transaction("prince ali disguise") {
            remove(INV, Objs.BLONDWIG, 1) && remove(INV, Objs.PINK_SKIRT, 1) && remove(INV, Objs.SKINPASTE, 1)
        }
        box("Prince Ali puts on the disguise and uses it to escape.")
        QuestsB.PRINCE_ALI_RESCUE.advance(p, FREED)
    }

    private companion object {
        const val OP_TALK = 1
        const val INV = Invs.INV

        /** The multi-NPC parents that switch on varp 273 (cache npc 11642-11645). */
        const val NEW_JOE = Npcs.JOE
        const val NEW_KELI = Npcs.LADY_KELI
        const val NEW_PRINCE_PRISON = Npcs.PRINCE_ALI_PRISON
        const val NEW_PRINCE_PALACE = Npcs.PRINCE_ALI_PALACE

        /** Quest variable stages (OPEN-QU1); 0 and 110 are the cache's values, the bounds 40 / 90 / 100 come from the multi-NPCs. */
        const val STARTED = 10
        const val OSMAN = 20
        const val LEELA = 30
        const val EQUIPPED = 32
        const val JOE_TALKED = 34
        const val DRUNK = 40
        const val KELI_TIED = 50
        const val CELL = 60
        const val FREED = 100
        const val END = 110

        /** Wiki quest page: three beers make Joe drunk. */
        const val BEERS = 3

        /** Wiki "Talking to Leela again if the player lost the key": a new key costs 15 coins. */
        const val KEY_FEE = 15

        /** Wiki NPC transcript Aggie: yellow dye needs 2 onions and 5 coins. */
        const val ONIONS = 2
        const val DYE_FEE = 5

        const val OSMAN_KEY = "quests.par.osman_key"
        const val KELI_ASKED = "quests.par.keli_asked"

        const val JOE_MAIN = 0
        const val JOE_LIFE = 1
        const val JOE_BOY = 2
        const val JOE_CHILL = 3

        /** Furnaces (cache locs named "Furnace" with a Smelt op, as `SmithingData.FURNACES`). */
        val FURNACES =
            intArrayOf(
                2030,
                3294,
                4304,
                6189,
                11010,
                12100,
                12809,
                16469,
                16657,
                24009,
                26300,
                28565,
                33504,
                36555,
                39241,
                43895,
                47927,
                48171,
                50698,
            )

        /** Wiki "Prince Ali Rescue" rewards: 3 quest points (cache), 700 coins, 500 Smithing and 500 Crafting experience. */
        val REWARD =
            QuestReward(
                model = Objs.PRINCESKEY,
                xp = listOf(Stats.SMITHING to 500.0, Stats.CRAFTING to 500.0),
                items = listOf(Objs.COINS to 700),
                notes = listOf("Free passage through the Lumbridge - Al Kharid toll gate."),
            )
    }
}

/** Prince Ali Rescue is under way (started, not finished): Aggie's paste dialogue applies. */
internal fun princeAliActive(p: PlayerRef): Boolean = QuestsB.PRINCE_ALI_RESCUE.let { it.stage(p) != it.unstarted && it.stage(p) < it.end }

/** Aggie's Prince Ali Rescue dialogue, for the Goblin Diplomacy handler that owns Aggie's Talk-to. */
internal suspend fun PlayerScript.aggiePrinceAli(aggie: NpcRef) {
    with(PrinceAliRescue()) { aggieTalk(aggie) }
}
