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
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Rune Mysteries (docs/mechanics/quest-rune-mysteries.md): Duke Horacio's air talisman goes to Archmage Sedridor, his research
 * package to Aubury in Varrock and Aubury's notes back to Sedridor. The quest variable is varp 63; the cache's multi-NPCs
 * Archmage Sedridor (5034) and Aubury (2886) read it too (value 6 = finished shows the Teleport option). Stages: 0 not started,
 * 1 talisman from the Duke, 2 talisman given to Sedridor, 3 package from Sedridor, 4 package given to Aubury, 5 notes from
 * Aubury, 6 finished (OPEN-QU1: only 0 and 6 are cache values). Dialogue is the Wiki "Transcript:Rune Mysteries".
 */
public class RuneMysteries : ContentPlugin {
    private lateinit var bus: EventBus

    override fun ContentScope.register() {
        bus = events
        onOpNpc(Npcs.DUKE_OF_LUMBRIDGE, OP_TALK) { duke ->
            player.stopInteraction()
            // Dragon Slayer I's anti-dragon shield talk while that quest is under way and this one is not mid-way.
            if (player.stage != 1 && dragonSlayerActive(player)) dukeDragonSlayer(duke) else dukeTalk(duke)
        }
        onOpNpc(Npcs.HEAD_WIZARD, OP_TALK) { sedridor ->
            player.stopInteraction()
            sedridorTalk(sedridor)
        }
        onOpNpc(Npcs.AUBURY, OP_TALK) { aubury ->
            player.stopInteraction()
            auburyTalk(aubury)
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.RUNE_MYSTERIES) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private fun journal(stage: Int): List<String> =
        when {
            stage == 0 -> {
                listOf("I can start this quest by speaking to Duke Horacio on the first floor of Lumbridge Castle.")
            }

            stage == 1 -> {
                listOf("Duke Horacio gave me an air talisman to take to Archmage Sedridor in the Wizards' Tower.")
            }

            stage == 2 -> {
                listOf("Archmage Sedridor is studying the talisman. He wants me to take his research to Aubury in Varrock.")
            }

            stage == 3 -> {
                listOf("I have a research package from Archmage Sedridor to deliver to Aubury in Varrock.")
            }

            stage < FINISHED -> {
                listOf("Aubury gave me research notes to take back to Archmage Sedridor in the Wizards' Tower.")
            }

            else -> {
                listOf(
                    "I took the air talisman to Archmage Sedridor, and Aubury's notes helped rediscover the teleport to the " +
                        "essence mine.",
                )
            }
        }

    private val PlayerRef.stage: Int get() = Quests.RUNE_MYSTERIES.stage(this)

    // ---- Duke Horacio

    private suspend fun PlayerScript.dukeTalk(duke: NpcRef) {
        val p = player
        npc(duke, "Greetings. Welcome to my castle.")
        val stage = p.stage
        val first = if (stage == 1) "What did you want me to do again?" else "Have you any quests for me?"
        if (choice("Select an option", first, "Where can I find money?") == 2) {
            me("Where can I find money?")
            npc(duke, "I've heard that the blacksmiths are prosperous amongst the peasantry. Maybe you could try your hand at that?")
            return
        }
        me(first)
        when {
            stage == 0 -> {
                startQuest(duke)
            }

            stage == 1 && p.has(Objs.AIR_TALISMAN) -> {
                npc(
                    duke,
                    "Take that talisman I gave you to Sedridor at the Wizards' Tower. You'll find it south west of here, across " +
                        "the bridge from Draynor Village.",
                )
                me("Okay, will do.")
            }

            stage == 1 -> {
                npc(duke, "Did you take that talisman to Sedridor?")
                me("No, I lost it.")
                npc(
                    duke,
                    "Ah, well that explains things. One of my servants found it outside, and it seemed too much of a " +
                        "coincidence that another would suddenly show up.",
                    "Here, take it to the Wizards' Tower, south west of here. Please try not to lose it this time.",
                )
                p.giveOrDrop(Objs.AIR_TALISMAN, 1, "rune mysteries talisman")
                itemBox(Objs.AIR_TALISMAN, "The Duke hands you the talisman.")
            }

            stage < FINISHED -> {
                npc(duke, "The only job I had was the delivery of that talisman, so I'm afraid not.")
            }

            else -> {
                npc(duke, "No, all is well for me.")
            }
        }
    }

    private suspend fun PlayerScript.startQuest(duke: NpcRef) {
        npc(duke, "Well, I wouldn't describe it as a quest, but there is something I could use some help with.")
        me("What is it?")
        npc(duke, "We were recently sorting through some of the things stored down in the cellar, and we found this old talisman.")
        itemBox(Objs.AIR_TALISMAN, "The Duke shows you a talisman.")
        npc(
            duke,
            "The Order of Wizards over at the Wizards' Tower have been on the hunt for magical artefacts recently. I wonder if " +
                "this might be just the kind of thing they're after.",
            "Would you be willing to take it to them for me?",
        )
        if (choice("Start the Rune Mysteries quest?", "Yes.", "No.") != 1) {
            me("Not right now.")
            npc(duke, "As you wish. Hopefully I can find someone else to help.")
            return
        }
        me("Sure, no problem.")
        npc(
            duke,
            "Thank you very much. You'll find the Wizards' Tower south west of here, across the bridge from Draynor Village. " +
                "When you arrive, look for Sedridor. He is the Archmage of the wizards there.",
        )
        if (player.give(Objs.AIR_TALISMAN, 1, "rune mysteries talisman")) {
            Quests.RUNE_MYSTERIES.advance(player, TALISMAN)
            itemBox(Objs.AIR_TALISMAN, "The Duke hands you the talisman.")
        } else {
            player.message("You don't have enough inventory space.")
        }
    }

    // ---- Archmage Sedridor

    private suspend fun PlayerScript.sedridorTalk(sed: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(sed, "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of Wizards. How may I help you?")
                me("I'm just looking around.")
                npc(
                    sed,
                    "Well, take care adventurer. You stand on the ruins of the old destroyed Wizards' Tower. Strange and " +
                        "powerful magicks lurk here.",
                )
            }

            1 -> {
                firstMeeting(sed)
            }

            2 -> {
                if (p.flag(AGREED)) {
                    npc(
                        sed,
                        "Hello again, adventurer. Please, take this package of research notes to Aubury in Varrock. He runs a " +
                            "rune shop in the south east of the city.",
                    )
                    givePackage(sed)
                } else {
                    me("So is that talisman of any use to you?")
                    talismanExplained(sed)
                }
            }

            PACKAGE, DELIVERED -> {
                npc(sed, "Hello again, adventurer. Did you take that package to Aubury?")
                if (p.stage == PACKAGE && !p.has(Objs.RESEARCH_PACKAGE)) {
                    me("I lost it. Could I have another?")
                    npc(sed, "Well it's a good job I have copies of everything.")
                    givePackage(sed)
                } else {
                    me("Not yet.")
                    npc(sed, "He runs a rune shop in the south east of Varrock. Please deliver it to him soon.")
                }
            }

            NOTES -> {
                notes(sed)
            }

            else -> {
                afterQuest(sed)
            }
        }
    }

    private suspend fun PlayerScript.firstMeeting(sed: NpcRef) {
        val p = player
        if (p.flag(MET)) {
            npc(sed, "Welcome back, adventurer. Do you have that talisman now?")
            if (!p.has(Objs.AIR_TALISMAN)) {
                me("Not yet.")
                npc(sed, "Well come back when you have it.")
                return
            }
            me("Here you go.")
            return takeTalisman(sed)
        }
        npc(
            sed,
            "Welcome adventurer, to the world renowned Wizards' Tower, home to the Order of Wizards. We are the oldest and most " +
                "prestigious group of wizards around. Now, how may I help you?",
        )
        me("Are you Sedridor?")
        npc(sed, "Sedridor? What is it you want with him?")
        me("The Duke of Lumbridge sent me to find him. I have this talisman he found. He said Sedridor would be interested in it.")
        npc(sed, "Did he now? Well hand it over then, and we'll see what all the hubbub is about.")
        while (true) {
            when (choice("Select an option", "Okay, here you are.", "No, I'll only give it to Sedridor.")) {
                1 -> {
                    me("Okay, here you are.")
                    if (!p.has(Objs.AIR_TALISMAN)) {
                        npc(sed, "...")
                        me("...")
                        npc(sed, "Well?")
                        me("I don't seem to have it with me.")
                        npc(sed, "Hmm? You are a very odd person. Come back again when you have it.")
                        p.setFlag(MET, true)
                        return
                    }
                    return takeTalisman(sed)
                }

                2 -> {
                    me("No, I'll only give it to Sedridor.")
                    npc(sed, "Well good news, for I am Sedridor! Now, hand it over and let me have a proper look at it, hmm?")
                    when (choice("Select an option", "Okay, here you are.", "No, I don't think you are Sedridor.")) {
                        1 -> {
                            me("Okay, here you are.")
                            if (p.has(Objs.AIR_TALISMAN)) return takeTalisman(sed)
                            npc(sed, "...")
                            me("...")
                            npc(sed, "Well?")
                            me("I don't seem to have it with me.")
                            npc(sed, "Hmm? You are a very odd person. Come back again when you have it.")
                            p.setFlag(MET, true)
                            return
                        }

                        2 -> {
                            me("No, I don't think you are Sedridor.")
                            npc(
                                sed,
                                "Hmm... Well, I admire your caution adventurer. Perhaps I can prove myself? I will use my " +
                                    "mental powers to discover...",
                                "Your name is... $name!",
                            )
                            me("You're right! How did you know that?")
                            npc(
                                sed,
                                "Well I am the Archmage you know! You don't get to my position without learning a few tricks " +
                                    "along the way!",
                                "So now that I have proved myself to you, why don't you hand over that talisman, hmm?",
                            )
                            p.setFlag(DOUBTED, true)
                            me("Okay, here you are.")
                            if (p.has(Objs.AIR_TALISMAN)) return takeTalisman(sed)
                            npc(sed, "...")
                            me("...")
                            npc(sed, "Well?")
                            me("I don't seem to have it with me.")
                            npc(sed, "Hmm? You are a very odd person. Come back again when you have it.")
                            p.setFlag(MET, true)
                            return
                        }

                        else -> {
                            return
                        }
                    }
                }

                else -> {
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.takeTalisman(sed: NpcRef) {
        val p = player
        if (!p.take(Objs.AIR_TALISMAN, 1, "rune mysteries talisman")) return
        Quests.RUNE_MYSTERIES.advance(p, HANDED)
        itemBox(Objs.AIR_TALISMAN, "You hand the talisman to Sedridor.")
        npc(
            sed,
            "Hmm... Doesn't seem to be anything too special. Just a normal air talisman by the looks of things. Still, looks " +
                "can be deceiving. Let me take a closer look...",
        )
        itemBox(Objs.AIR_TALISMAN, "Sedridor murmurs some sort of incantation and the talisman glows slightly.")
        npc(
            sed,
            "How interesting... It would appear I spoke too soon. There's more to this talisman than meets the eye. In fact, it " +
                "may well be the last piece of the puzzle.",
        )
        me("Puzzle?")
        npc(sed, "Indeed! The lost legacy of the first tower. This talisman may in fact be key to finding the forgotten essence mine!")
        me("First tower? Forgotten essence mine? What are you on about?")
        npc(sed, "Ah, my apologies, adventurer. Allow me to fill you in.")
        talismanExplained(sed, fromHandover = true)
    }

    /** From "Select an option: Go ahead. / Actually, I'm not interested." to the request to visit Aubury. */
    private suspend fun PlayerScript.talismanExplained(sed: NpcRef, fromHandover: Boolean = false) {
        if (!fromHandover) {
            npc(
                sed,
                "Hmm... Doesn't seem to be anything too special. Just a normal air talisman by the looks of things. Still, " +
                    "looks can be deceiving. Let me take a closer look...",
            )
            itemBox(Objs.AIR_TALISMAN, "Sedridor murmurs some sort of incantation and the talisman glows slightly.")
            npc(
                sed,
                "How interesting... It would appear I spoke too soon. There's more to this talisman than meets the eye. In " +
                    "fact, it may well be the last piece of the puzzle.",
            )
            me("Puzzle?")
            npc(
                sed,
                "Indeed! The lost legacy of the first tower. This talisman may in fact be key to finding the forgotten essence " +
                    "mine!",
            )
            me("First tower? Forgotten essence mine? What are you on about?")
            npc(sed, "Ah, my apologies, adventurer. Allow me to fill you in.")
        }
        if (choice("Select an option", "Go ahead.", "Actually, I'm not interested.") == 1) {
            me("Go ahead.")
            npc(
                sed,
                "As you are likely aware, when we cast spells, we do so using the power of runes.",
                "These runes are crafted from a highly unique material, and then imbued with magical power from various runic " +
                    "altars. Different altars create different runes with different magical effects.",
                "The process of imbuing runes is called runecrafting. Legend has it that this was once a common art, but the " +
                    "secrets of how to do it were lost until just under two hundred years ago.",
                "The rediscovery of runecrafting had such a large impact on the world, that it marked the dawn of the Fifth " +
                    "Age. It also resulted in the birth of our order, and the construction of the first Wizards' Tower.",
            )
            me("If it was the first tower, I'm guessing it doesn't exist anymore? What happened?")
            npc(
                sed,
                "It was burnt down by traitorous members of our own order. They followed the evil god of chaos, Zamorak, and " +
                    "they wished to claim our magical discoveries in his name.",
                "When the tower burnt down, much was lost, including an important incantation. A spell that could be used to " +
                    "teleport to a hidden essence mine.",
            )
            me("The essence mine you mentioned earlier, I assume?")
            npc(
                sed,
                "Precisely. Rune essence is the material used to make runes, but it is incredibly rare. That essence mine was " +
                    "the only place it could be found that our order knew of.",
                "Since the incantation was lost, we have struggled to maintain our stocks of rune essence.",
                "There are seemingly those out there that still know where to find some, but while they have been willing to " +
                    "sell essence to us, they have refused to share knowledge on how to find it ourselves.",
            )
            me("I'm starting to see why this is so important. So you think this talisman can help you rediscover that incantation?")
            npc(
                sed,
                "I do! All magic leaves traces, and from what I can tell, this talisman was used heavily during the time of the " +
                    "first tower.",
                "It would have been taken to the essence mine many times, and the magical energies there will have left an " +
                    "imprint on it. To think that it was hidden in Lumbridge all this time!",
            )
            me("So what happens now?")
        } else {
            me("Actually, I'm not interested.")
            npc(
                sed,
                "Oh... Well I guess the short of it is that this talisman could be key to helping us rediscover an important " +
                    "teleportation incantation.",
                "With it, we'll be able to access a hidden essence mine, our lost source of rune essence.",
            )
        }
        npc(
            sed,
            "It is critical I share this discovery with my associate, Aubury, as soon as possible. He's not much of a wizard, " +
                "but he's an expert on runecrafting, and his insight will be essential.",
            "Would you be willing to visit him for me? I would go myself, but I wish to study this talisman some more.",
        )
        if (choice("Select an option", "Yes, certainly.", "No, I'm busy.") == 1) {
            me("Yes, certainly.")
            npc(
                sed,
                "He runs a rune shop in the south east of Varrock. Please, take this package of research notes to him. If all " +
                    "goes well, the secrets of the essence mine may soon be ours once more!",
            )
            player.setFlag(AGREED, true)
            givePackage(sed)
        } else {
            me("No, I'm busy.")
            npc(
                sed,
                "As you wish adventurer. I will continue to study this talisman you have brought me. Return here if you find " +
                    "yourself with some spare time to help me.",
            )
        }
    }

    private suspend fun PlayerScript.givePackage(sed: NpcRef) {
        val p = player
        if (!p.give(Objs.RESEARCH_PACKAGE, 1, "rune mysteries package")) {
            p.message("You don't have enough inventory space.")
            return
        }
        Quests.RUNE_MYSTERIES.advance(p, PACKAGE)
        itemBox(Objs.RESEARCH_PACKAGE, "Sedridor hands you a package.")
        npc(sed, "Best of luck, $name.")
        if (!p.flag(DOUBTED)) {
            me("I don't remember telling you my name... How do you know it?")
            npc(sed, "Really now? I am the Archmage you know.")
        }
    }

    private suspend fun PlayerScript.notes(sed: NpcRef) {
        val p = player
        npc(sed, "Ah, $name. How goes your quest? Have you delivered my research to Aubury yet?")
        if (!p.has(Objs.RESEARCH_NOTES)) {
            me("Err, you're not going to believe this...")
            npc(sed, "What?")
            me("I don't have them.")
            npc(
                sed,
                "Right... You're rather careless aren't you. I suggest you go and speak to Aubury once more. With luck he will " +
                    "have made copies.",
            )
            return
        }
        me("Yes, I have. He gave me some notes to give to you.")
        npc(sed, "Wonderful! Let's have a look then.")
        p.take(Objs.RESEARCH_NOTES, 1, "rune mysteries notes")
        itemBox(Objs.RESEARCH_NOTES, "You hand the notes to Sedridor.")
        npc(sed, "Alright, let's see what Aubury has for us...", "Yes, this is it! The lost incantation!")
        me("So you'll be able to access that essence mine now?")
        npc(
            sed,
            "That's right! Because of you, our order finally has a proper source of rune essence again! Thank you, friend.",
            "If you ever want to access the essence mine yourself, just let me know. It's the least I can do.",
            "I will also share the incantation with others, including Aubury. When I do, I'll let them know that you are to be " +
                "given unlimited access to the mine.",
            "Oh, and you can have this air talisman back as well. I have no further need of it, and I'm sure you will find it useful.",
            "In case you didn't know, the talisman can be used to craft air runes. Just take it to the Air Altar south of " +
                "Falador along with some rune essence.",
            "Don't worry if you can't find the altar. The talisman can guide you there. You may find talismans for other altars " +
                "as well while adventuring. They'll let you craft other types of rune.",
        )
        me("Great! Thanks!")
        npc(sed, "My pleasure!")
        QuestCompletion.complete(p, Quests.RUNE_MYSTERIES, REWARD)
    }

    private suspend fun PlayerScript.afterQuest(sed: NpcRef) {
        me("Hello there.")
        npc(sed, "Hello again, $name. What can I do for you?")
        // OPEN-QU7: "Can you teleport me to the Rune Essence Mine?" needs the mine's entry point and a teleport step; not implemented.
        while (true) {
            when (
                choice(
                    "Select an option",
                    "Who else knows the teleport to the Rune Essence Mine?",
                    "Could you tell me about the old Wizards' Tower?",
                    "I'd better get going.",
                )
            ) {
                1 -> {
                    me("Who else knows the teleport to the Rune Essence Mine?")
                    npc(
                        sed,
                        "Apart from myself, there's also Aubury in Varrock, Wizard Cromperty in East Ardougne, Brimstail in the " +
                            "Tree Gnome Stronghold and Wizard Distentor in Yanille's Wizards' Guild.",
                    )
                }

                2 -> {
                    me("Could you tell me about the old Wizards' Tower?")
                    npc(
                        sed,
                        "Of course. The first Wizards' Tower was built at the same time the Order of Wizards was founded. It " +
                            "was at the dawn of the Fifth Age, when the secrets of runecrafting were rediscovered.",
                        "For years, the tower was a hub of magical research. Wizards of all races and religions were welcomed " +
                            "into our order.",
                        "Alas, that openness is what ultimately led to disaster. The wizards who served Zamorak, the evil god " +
                            "of chaos, tried to claim our magical discoveries in his name.",
                        "They failed, but in retaliation, they burnt the entire tower to the ground. Years of work was destroyed.",
                        "The tower was soon rebuilt of course, but even now we are still trying to regain knowledge that was lost.",
                        "That's why I spend my time down here, in fact. This basement is all that is left of the old tower, and " +
                            "I believe there are still some secrets to discover here.",
                        "Of course, one secret I am no longer looking for is the teleportation incantation to the Rune Essence " +
                            "Mine. We have you to thank for that.",
                    )
                }

                else -> {
                    me("I'd better get going.")
                    return
                }
            }
        }
    }

    // ---- Aubury

    private suspend fun PlayerScript.auburyTalk(aubury: NpcRef) {
        val p = player
        when (p.stage) {
            DELIVERED -> {
                npc(aubury, "Hello. Did you take those notes back to Sedridor?")
                me("I'm still working on it.")
                return
            }

            NOTES -> {
                npc(aubury, "Hello. Did you take those notes back to Sedridor?")
                if (!p.has(Objs.RESEARCH_NOTES)) {
                    me("Sorry, but I lost them.")
                    npc(
                        aubury,
                        "Well, luckily I have duplicates. It's a good thing they are written in code. I wouldn't want the wrong " +
                            "kind of person to get access to the information contained within.",
                    )
                    p.giveOrDrop(Objs.RESEARCH_NOTES, 1, "rune mysteries notes")
                    itemBox(Objs.RESEARCH_NOTES, "Aubury hands you some research notes.")
                } else {
                    me("I'm still working on it.")
                    npc(aubury, "Don't take too long. He'll be eager to see if this is indeed the breakthrough we were hoping for.")
                }
                return
            }
        }
        npc(aubury, "Do you want to buy some runes?")
        val deliver = p.stage == PACKAGE
        val options =
            if (deliver) {
                arrayOf(
                    "Yes please!",
                    "I've been sent here with a package for you.",
                    "Oh, it's a rune shop. No thank you, then.",
                )
            } else {
                arrayOf("Yes please!", "Oh, it's a rune shop. No thank you, then.")
            }
        val pick = choice("Select an option", *options)
        when {
            pick == 1 -> {
                me("Yes please!")
                bus.publish(OpenShopRequest(p, Npcs.AUBURY_2OP))
            }

            deliver && pick == 2 -> {
                deliverPackage(aubury)
            }

            else -> {
                me("Oh, it's a rune shop. No thank you, then.")
                npc(aubury, "Well, if you find someone who does want runes, please send them my way.")
            }
        }
    }

    private suspend fun PlayerScript.deliverPackage(aubury: NpcRef) {
        val p = player
        me("I've been sent here with a package for you.")
        npc(aubury, "A package? From who?")
        me("From Sedridor at the Wizards' Tower.")
        npc(
            aubury,
            "From Sedridor? But... surely, he can't have? Please, let me have it. It must be extremely important for him to " +
                "have sent a stranger.",
        )
        if (!p.has(Objs.RESEARCH_PACKAGE)) {
            me("Uh... yeah... about that... I kind of don't have it with me...")
            npc(
                aubury,
                "What kind of person says they have a delivery for me, but not with them? Honestly.",
                "Come back when you have it.",
            )
            return
        }
        p.take(Objs.RESEARCH_PACKAGE, 1, "rune mysteries package")
        Quests.RUNE_MYSTERIES.advance(p, DELIVERED)
        itemBox(Objs.RESEARCH_PACKAGE, "You hand the package to Aubury.")
        npc(aubury, "Now, let's have a look...")
        itemBox(Objs.RESEARCH_PACKAGE, "Aubury goes through the package of research notes.")
        npc(
            aubury,
            "This... this is incredible.",
            "My gratitude to you adventurer for bringing me these research notes. Thanks to you, I think we finally have it.",
        )
        me("You mean the incantation?")
        npc(
            aubury,
            "Well when we combine my own research with this latest discovery, I think we might just...",
            "No, no, I'm getting ahead of myself. The signs are promising, but let's not jump to any conclusions just yet.",
            "Here, take these notes back to Sedridor. They should hopefully give him everything he needs.",
        )
        p.giveOrDrop(Objs.RESEARCH_NOTES, 1, "rune mysteries notes")
        Quests.RUNE_MYSTERIES.advance(p, NOTES)
        itemBox(Objs.RESEARCH_NOTES, "Aubury hands you some research notes.")
        npc(aubury, "Before you leave, why not have a cup of tea?")
        if (choice("Select an option", "I'd love a cup of tea.", "No, thank you.") == 1) {
            me("I'd love a cup of tea.")
            // Wiki transcript: the tea restores run energy fully.
            p.say("Aaah, nothing like a nice cuppa tea!")
            p.runEnergy = MAX_RUN_ENERGY
        } else {
            me("No, thank you.")
        }
    }

    private companion object {
        const val OP_TALK = 1
        const val TALISMAN = 1
        const val HANDED = 2
        const val PACKAGE = 3
        const val DELIVERED = 4
        const val NOTES = 5
        const val FINISHED = 6

        /** Run energy is stored in hundredths of a percent (full = 10000). */
        const val MAX_RUN_ENERGY = 10_000

        /** Account flags: Sedridor was met without the talisman; the player agreed to visit Aubury; doubted Sedridor's identity. */
        const val MET = "quests.runemysteries.met"
        const val AGREED = "quests.runemysteries.agreed"
        const val DOUBTED = "quests.runemysteries.doubted"

        /** Cache `Quest` row 125 stat reward (20, 5000 tenths) = 500 Runecraft experience; the talisman is handed back (Wiki). */
        val REWARD =
            QuestReward(
                model = Objs.AIR_TALISMAN,
                xp = listOf(Stats.RUNECRAFT to 500.0),
                items = listOf(Objs.AIR_TALISMAN to 1),
                notes = listOf("Rune Essence Mine Access"),
            )
    }
}
