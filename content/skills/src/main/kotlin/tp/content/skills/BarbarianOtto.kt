@file:Suppress("LongMethod")
// The long functions are a Wiki transcript: straight-line dialogue.

package tp.content.skills

import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Varbits

/**
 * Otto Godblessed's conversation (docs/mechanics/barbarian-training.md BT2). Dialogue: OSRS Wiki "Transcript:Barbarian Training"
 * (the Fishing rod, Firemaking, Smithing and Herblore sections). The harpoon, farming, notebook and hasta-conversion topics are not
 * implemented (OPEN-BT2), so their options are left out of the menu. Each topic moves the section's varbit one step
 * ([BarbarianProgress]); the menu shows the option for the step the player is on.
 */
internal object BarbarianOtto {
    private class Topic(val label: String, val run: suspend PlayerScript.(NpcRef) -> Unit)

    /**
     * Tai Bwo Wannai Trio gates the spear lesson (Wiki "Barbarian Training" > Smithing). The quest is not implemented (OPEN-FS11), so
     * this is off, like the quest gates of Fishing; the varp is the "quest-states.tsv" row of content/quests (state 6 = complete).
     */
    const val ENFORCE_TBWT = false
    private const val TBWT_VARP = 320
    private const val TBWT_DONE = 6

    suspend fun PlayerScript.talk(otto: NpcRef) {
        if (!BarbarianProgress.started(player)) {
            if (!intro(otto)) return
        } else {
            npc(otto, "Good day to you, hearty warrior! May the spirits guide you.")
        }
        topics(otto)
    }

    private suspend fun PlayerScript.npc(otto: NpcRef, text: String) = chatNpc(otto, text)

    private suspend fun PlayerScript.me(text: String) = chatPlayer(text)

    /** The first meeting: "You think so?" starts the miniquest. False when the conversation ended. */
    private suspend fun PlayerScript.intro(otto: NpcRef): Boolean {
        npc(otto, "Good day! You seem a hearty warrior. Maybe even some barbarian blood in that body of yours?")
        var asked = false
        while (true) {
            val options =
                if (asked) {
                    listOf(THINK_SO, NOT_RELATED)
                } else {
                    listOf(THINK_SO, WHO_ARE_YOU, NOT_RELATED, TOO_BUSY)
                }
            val pick = options.getOrNull(choice(TITLE, *options.toTypedArray()) - 1) ?: return false
            me(pick)
            when (pick) {
                THINK_SO -> {
                    thinkSo(otto)
                    player.setVarbit(Varbits.BRUT_MINIQUEST, BarbarianProgress.STARTED)
                    return true
                }

                WHO_ARE_YOU -> {
                    npc(
                        otto,
                        "Me? I am Otto, known as the Godblessed. I follow the ways of our barbarian ancestors, communing with the " +
                            "spirits of the dead and teaching their ways to worthy disciples.",
                    )
                    npc(
                        otto,
                        "Perhaps you could be one of them. You look a likely sort to learn of these ways. As I say, you might even " +
                            "have a bit of barbarian blood hidden away in you.",
                    )
                    asked = true
                }

                NOT_RELATED -> {
                    npc(otto, "Your scepticism will be your loss.")
                    return false
                }

                else -> {
                    npc(otto, "Well then, we will talk when you learn to be less impetuous.")
                    return false
                }
            }
        }
    }

    private suspend fun PlayerScript.thinkSo(otto: NpcRef) {
        npc(
            otto,
            "Who can tell? My forefathers weren't averse to travelling, so it is possible. They tended to cause too much trouble in " +
                "your so-called civilised lands, however, so most returned to their ancestral homes.",
        )
        npc(otto, "In any case, given you seem the type, I think you are ready to learn our more secret tribal feats for yourself.")
        me("Oh, that sounds interesting. What sort of skills would these be?")
        npc(
            otto,
            "To begin with, I can supply knowledge in the ways of firemaking, our special rod fishing tricks, a selection of spear " +
                "skills and our agricultural techniques.",
        )
        me("There are more advanced stages though, going by your description?")
        npc(
            otto,
            "Your perception is creditable. I can eventually teach of more advanced firemaking techniques, and the rod fishing skills " +
                "are but a preliminary to our special potions and brews.",
        )
        npc(otto, "These secrets must, however, wait until you have mastered the basics.")
    }

    /** The topic menu: one option per section, for the step the player is on, until the player leaves. */
    private suspend fun PlayerScript.topics(otto: NpcRef) {
        while (true) {
            val topics = menu(player)
            val labels = topics.map { it.label } + GOING
            val pick = choice(TITLE, *labels.toTypedArray())
            if (pick !in 1..labels.size) return
            if (pick == labels.size) return me(GOING)
            val topic = topics[pick - 1]
            me(topic.label)
            topic.run(this, otto)
            val more = choice(TITLE, MORE, NO_MORE)
            if (more == 2) {
                me(NO_MORE)
                return npc(otto, "In that case, farewell.")
            }
            if (more != 1) return
        }
    }

    private fun menu(p: PlayerRef): List<Topic> {
        val topics = ArrayList<Topic>(4)
        when (p.varbit(Varbits.BRUT_FISHING_R)) {
            0 -> topics += Topic(ROD_ASK) { otto -> rodExplain(otto) }
            BarbarianProgress.ROD_TAUGHT -> topics += Topic(ROD_ASK) { otto -> rodWaiting(otto) }
            BarbarianProgress.ROD_DONE -> topics += Topic("I've fished with a barbarian rod!") { otto -> rodDone(otto) }
        }
        when (p.varbit(Varbits.BRUT_FIRE)) {
            0 -> topics += Topic(FIRE_ASK) { otto -> fireExplain(otto) }
            BarbarianProgress.FIRE_TAUGHT -> topics += Topic(FIRE_ASK) { otto -> fireWaiting(otto) }
            BarbarianProgress.FIRE_BOW_LIT -> topics += Topic("I've set fire to logs with a bow!") { otto -> fireDone(otto) }
            else -> topics += Topic("I have completed firemaking with a bow. What follows this?") { otto -> pyreExplain(otto) }
        }
        spearTopic(p)?.let { topics += it }
        herbTopic(p)?.let { topics += it }
        return topics
    }

    private fun spearTopic(p: PlayerRef): Topic? {
        val spear = p.varbit(Varbits.BRUT_SMITH_SPEAR)
        val hasta = p.varbit(Varbits.BRUT_SMITH_HASTA)
        return when {
            spear == 0 -> Topic(SPEAR_ASK) { otto -> spearExplain(otto) }
            spear == BarbarianProgress.SPEAR_TAUGHT -> Topic(SPEAR_ASK) { otto -> spearWaiting(otto) }
            hasta == 0 -> Topic(SPEAR_ASK) { otto -> hastaExplain(otto) }
            hasta == BarbarianProgress.HASTA_TAUGHT -> Topic(HASTA_ASK) { otto -> hastaWaiting(otto) }
            else -> Topic(HASTA_ASK) { otto -> hastaDone(otto) }
        }
    }

    private fun herbTopic(p: PlayerRef): Topic? {
        val herb = p.varbit(Varbits.BRUT_HERB_POTION)
        return when {
            p.varbit(Varbits.BRUT_FISHING_R) < BarbarianProgress.ROD_ACKNOWLEDGED || herb >= BarbarianProgress.HERB_DONE -> null
            herb == 0 -> Topic(HERB_ASK) { otto -> herbExplain(otto) }
            p.inv(Invs.INV).total(BarbarianData.ATTACK_MIX) > 0 -> Topic("I've made a barbarian potion!") { otto -> herbHandIn(otto) }
            else -> Topic(HERB_ASK) { otto -> herbWaiting(otto) }
        }
    }

    // ---- fishing

    private suspend fun PlayerScript.rodExplain(otto: NpcRef) {
        npc(
            otto,
            "While you civilised folk use small, weak fishing rods, we barbarians are skilled with heavier tackle. We fish in the " +
                "lake nearby.",
        )
        me("So can you teach me of your methods?")
        npc(
            otto,
            "Certainly. Take the rod from under my bed and fish in the lake. When you have caught a few fish, I am sure you will be " +
                "ready to talk more with me.",
        )
        npc(otto, "You will know when you are ready, since inspiration will fill your mind.")
        me("So I can obtain new foods from these fishing spots?")
        npc(otto, "We do not use these fish quite as you might expect. When you return we can speak more of this matter.")
        player.setVarbit(Varbits.BRUT_FISHING_R, BarbarianProgress.ROD_TAUGHT)
    }

    private suspend fun PlayerScript.rodWaiting(otto: NpcRef) {
        npc(
            otto,
            "Alas, I do not sense that you have been successful in your fishing yet. The look in your eyes is not that of the osprey.",
        )
        me("Osprey?")
        npc(otto, "Legendary birds, which the ignorant call eagles. They prey upon fish. Do as they do to gain inspiration.")
    }

    private suspend fun PlayerScript.rodDone(otto: NpcRef) {
        npc(otto, "Your mind is as clear as the waters you have fished. This is good.")
        me("So what do I do with these fish? They are all bloated and so nasty looking that even a starving vulture wouldn't eat one.")
        npc(
            otto,
            "Patience young one. These are fish which are fat with eggs rather than fat of flesh. It is these eggs that are the thing " +
                "to make use of.",
        )
        player.setVarbit(Varbits.BRUT_FISHING_R, BarbarianProgress.ROD_ACKNOWLEDGED)
    }

    // ---- firemaking

    private suspend fun PlayerScript.fireExplain(otto: NpcRef) {
        npc(otto, "The first point in your progression is that of lighting fires without a tinderbox.")
        me("That sounds pretty useful. Tell me more.")
        npc(
            otto,
            "For this process you will require a strung bow. You use the bow to quickly rotate pieces of wood against one another. As " +
                "you rub the wood, it becomes hot, eventually springing into flame.",
        )
        me("No more secret details?")
        npc(
            otto,
            "The spirits will aid you. The power they supply will guide your hands. Go and benefit from their guidance upon oak logs.",
        )
        player.setVarbit(Varbits.BRUT_FIRE, BarbarianProgress.FIRE_TAUGHT)
    }

    private suspend fun PlayerScript.fireWaiting(otto: NpcRef) {
        npc(otto, "By now you know my response.")
        me("Go and light some oak logs using a strung bow?")
        npc(otto, "Exactly.")
    }

    private suspend fun PlayerScript.fireDone(otto: NpcRef) {
        npc(otto, "Fine news indeed!")
        player.setVarbit(Varbits.BRUT_FIRE, BarbarianProgress.FIRE_ACKNOWLEDGED)
    }

    /** The pyre ship lesson. The pyre ships themselves are not implemented (OPEN-BT3). */
    private suspend fun PlayerScript.pyreExplain(otto: NpcRef) {
        npc(
            otto,
            "The next stage is quite complex, so listen well. In order to send our ancestors into the spirit world, their mortal " +
                "remains must be burned with due ceremony.",
        )
        npc(
            otto,
            "This can only be done close to the water on the shore of the lake, just to our north-east. You will recognise the " +
                "correct places by the ashes to be seen there.",
        )
        npc(
            otto,
            "You will need to construct a small ship by using an axe upon logs in this area, then add the bones of a long dead " +
                "barbarian hero.",
        )
        me("Where do I obtain these bones?")
        npc(
            otto,
            "From the caverns beneath this lake. Many of our ancestors travelled to these caverns in order to hunt for glory, but " +
                "found only death. Their bones must still lie inside, their spirits trapped in torment.",
        )
        me("So I make sure I am in the right place, then throw these bones on a ceremonial ship before I set light to it?")
        npc(
            otto,
            "Correct. The spirit will ascend to glory while the pyre will send the earthly remains to the depths. You will also " +
                "obtain a closer link to the spirit world.",
        )
        npc(
            otto,
            "During this heightened contact, any bones you bury will have increased importance to the gods. The number of bones that " +
                "may be buried before the link fades will depend on the wood used.",
        )
        me("Is there anything else I should know?")
        npc(
            otto,
            "I have little knowledge of the caverns - they are blocked from the sight of the spirits with whom I commune. I can only " +
                "suspect that whatever slew the barbarian heroes is indeed mighty.",
        )
        npc(otto, "I also suspect that the bones you seek might well be very uncommon, since heroes are not found in vast numbers.")
        me("How do I enter the caverns?")
        npc(
            otto,
            "Dive into the whirlpool in the lake to the east. The spirits will use their abilities to ensure you arrive in the " +
                "correct location. Be warned, their influence fades, so you must find your own way out.",
        )
    }

    // ---- smithing

    private suspend fun PlayerScript.spearExplain(otto: NpcRef) {
        if (ENFORCE_TBWT && player.varp(TBWT_VARP) < TBWT_DONE) {
            npc(
                otto,
                "The next step is to manufacture a spear, suitable for combat. To do so, you must speak with our distant cousins on " +
                    "Karamja, in the village of Tai Bwo Wannai.",
            )
            me("Couldn't you just take a bribe or something? I am sure you could do with some cash.")
            npc(
                otto,
                "I am afraid this is a vital step. The spirits foresee that your understanding of spears will increase through your " +
                    "journey there. You may not progress in the use of spears until you have completed this mission.",
            )
            return
        }
        npc(otto, "Many warriors complain that spears are difficult to find, we barbarians thus forge our own.")
        me("Can I just use some metal on an anvil, or is there something else involved?")
        npc(
            otto,
            "If you use our special barbarian anvil here, you will find it ideal. Other anvils are not sturdy enough or shaped " +
                "appropriately for the forging work involved. Make any of our spears and return.",
        )
        npc(
            otto,
            "Note well that you will require wood for the spear shafts; the quality of wood must be similar to that of the metal involved.",
        )
        player.setVarbit(Varbits.BRUT_SMITH_SPEAR, BarbarianProgress.SPEAR_TAUGHT)
    }

    private suspend fun PlayerScript.spearWaiting(otto: NpcRef) {
        npc(otto, "You do not exude the presence of one who has poured his soul into manufacturing spears.")
        me("The spirits told you?")
        npc(otto, "Indeed they did. There are no secrets from those who live eternally.")
    }

    private suspend fun PlayerScript.hastaExplain(otto: NpcRef) {
        npc(
            otto,
            "The manufacture of spears is now yours as a speciality. Use your skill well. In addition I am ready to reveal " +
                "one-handed spear crafts.",
        )
        me("This sounds interesting indeed.")
        npc(
            otto,
            "The next step is to manufacture a one-handed version of a spear, suitable for combat. Such a spear is known to us as a hasta.",
        )
        me("Can't I just pick one up and use it in one hand?")
        npc(
            otto,
            "As you might suspect, our ways require greater understanding than is gained by simply looking at a weapon. It is also " +
                "the case that the process involves a differently balanced spear. Before you may use such a weapon in anger, you must " +
                "make an example. Only then will you fully understand the poise and techniques involved.",
        )
        me("So I must manufacture one? I see. This makes sense.")
        npc(
            otto,
            "Indeed. You may use our special anvil for this spear type too. The ways of black and dragon spears are beyond our " +
                "knowledge, however; these spears may not be created.",
        )
        player.setVarbit(Varbits.BRUT_SMITH_HASTA, BarbarianProgress.HASTA_TAUGHT)
    }

    private suspend fun PlayerScript.hastaWaiting(otto: NpcRef) {
        npc(
            otto,
            "Take some wood and metal and make a spear upon the nearby anvil, then you may return to me. As an example, you may use " +
                "bronze bars with normal logs or iron bars with oak logs. If you perform this task I will be freed from the spirits " +
                "complaining of your lack of dedication.",
        )
        me("I'll be back.")
    }

    private suspend fun PlayerScript.hastaDone(otto: NpcRef) {
        npc(otto, "I see you have constructed your hasta, and are approaching readiness.")
        me("If I may ask, readiness for what?")
        npc(otto, "To live life to it's fullest of course - that you may be a peaceful spirit when your time ends.")
    }

    // ---- herblore

    private suspend fun PlayerScript.herbExplain(otto: NpcRef) {
        npc(
            otto,
            "The secret is linked to the fish in the lake nearby. If you use your knife upon them, several new fishy items will be " +
                "produced. Fish parts can be used as bait, but the roe and caviar is more useful for us.",
        )
        me("Do I cook them?")
        npc(
            otto,
            "Certainly not. Mixing these items with two dose potions is what should be done. This results in a nutritious slop, " +
                "perfect for healing as well as imparting the effect of the potion.",
        )
        npc(
            otto,
            "Roe can only be used for some of the more easily combined mixes, while caviar may be used for any of the mixes of which " +
                "I am aware.",
        )
        me("What if I only have four dose potions?")
        npc(
            otto,
            "You are able to decant a four dose potion into an empty vial, thus giving two potions of two doses. This will aid you " +
                "in the process.",
        )
        me("I guess you just want me to go away and make one of these potions?")
        npc(
            otto,
            "Have I become so predictable? But yes, I do indeed require a potion. It is of the highest importance that you bring me " +
                "a lesser attack potion combined with fish roe.",
        )
        player.setVarbit(Varbits.BRUT_HERB_POTION, BarbarianProgress.HERB_TAUGHT)
    }

    private suspend fun PlayerScript.herbWaiting(otto: NpcRef) {
        npc(otto, "Do you have my potion?")
        me("What was it you needed again?")
        npc(otto, "Bring me a lesser attack potion combined with fish roe. There is more importance in this than you will ever know.")
    }

    /** The attack mix is taken and the training is complete (Wiki: "hand it back to Otto. This will fully complete the training"). */
    private suspend fun PlayerScript.herbHandIn(otto: NpcRef) {
        npc(otto, "I will take that off your hands now. I will say no more than that I am eternally grateful.")
        if (!player.transaction("barbarian herblore") { remove(Invs.INV, BarbarianData.ATTACK_MIX, 1) }) return
        player.setVarbit(Varbits.BRUT_HERB_POTION, BarbarianProgress.HERB_DONE)
        me("I feel I am missing some vital information about your need for this potion, though I often have this suspicion.")
        npc(otto, "I will not reveal all of my private matters to you. Some secrets are best kept rather than revealed.")
    }

    private const val TITLE = "Select an Option"
    private const val THINK_SO = "You think so?"
    private const val WHO_ARE_YOU = "Who are you and why do you live all the way out here?"
    private const val NOT_RELATED = "I really don't think I'm related to any barbarian."
    private const val TOO_BUSY = "Sorry, I'm too busy to talk about genealogy today."
    private const val ROD_ASK = "What can I learn about the use of a fishing rod?"
    private const val FIRE_ASK = "I'm ready for your firemaking wisdom. Please instruct me."
    private const val SPEAR_ASK = "Tell me more about the use of spears."
    private const val HASTA_ASK = "What of the one-handed spears of which you spoke?"
    private const val HERB_ASK = "What was that secret knowledge of herblore we talked of?"
    private const val MORE = "I seek more knowledge."
    private const val NO_MORE = "I have no more questions at this time."
    private const val GOING = "I'd better get going."
}
