@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.EventBus
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * The scrying orb of Enter the Abyss (docs/mechanics/quest-enter-the-abyss.md EA2): teleporting to the Rune Essence
 * Mine with the
 * empty orb in the backpack records the origin in the cache's `rcu_essencespot_*` varbits; three different origins
 * fill it.
 * The readings are kept when the orb is lost, so a replacement orb fills on the next teleport (Wiki "Scrying orb").
 */
internal object AbyssOrb {
    /** Wiki "Enter the Abyss": travel to the mine "from three different locations" fills the orb. */
    const val SPOTS_NEEDED = 3

    fun spots(p: PlayerRef): Int = EssenceTeleporter.entries.count { p.varbit(it.spotVarbit) == 1 }

    /** Called by every essence mine teleport: records the origin and fills an empty orb that has three readings. */
    fun charge(p: PlayerRef, from: EssenceTeleporter) {
        if (Quests.ENTER_THE_ABYSS.stage(p) != STAGE_ORB) return
        if (!p.has(Objs.SCRYING_ORB_EMPTY) && !p.has(Objs.SCRYING_ORB_FULL)) return
        p.setVarbit(from.spotVarbit, 1)
        if (p.has(Objs.SCRYING_ORB_EMPTY) && spots(p) >= SPOTS_NEEDED) {
            val inv = p.inv(Invs.INV)
            val slot = (0 until inv.size).firstOrNull { inv.obj(it) == Objs.SCRYING_ORB_EMPTY } ?: return
            p.transaction("scrying orb") { replaceAt(Invs.INV, slot, Objs.SCRYING_ORB_EMPTY, Objs.SCRYING_ORB_FULL) }
            p.message("The scrying orb is filled with the energy of the teleport.")
        } else {
            p.message("The scrying orb absorbs some of the energy of the teleport.")
        }
    }

    /** Stages of varp 492 (OPEN-EA1: only 0 and 4 are cache values; the Varrock mage's multi-NPC shows from 1). */
    const val STAGE_TOLD = 1
    const val STAGE_ORB = 2
    const val STAGE_DONE = 4
}

/**
 * Enter the Abyss (docs/mechanics/quest-enter-the-abyss.md): the Mage of Zamorak in the Wilderness sends the player
 * to the Zamorakian
 * chapel in Varrock, where he hands over a scrying orb; three Rune Essence Mine teleports from different wizards
 * fill it, and he
 * pays 1,000 Runecraft xp, an abyssal book and a small pouch. The Abyss teleport itself is `content/skills`
 * `RunecraftAbyssContent`,
 * which reads varp 492 = 4. The Wiki transcript "Transcript:Enter the Abyss" is the dialogue.
 */
public class EnterTheAbyss : ContentPlugin {
    private lateinit var bus: EventBus

    override fun ContentScope.register() {
        bus = events
        onOpNpc(Npcs.RCU_ZAMMY_MAGE1, OP_TALK) { mage ->
            player.stopInteraction()
            wildernessTalk(mage)
        }
        onOpNpc(Npcs.RCU_ZAMMY_MAGE1_EDGE, OP_TALK) { mage ->
            player.stopInteraction()
            varrockTalk(mage)
        }
        // Wiki "Mage of Zamorak": the second one stands at the Varrock chaos altar (map 3259,3386 r=5).
        onWorldStart { spawnNpc(Npcs.RCU_ZAMMY_MAGE1_EDGE, CoordGrid(VARROCK_X, VARROCK_Z, 0), VARROCK_WANDER) }
        events.on<JournalRequest> {
            if (it.quest == Quests.ENTER_THE_ABYSS) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        when {
            stage == 0 -> {
                listOf(
                    "I can start this miniquest by speaking to the Mage of Zamorak in the Wilderness, near " + "the end of the River Lum.",
                )
            }

            stage == AbyssOrb.STAGE_TOLD -> {
                listOf("The Mage of Zamorak wants to talk in the chaos temple in south-east Varrock.")
            }

            stage < AbyssOrb.STAGE_DONE -> {
                listOf(
                    "The Mage of Zamorak gave me a scrying orb. I must teleport to the Rune Essence Mine from " +
                        "three different wizards",
                    "(Aubury, Archmage Sedridor, Wizard Cromperty, Brimstail or Wizard Distentor), then show the " +
                        "orb to him.",
                )
            }

            else -> {
                listOf("I helped the Zamorak Magical Institute reach the Rune Essence Mine, and they gave me access to the Abyss.")
            }
        }

    // ---- the Wilderness mage

    private suspend fun PlayerScript.wildernessTalk(mage: NpcRef) {
        val p = player
        val stage = Quests.ENTER_THE_ABYSS.stage(p)
        if (stage >= AbyssOrb.STAGE_DONE) return afterQuest(mage)
        if (stage == 0) {
            npc(
                mage,
                "If you want to talk, this isn't the place for it. Meet me in Varrock's Chaos Temple, " +
                    "by the rune shop. Unless you're here to buy something?",
            )
            Quests.ENTER_THE_ABYSS.advance(p, AbyssOrb.STAGE_TOLD)
        } else {
            npc(mage, "I already told you to meet me in Varrock's Chaos Temple, by the rune shop. Unless you're here to buy something?")
        }
        if (choice("Select an option", "Let's see what you're selling.", "Alright, I'll go.") == 1) {
            me("Let's see what you're selling.")
            bus.publish(OpenShopRequest(p, p.seenType(mage)))
        } else {
            me("Alright, I'll go.")
        }
    }

    // ---- the Varrock mage

    private suspend fun PlayerScript.varrockTalk(mage: NpcRef) {
        val p = player
        when (Quests.ENTER_THE_ABYSS.stage(p)) {
            AbyssOrb.STAGE_TOLD -> if (p.flag(RECONSIDER)) reconsider(mage) else firstMeeting(mage)
            AbyssOrb.STAGE_ORB -> orbMeeting(mage)
            else -> afterQuest(mage)
        }
    }

    private suspend fun PlayerScript.firstMeeting(mage: NpcRef) {
        npc(mage, "Ah, you again. The Wilderness is hardly the appropriate place for a conversation now, is it? What was it you wanted?")
        me("Err... I didn't really want anything.")
        npc(mage, "So why did you approach me?")
        me("I was just wondering why you sell runes in the Wilderness?")
        npc(
            mage,
            "Well I can't go doing it in the middle of Varrock, can I? In case you hadn't noticed, I'm a servant " +
                "of Zamorak. The " +
                "Saradominists have made sure that people like me are not welcome in these parts.",
        )
        if (choice("Select an option", "Where do you get your runes from?", "Interesting. Thanks for the information.") != 1) {
            return me("Interesting. Thanks for the information.")
        }
        me("Where do you get your runes from?")
        npc(mage, "Well we craft them of course.")
        me("We?")
        npc(mage, "My associates and I. Despite the best attempts of the Saradominists, there's still more of us around than they'd like.")
        me("I can't imagine they like you crafting runes much. Do they not try and stop you?")
        npc(mage, "Ha! I'm sure they'd love to, but we have methods of runecrafting that they can only dream of!")
        me("Care to share?")
        npc(
            mage,
            "Why would I? You are not a member of our institute. How do I know you won't just go and share all of " +
                "our secrets with " +
                "those Saradominist fools in the Order of Wizards.",
        )
        when (
            choice(
                "Select an option",
                "Maybe I could make it worth your while?",
                "But I'm a loyal servant of Zamorak as well!",
                "You're right. I'm a faithful follower of Saradomin.",
                "Actually, I'm not interested.",
            )
        ) {
            1 -> {
                worthYourWhile(mage)
            }

            2 -> {
                loyalServant(mage)
            }

            3 -> {
                me("You're right. I'm a faithful follower of Saradomin.")
                npc(mage, "Then you have no place here! Leave, before I make you!")
            }

            else -> {
                me("Actually, I'm not interested.")
                npc(mage, "Then you should be gone, for I am very busy.")
            }
        }
    }

    private suspend fun PlayerScript.worthYourWhile(mage: NpcRef) {
        me("Maybe I could make it worth your while?")
        npc(mage, "How? What do you have to offer?")
        me("Well what is it you want?")
        accessSpeech(mage)
        when (
            choice(
                "Select an option",
                "Yes, but I can still help you as well.",
                "I did it so that I could then steal their secrets.",
                "Sorry, I just remembered that I have to take my pet rat for a walk.",
            )
        ) {
            1 -> {
                me("Yes, but I can still help you as well.")
                npc(
                    mage,
                    "So you're a mercenary with no allegiance? Not the type I like working with, but if you have " +
                        "knowledge of the Rune " +
                        "Essence Mine, I seemingly have little choice.",
                    "Alright, if you help us access the Rune Essence Mine, we will share our runecrafting secrets " +
                        "with you in return.",
                )
                dealMenu(mage)
            }

            2 -> {
                stealSecrets(mage)
            }

            else -> {
                petRat(mage)
            }
        }
    }

    /** From "Until recently, our runecrafting secrets..." to "Err..." (shared by the two ways into the offer). */
    private suspend fun PlayerScript.accessSpeech(mage: NpcRef) {
        npc(
            mage,
            "Until recently, our runecrafting secrets allowed us to produce runes at a far superior rate compared " +
                "to the inept Order " +
                "of Wizards, but something has changed.",
            "From what we can gather, they've somehow rediscovered how to access the lost Rune Essence Mine.",
        )
        me("Ah, well I know all about that. I was actually the one to help them do it!")
        npc(mage, "You did what? You helped the Order of Wizards?")
        me("Err...")
    }

    private suspend fun PlayerScript.stealSecrets(mage: NpcRef) {
        me("I did it so that I could then steal their secrets.")
        npc(
            mage,
            "You did? Perhaps I underestimated you.",
            "Alright, if you help us access the Rune Essence Mine, we will share our runecrafting secrets with " +
                "you in return.",
        )
        dealMenu(mage)
    }

    private suspend fun PlayerScript.petRat(mage: NpcRef) {
        me("Sorry, I just remembered that I have to take my pet rat for a walk.")
        npc(mage, "What?")
        me("Yup! Got to go!")
    }

    private suspend fun PlayerScript.loyalServant(mage: NpcRef) {
        me("But I'm a loyal servant of Zamorak as well!")
        npc(
            mage,
            "Even if you speak the truth, it takes more than just being a follower of Zamorak to gain the secrets " +
                "of the institute. " +
                "You would need to offer something in return.",
        )
        me("Like what?")
        accessSpeech(mage)
        // The Wiki's "You did what?" is followed by "I thought you claimed to be a servant of Zamorak?" here.
        when (
            choice(
                "Select an option",
                "I did it so that I could then steal their secrets.",
                "Okay, fine. I don't really serve Zamorak.",
                "Sorry, I just remembered that I have to take my pet rat for a walk.",
            )
        ) {
            1 -> {
                stealSecrets(mage)
            }

            2 -> {
                me("Okay, fine. I don't really serve Zamorak.")
                npc(mage, "Then give me one good reason why I shouldn't have you teleported into the depths of a volcano!")
                if (choice(
                        "Select an option",
                        "Because I can still help you.",
                        "Alright, I'll leave. Just don't go teleporting me anywhere.",
                    ) ==
                    1
                ) {
                    me("Because I can still help you.")
                    npc(
                        mage,
                        "You would help both sides for your own gain? I suppose as a Zamorakian, I can respect " +
                            "that, even if I don't " +
                            "like it.",
                        "Alright, if you help us access the Rune Essence Mine, we will share our runecrafting " +
                            "secrets with you in return.",
                    )
                    dealMenu(mage)
                } else {
                    me("Alright, I'll leave. Just don't go teleporting me anywhere.")
                    npc(mage, "Be glad that I am merciful! Now go!")
                }
            }

            else -> {
                petRat(mage)
            }
        }
    }

    private suspend fun PlayerScript.reconsider(mage: NpcRef) {
        npc(
            mage,
            "You again. Have you considered my offer? If you help us access the Rune Essence Mine, we will share " +
                "our runecrafting " +
                "secrets with you in return.",
        )
        dealMenu(mage)
    }

    private suspend fun PlayerScript.dealMenu(mage: NpcRef) {
        val p = player
        when (choice("Select an option", "Deal.", "No deal.", "I need to think about it.")) {
            1 -> {
                me("Deal.")
                npc(mage, "Good. Now, all I need from you is the spell that will teleport me to the Rune Essence Mine.")
                me("Err... I don't actually know the spell.")
                npc(mage, "What? Then how do you get there.")
                me("Oh, well the people who do know the spell just teleport me there directly.")
                npc(mage, "Hmm... I see. That makes this slightly more complex, but no matter. You can still help us.")
                me("How?")
                npc(
                    mage,
                    "I'll give you a scrying orb with a standard cypher spell cast upon it. The orb will absorb " +
                        "mystical energies that " +
                        "it is exposed to.",
                    "If you teleport to the Rune Essence Mine from three different locations, the orb will " +
                        "absorb the energies of the spell " +
                        "and allow us to reverse-engineer the magic behind it.",
                    "Do you know of three different people who can teleport you there?",
                )
                me("Maybe?")
                npc(mage, "Well if not, I'm sure one of those fools in the Order of Wizards can tell you. Now, here's the orb.")
                if (p.give(Objs.SCRYING_ORB_EMPTY, 1, "scrying orb")) {
                    Quests.ENTER_THE_ABYSS.advance(p, AbyssOrb.STAGE_ORB)
                    p.setFlag(RECONSIDER, false)
                    itemBox(Objs.SCRYING_ORB_EMPTY, "The Mage of Zamorak hands you an orb.")
                } else {
                    p.setFlag(RECONSIDER, true)
                    itemBox(
                        Objs.SCRYING_ORB_EMPTY,
                        "The Mage of Zamorak tries to hand you an orb, but you don't have enough room to take " + "it.",
                    )
                }
            }

            2 -> {
                me("No deal.")
                npc(mage, "Fine. I will find another way.")
            }

            else -> {
                me("I need to think about it.")
                npc(mage, "I will be here once you have decided.")
                p.setFlag(RECONSIDER, true)
            }
        }
    }

    private suspend fun PlayerScript.orbMeeting(mage: NpcRef) {
        val p = player
        npc(mage, "You again. Have you managed to use that scrying orb to obtain the information I need?")
        when {
            p.has(Objs.SCRYING_ORB_FULL) -> {
                me("Here you go.")
                p.take(Objs.SCRYING_ORB_FULL, 1, "scrying orb")
                itemBox(Objs.SCRYING_ORB_FULL, "You hand the orb to the Mage of Zamorak.")
                npc(
                    mage,
                    "Right, let's take a look at this orb...",
                    "Yes, this will do nicely. Once again, the Zamorak Magical Institute has overcome the Order " +
                        "of Wizards!",
                    "You have done well. Now, time for us to uphold our end of the bargin.",
                    "The reason we are able to craft so many runes is because we do not visit the runic altars in " +
                        "the traditional way. " +
                        "Instead, we have found a way to teleport to them directly.",
                )
                me("How?")
                npc(
                    mage,
                    "Via another plane known as the Abyss. It is a complex place that cannot be easily explained, " +
                        "but I will share our " +
                        "research notes with you so you may better understand it.",
                )
                me("So can I use the Abyss?")
                npc(
                    mage,
                    "Yes. Visit me in the Wilderness whenever you wish to be teleported there. Just be " +
                        "careful, for it is a dangerous place. " +
                        "Still, I'm sure you'll agree that the risk is worth the reward.",
                )
                me("How is it dangerous?")
                npc(
                    mage,
                    "There are creatures there that will hunt and attack any visitors on sight. The magic " +
                        "we use for teleporting there can " +
                        "also be a bit... unstable.",
                )
                me("What do you mean?")
                npc(
                    mage,
                    "Just don't expect to be using any prayers in there.",
                    "Anyway, you may also have this pouch as well. I'm sure you will find it useful. Now, we're " +
                        "done here.",
                )
                itemBox(Objs.RCU_INSTRUCTION_BOOK, "The Mage of Zamorak hands you a book and a pouch.")
                moreAbout(mage)
                QuestCompletion.complete(p, Quests.ENTER_THE_ABYSS, REWARD)
            }

            p.has(Objs.SCRYING_ORB_EMPTY) -> {
                me("Not yet.")
                npc(
                    mage,
                    "You must carry it with you and teleport to the Rune Essence Mine from three different " +
                        "locations. Return to me once " +
                        "you have done so.",
                )
            }

            p.inv(Invs.BANK).total(Objs.SCRYING_ORB_FULL) > 0 -> {
                me("Yes, but it's in my bank.")
                npc(mage, "Well it is of no use there. Bring it to me.")
            }

            else -> {
                me("I lost it. Could I have another?")
                npc(mage, "Fool! Take this, and don't lose it this time!")
                if (p.give(Objs.SCRYING_ORB_EMPTY, 1, "scrying orb")) {
                    itemBox(Objs.SCRYING_ORB_EMPTY, "The Mage of Zamorak hands you an orb.")
                } else {
                    p.message("You don't have enough inventory space.")
                }
            }
        }
    }

    // ---- after the miniquest (also the Temple of the Eye options, TempleOfTheEye.mageOptions)

    private suspend fun PlayerScript.afterQuest(mage: NpcRef) {
        npc(mage, "Ah, you again. What do you want?")
        while (true) {
            val extra = TempleOfTheEye.mageOptions(player)
            val labels =
                extra.map { it.first } +
                    listOf(
                        "Can you tell me more about the Abyss?",
                        "Can you tell me more about your group?",
                        "Nothing. I'm just looking around.",
                    )
            val pick = choice("Select an option", *labels.toTypedArray())
            when {
                pick <= extra.size -> {
                    extra[pick - 1].second(this, mage)
                    return
                }

                pick == extra.size + 1 -> {
                    abyssInfo(mage)
                }

                pick == extra.size + 2 -> {
                    groupInfo(mage)
                }

                else -> {
                    me("Nothing. I'm just looking around.")
                    return
                }
            }
        }
    }

    /** After the reward: the two information branches of the Wiki transcript and "I'd better be off.". */
    private suspend fun PlayerScript.moreAbout(mage: NpcRef) {
        while (true) {
            when (
                choice(
                    "Select an option",
                    "Can you tell me more about the Abyss?",
                    "Can you tell me more about your group?",
                    "I'd better be off.",
                )
            ) {
                1 -> abyssInfo(mage)
                2 -> groupInfo(mage)
                else -> return me("I'd better be off.")
            }
        }
    }

    private suspend fun PlayerScript.abyssInfo(mage: NpcRef) {
        me("Can you tell me more about the Abyss?")
        npc(
            mage,
            "It is a hard place to describe. We often refer to it as another plane, but that isn't quite " +
                "accurate. If anything, it is " +
                "more like a plane that sits between all other planes.",
        )
        me("Right... And what does it have to do with runecrafting?")
        npc(
            mage,
            "In truth, nothing at all. However, it has everything to do with teleportation. You " +
                "see, time and space work differently in the Abyss.",
        )
        npc(
            mage,
            "For example, you can travel to the Abyss, walk a few steps and then exit, only to find yourself " +
                "somewhere completely " +
                "different from where you entered. You might even find yourself on a different plane.",
            "You can also enter the Abyss and spend days there, but on your return, only moments have passed.",
            "This is why the Abyss is so useful. It can be used as an effective hub for " +
                "teleportation. In fact, all teleportation that we " +
                "know of uses the Abyss, you just don't realise it.",
        )
        me("Wait... So when I teleport, I'm actually travelling through the Abyss?")
        npc(
            mage,
            "In some ways, I suppose you are. Whenever you use a teleport spell, that spell is actually " +
                "travelling through the Abyss and " +
                "using it to link two places together.",
        )
        me("So did you discover the Abyss?")
        npc(
            mage,
            "No. Knowledge of the Abyss has existed for a long time, hence it being used for " +
                "teleportation. However, we believe we may be " +
                "the first to have properly gained access in centuries.",
        )
        me("How did you manage it?")
        npc(
            mage,
            "By complete accident. One of our initiates was performing a routine teleportation experiment when " +
                "something went wrong. " +
                "Instead of ending up at their destination, they found themselves in the Abyss.",
            "Now, did you want something else?",
        )
    }

    private suspend fun PlayerScript.groupInfo(mage: NpcRef) {
        me("Can you tell me more about your group?")
        npc(
            mage,
            "I suppose you have proven yourself trustworthy. We are a group of mages in service to Zamorak. Our " +
                "group is called the " +
                "Zamorak Magical Institute, or Z.M.I. for short.",
            "Few actually know of us. Saradominist groups like the Order of Wizards hold sway over " +
                "these lands, so we are forced to work in " +
                "the shadows. However, make no mistake, our power far exceeds theirs.",
        )
        me("You don't seem to like the Order of Wizards very much.")
        npc(
            mage,
            "And why would we? Did you know that the Order of Wizards wasn't always a Saradominist group? Once " +
                "they allowed mages of all " +
                "faiths to study with them, but they became greedy.",
        )
        me("What happened?")
        npc(
            mage,
            "They started to desire more control. They banned Zamorakian wizards from certain areas of study, " +
                "claiming them to be too " +
                "dangerous. In reality, they just wanted that knowledge for themselves.",
            "It all went wrong for them, of course. A Zamorakian wizard made a great breakthrough in " +
                "teleportation magic, but the " +
                "Saradominists stole his research.",
            "They used it to perform a ritual, but their lack of understanding caused it to go wrong. The entire " +
                "Wizards' Tower burnt " +
                "down. Many were killed, and years of research was destroyed.",
            "Naturally, they blamed the Zamorakians. Claimed we intentionally burnt the tower down. After the " +
                "tower was rebuilt, we were " +
                "banished from the Order of Wizards and forced underground.",
        )
        me("And that's how the Z.M.I. was formed?")
        npc(mage, "Exactly.", "Now, did you want something else?")
    }

    private companion object {
        const val OP_TALK = 1

        /** Wiki "Mage of Zamorak": Varrock chaos altar map 3259,3386 r=5. */
        const val VARROCK_X = 3259
        const val VARROCK_Z = 3386
        const val VARROCK_WANDER = 5

        /**
         * Account flag: the player asked for time to think about the offer (the cache varbit
         * `abyssal_miniquest_reconsider` holds the same
         * fact).
         */
        const val RECONSIDER = "quests.abyss.reconsider"

        /**
         * Wiki "Enter the Abyss" Rewards: 1,000 Runecraft experience, an abyssal book and a small pouch (3 essence).
         */
        val REWARD =
            QuestReward(
                model = Objs.SCRYING_ORB_FULL,
                xp = listOf(Stats.RUNECRAFT to 1000.0),
                items = listOf(Objs.RCU_INSTRUCTION_BOOK to 1, Objs.RCU_POUCH_SMALL to 1),
                notes = listOf("Access to the Abyss"),
            )
    }
}
