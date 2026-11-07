package tp.content.sailing

import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpcAs
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.plugin.mesbox
import tp.symbols.Invs
import tp.symbols.Npcs

/**
 * The conversations of the Pandemonium quest, transcribed from the OSRS Wiki "Transcript:Pandemonium"
 * (docs/mechanics/sailing-pandemonium.md). Every conversation reads the quest stage ([Pandemonium.stage]) and moves it on
 * through [quest]. Speakers are shown by chat head type, so either character can speak whichever one was clicked.
 */
internal class PandemoniumDialogue(private val quest: Pandemonium) {
    private suspend fun PlayerScript.will(text: String) = chatNpcAs(Npcs.SAILING_INTRO_WILL_SARIM, "Will", text)

    private suspend fun PlayerScript.anne(text: String) = chatNpcAs(Npcs.SAILING_INTRO_ANNE_SARIM, "Anne", text)

    private suspend fun PlayerScript.steve(text: String) = chatNpcAs(Npcs.STEVE_BEANIE, STEVE, text)

    private suspend fun PlayerScript.ribs(text: String) = chatNpcAs(Npcs.SAILING_INTRO_RIBS, "Ribs", text)

    private suspend fun PlayerScript.grog(text: String) = chatNpcAs(Npcs.SAILING_INTRO_GROG, "Old Grog", text)

    private suspend fun PlayerScript.me(text: String) = chatPlayer(text)

    // ---- Port Sarim: starting the quest

    /** Will or Anne on the dock at Port Sarim. */
    suspend fun PlayerScript.sarim() {
        val stage = quest.stage(player)
        when {
            stage == Pandemonium.NOT_STARTED -> {
                start()
            }

            stage == Pandemonium.STARTED -> {
                anne("Ah, there you are! Time to get on with your interview!")
                interview()
            }

            stage == Pandemonium.OFFERED -> {
                anne("Ah, there you are, ${player.username}. Are you ready to hop aboard our ship?")
                if (choice("Select an option", "I guess so...", "I'd rather not...") == 1) boardOffer() else decline()
            }

            stage in Pandemonium.ON_BOAT until Pandemonium.WASHED_UP -> {
                backAtSarim()
            }

            else -> {
                player.message("Nothing interesting happens.")
            }
        }
    }

    private suspend fun PlayerScript.start() {
        anne("Ah, look what we have here, Will! This looks like someone who needs a good job!")
        me("What?")
        will(
            "Goodness, Anne, I think you're right! This one's clearly never worked an honest day in their life, and it's about " +
                "time someone changed that!",
        )
        me("But I don't need a...")
        anne("Well, let's not waste any more time! Stranger, are you ready for your interview?")
        if (choice("Start the Pandemonium quest?", "Yes.", "No.") == 1) {
            me("I guess so...?")
            quest.setStage(player, Pandemonium.STARTED)
            interview()
        } else {
            me("Sorry, but I'm not looking for a job right now. I'm just going to go...")
            will("Oh, you'll be back, friend! You already know you can't turn down an opportunity like this!")
        }
    }

    private suspend fun PlayerScript.interview() {
        will("Let's get you started with a nice easy question: What is your name?")
        me(player.username + ".")
        anne("Pleased to meet you, ${player.username}. I'm Anne, and this is Will.")
        will("Next question: Do you have any experience captaining a ship?")
        me("Not really...")
        will("Oh...")
        anne("Will, it's important we not be too picky! It's a competitive market, after all.")
        will(
            "Okay, final question: Do you agree to waive all rights to pursue legal action against your new employer in the " +
                "event of injury or horrific death?",
        )
        me("Is that likely to happen?")
        anne("Not at all!")
        me("I'm not sure if I...")
        will(
            "Excellent! Well then, ${player.username}, I'm pleased to say that we have the results of your interview. You bring " +
                "some impressive stuff to the table. We'd love to offer you the role!",
        )
        anne("Congratulations!")
        me("Thanks... only, you didn't actually mention what the role was...")
        will("Ah, how forgetful of us! Given that you've already accepted, how about we discuss the details on our ship.")
        me("But I didn't accept anything...")
        anne("Just let us know when you're ready, and we'll hop aboard!")
        quest.setStage(player, Pandemonium.OFFERED)
        if (choice("Select an option", "I guess I'm ready...", "I'd rather not...") == 1) {
            me("I guess I'm ready...")
            boardOffer()
        } else {
            decline()
        }
    }

    private suspend fun PlayerScript.decline() {
        me("I'd rather not...")
        will("Well, don't take too long. This work won't do itself, will it?")
    }

    private suspend fun PlayerScript.boardOffer() {
        if (player.inv(Invs.INV).freeSlots == 0) {
            return will(NO_ROOM)
        }
        will("Then let us away!")
        quest.boardIntroBoat(player, firstTime = true)
    }

    private suspend fun PlayerScript.backAtSarim() {
        anne("Hello there, ${player.username}. Shall we hop back aboard our ship?")
        if (choice("Select an option", "Okay.", "Not right now.") == 1) {
            me("Okay.")
            boardOffer2()
        } else {
            me("Not right now.")
            will("Well, don't take too long. This work won't do itself, will it?")
        }
    }

    private suspend fun PlayerScript.boardOffer2() {
        if (player.inv(Invs.INV).freeSlots == 0) {
            return will(NO_ROOM)
        }
        will("Excellent! Let us away!")
        quest.boardIntroBoat(player, firstTime = false)
    }

    // ---- on the boat

    /** The briefing the first time the player is aboard (stage [Pandemonium.ON_BOAT]). */
    suspend fun PlayerScript.briefing() {
        will("Lovely! The open sea!")
        me("It's not really that open... We haven't even left port yet.")
        anne("Well, good news, ${player.username}, because that's where you come in! You're our new helm!")
        me("Helm?")
        will("It means you're in charge of the ship's helm! In other words, you're responsible for steering the ship!")
        me("It's more of a boat really...")
        anne("Well, we all have to start somewhere!")
        me("Right... So if I'm manning the helm, what will you two be doing?")
        will("Well, it's simple, isn't it? We're in charge!")
        me("Have you ever actually done this before?")
        anne("Many times!")
        will(
            "Just never on our own ship before! This is an exciting first day for... well, we haven't actually named our " +
                "business yet, but I'm sure we'll come up with a good name soon.",
        )
        me("And what does this business do?")
        anne("We salvage shipwrecks of course!")
        me("Of course. Alright then, I don't remember doing it, but apparently I committed to this. Where is our first wreck?")
        will(
            "We've got a good one to get us started! Last time we were on the Pandemonium, we purchased a map that should lead " +
                "us to an old pirate wreck.",
        )
        me("The Pandemonium?")
        anne("Have you never been? It's a great place for a drink! We'll have to take you there!")
        will(
            "But first, let's collect some salvage! According to the map, the wreck is just east of the Karamja Shipyard, so " +
                "south of here.",
        )
        anne("Over to you, ${player.username}! Take the helm when ready.")
        quest.setStage(player, Pandemonium.BRIEFED)
    }

    /** Will or Anne aboard, any time after the briefing and before the hook is lowered. */
    suspend fun PlayerScript.aboard() {
        val stage = quest.stage(player)
        when {
            stage == Pandemonium.ON_BOAT -> {
                briefing()
            }

            stage == Pandemonium.BRIEFED -> {
                anne("This is your bit, ${player.username}! Take the helm when ready.")
                will("Remember, you need to take us to the wreck. It should be east of the Karamja Shipyard, so south of here.")
                if (choice("Select an option", "Okay, I'll get to it.", "Actually, I want to go back to Port Sarim.") == 1) {
                    me("Okay, I'll get to it.")
                } else {
                    me("Actually, I want to go back to Port Sarim.")
                    anne("This isn't the can-do attitude we need, ${player.username}, but fine, we'll go back...")
                    quest.returnToSarim(player)
                }
            }

            stage == Pandemonium.AT_WRECK -> {
                anne(
                    "Time for some salvaging, ${player.username}! When you're ready, just lower the salvaging hook and let's see " +
                        "what we can find!",
                )
            }
        }
    }

    /** Arrival at the wreck (Wiki "Reaching the salvaging point"). */
    suspend fun PlayerScript.reachedWreck() {
        will("Right, now that we're here, I think it's time to do some salvaging!")
        anne("When you're ready, ${player.username}, just lower the salvaging hook and let's see what we can find!")
        me("Hang on... why am I lowering the hook? I thought the helm was my responsibility?")
        will("Yes! The helm... and the hook!")
        me("Of course. I'll just do everything while you two reap the rewards. Sounds about right.")
        will("Don't go getting grumpy on us now, ${player.username}. This isn't about the rewards. It's all about the experience!")
        anne("That's right! Think of what you'll learn today!")
        me("*sigh*")
    }

    /** The salvaging hook before the boat is at the wreck. */
    suspend fun PlayerScript.hookTooEarly() {
        will(
            "You need to take us to the wreck before we can use the salvaging hook, ${player.username}. It should be east of the " +
                "Karamja Shipyard, so south of here.",
        )
    }

    /** The salvaging hook at the wreck. Returns false when the player lacks room for the cup. */
    suspend fun PlayerScript.hook(): Boolean {
        if (player.inv(Invs.INV).freeSlots == 0) {
            mesbox("You don't have enough room in your inventory to use the salvaging hook.")
            return false
        }
        me("Right then, let's give this a go.")
        mesbox("You use the salvaging hook to gather an old cup and a mysterious medallion.")
        will("Well?")
        me("What do you make of these?")
        mesbox("You show Will what you found.")
        will("The medallion! Anne, we've found it!")
        me("You were looking for this?")
        anne("That's right! And now that we've found it, I'm afraid we don't really need you anymore.")
        me("What?")
        anne("Sorry, ${player.username}. If it's any consolation, you did a great job! Unfortunately, we just have to let you go.")
        return true
    }

    /** After the push overboard. */
    suspend fun PlayerScript.overboard() {
        me("Eugh...")
        me("Where am I?")
    }

    // ---- the Pandemonium: Steve, Ribs

    suspend fun PlayerScript.steveTalk() {
        val stage = quest.stage(player)
        when {
            stage < Pandemonium.WASHED_UP -> player.message("Nothing interesting happens.")
            stage == Pandemonium.WASHED_UP -> steveIntro()
            stage == Pandemonium.STEVE_TOLD -> steveReminder()
            stage == Pandemonium.RIBS_TOLD -> steveAboutBounty()
            stage == Pandemonium.JIM_REPORTED -> steveFinale()
            else -> steveMenu(stage)
        }
    }

    private suspend fun PlayerScript.steveIntro() {
        steve(
            "Yo ho ho and a Jolly Roger to you, me hearty! I can see from the fact that you're totally drenched from head to toe " +
                "that you are a fellow scallywag sailor! The name's Steve Beanie! Pleased to meet you.",
        )
        me("I think I must be having some sort of fever dream... or maybe I'm dead?")
        steve(
            "Ha! Far from it, matey! Though the fever dream I can't comment on. They're rather common around here... but I " +
                "assure you that it's well-nigh certainly like enough most likely not the rum doing it!",
        )
        me("Okay... You said words there, but I don't think they actually formed a sentence.")
        steve(
            "Ah, me wee hearty! You seem to be having quite the crisis! Thankfully, you've rigged the starboard and docked " +
                "yourself at the right port! Welcome to the Pandemonium!",
        )
        me("Pandemonium?")
        steve(
            "Aye! To be sure! This here is the finest establishment in all the seas. Sit back! Have a drink! Let the good times, " +
                "and the rum, flow!",
        )
        me("If this is the Pandemonium, do you know of a pair of salvagers called Will and Anne?")
        steve("Avast ye! You name a pair of pinching looting plundering seadogs!")
        me(
            "I see. So you do know them. They claimed to have purchased a map here, but I'm guessing they weren't being totally " +
                "honest about that...",
        )
        steve(
            "Purchased?! Purchased?! You're pulling my leg, you scallywag! Those scurvy dogs came here and broke the one rule of " +
                "the Pandemonium! That map was pilfered! Stolen!",
        )
        me(
            "Well, I had quite the fun run in with them myself. They had me help them salvage an old shipwreck " +
                "before throwing me overboard.",
        )
        steve("Arrr, you poor scallywag.")
        me("Thank you. It was the map they stole that led them to the wreck. What do you know about it?")
        steve(
            "Not a lot, I'm afraid. You should hoist your sails and go speak to Ribs instead. It was his map they looted. He's " +
                "the old dog over in the corner. Avast!",
        )
        quest.setStage(player, Pandemonium.STEVE_TOLD)
    }

    private suspend fun PlayerScript.steveReminder() {
        steve("Yarr! What will it be?")
        val pick = choice("Select an option", "Let's see what you have.", "About Will and Anne...", "Nothing, thanks.")
        when (pick) {
            1 -> {
                shopNotYet()
            }

            2 -> {
                steve(
                    "Those plundering seadogs! Did you speak to Ribs about them? It was his map they looted. He's the old dog over " +
                        "in the corner.",
                )
                me("Thanks. I'll go and speak to him now.")
            }

            3 -> {
                me("Nothing, thanks.")
                steve("Shiver me timbers!")
            }
        }
    }

    private suspend fun PlayerScript.shopNotYet() {
        me("Let's see what you have.")
        steve("Aye!")
        player.message("The Pandemonium's drinks shop is not available yet.")
    }

    private suspend fun PlayerScript.steveAboutBounty() {
        steve("Yarr! What will it be?")
        val pick = choice("Select an option", "Let's see what you have.", "About Will and Anne...", "Nothing, thanks.")
        when (pick) {
            1 -> {
                shopNotYet()
            }

            2 -> {
                bountyTalk()
            }

            3 -> {
                me("Nothing, thanks.")
                steve("Shiver me timbers!")
            }
        }
    }

    private suspend fun PlayerScript.bountyTalk() {
        me("About Will and Anne...")
        steve("Those plundering seadogs! Did you speak to Ribs about them?")
        me("I did. He seems to think they might have unleashed some sort of curse.")
        steve(
            "Ah, the curse of Barnacle Barnaby! It's one of Ribs' favourite tales. Just a myth of course, but the " +
                "old dog does love his stories.",
        )
        me(
            "Well, myths aside, Ribs mentioned you'd put a bounty out for Will and Anne. I'd be quite keen to track them down myself.",
        )
        steve(
            "Yo ho ho! A mighty good decision! It's a generous bounty indeed. You don't go breaking the one rule of the " +
                "Pandemonium and expect to get away with it!",
        )
        val ruleFirst = choice("Select an option", RULE_OPTION, WHERE_OPTION) == 1
        if (ruleFirst) oneRule() else me(WHERE_OPTION)
        steve(
            "Shiver me timbers! I'm afraid not. Old Grog said he might try and track them down. Perhaps he has an idea of where " +
                "to find them.",
        )
        me("And where would I find Old Grog?")
        steve(
            "Ah, the old scallywag left not that long ago, but he'll be back here before long. He enjoys a drink far too much! " +
                "In the meantime, you should hoist the sails and ready your vessel!",
        )
        me("But I don't have a vessel...")
        steve("What? But then how did you get here?")
        me("Will and Anne threw me overboard and I washed up here. I did mention this earlier...")
        steve(
            "Blistering barnacles! Well, you won't be getting far without a boat of your own. You should head to the docks and " +
                "speak with Junior Jim, our local shipwright. He'll get you sorted out.",
        )
        me("Alright, I'll go and see him.")
        steve("Jolly Roger!")
        quest.setStage(player, Pandemonium.JIM_SENT)
    }

    private suspend fun PlayerScript.oneRule() {
        me("What exactly is this one rule you keep mentioning?")
        steve(
            "Avast! The mainland kingdoms have no authority here. Their laws do not apply. Instead, we just have our one rule that " +
                "keeps the Pandemonium afloat: Never disrespect your fellow patron.",
        )
        steve("We don't care who you are. Indeed, pirates and customs officers can enjoy a drink here, side by side. Savvy?")
        me("So anyone is welcome here as long as they don't commit an offense against any of the other patrons?")
        steve("Yarr! You've got it!")
        me("I see. So, back to Will and Anne. Do you have any idea where they might have gone?")
    }

    /** Steve once Jim has been named (Wiki "Subsequent dialogue"). */
    private suspend fun PlayerScript.steveMenu(stage: Int) {
        steve("Yarr! What will it be?")
        val pick = choice("Select an option", "Let's see what you have.", "About Will and Anne...", "Nothing, thanks.")
        when (pick) {
            1 -> {
                shopNotYet()
            }

            2 -> {
                me("About Will and Anne...")
                if (stage < Pandemonium.JIM_REPORTED) {
                    steve(
                        "Aye! Old Grog hasn't returned yet, but I'm sure he'll be back soon. In the meantime, you should head to the " +
                            "docks and speak with Junior Jim, our local shipwright. He'll get you sorted out with a boat of your own.",
                    )
                    me("Alright, I'll go and see him.")
                    steve("Jolly Roger!")
                }
            }

            3 -> {
                me("Nothing, thanks.")
                steve("Shiver me timbers!")
            }
        }
    }

    /** Ribs: the tale of the Crimson Kraken. */
    suspend fun PlayerScript.ribsTalk() {
        val stage = quest.stage(player)
        when {
            stage < Pandemonium.STEVE_TOLD -> {
                player.message("Nothing interesting happens.")
            }

            stage == Pandemonium.STEVE_TOLD -> {
                ribsTale()
            }

            else -> {
                ribs("Ahoy, young whippersnapper. Did you speak to Steve about that bounty?")
                me("Not yet. I'll go and see him now.")
            }
        }
    }

    private suspend fun PlayerScript.ribsTale() {
        ribs("Ahoy, young whippersnapper. Have you come to hear a tale from old Ribs?")
        me("Kind of, I guess. I was hoping you could tell me about Will and Anne, along with the map they stole.")
        ribs("A dark day. A very dark day. Those young fools know not what they have wrought upon themselves.")
        me("That being?")
        ribs(
            "A curse! A curse most foul! That map marked the resting place of the Crimson Kraken! She was a deadly vessel, " +
                "captained by none other than Barnacle Barnaby himself!",
        )
        me("Barnacle Barnaby? Who's that then?")
        ribs("You have not heard the tale of Barnacle Barnaby? You have not heard of the scourge of the Unquiet Ocean?")
        if (choice("Select an option", "No, tell me about him.", "Now's not really the time for tales.") == 1) {
            kraken()
        } else {
            me("Now's not really the time for tales.")
            ribs(
                "You are a fool for thinking that way, but on your head be it. The short of it is that Barnaby and the Kraken were " +
                    "cursed, condemning him and his crew to a watery grave.",
            )
            me("I see. Well, I'm afraid Will and Anne found and looted the Kraken.")
        }
        ribs("I feared as much, though part of me still hoped it would not come to be. Tell me, did they find Barnaby's medallion?")
        me("They did. It was actually the only thing they were interested in.")
        ribs("Then the foul curse is now free once more. They must be stopped!")
        me("Well, I do have my own personal reasons for wanting Will and Anne dealt with. Maybe I could try and find them.")
        ribs("You are a brave soul indeed, but that is just what is needed at a time like this.")
        ribs(
            "You should talk to Steve. He already put out a bounty for Will and Anne after the theft. He might " +
                "have heard something about where they could have gone.",
        )
        quest.setStage(player, Pandemonium.RIBS_TOLD)
    }

    private suspend fun PlayerScript.kraken() {
        me("No, tell me about him.")
        ribs(
            "He was a cruel and vicious pirate. Orchestrator of the Driftwood Massacre and unleasher of the Plague of Black Mists. " +
                "He believed himself untouchable, but his arrogance was his undoing.",
        )
        ribs(
            "Despite the warnings of many, Barnacle Barnaby attempted to plunder the Skullhorde, but his only reward was a deadly " +
                "curse. It destroyed the Crimson Kraken, condemning all on board to a watery grave.",
        )
        me("And you had a map leading to the wreck of the Kraken?")
        ribs(
            "Aye! I purchased the map from Ulrich the Unfortunate, the sole survivor of the Kraken, but I had no intention of using " +
                "it. I knew that no good would ever come from seeking the Kraken.",
        )
        me("Then why not destroy the map?")
        ribs(
            "And risk the wrath of Barnaby's ghost? No. Better the map be locked away, so that neither it nor the " +
                "curse could harm anyone else.",
        )
        me(
            "I'm not sure I quite follow your reasoning for keeping it, but I guess it doesn't matter now. I'm afraid Will and " +
                "Anne found and looted the Kraken.",
        )
    }

    // ---- Steve's finale and Old Grog

    private suspend fun PlayerScript.steveFinale() {
        val grogNpc: NpcRef = quest.spawnGrog(player)
        chatNpcAs(Npcs.SAILING_INTRO_GROG, "Old Grog", "Is this the one?")
        steve("Aye! This Jolly Roger here is pretty keen to see some justice dealt! Old Grog, meet... er...")
        me(player.username + ".")
        steve("${player.username}! And ${player.username}, meet Old Grog.")
        me("Pleased to meet you.")
        grog(
            "Steve tells me you're keen to see Will and Anne dealt with. I'm open to sharing the bounty, but only if you have what " +
                "it takes. I have no time for layabouts.",
        )
        me("I won't let you down!")
        grog("We'll see. I saw that raft you rocked up in. If that's all you bring to the table, then you have some work to do.")
        grog(
            "It will take me some time to track Will and Anne down, but make no mistake, I will find them. I expect you to be " +
                "ready by the time that happens. Don't be a disappointment.",
        )
        me("You have my word.")
        grog(
            "Good. Now, that's me done drinking. Time to get to work. I'll keep Steve updated on my progress, so make sure " +
                "you're checking in with him. He'll let you know once I have something.",
        )
        mesbox("Old Grog departs.")
        grogNpc.despawn()
        steve(
            "Yo ho ho, ${player.username}! Best you not waste time! Belay your helm and abaft your rigging! Get out there and " +
                "make sure you're ready for Old Grog!",
        )
        me("Will do!")
        steve(
            "To help you on your way, I've got some pirate looty here for you! Sawmill coupons! Useful if you " +
                "want a top vessel like mine.",
        )
        steve("Oh, and don't forget to pop back for a drink from time to time. My rum is to die for! Now, avast!")
        quest.complete(player)
    }

    private companion object {
        const val STEVE = "'Squawking' Steve Beanie"
        const val NO_ROOM =
            "Oh dear! It doesn't quite look like you are! You'll need to make some room to carry some things before we head off!"
        const val RULE_OPTION = "What exactly is this one rule you keep mentioning?"
        const val WHERE_OPTION = "Do you have any idea where Will and Anne might have gone?"
    }
}
