@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Romeo & Juliet (docs/mechanics/quest-romeo-and-juliet.md): Romeo, Juliet, Father Lawrence, the Apothecary and the cadava
 * potion. The quest variable is varp 144; 0 and 100 are the cache's values, the stages between are ours (OPEN-QU1): 10 started,
 * 20 message from Juliet, 30 message delivered, 40 sent by Father Lawrence, 50 the Apothecary needs berries, 60 potion made,
 * 70 Juliet drank it. Dialogue is the Wiki "Transcript:Romeo & Juliet" and the NPC transcripts. The two cutscenes are not
 * staged (OPEN-QU8): their dialogue plays in place.
 */
public class RomeoAndJuliet : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.ROMEO, OP_TALK) { romeo ->
            player.stopInteraction()
            romeoTalk(romeo)
        }
        onOpNpc(Npcs.JULIET, OP_TALK) { juliet ->
            player.stopInteraction()
            julietTalk(juliet)
        }
        onOpNpc(Npcs.DRAUL_LEPTOC, OP_TALK) { draul ->
            player.stopInteraction()
            draulTalk(draul)
        }
        onOpNpc(Npcs.PHILLIPA, OP_TALK) { phillipa ->
            player.stopInteraction()
            phillipaTalk(phillipa)
        }
        onOpNpc(Npcs.FATHER_LAWRENCE, OP_TALK) { lawrence ->
            player.stopInteraction()
            lawrenceTalk(lawrence)
        }
        onOpNpc(Npcs.APOTHECARY, OP_TALK) { apothecary ->
            player.stopInteraction()
            apothecaryTalk(apothecary)
        }
        for (bush in CADAVA_BUSHES) {
            onOpLoc(bush, OP_FIRST) {
                player.stopInteraction()
                pickBerries()
            }
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.ROMEO_AND_JULIET) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        when {
            stage == 0 -> listOf("I can start this quest by speaking to Romeo in Varrock Square.")
            stage < MESSAGE -> listOf("Romeo wants me to find Juliet, who is locked in her father's mansion west of Varrock.")
            stage < DELIVERED -> listOf("Juliet gave me a message to take to Romeo.")
            stage < PRIEST -> listOf("Romeo needs help. I should speak to Father Lawrence in the Varrock church.")
            stage < APOTHECARY -> listOf("Father Lawrence suggests a cadava potion from the Apothecary in south-west Varrock.")
            stage < POTION -> listOf("The Apothecary needs cadava berries, which grow west of the south-east Varrock mine.")
            stage < DRUNK -> listOf("I have a cadava potion. I should take it to Juliet.")
            stage < END -> listOf("Juliet has drunk the potion. I should tell Romeo to collect her from the crypt.")
            else -> listOf("Romeo forgot about Juliet when he met her cousin Phillipa.")
        }

    private val stageOf: (tp.plugin.PlayerRef) -> Int = { Quests.ROMEO_AND_JULIET.stage(it) }

    private fun PlayerScript.pickBerries() {
        val p = player
        p.anim(tp.symbols.Seqs.HUMAN_PICKUPFLOOR)
        // OPEN-QU14: the bush's berries are not modelled as running out (Wiki "Cadava berries": bushes regrow).
        if (p.give(
                Objs.CADAVABERRIES,
                1,
                "pick cadava berries",
            )
        ) {
            p.message("You pick some cadava berries.")
        } else {
            p.message("You don't have enough inventory space.")
        }
    }

    // ---- Romeo

    private suspend fun PlayerScript.romeoTalk(romeo: NpcRef) {
        val p = player
        when (val stage = stageOf(p)) {
            0 -> {
                romeoStart(romeo)
            }

            MESSAGE -> {
                if (p.has(Objs.JULIETMESSAGE)) {
                    me("Romeo...great news...I've been in touch with Juliet! She's written a message for you...")
                    deliverMessage(romeo)
                } else {
                    me("Romeo...great news...I've been in touch with Juliet!")
                    npc(romeo, "Oh great! That is great news! Well done...well done... what a total success!")
                    me("Yes, and she gave me a message to give you...")
                    npc(romeo, "Ohhh great! A message....wow!")
                    me("Yes!")
                    npc(romeo, "A message...oh, I can't wait to read what my dear Juliet has to say....")
                    me("I know...it's exciting isn't it...?")
                    npc(romeo, "Yes...yes...", "...", "You've lost the message haven't you?")
                    me("Yep, haven't got a clue where it is.")
                }
            }

            STARTED -> {
                me("Hello again, remember me?")
                npc(romeo, "Of course, yes....how are.. you....ermmm...")
                me("You haven't got a clue who I am do you?")
                npc(
                    romeo,
                    "Not a clue my friend, but you seem to have a friendly face...a little blood stained, and perhaps in need " +
                        "of a wash, but friendly none the less.",
                )
                me("You asked me to look for Juliet for you!")
                npc(romeo, "Ah yes, Juliet...my sweet darling...what news?")
                me("Nothing so far, but I need to ask a few questions?")
                romeoQuestions(romeo)
            }

            DELIVERED -> {
                me("Hello again Romeo!")
                romeoAgain(romeo)
            }

            PRIEST -> {
                me("Hello again Romeo!")
                npc(
                    romeo,
                    "Ooh did you manage to survive one of Lather Fawrences sermons? I bet not, you were ages! I bet you snoozed " +
                        "on the welcome mat just as soon as you heard his voice!",
                )
                me("Did not!")
                npc(romeo, "Oh, come on, come on, what did he say?")
                while (true) {
                    when (
                        choice(
                            "Select an option",
                            "He wants me to go to the Apothecary!",
                            "He seems keen for you to marry Juliet.",
                            "Ok, thanks.",
                        )
                    ) {
                        1 -> {
                            me("He wants me to go to the Apothecary!")
                            npc(romeo, "The Apothecary?", "Oh...is he the one who mixes up all them magical potion- ey things?")
                            me("Yeah, I think so...but the word potion-ey doesn't exist.")
                            npc(romeo, "Well, you just used it...so I guess it does exist!")
                            me("It doesn't matter...do you know where the Apothecary is?")
                            npc(romeo, "Why should I tell you?")
                            me("Because I'm doing you a favour!")
                            npc(
                                romeo,
                                "Right, yes...of course! Well, I think the potion-ey place is Wouth Sest of here and near a sword shop.",
                            )
                        }

                        2 -> {
                            me("He seems very keen for you to marry Juliet.")
                            npc(romeo, "Me too! I can't wait! Do you think it will be soon?")
                            me(
                                "I don't know, but I'll do what I can. It just seems a bit odd that Father Lawrence is so keen " +
                                    "for you to marry?",
                            )
                            npc(
                                romeo,
                                "I can't imagine why he's so keen? Though, he was our messenger before you. Of course, he had a " +
                                    "lot more hair back then...and I think we may have gone to school together...",
                            )
                            me("What, since you were kids?")
                            npc(romeo, "Yeah! Thinking about it now, we called him 'Diddy Dorrence' back then!")
                            me(
                                "So, maybe, he just wants a bit of peace? And when you two are married, he won't have to " +
                                    "traipse around delivering your messages anymore?",
                            )
                            npc(romeo, "Yes, the years haven't been good to him.")
                        }

                        else -> {
                            me("Ok, thanks.")
                            return
                        }
                    }
                }
            }

            APOTHECARY, POTION -> {
                if (!p.has(Objs.CADAVA)) {
                    me("Hi Romeo!")
                    npc(romeo, "Oh hello, have you seen Lather Fawrence?")
                    me(
                        "Yes, he's given me details of a potion which should help resolve this situation. The Apothecary is " +
                            "helping me prepare it.",
                    )
                    npc(
                        romeo,
                        "Ooooh, it all sounds horribly complicated?",
                        "All I can say is I'll be glad when Juliet's finally in that crypt!",
                    )
                    me("Spoken like a true lover!")
                } else {
                    itemBox(Objs.CADAVA, "Romeo spots the Cadava potion.")
                    npc(romeo, "Ooohh, that's the potion is it? Rather you than me!")
                    me("I'm not drinking it! It's for Juliet!")
                    npc(
                        romeo,
                        "Well, I mean...it's probably delicious...and not dangerous at all. And you quite often see harmless " +
                            "drinks in a glowing sort of pink colour.",
                    )
                    me("Hmm, I'm not convinced...maybe you ought to try some before I give it to Juliet?")
                    npc(romeo, "Urgh! No way!  Not in a million years!")
                    me("Ok...but you know what to do when I've given this to Juliet?")
                    npc(romeo, "Oh, yeah...sure Lather Fawrence explained all that to me...")
                }
            }

            DRUNK -> {
                rescue(romeo)
            }

            else -> {
                check(stage >= END)
                npc(
                    romeo,
                    "I heard Juliet had died. Terrible business.",
                    "Her cousin and I are getting on well though. Thanks for your help.",
                )
            }
        }
    }

    private suspend fun PlayerScript.romeoStart(romeo: NpcRef) {
        val lines =
            listOf(
                "Blub! Blub...where is my Juliet? Have you seen her?",
                "Looking for a blonde girl, goes by the name of Juliet..quite pretty...haven't seen her have you?",
                "Juliet, Juliet, wherefore art thou Juliet? Have you seen my Juliet?",
                "Oh woe is me that I cannot find my Juliet! You haven't seen Juliet have you?",
                "Sadness surrounds me now that Juliet's father forbids us to meet. Have you seen my Juliet?",
                "What is to become of me and my darling Juliet, I cannot find her anywhere, have you seen her?",
            )
        npc(romeo, lines[player.random(lines.size)])
        when (
            choice(
                "Select an option",
                "Yes, I have seen her actually!",
                "No sorry, I haven't seen her.",
                "Perhaps I could help to find her for you?",
            )
        ) {
            1 -> {
                me("Yes, I have seen her actually!", "At least, I think it was her... Blonde? A bit stressed?")
                npc(romeo, "Golly...yes, yes...you make her sound very interesting!", "And I'll bet she's a bit of a fox!")
                me("Well, I guess she could be considered attractive...")
                npc(romeo, "I'll bet she is!  Wooooooooo!", "Sorry, all that jubilation has made me forget what we were talking about.")
                me("You were asking me about Juliet? You seemed to know her?")
                npc(
                    romeo,
                    "Oh yes, Juliet!",
                    "The fox...could you tell her that she is the love of my long and that I life to be with her?",
                )
                me("What?  Surely you mean that she is the love of your life and that you long to be with her?")
                npc(romeo, "Oh yeah...what you said...tell her that, it sounds much better!  Oh you're so good at this!")
                romeoOffer(romeo)
            }

            2 -> {
                me("No sorry, I haven't seen her.")
                npc(romeo, "Oh...well, that's a shame...I was rather hoping you had.")
                me("Why?  Is she a fugitive?  Does she owe you some money or something?")
                npc(romeo, "Hmmm, she might do?  Perhaps she does?  How do you know?")
                me("I don't know? I was asking 'YOU' how 'YOU' know Juliet!")
                npc(
                    romeo,
                    "Ahh, yes Juliet, she's my one true love.  Well, one of my one true loves! If you see her, could you tell " +
                        "her that she is the love of my long and that I life to be with her?",
                )
                me("What?  Surely you mean that 'she is the love of your life and that you long to be with her?'")
                npc(romeo, "Oh yeah...what you said...tell her that, it sounds much better!  Oh you're so good at this!")
                romeoOffer(romeo)
            }

            3 -> {
                me("Perhaps I can help find her for you? What does she look like?")
                npc(romeo, "Oh would you? That would be great! She has this sort of hair...")
                me("Hair...check..")
                npc(romeo, "...and she these...great lips...")
                me("Lips...right.")
                npc(romeo, "Oh and she has these lovely shoulders as well..")
                me("Shoulders...right, so she has hair, lips and shoulders...that should cut it down a bit.")
                npc(
                    romeo,
                    "Oh yes, Juliet is very different...please tell her that she is the love of my long and that I life to be with her?",
                )
                me("What?  Surely you mean that 'she is the love of your life and that you long to be with her?'")
                npc(romeo, "Oh yeah...what you said...tell her that, it sounds much better!  Oh you're so good at this!")
                romeoOffer(romeo)
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.romeoOffer(romeo: NpcRef) {
        if (choice("Start the Romeo & Juliet quest?", "Yes.", "No.") != 1) {
            me("Sorry Romeo, I've got better things to do right now. Maybe later?")
            npc(
                romeo,
                "Oh, okay, well, I guess my Juliet and I can spend some time apart.  And as the old saying goes, 'Absinthe " +
                    "makes the heart glow longer'.",
            )
            me("Don't you mean that, 'Absence makes the...", "Actually forget it...")
            npc(romeo, "Okay!")
            return
        }
        me("Yes, okay. I'll let her know.")
        npc(
            romeo,
            "Oh great! And tell her that I want to kiss her a give.",
        )
        me("You mean you want to give her a kiss!")
        npc(romeo, "Oh you're good...you are good!", "I see I've picked a true professional...!")
        Quests.ROMEO_AND_JULIET.advance(player, STARTED)
        romeoQuestions(romeo)
    }

    /** "Where can I find Juliet?" and the other questions of a started quest (loops back to the menu). */
    private suspend fun PlayerScript.romeoQuestions(romeo: NpcRef) {
        while (true) {
            when (
                choice(
                    "Select an option",
                    "Where can I find Juliet?",
                    "Is there anything else you can tell me about Juliet?",
                    "Ok, thanks.",
                )
            ) {
                1 -> {
                    me("Where can I find Juliet?")
                    npc(romeo, "Why do you ask?")
                    me("So that I can try and find her for you!")
                    npc(
                        romeo,
                        "Ah yes....quite right. Hmmm, let me think now.",
                        "She may still be locked away at her Father's house on the sest vide of Warrock.",
                        "Oh, I remember how she loved it when I would sing up to her balcony! She would reward me with her own " +
                            "personal items...",
                    )
                    me("What, she just gave you her stuff?")
                    npc(romeo, "Well, not exactly give...more like 'throw with considerable force'...she's always a kidder that Juliet!")
                }

                2 -> {
                    me("Is there anything else you can tell me about Juliet?")
                    npc(
                        romeo,
                        "Oh, there is so much to tell...she is my true love, we intend to spend together forever...I can tell " +
                            "you so much about her..",
                    )
                    me("Great!")
                    npc(romeo, "Ermmm.....", "So much can I tell you...")
                    me("Yes..")
                    npc(romeo, "So much to tell...why, where do I start!")
                    me("Yes..yes!  Please go on...don't let me interrupt...")
                    npc(romeo, "Ermmm.....", "...")
                    me("You can't remember can you?")
                    npc(romeo, "Not a thing sorry....")
                }

                else -> {
                    me("Ok, thanks.")
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.deliverMessage(romeo: NpcRef) {
        val p = player
        if (!p.take(Objs.JULIETMESSAGE, 1, "romeo and juliet message")) return
        Quests.ROMEO_AND_JULIET.advance(p, DELIVERED)
        itemBox(Objs.JULIETMESSAGE, "You hand over Juliet's message to Romeo.")
        npc(romeo, "Oh, a message! A message! I've never had a message before...")
        me("Really?")
        npc(
            romeo,
            "No, no, not one!",
            "Oh, well, except for the occasional court summons.",
            "But they're not really 'nice' messages. Not like this one! I'm sure that this message will be lovely.",
        )
        me("Well are you going to open it or not?")
        npc(
            romeo,
            "Oh yes, yes, of course!  'Dearest Romeo, I am very pleased that you sent $name to look for me and to tell me that " +
                "you still hold affliction...', Affliction! She thinks I'm diseased?",
        )
        me("'Affection?'")
        npc(
            romeo,
            "Ahh yes...'still hold affection for me. I still feel great affection for you, but unfortunately my Father opposes " +
                "our marriage.'",
        )
        me("Oh dear...that doesn't sound too good.")
        npc(
            romeo,
            "What? '...great affection for you. Father opposes our..",
            "'...marriage and will...",
            "...will kill you if he sees you again!'",
        )
        me("I have to be honest, it's not getting any better...")
        npc(romeo, "'Our only hope is that Father Lawrence, our long time confidant, can help us in some way.'")
        itemBox(Objs.JULIETMESSAGE, "Romeo folds the message away.")
        npc(romeo, "Well, that's it then...we haven't got a chance...")
        me("What about Father Lawrence?")
        npc(romeo, "...our love is over...the great romance, the life of my love...")
        me("...or you could speak to Father Lawrence!")
        npc(romeo, "Oh, my aching, breaking, heart...how useless the situation is now...we have no one to turn to...")
        me("FATHER LAWRENCE!")
        npc(
            romeo,
            "Father Lawrence?",
            "Oh yes, Father Lawrence...he's our long time confidant, he might have a solution! Yes, yes, you have to go and " +
                "talk to Lather Fawrence for us and ask him if he's got any suggestions for our predicament?",
        )
        me("Where can I find Father Lawrence?")
        lawrenceDirections(romeo)
        romeoAgain(romeo)
    }

    private suspend fun PlayerScript.lawrenceDirections(romeo: NpcRef) {
        npc(romeo, "Lather Fawrence! Oh he's...", "You know he's not my 'real' Father don't you?")
        me("I think I suspected that he wasn't.")
        npc(
            romeo,
            "Well anyway...he tells these song, loring bermons...and keeps these here Carrockian vitizens snoring in his church " +
                "to the East North.",
        )
    }

    /** The four-option menu after the message (and when talking to Romeo again). */
    private suspend fun PlayerScript.romeoAgain(romeo: NpcRef) {
        while (true) {
            when (
                choice(
                    "Select an option",
                    "How are you?",
                    "Where can I find Father Lawrence?",
                    "Have you heard anything from Juliet?",
                    "Ok, thanks.",
                )
            ) {
                1 -> {
                    me("How are you?")
                    npc(romeo, "Not so good my friend...I miss Judi..., Junie..., Joopie...")
                    me("Juliet?")
                    npc(romeo, "Juliet! I miss Juliet, terribly!")
                    me("Hmmm, so I see!")
                }

                2 -> {
                    me("Where can I find Father Lawrence?")
                    lawrenceDirections(romeo)
                }

                3 -> {
                    me("Have you heard anything from Juliet?")
                    npc(
                        romeo,
                        "Sadly not my friend! And what's worse, her Father has threatened to kill me if he sees me. I mean, " +
                            "that seems a bit harsh!",
                    )
                    me("Well, I shouldn't worry too much...you can always run away if you see him...")
                    npc(romeo, "I just wish I could remember what he looks like! I live in fear of every man I see!")
                }

                else -> {
                    me("Ok, thanks.")
                    return
                }
            }
        }
    }

    /** The potion has been drunk: Romeo goes to the crypt and meets Phillipa; the quest ends (cutscene not staged, OPEN-QU8). */
    private suspend fun PlayerScript.rescue(romeo: NpcRef) {
        me(
            "Romeo, it's all set. Juliet has drunk the potion and has been taken down into the Crypt...now you just need to pop " +
                "along and collect her.",
        )
        npc(romeo, "Ah right, the potion! Great...", "What potion would that be then?")
        me(
            "The Cadava potion...you know, the one which will make her appear dead! She's in the crypt, pop along and claim your " +
                "true love.",
        )
        npc(romeo, "But I'm scared...will you come with me?")
        me("Oh, ok...come on! I think I saw the entrance when I visited there last...")
        npc(romeo, "This is pretty scary...")
        me("Oh, be quiet...", "We're here. Look, Juliet is over there!", "You go over to her...and I'll go and wait over here...")
        npc(romeo, "Ohhh, ok then...", "Hey...Juliet...", "Juliet....?", "Oh dear...you seem to be dead.")
        npcAs(Npcs.PHILLIPA, "Phillipa", "Hi Romeo...I'm Phillipa!")
        npc(romeo, "Wow! You're a fox!")
        npcAs(Npcs.PHILLIPA, "Phillipa", "It's a shame about Juliet...but perhaps we can meet up later?")
        npc(romeo, "Who's Juliet?")
        QuestCompletion.complete(player, Quests.ROMEO_AND_JULIET, REWARD)
    }

    // ---- Juliet

    private suspend fun PlayerScript.julietTalk(juliet: NpcRef) {
        val p = player
        val stage = stageOf(p)
        when {
            stage == 0 -> {
                npc(juliet, "Romeo, Romeo, wherefore art thou Romeo?")
                box("She seems to be lost in thought.")
            }

            stage == STARTED -> {
                me("Juliet, I come from Romeo.  He begs me to tell you that he cares still.")
                npc(
                    juliet,
                    "Oh how my heart soars to hear this news! Please take this message to him with great haste.",
                )
                me("Well, I hope it's good news...he was quite upset when I left him.")
                npc(
                    juliet,
                    "He's quite often upset...the poor sensitive soul. But I don't think he's going to take this news very " +
                        "well, however, all is not lost.",
                    "Everything is explained in the letter, would you be so kind and deliver it to him please?",
                )
                me("Certainly, I'll do so straight away.")
                npc(juliet, "Many thanks! Oh, I'm so very grateful. You may be our only hope.")
                if (p.give(Objs.JULIETMESSAGE, 1, "romeo and juliet message")) {
                    Quests.ROMEO_AND_JULIET.advance(p, MESSAGE)
                    itemBox(Objs.JULIETMESSAGE, "Juliet gives you a message.")
                } else {
                    p.message("You don't have enough inventory space.")
                }
            }

            stage == MESSAGE -> {
                me("Hello Juliet!")
                npc(juliet, "Hello there...have you delivered the message to Romeo yet? What news do you have from my loved one?")
                if (p.has(Objs.JULIETMESSAGE)) {
                    me("Oh, sorry, I've not had chance to deliver it yet!")
                    npc(juliet, "Oh, that's a shame. I've been waiting so patiently to hear some word from him.")
                } else {
                    me("Hmmm, that's the thing about messages...they're so easy to misplace...")
                    npc(
                        juliet,
                        "How could you lose that message? It was incredibly important...and it took me an age to write! I used " +
                            "joined up writing and everything!",
                        "Please, take this new message to him, and please don't lose it.",
                    )
                    if (p.give(
                            Objs.JULIETMESSAGE,
                            1,
                            "romeo and juliet message",
                        )
                    ) {
                        itemBox(Objs.JULIETMESSAGE, "Juliet gives you another message.")
                    }
                }
            }

            stage == DELIVERED -> {
                me(
                    "Hi Juliet, I have passed your message on to Romeo..he's scared half out of his wits at the news that your " +
                        "father wants to kill him.",
                )
                npc(
                    juliet,
                    "Yes, unfortunately my father is quite the hunter, you may have seen some the animal head trophies on the " +
                        "wall. And it would be so awful to see Romeo's head up there with them!",
                )
                me("I know what you mean...", "...his hair colour will clash terribly with the rest of the decoration.")
                npc(juliet, "That's not what I was suggesting at all...")
                me(
                    "I know, I know...I was just kidding.",
                    "Anyway, don't worry because I'm on the case. I'm going to get some help from Father Lawrence.",
                )
                npc(juliet, "Oh yes, I'm sure that Father Lawrence will come up with a solution. I hope you find him soon.")
            }

            stage == PRIEST -> {
                me("Hi Juliet, I've found Father Lawrence and he has a cunning plan. But for it to work, I need to seek the Apothecary!")
                npc(
                    juliet,
                    "Oh good! I knew Father Lawrence would come up with something. However, I don't know where the apothecary " +
                        "is...I hope you find him soon. My father's temper gets no better.",
                )
            }

            stage < DRUNK && !p.has(Objs.CADAVA) -> {
                me("Hi Juliet!")
                npc(juliet, "Hi $name, how close am I to being with my true love Romeo?")
                me("Sorry, I still have to get a special potion for you.")
                npc(
                    juliet,
                    "Oh, I hope it isn't a love potion because you would be wasting your time. My love for Romeo grows stronger " +
                        "every minute...",
                )
                me("That must be because you're not with him...")
                npc(juliet, "Oh no! I long to be close to my true love Romeo!")
                me("Well, ok then...I'll set about getting this potion as quickly as I can!")
                npc(juliet, "Fair luck to you, the end is close.")
            }

            stage < DRUNK -> {
                drinkPotion(juliet)
            }

            stage < END -> {
                npc(juliet, "...")
            }

            else -> {
                npc(
                    juliet,
                    "I sat in that cold crypt for ages waiting for Romeo.",
                    "That useless fool never showed up.",
                    "And all I got was indigestion. I am done with men like him.",
                    "Now go away before I call my father!",
                )
            }
        }
    }

    private suspend fun PlayerScript.drinkPotion(juliet: NpcRef) {
        val p = player
        me(
            "Hi Juliet! I have an interesting proposition for you...suggested by Father Lawrence. It may be the only way you'll " +
                "be able to escape from this house and be with Romeo.",
        )
        npc(juliet, "Go on....")
        me("I have a Cadava potion here, suggested by Father Lawrence. If you drink it, it will make you appear dead!")
        npc(juliet, "Yes...")
        me("And when you appear dead...your still and lifeless corpse will be removed to the crypt!")
        npc(juliet, "Oooooh, a cold dark creepy crypt...", "...sounds just peachy!")
        me("Then...Romeo can steal into the crypt and rescue you just as you wake up!")
        npc(juliet, "...and this is the great idea for getting me out of here?")
        me("To be fair, I can't take all the credit...in fact...it was all Father Lawrence's suggestion...")
        npc(juliet, "Ok...if this is the best we can do...hand over the potion!")
        if (!p.take(Objs.CADAVA, 1, "romeo and juliet potion")) return
        itemBox(Objs.CADAVA, "You pass the suspicious potion to Juliet.")
        npc(
            juliet,
            "Wonderful! I just hope Romeo can remember to get me from the crypt.",
            "Please go to Romeo and make sure he understands. Although I love his gormless, lovelorn soppy ways, he can be a " +
                "bit dense sometimes and I don't want to wake up in that crypt on my own.",
            "Oh, here's Phillipa, my cousin...she's in on the plot too!",
            "She's going to make it seem even more convincing!",
        )
        val phillipa = "Phillipa"
        npcAs(Npcs.PHILLIPA, phillipa, "Yes, I'm quite the actress! Good luck dear cousin!")
        npc(juliet, "Right...bottoms up!", "Urk!")
        npcAs(Npcs.PHILLIPA, phillipa, "Oh no...Juliet has...died!")
        me("You might be more believable if you're not smiling when you say it...")
        npcAs(Npcs.PHILLIPA, phillipa, "Oh yeah...you might be right...ok, let's try again.", "Oh no...Juliet has...died?")
        me("Perhaps a bit louder, like you're upset...that your cousin has died!")
        npcAs(
            Npcs.PHILLIPA,
            phillipa,
            "Right...yes...Ok, ok, I think I'm getting my motivation now. Ok, let's try this again!",
            "OH NO...JULIET HAS...DIED?.... Oooooohhhhhh....(sob), (sob).Juliet...my poor dead cousin!",
        )
        npcAs(Npcs.DRAUL_LEPTOC, "Draul Leptoc", "What's all that screaming?", "Oh no! My poor daughter...what has become of you?")
        npcAs(Npcs.PHILLIPA, phillipa, "Poor Juliet...make preparations for her body to be placed in the Crypt...")
        Quests.ROMEO_AND_JULIET.advance(p, DRUNK)
    }

    // ---- Draul Leptoc

    private suspend fun PlayerScript.draulTalk(draul: NpcRef) {
        val p = player
        val stage = stageOf(p)
        when {
            stage == 0 -> {
                npc(
                    draul,
                    "What are you doing in my house...why the impertinence...the sheer cheek...how dare you violate my personal " +
                        "lodgings....",
                )
                me("I..I was just looking around....")
                npc(
                    draul,
                    "Well get out! Get out....this is my house....and don't go near my daughter Juliet...she's grounded in her " +
                        "room to keep her away from that good for nothing Romeo.",
                )
                me("Yes....sir....")
            }

            stage == STARTED -> {
                snoop(draul)
            }

            stage == MESSAGE -> {
                npc(draul, "What are you doing in my house? Up to no good I shouldn't wonder!")
                me("Just a small chore for Juliet, you do have a lovely daughter in her sir.")
                npc(
                    draul,
                    "Oh...why, thank you...I've always tried to my best...",
                    "...Hang on! Enough of that smiley talk. I have a daughter and I know what she's like. Don't even think of " +
                        "carrying on anything behind my back, I have the eyes of a hawk, nothing gets past me!",
                )
                if (p.has(Objs.JULIETMESSAGE)) {
                    itemBox(Objs.JULIETMESSAGE, "Sir Draul notices the message!")
                    npc(draul, "Hey! What's that in your hands...looks like a message to me...with Juliet's barely legible scrawl on it...")
                    me(
                        "Yes, yes, that's probably why I can't read it!",
                        "Sorry, I mean, that's right sir. I'm just popping to the shops to get some groceries for Juliet.",
                        "Right, have to be off now...thanks...",
                    )
                    npc(draul, "Groceries!", "Groceries!...at a time like this, does that girl know what she's putting me through!")
                }
            }

            stage <= PRIEST -> {
                npc(
                    draul,
                    "Do you live here? If so, how's about a couple of hundred gold towards the rent eh? Pay your share I " +
                        "say...you don't want to be like that freeloading Romeo!",
                )
            }

            stage < DRUNK -> {
                npc(draul, "Hey , what are you doing here?")
                me("Nothing much sir...I promise...I'm just an innocent friend of Juliet's...doing her a few favours...as a friend.")
                npc(draul, "Well, just make sure there's no funny business going on that's all I can say. Do you know why?")
                me("I think I can guess sir...")
                npc(draul, "There's no need to guess! You can see it in my hawk like eyes, in my cat like ears and my dog like nose...")
                me("Are you saying that you look like an animal sir?")
                npc(
                    draul,
                    "NO! Not look like an animal! I have the keen 'SENSES' of an animal and I don't miss a thing. Don't even " +
                        "think about trying anything!",
                )
                if (p.has(Objs.CADAVA)) {
                    itemBox(Objs.CADAVA, "Draul notices the potion!")
                    npc(draul, "Hey!  What's that in your hands? Looks like some sort of potion to me!")
                    me(
                        "Err...no! Nope! Not a potion!  Some medicine...I have a terrible cough  ...cough...cough....see?",
                        "Except, I can't drink too much or else it makes me feel 'really' tired. One sip of this and the lights " +
                            "go out...I mean, I'm asleep in a minute.",
                    )
                    npc(
                        draul,
                        "Sleep!",
                        "Sleep...! How can you even think of such a thing at a time like this? And I hope you have no " +
                            "intentions of sleeping in my house!",
                        "You'll get a bill for rent if I catch you dozing off around here!",
                    )
                }
            }

            stage < END -> {
                npc(
                    draul,
                    "My poor Juliet....she's dead...dead! I shouldn't have been so hard on her...she was my lovely daughter. " +
                        "Booo hoooo hooooo!",
                )
            }

            else -> {
                npc(draul, "I suppose you're quite pleased with yourself now...")
            }
        }
    }

    private suspend fun PlayerScript.snoop(draul: NpcRef) {
        npc(draul, "What are you doing here? Snooping around...")
        when (
            choice(
                "Select an option",
                "I've come to see Juliet on Romeo's behalf.",
                "I've just come to have a chat with Juliet.",
                "Oh...just looking around...",
                "Ok, thanks.",
            )
        ) {
            1 -> {
                me("I've come to see Juliet on Romeo's behalf.")
                npc(
                    draul,
                    "What...what...Romeo! Why that good for nothing swine...he's always trying to get the affections of my " +
                        "daughter..that soppy, half brained nincompoop won't ever have the heart of my daughter.",
                    "She deserves someone of character, wit and repose.",
                )
                me("What's so wrong about Romeo?")
                npc(
                    draul,
                    "Wrong! What's wrong with him...have you actually talked to him? He's nothing but a dim witted upperclass " +
                        "twit, totally useless.",
                    "If he threw a stone at the ground, he'd probably miss! He's totally invisible when it's raining because " +
                        "he's so wet!",
                    "If you started with what's right with him, you'd have much less to consider!",
                )
                me("Well, I admit, he's probably not the sharpest knife in the cutlery draw...")
                npc(
                    draul,
                    "Sharp? I've seen keener wit in root vegetables.  Anyway, stop changing the subject. Get out of here and " +
                        "don't think you can sneak up those stairs to see Juliet, because I'll catch you and then you'll be for it!",
                )
                me("That seems a bit harsh....")
                npc(draul, "Harsh but fair I think you'll find...now get OUT!")
            }

            2 -> {
                me("I've just come to have a chat with Juliet.")
                npc(draul, "What on earth about?  I hope you're not in cahoots with that good for nothing Romeo!")
                me("Err..no of course not....why would I be?")
                npc(
                    draul,
                    "He's been trying to wooo my daughter for an age. Up until now she's had the good sense to just ignore him. " +
                        "I just don't know what's gotten into her recently so that she would give him the time of day.",
                )
                me("Well, love is mysterious!  Perhaps one day someone may even learn to love you!")
                npc(draul, "What!  Someone may fall in love with me...what are you trying to insinuate?")
                me("Err...Nothing....I guess I'd better be going now...")
            }

            3 -> {
                me("Oh...just looking around...")
                npc(
                    draul,
                    "Just looking around! This is MY house!  You might have at least 'ASKED' to view my considerably well " +
                        "appointed abode...but no, you've just burst in with all the elegance of a Troll at a tea party.",
                )
                me("I can see that you're busy ranting so I'll just nip off and investigate a bit.")
            }

            4 -> {
                me("Ok, thanks.")
            }

            else -> {
                Unit
            }
        }
    }

    // ---- Phillipa

    private suspend fun PlayerScript.phillipaTalk(phillipa: NpcRef) {
        val stage = stageOf(player)
        when {
            stage == 0 -> {
                me("Hello, who are you?")
                npc(
                    phillipa,
                    "Hi, I'm Phillipa, Juliet's cousin. I like to keep an eye on her, make sure that dashing young Romeo " +
                        "doesn't just steal her away under our plain old noses!",
                    "He'd do it, you know - he's ever so dashing, and cavalier, in a wet blanket sort of way.",
                )
                me("Romeo? Where would I find him then?")
                npc(
                    phillipa,
                    "Well, that's a good question! Who knows where his head's at most of the time? In the clouds, most likely!",
                    "But he's probably chasing the ladies who frequent Varrock market. He does like a bit of kiss chase, so I've heard!",
                )
            }

            stage == STARTED -> {
                me("Hello.")
                npc(
                    phillipa,
                    "Hi, I'm Phillipa, Juliet's cousin. I like to keep an eye on her, make sure that dashing young Romeo " +
                        "doesn't just steal her away under our plain old noses!",
                    "He'd do it, you know - he's ever so dashing, and cavalier, in a wet blanket sort of way.",
                )
            }

            stage < DRUNK -> {
                npc(
                    phillipa,
                    "Oh, hello. Juliet has told me what you're doing for her and Romeo, and I have to say I'm very grateful to " +
                        "you. Juliet deserves a bit of happiness in her life.",
                    "And I'm sure Romeo is just the sort of jester to make her laugh out loud - hysterically you might say.",
                    "He always brings a tear to my eyes - tears of happiness at his foolish antics!",
                )
                me("Oh, thanks. I like to do my cupid bit.")
            }

            stage < END -> {
                npc(phillipa, "Oh, hello again! How was I - do you think I was convincing?")
                me("Oh, yes, totally!")
                npc(
                    phillipa,
                    "Ohhh good! I do hope Juliet will be pleased as well! That dashing young Romeo must be beside himself with " +
                        "excitement! Have you told him the good news yet?",
                )
                me("Not yet, but soon!")
                npc(phillipa, "Good, good - you lucky thing, I can't wait to see the look on his face!")
            }

            else -> {
                npc(
                    phillipa,
                    "Hello, I seem to be getting on really well with that dashing lovely dovely, Romeo. Pity about poor Juliet " +
                        "- she missed out there I reckon!",
                )
            }
        }
    }

    // ---- Father Lawrence

    private suspend fun PlayerScript.lawrenceTalk(lawrence: NpcRef) {
        val p = player
        val stage = stageOf(p)
        when {
            stage == DELIVERED -> {
                sermon(lawrence)
            }

            stage == PRIEST -> {
                npc(lawrence, "Ah, have you found the Apothecary yet? Remember, Cadava potion, for Juliet.")
            }

            stage in APOTHECARY until DRUNK -> {
                npc(lawrence, "Did you find the Apothecary?")
                if (!p.has(Objs.CADAVA)) {
                    me("Yes I did. He's told me I must find some Cadava berries.")
                    npc(lawrence, "Well, good luck with that...they're quite tricky to find.")
                    me("Any clues where I can start to look?")
                    npc(
                        lawrence,
                        "I heard some kids saying they saw some the other day. They were visiting the mining place to the south " +
                            "east of Varrock.",
                    )
                    me("Ok, that's as good a place to start looking as any.")
                } else {
                    me("I've got the cadava potion.")
                    npc(
                        lawrence,
                        "Good! Good work! Ok, take it to Juliet, she's expecting you. I'll talk to Romeo and make sure he knows " +
                            "what to do.",
                    )
                }
            }

            stage >= DRUNK -> {
                npc(lawrence, "Oh to be a father in the times of whiskey.", "I sing and I drink and I wake up in gutters.")
                if (p.random(2) ==
                    0
                ) {
                    npc(lawrence, "Top of the morning to you.")
                } else {
                    npc(lawrence, "To err is human, to forgive, quite difficult.", "I need a think I drink.")
                }
            }

            else -> {
                npc(lawrence, "Hello adventurer, do you seek a quest?")
                while (true) {
                    when (
                        choice(
                            "Select an option",
                            "I am always looking for a quest.",
                            "No, I prefer just to kill things.",
                            "Can you recommend a good bar?",
                            "Ok, thanks",
                        )
                    ) {
                        1 -> {
                            me("I am always looking for a quest.")
                            npc(
                                lawrence,
                                "Well, I see poor Romeo wandering around the square. I think he may need help.",
                                "I was helping him and Juliet to meet, but it became impossible.",
                                "I am sure he can use some help.",
                            )
                        }

                        2 -> {
                            me("No, I prefer just to kill things.")
                            npc(lawrence, "That's a fine career in these lands. There is more that needs killing every day.")
                        }

                        3 -> {
                            me("Can you recommend a good bar?")
                            npc(
                                lawrence,
                                "Drinking will be the death of you.",
                                "But the Blue Moon in the city is cheap enough.",
                                "And providing you buy one drink an hour they let you stay all night.",
                            )
                        }

                        else -> {
                            me("Ok, thanks")
                            return
                        }
                    }
                }
            }
        }
    }

    private suspend fun PlayerScript.sermon(lawrence: NpcRef) {
        npc(lawrence, "...and let Saradomin light the way for you... Urgh!", "Can't you see that I'm in the middle of a Sermon?!")
        me("But Romeo sent me!")
        npc(lawrence, "But I'm busy delivering a sermon to my congregation!")
        box("The camera pans around to face his sleeping congregation.")
        me("Yes, well, it certainly seems like you have a captive audience!")
        npc(lawrence, "Ok, ok...what do you want so I can get rid of you and continue with my sermon?")
        me("Romeo sent me. He says you may be able to help.")
        npc(lawrence, "Ah Romeo, yes. A fine lad, but a little bit confused.")
        me("Yes, very confused....Anyway, Romeo wishes to be married to Juliet! She must be rescued from her father's control!")
        npc(lawrence, "I agree, and I think I have an idea! A potion to make her appear dead...")
        me("Dead! Sounds a bit creepy to me...but please, continue.")
        npc(lawrence, "The potion will only make Juliet 'appear' dead...then she'll be taken to the crypt...")
        me("Crypt! Again...very creepy! You must have some strange hobbies.")
        npc(
            lawrence,
            "Then Romeo can collect her from the crypt! Go to the Apothecary, tell him I sent you and that you'll need a " +
                "'Cadava' potion.",
        )
        me("Apart from the strong overtones of death, this is turning out to be a real love story.")
        Quests.ROMEO_AND_JULIET.advance(player, PRIEST)
    }

    // ---- Apothecary

    private suspend fun PlayerScript.apothecaryTalk(apothecary: NpcRef) {
        npc(apothecary, "I am the Apothecary. I brew potions. Do you need anything specific?")
        while (true) {
            // OPEN-QU15: "Can you make potions for me?" opens a shop, which no plugin-api call does from a dialogue.
            when (choice("Select an option", "Can you make potions for me?", "Talk about something else.")) {
                1 -> {
                    me("Can you make potions for me?")
                    player.message("Use the Potions option to see what he sells.")
                    return
                }

                2 -> {
                    if (somethingElse(apothecary)) return
                }

                else -> {
                    return
                }
            }
        }
    }

    /** "Talk about something else." Returns true when the conversation is over. */
    private suspend fun PlayerScript.somethingElse(apothecary: NpcRef): Boolean {
        val stage = stageOf(player)
        val quest = stage >= PRIEST && stage < DRUNK
        val options =
            if (quest) {
                arrayOf(
                    "Talk about Romeo & Juliet.",
                    "Have you got any decent gossip to share?",
                    "Do you know a potion to make hair fall out?",
                    "Have you got any good potions to give away?",
                    "No thanks.",
                )
            } else {
                arrayOf(
                    "Have you got any decent gossip to share?",
                    "Do you know a potion to make hair fall out?",
                    "Have you got any good potions to give away?",
                    "No thanks.",
                )
            }
        val raw = choice("Select an option", *options)
        val pick = if (quest) raw else raw + 1
        when (pick) {
            1 -> {
                romeoJulietTalk(apothecary)
            }

            2 -> {
                me("Have you got any decent gossip to share?")
                if (stage >= END) {
                    npc(
                        apothecary,
                        "Sad about that affair with young Romeo and Juliet...",
                        "I hear every time Romeo sees Juliet now he runs away screaming something about ghosts and Juliet's cousin?",
                        "Always did think he was a bit of a strange one...",
                        "Anyway! Life goes on and so does business! Can I do something for you?",
                    )
                } else {
                    npc(
                        apothecary,
                        "Well I hear young Romeo's having a little woman trouble but other than that all's quiet on the eastern " +
                            "front. Can I do something for you?",
                    )
                }
            }

            3 -> {
                me("Do you know a potion to make hair fall out?")
                npc(apothecary, "I do indeed. I gave it to my mother. That's why I now live alone.", "But can I do something for you?")
            }

            4 -> {
                me("Have you got any good potions to give away?")
                npc(apothecary, "Sorry, charity is not my strong point. Do you need anything else?")
            }

            else -> {
                me("No thanks.")
                return true
            }
        }
        return false
    }

    private suspend fun PlayerScript.romeoJulietTalk(apothecary: NpcRef) {
        val p = player
        val stage = stageOf(p)
        when {
            stage == PRIEST -> {
                me("Apothecary, Father Lawrence sent me. I need a Cadava potion to help Romeo and Juliet.")
                npc(
                    apothecary,
                    "Cadava potion. It's pretty nasty. And hard to make.",
                    "Wing of rat, tail of frog. Ear of snake and horn of dog.",
                    "I have all that, but I need some Cadava berries.",
                    "You will have to find them while I get the rest ready. Bring them here when you have them. But be careful. " +
                        "They are nasty.",
                )
                Quests.ROMEO_AND_JULIET.advance(p, APOTHECARY)
                if (p.has(Objs.CADAVABERRIES)) {
                    me("Conveniently, I have some here.")
                    makePotion(apothecary, firstTime = true)
                } else {
                    berryQuestions(apothecary)
                }
            }

            p.has(Objs.CADAVABERRIES) && stage == APOTHECARY -> {
                npc(apothecary, "Well done. You have the berries.")
                makePotion(apothecary, firstTime = false)
            }

            stage == APOTHECARY -> {
                npc(apothecary, "Keep searching for those Cadavaberries. They're needed for the potion.")
            }

            p.has(Objs.CADAVA) -> {
                me("Thank you for the Cadava potion.")
                npc(apothecary, "You're welcome. I hope it helps that young couple find happiness.")
            }

            else -> {
                me("I gave Juliet the Cadava potion.")
                npc(apothecary, "Good, good. I hope it helps that young couple find happiness.")
            }
        }
    }

    private suspend fun PlayerScript.makePotion(apothecary: NpcRef, firstTime: Boolean) {
        val p = player
        if (!p.transaction("romeo and juliet potion") { remove(INV, Objs.CADAVABERRIES, 1) && add(INV, Objs.CADAVA, 1) }) {
            p.message("You don't have enough inventory space.")
            return
        }
        Quests.ROMEO_AND_JULIET.advance(p, POTION)
        itemBox(Objs.CADAVABERRIES, "You hand over the berries, which the Apothecary shakes up in a vial of strange liquid.")
        if (!firstTime) box("The apothecary mixes the potion, creating a burst of smoke.")
        npc(apothecary, "Phew! Here is what you need.")
        itemBox(Objs.CADAVA, "The Apothecary gives you a Cadava potion.")
    }

    private suspend fun PlayerScript.berryQuestions(apothecary: NpcRef) {
        while (true) {
            when (
                choice(
                    "Select an option",
                    "What do these berries look like?",
                    "Where can I get these berries?",
                    "How are these berries dangerous?",
                    "Ok, thanks.",
                )
            ) {
                1 -> {
                    me("What do these berries look like?")
                    npc(
                        apothecary,
                        "They're a nice bright pink colour, with green leaves, stalks are a pale brown colour...you can't miss " +
                            "them. They look pretty tasty, but I wouldn't advise eating them.",
                    )
                }

                2 -> {
                    me("Where can I get these berries?")
                    npc(
                        apothecary,
                        "I think I saw some recently in the local vicinity. Oh yes, that's right! There was a little bit of " +
                            "commotion when some kids visited the south eastern Varrock mining pit. The trip was disrupted because " +
                            "some kids",
                        "had found some cadavaberry bushes and they started picking them. They wrapped that trip up pretty quickly!",
                    )
                }

                3 -> {
                    me("How are these berries dangerous?")
                    npc(
                        apothecary,
                        "Hmm, they're generally okay, but if handled improperly they can be a bit nasty. And I certainly " +
                            "wouldn't advise drinking the potion!",
                    )
                }

                else -> {
                    me("Ok, thanks.")
                    return
                }
            }
        }
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1
        const val INV = tp.symbols.Invs.INV

        /** Cadava bush locs with berries (`fai_varrock_cadavabush_2`, `_1`; the three bushes by the south-east Varrock mine are 23625). */
        val CADAVA_BUSHES = intArrayOf(23625, 23626)

        /** Quest variable stages (OPEN-QU1: 0 and 100 are the cache's `unstarted` / `endstate`). */
        const val STARTED = 10
        const val MESSAGE = 20
        const val DELIVERED = 30
        const val PRIEST = 40
        const val APOTHECARY = 50
        const val POTION = 60
        const val DRUNK = 70
        const val END = 100

        /**
         * Wiki "Romeo & Juliet" rewards: 1... the quest row's `questpoints_override` is 5. The Wiki's antique lamp (500 XP,
         * added 3 November 2026) has no obj in cache 2757, so it is OPEN-QU9.
         */
        val REWARD = QuestReward(model = Objs.CADAVA)
    }
}
