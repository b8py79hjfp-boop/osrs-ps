package tp.content.sailing

import tp.plugin.PlayerScript
import tp.plugin.chatNpcAs
import tp.plugin.chatPlayer
import tp.plugin.mesbox
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Junior Jim's conversations of the Pandemonium quest (Wiki "Transcript:Pandemonium", "Talking to Junior Jim"): the cup for the
 * raft, the captain's log and the report after the cargo run (docs/mechanics/sailing-pandemonium.md).
 */
internal class PandemoniumJim(private val quest: Pandemonium) {
    private suspend fun PlayerScript.jim(text: String) = chatNpcAs(Npcs.JUNIOR_JIM, "Junior Jim", text)

    private suspend fun PlayerScript.me(text: String) = chatPlayer(text)

    suspend fun PlayerScript.jimTalk() {
        val stage = quest.stage(player)
        when {
            stage < Pandemonium.JIM_SENT -> {
                jim("Can I help you, mate?")
                me("I don't think so.")
                jim("Okay... You should probably get going then.")
            }

            stage == Pandemonium.JIM_SENT -> {
                jimRaft()
            }

            stage < Pandemonium.CARGO_DELIVERED -> {
                jimErrand(stage)
            }

            stage == Pandemonium.CARGO_DELIVERED -> {
                jimDone()
            }

            else -> {
                jimAfter()
            }
        }
    }

    private suspend fun PlayerScript.jimRaft() {
        jim("Can I help you, mate?")
        me("Are you Junior Jim?")
        jim("That's me. What do you need then?")
        me("Steve said you might be able to sort me out with a boat.")
        jim("You don't have a boat?")
        me("No.")
        jim("Ha! We've got ourselves a Billy-no-boats! Classic! Yeah, I can sort you out with a boat, but it's gonna cost you.")
        me("How much?")
        jim("Well, what do you have?")
        me("Er... I do have this cup I found. Perhaps I could trade it for a boat.")
        jim("Alright, let's have a gander at it.")
        if (player.inv(Invs.INV).total(Objs.SAILING_INTRO_CUP) == 0L) {
            me("I seem to have lost it.")
            jim(
                "You've got no boat. You've got no cup. You're fresh out of luck it would seem, mate. You'd best hope you lost it " +
                    "here on the Pandemonium. If so, someone might have handed it in to Steve.",
            )
            return me("Then I guess I'd better go see if Steve has it.")
        }
        mesbox("You show the cup to Junior Jim.")
        jim("Hmm... Yeah, yeah, I can work with this. This old rusty cup will get you the finest raft I have!")
        raftTerms()
        if (!quest.takeCup(player)) return
        mesbox("You give the cup to Junior Jim.")
        jim("Right, time to sort you out with a cargo hold. Let's head on over to the shipyard...")
        quest.giveRaft(player)
        // OPEN-PAN1: the shipyard (S4) is not built yet, so the cargo hold is fitted at once and the conversation goes on here.
        mesbox("Junior Jim and the player head to the shipyard.")
        quest.setStage(player, Pandemonium.HOLD_BUILT)
        jim("Smashing job on that cargo hold, mate. Let's get back to the Pandemonium, shall we?")
        jimLog()
    }

    private suspend fun PlayerScript.raftTerms() {
        me("Sorry, I think I must have misheard you. Did you say raft?")
        jim("That's right! The best one I have!")
        me(
            "I was kind of hoping for a bit more than a raft. It's not just the cup I have. I'm sure I can make " +
                "up the funds to buy something bigger.",
        )
        jim(
            "Sorry, mate, but even if you could, the raft is all I've got to hand right now. Besides, you're gonna want to build " +
                "up some experience before you captain anything too big. A raft will be perfect for you.",
        )
        me("Okay...")
        jim(
            "Lighten up, mate. I'll have plenty more vessels in stock soon. Come back once you've got a bit more " +
                "know-how and I'll be able to sort you right out.",
        )
        jim(
            "Now, about your raft... With that cup I'll be able to sort you out with the finest helm, but it " +
                "won't be enough to cover the sails.",
        )
        me("But I won't get anywhere without sails!")
        jim(
            "Don't panic, you silly plonker. I ain't about to send you off on a raft with no sails. I'll sort " +
                "them out for you, but I'll just need a little favour in return.",
        )
        me("What favour?")
        jim(
            "I've got some cargo I need picking up from Port Sarim and bringing back here. In return for sorting " +
                "you out with sails, I'll need you to go and collect it for me.",
        )
        jim(
            "Oh, and I tell you what, I'll even set you up with the materials to whack together a cargo hold. You " +
                "won't be shifting that cargo far without one.",
        )
        me("Thanks!")
        jim("Now, hand over that cup and we can get cracking.")
    }

    /** Jim gives the captain's log and sends the player to Port Sarim. */
    private suspend fun PlayerScript.jimLog() {
        jim("Okay, looks like that's you all sorted, mate. I'd say you're now ready to set sail!")
        me("How exciting!")
        jim(
            "Why don't you take your new raft out for a spin? You can head over to Port Sarim and collect that cargo for me. Oh, " +
                "which reminds me, now that you're officially a captain, you'll need one of these.",
        )
        if (!quest.giveLog(player)) {
            return jim("Ah, seems you don't have room for it, mate. Why don't you come back later?")
        }
        mesbox("Junior Jim gives you a log book.")
        jim(
            "Your very own captain's log! You can use that to keep track of your adventures out at sea, including " +
                "any work you might end up taking on.",
        )
        me("Like collecting your cargo?")
        jim("Right on, mate! In fact, I'll make a note of that in your log now.")
        mesbox(
            "Junior Jim updates your log with details on his request. You can consult the log at any point for a " +
                "reminder on what he needs you to do.",
        )
        jim(
            "And that's you all ready to go, mate! Just hop on your new raft when ready. If you need help finding my cargo, just " +
                "check in with the port master when you get to Port Sarim.",
        )
        jim("Now, unless you fancy a cheeky dip, the gangplank down the docks will be your best bet for getting on board.")
        mesbox(
            "You can embark or disembark from your boat via docking points found around the world. To find a docking point, just " +
                "look for the relevant icon on your map.",
        )
        quest.setStage(player, Pandemonium.ERRAND)
    }

    private suspend fun PlayerScript.jimErrand(stage: Int) {
        if (stage < Pandemonium.ERRAND) return jimLog()
        jim("Alright, mate. How are you doing collecting my cargo?")
        if (stage >= Pandemonium.CARGO_TAKEN) {
            me("I've got it right here.")
            jim("Very nice! Just hand that over to the port master and he'll sort it out. Come let me know once you're done.")
            me("Okay, will do.")
        } else {
            me("I'm still working on it.")
            jim(
                "Well, you'd better hop back onto your raft then. Unless you fancy a cheeky dip, the gangplank down " +
                    "the docks will be your best bet for getting on board.",
            )
        }
    }

    private suspend fun PlayerScript.jimDone() {
        jim("Alright, mate.")
        me("I collected your cargo and left it with the port master.")
        jim(
            "Very nice! That's us all squared up then. You ought to pop back once you've got a bit more " +
                "experience with that raft. By then I should have some new vessels for sale.",
        )
        mesbox(
            "Shipwrights like Junior Jim can sell you new boats or help you upgrade your existing ones. To find a " +
                "shipwright, just look for the relevant icon on your map.",
        )
        me("I might just do that. Any tips on the best ways to get that experience?")
        jim(
            "To be honest, mate, I'd say one of the best things would be to take on more work like what you just " +
                "did for me. A lot of ports will have a notice board where folks advertise jobs they want sorting.",
        )
        mesbox(
            "Port tasks can be accepted from notice boards found in various ports around the world. To find a " +
                "notice board, just look for the relevant icon on your map.",
        )
        me("Sounds good. Anything else?")
        jim(
            "Well, now that you're officially a big cheese captain, you might want to start charting the sea for " +
                "your own records. That log book I gave you has all sorts of cracking tips on how to do so.",
        )
        jim("Oh, and while we're on about charting, I've also chucked some useful bits and bobs in your cargo hold.")
        jim(
            "First up, I've left you a couple of repair kits in there. Perfect for if you go being a numpty and " +
                "end up sailing straight into a proper big storm or a big group of sharks.",
        )
        jim(
            "The other thing in there is a spyglass. It's a proper handy charting tool that's great for getting a " +
                "good look at things while out at sea.",
        )
        jim(
            "There's a chap called Charles who used to be pretty good at the whole charting thing who might be " +
                "able to help you more. You can find him down in the cave on the island.",
        )
        mesbox(
            "Your captain's log contains details on how to chart the sea. You can consult the log at any time for " +
                "hints on what to chart. Charles in the cave on the Pandemonium can also help you out with sea " +
                "charting.",
        )
        quest.giveJimsGifts(player)
        me("Great! Thanks, Jim. I'd better go check in with Steve now and see if Old Grog is back.")
        jim("Don't mention it, mate.")
        quest.setStage(player, Pandemonium.JIM_REPORTED)
    }

    private suspend fun PlayerScript.jimAfter() {
        jim(
            "You should be all ready to go, mate! Just hop on your new raft when ready. If you need help finding my cargo, just " +
                "check in with the port master when you get to Port Sarim.",
        )
        jim("Now, unless you fancy a cheeky dip, the gangplank down the docks will be your best bet for getting on board.")
    }
}
