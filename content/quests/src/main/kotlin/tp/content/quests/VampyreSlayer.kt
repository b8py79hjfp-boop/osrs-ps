@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * Vampyre Slayer (docs/mechanics/quest-vampyre-slayer.md): Morgan in Draynor Village sends the player to Dr Harlow in Varrock's Blue
 * Moon Inn for a stake; Count Draynor, in the coffin of Draynor Manor's cellar, dies for the quest only with a stake and a hammer in
 * the backpack. The quest variable (varp 178) is 0 / 1 started / 2 Harlow gave the stake (ours, OPEN-QU1) / 3 finished (cache
 * `endstate`). Morgan is a multi-NPC on varbit 15611 (0 before, 1 after). The Count's regeneration without a stake and the garlic
 * weakening need combat hooks the plugin API lacks (OPEN-QUA16, OPEN-QUA18). Dialogue is the Wiki "Transcript:Vampyre Slayer",
 * "Transcript:Morgan" and "Transcript:Dr Harlow".
 */
public class VampyreSlayer : ContentPlugin {
    override fun ContentScope.register() {
        for (morgan in MORGANS) {
            onOpNpc(morgan, OP_TALK) { npc ->
                player.stopInteraction()
                morganTalk(npc)
            }
        }
        for (harlow in HARLOWS) {
            onOpNpc(harlow, OP_TALK) { npc ->
                player.stopInteraction()
                harlowTalk(npc)
            }
        }
        // Morgan's cupboard upstairs (cache loc 2613, the open one of 2612): "Search" finds garlic.
        onOpLoc(CUPBOARD_OPEN, OP_SEARCH) {
            player.stopInteraction()
            searchCupboard()
        }
        onOpLoc(COFFIN, OP_OPEN) { coffin ->
            player.stopInteraction()
            openCoffin(coffin)
        }
        events.on<DeathEvent> { e ->
            val count = e.entity as? NpcRef ?: return@on
            if (count.type != Npcs.COUNT_DRAYNOR) return@on
            val killer = (e.killer as? PlayerRef) ?: e.lootFor ?: return@on
            slain(killer)
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.VAMPYRE_SLAYER) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private val PlayerRef.stage: Int get() = Quests.VAMPYRE_SLAYER.stage(this)

    private fun journal(stage: Int): List<String> =
        when (stage) {
            0 -> {
                listOf("I can start this quest by speaking to Morgan in his house in Draynor Village.")
            }

            STARTED -> {
                listOf(
                    "Morgan wants Count Draynor, the vampyre of Draynor Manor, slain. Dr Harlow, who drinks in the Blue Moon Inn in " +
                        "Varrock, can help; he wants a beer first.",
                    "Garlic is in the cupboard upstairs in Morgan's house.",
                )
            }

            STAKE_GIVEN -> {
                listOf(
                    "Dr Harlow gave me a stake. I need a hammer too, to drive it into Count Draynor in the cellar of Draynor Manor.",
                    "Garlic weakens him.",
                )
            }

            else -> {
                listOf("I slayed Count Draynor.")
            }
        }

    // ---- Morgan

    private suspend fun PlayerScript.morganTalk(morgan: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(morgan, "Could it be? A bold adventurer! Please, you must help us!")
                me("What is it? What's the problem?")
                npc(
                    morgan,
                    "It's the evil vampyre, Count Draynor! For too long he's terrorised us from his manor to the north. Someone " +
                        "finally needs to put a stop to him once and for all!",
                )
                if (p.combatLevel < COMBAT_LEVEL) {
                    box("Before starting this quest, be aware that your combat level is lower than the recommended level of 20.")
                }
                if (choice("Start the Vampyre Slayer quest?", "Yes.", "No.") != 1) {
                    me("An evil vampyre? I don't think this is the job for me. It sounds far too scary!")
                    npc(morgan, "I understand... Hopefully someone else will come along to finally save us from this evil.")
                    return
                }
                me("Sounds like a job for me. Where should I start?")
                Quests.VAMPYRE_SLAYER.advance(p, STARTED)
                npc(
                    morgan,
                    "Oh, thank goodness! I've been hoping this day would come for a long time, so I've made sure to do my research. " +
                        "I've heard of a retired vampyre hunter called Dr Harlow who lives in Varrock.",
                    "If you speak to him, I'm sure he'll be able to help. I hear he's a bit of an old soak these days, so he spends " +
                        "most of his time in the Blue Moon Inn.",
                )
                moreAboutCount(morgan)
            }

            STARTED, STAKE_GIVEN -> {
                npc(morgan, "Adventurer! Has Count Draynor been dealt with yet?")
                if (p.stage == STAKE_GIVEN && !p.has(Objs.GARLIC)) {
                    me("I'm still working on it, but I could use some garlic to help me out.")
                    npc(
                        morgan,
                        "Ah, yes! I heard vampyres didn't like garlic. I keep some in the cupboard upstairs, just in case Count " +
                            "Draynor comes and tries anything!",
                    )
                    if (choice("Select an option", "What else can you tell me about Count Draynor?", "Perfect, thanks.") == 1) {
                        legends(morgan)
                    } else {
                        me("Perfect, thanks.")
                        npc(morgan, "Good luck in Draynor Manor, adventurer.")
                    }
                    return
                }
                me("I'm still working on it.")
                npc(morgan, "Please hurry! I'm sure Dr Harlow in Varrock's Blue Moon Inn can help.")
                moreAboutCount(morgan)
            }

            else -> {
                if (!p.flag(FLAG_THANKED)) {
                    me("I have some good news. Count Draynor is no more!")
                    npc(
                        morgan,
                        "He's really gone? Finally, we can live without fear! Thank you, thank you! You're a true hero!",
                    )
                    me("I'm happy to have helped.")
                    p.setFlag(FLAG_THANKED, true)
                } else {
                    npc(morgan, "Once again, thank you for slaying that vampyre! You will always be a hero!")
                    me("Don't mention it.")
                }
            }
        }
    }

    private suspend fun PlayerScript.moreAboutCount(morgan: NpcRef) {
        if (choice("Select an option", "What else can you tell me about this vampyre?", "Alright, I'll see about paying him a visit.") ==
            1
        ) {
            legends(morgan)
        } else {
            me("Alright, I'll see about paying him a visit.")
            npc(morgan, "Thank you, brave adventurer.")
        }
    }

    private suspend fun PlayerScript.legends(morgan: NpcRef) {
        me("What else can you tell me about this vampyre?")
        npc(morgan, "All sorts of things! I've studied the legends extensively!")
        me("Wait... legends? What do you mean legends?")
        npc(
            morgan,
            "The legends of Count Draynor! It's said that long ago, he travelled here from dark lands in the east. He built " +
                "Draynor Manor, and from there he began his campaign of darkness against our peaceful lands!",
        )
        me("Long ago? If this has been going on for so long, why has no one tried to stop him?")
        npc(
            morgan,
            "Many have tried. All have failed. Vampyres are not killed easily. Only those with the right skills and equipment " +
                "have any hope of slaying one.",
        )
        me("Like this Dr Harlow? Home come he's never tried to kill this Count Draynor?")
        npc(morgan, "I'm... not sure. Perhaps he was always too busy killing other vampyres?")
        me("Hmm... Something seems amiss here. Are you sure you're telling me everything?")
        npc(morgan, "Well... it is true that some claim Count Draynor hasn't left his manor for generations now.")
        me("So he's not actually attacked the village?")
        npc(morgan, "Er... no. At least, not for a long time! He used to though!")
        me("When?")
        npc(morgan, "Well... not since I've moved here at least.")
        me("So if there's no actual danger, why are you so keen for someone to kill this vampyre?")
        npc(morgan, "Because what if he does start attacking again? Are we just meant to live our lives constantly fearing the future?")
        me("It doesn't really sound like anyone is living in fear apart from you.")
        npc(
            morgan,
            "More fool them! Count Draynor has cast a shadow over Draynor Village for generations. They even named the place after " +
                "him for Saradomin's sake!",
            "Even if the people here don't realise it, we will never be at peace until we know he's dead!",
        )
        me("If you're sure... I guess I'll go and see this Dr Harlow.")
        npc(morgan, "Thank you, brave adventurer.")
    }

    // ---- Dr Harlow

    private suspend fun PlayerScript.harlowTalk(harlow: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(harlow, "Buy me a drink pleassh...")
                me("I think you've had enough.")
            }

            STARTED -> {
                if (!p.flag(FLAG_ASKED)) {
                    npc(harlow, "Buy me a drink pleassh...")
                    if (choice("Select an option", "I need your help dealing with a vampyre.", "I think you've had enough.") != 1) {
                        me("I think you've had enough.")
                        return
                    }
                    me("I need your help dealing with a vampyre.")
                    npc(harlow, "A vampyre you shhay...?")
                    me("Not just any vampyre. Count Draynor.")
                    npc(harlow, "Draynor? Well, buy me a beer firsht...")
                    me("Are you sure you've not had enough?")
                    npc(harlow, "Huh? No, I don't think ssho. Now, buy ush a beer.")
                    p.setFlag(FLAG_ASKED, true)
                    return
                }
                npc(harlow, "Have yoush got me a beer?")
                if (!p.has(Objs.BEER)) {
                    if (choice("Select an option", "I'll go and get you one.", "No. I think you've had enough.") == 1) {
                        me("I'll go and get you one.")
                        npc(harlow, "Cheersh, matey...")
                    } else {
                        me("No. I think you've had enough.")
                        npc(harlow, "No help for yoush then.")
                    }
                    return
                }
                if (choice("Select an option", "Yes, here you go.", "No. I think you've had enough.") != 1) {
                    me("No. I think you've had enough.")
                    npc(harlow, "No help for yoush then.")
                    return
                }
                me("Yes, here you go.")
                if (!p.take(Objs.BEER, 1, "vampyre slayer beer")) return
                box("You give a beer to Dr Harlow.")
                npc(harlow, "Cheersh, matey...")
                me("Now, about Count Draynor...")
                npc(
                    harlow,
                    "Yesh, Count Draynor! The evil nashty vampyre that no one's ever sheen! Every nowsh and then, some adventurer... " +
                        "theysh go and try to kill him, but none of 'em ever comesh back.",
                    "You want to havesh a go? Then don't be likesh them! Be prepared! Vampyres... Theysh hard to kill. Some... maybe " +
                        "imposhible.",
                )
                me("So how do I make sure I'm prepared?")
                npc(
                    harlow,
                    "Most vampyres regenerate. Yoush need to stop them. I knowsh a few ways, but the easiest ish a stake. Here, You can " +
                        "havesh thish one.",
                )
                if (!p.giveOrDrop(Objs.STAKE, 1, "vampyre slayer stake")) return
                Quests.VAMPYRE_SLAYER.advance(p, STAKE_GIVEN)
                box("Dr Harlow hands you a stake.")
                useStake(harlow)
                questions(harlow)
            }

            STAKE_GIVEN -> {
                npc(harlow, "Yoush back! Killed that vampyre yet?")
                if (!p.has(Objs.STAKE)) {
                    me("No. I lost that stake you gave me.")
                    npc(harlow, "Oh, hangsh on then...")
                    if (!p.giveOrDrop(Objs.STAKE, 1, "vampyre slayer stake")) return
                    box("Dr Harlow hands you a stake.")
                    npc(
                        harlow,
                        "Good job I've gotsh some spares. Now, takesh that to Draynor Manor. Find the vampyre inshide and show him " +
                            "what for! When hesh weak, use a hammer to drive the stake in! Mosht general stores have them.",
                    )
                    garlicAdvice(harlow)
                }
                questions(harlow, fromReturn = true)
            }

            else -> {
                npc(harlow, "Buy me a drink pleassh...")
                me("I think you've had enough.")
            }
        }
    }

    private suspend fun PlayerScript.useStake(harlow: NpcRef) {
        npc(
            harlow,
            "Takesh that to Draynor Manor. Find the vampyre inshide and show him what for! When hesh weak, use a hammer to drive " +
                "the stake in! Mosht general stores have them.",
        )
        garlicAdvice(harlow)
    }

    private suspend fun PlayerScript.garlicAdvice(harlow: NpcRef) {
        npc(harlow, "Oh, and yoush should take some garlic with you as well. Vampyres don't likesh garlic.")
        me("Garlic? Hmm... I'll see if Morgan knows where I can get some.")
    }

    /** The menu Dr Harlow keeps offering after the stake; [fromReturn] changes the way out ("Not yet. I'd best get going."). */
    private suspend fun PlayerScript.questions(harlow: NpcRef, fromReturn: Boolean = false) {
        val leave = if (fromReturn) "Not yet. I'd best get going." else "I'd best get going."
        while (true) {
            when (
                choice(
                    "Select an option",
                    "What do I need to do again?",
                    "You said no one's ever seen Count Draynor?",
                    "So you were once a proper vampyre hunter?",
                    leave,
                )
            ) {
                1 -> {
                    me("What do I need to do again?")
                    npc(
                        harlow,
                        "Go to Draynor Manor. Find the vampyre inshide and show him what for! When hesh weak, use a hammer to drive " +
                            "that stake I gave you in! Mosht general stores have them.",
                    )
                    garlicAdvice(harlow)
                }

                2 -> {
                    me("You said no one's ever seen Count Draynor?")
                    npc(harlow, "No, butsh every now and then someonesh tried to kill him.")
                    me("But they've never come back? Meaning someone or something killed them most likely. Presumably Count Draynor?")
                    npc(harlow, "Yesh, I'd say so.")
                    me("So why does he never leave his manor?")
                    npc(harlow, "Who knowsh. Most likely he's grown too weak to do ssho.")
                    me("Weak? So he shouldn't be too hard to kill then?")
                    npc(harlow, "Even a weak vampyre ish very dangerous. Just go ashk those other adventurers thatsh never came back.")
                }

                3 -> {
                    me("So you were once a proper vampyre hunter?")
                    npc(harlow, "Yesh! I travelled their homeland to the easht. Morytania! I killed lotsh of vampyres over there!")
                    me("What was the most dangerous vampyre you killed?")
                    npc(harlow, "Dangerous? Oh, well... Ish never killed one of the really dangerous ones.")
                    me("Why not?")
                    npc(harlow, "Can't. Theysh too strong. Nothing cansh hurt them.")
                    me("Oh... But what if Count Draynor's like that?")
                    npc(
                        harlow,
                        "Yoush don't get many vampyres around here. Pretty much none, actshually. There's speshial magic that stops " +
                            "them coming over here. Any that are here... theysh weak... and trapped.",
                    )
                    me("So how come you've never tried to kill him yourself?")
                    npc(
                        harlow,
                        "Why bother? Hesh not killed anyone in a long time, so no one really caresh about him that much, apart from " +
                            "the odd adventurer thatsh looking to prove themshelves.",
                    )
                    me("I see. Fair enough.")
                }

                else -> {
                    me(leave)
                    npc(harlow, "Good luck, matey...")
                    return
                }
            }
        }
    }

    // ---- the cupboard, the coffin, the Count

    private fun PlayerScript.searchCupboard() {
        val p = player
        if (p.stage < STARTED || p.has(Objs.GARLIC)) {
            p.message("You search the cupboard but find nothing interesting.")
            return
        }
        // Wiki "Vampyre Slayer": "you can search the cupboard upstairs to find some" garlic; the message wording is ours (OPEN-QU4).
        if (p.giveOrDrop(Objs.GARLIC, 1, "vampyre slayer garlic")) p.message("You find some garlic in the cupboard.")
    }

    /**
     * Opening the coffin of the cellar lets Count Draynor out to attack the player (Wiki "Count Draynor"). He returns to the coffin
     * and heals when the fight takes too long; the Wiki gives no time, so he leaves after [COUNT_LIFETIME] ticks (OPEN-QUA17).
     */
    private fun PlayerScript.openCoffin(coffin: LocRef) {
        val p = player
        val count = p.spawnNpc(Npcs.COUNT_DRAYNOR, coffin.coord.translate(1, 1), 0)
        count.huntRange = COUNT_HUNT
        count.face(p)
        count.queueHit(COUNT_LIFETIME, strong = false) { if (count.isInWorld) count.despawn() }
    }

    /** Count Draynor died: with a stake and a hammer in the backpack the quest is done (Wiki "Slaying Count Draynor"). */
    private fun slain(killer: PlayerRef) {
        if (killer.stage != STAKE_GIVEN) return
        if (!killer.has(Objs.STAKE)) return
        if (!killer.has(Objs.HAMMER)) {
            killer.message("<col=ff0000>You're unable to push the stake far enough in!</col>")
            return
        }
        killer.message("<col=ff0000>You hammer the stake into the vampyre's chest!</col>")
        killer.setVarbit(MORGAN_VARBIT, 1)
        QuestCompletion.complete(killer, Quests.VAMPYRE_SLAYER, REWARD)
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_SEARCH = 1
        const val OP_OPEN = 1

        /** Cache NPC 3479 (Morgan) multi-NPC varbit: 0 = 16274 before, 1 = 16275 after the quest. */
        const val MORGAN_VARBIT = 15611

        val MORGANS = intArrayOf(Npcs.MORGAN, Npcs.MORGAN_PRE, Npcs.MORGAN_POST)
        val HARLOWS = intArrayOf(Npcs.DR_HARLOW, DR_HARLOW_SEEN)

        /** Cache NPC 3480's multi-NPC child 16277 (`Dr Harlow`, "Talk-to"). */
        private const val DR_HARLOW_SEEN = 16277

        /** Cache loc 2613 (open cupboard of Morgan's house, op 1 "Search") and loc 46237 (the cellar's coffin, op 1 "Open", 3077,9775). */
        const val CUPBOARD_OPEN = 2613
        const val COFFIN = 46237

        /** Quest stages (ours, OPEN-QU1): 1 started, 2 stake given; 3 is the cache's `endstate`. */
        const val STARTED = 1
        const val STAKE_GIVEN = 2

        const val FLAG_ASKED = "quests.vamp.asked"
        const val FLAG_THANKED = "quests.vamp.thanked"

        /** Wiki "Vampyre Slayer": recommended combat level 20. */
        const val COMBAT_LEVEL = 20

        /** The Count hunts within 5 tiles of the coffin (ours, like the Restless Ghost's skeleton, OPEN-QUA17). */
        const val COUNT_HUNT = 5
        const val COUNT_LIFETIME = 500

        /** Wiki "Vampyre Slayer" rewards: 3 quest points, 4,825 Attack XP (cache col 33 = (0, 48250 tenths)). */
        val REWARD = QuestReward(model = Objs.STAKE, xp = listOf(Stats.ATTACK to 4825.0))
    }
}
