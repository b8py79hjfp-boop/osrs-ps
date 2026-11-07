@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * X Marks the Spot (docs/mechanics/quest-x-marks-the-spot.md): Veos hands out a treasure scroll; four digs (a scroll, a map, a
 * mysterious orb, a cipher) end with the ancient casket, which goes back to Veos. The quest variable is varbit 8063 (0 / 8 in
 * the cache); ours (OPEN-QU1): 1 first scroll, 2 map, 3 orb, 4 cipher, 5 casket dug up, 8 finished. Dialogue is the Wiki
 * "Transcript:X Marks the Spot". Digging uses [DigEvent] ([QuestDigging]).
 */
public class XMarksTheSpot : ContentPlugin {
    override fun ContentScope.register() {
        for (veos in listOf(Npcs.VEOS_LUMBRIDGE, Npcs.VEOS_SARIM, Npcs.VEOS_VISIBLE, Npcs.VEOS_VISIBLE_AMULET)) {
            onOpNpc(veos, OP_TALK) { npc ->
                player.stopInteraction()
                veosTalk(npc)
            }
        }
        onLogin { syncVeos(this) }
        onItemOp(CLUE1, "Read") {
            startScript {
                box(
                    "Within the town of Lumbridge lives a man named Bob. He walks out of his door and takes 1 step east, 7 steps " +
                        "north, 5 steps west and 1 step south. Once he arrives, he digs a hole and buries his treasure.",
                )
            }
        }
        // OPEN-QUB13: the map clue is an interface picture; the cipher text is the Wiki's.
        onItemOp(CLUE2, "Read") { message("The scroll is a map of Lumbridge with an X marked on it.") }
        onItemOp(CLUE4, "Read") { startScript { box("ESBZOPS QJH QFO") } }
        onItemOp(ORB_ITEM, "Feel") { feelOrb(this) }
        onItemOp(CASKET, "Open") {
            startScript {
                me(
                    "I don't think Veos would want me to open his treasure. I should take it to him. If I remember right, it's docked " +
                        "at the northernmost pier in Port Sarim.",
                )
            }
        }
        events.on<DigEvent> { dig(it) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.X_MARKS_THE_SPOT) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.X_MARKS_THE_SPOT.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when (stage(p)) {
            0 -> listOf("I can start this quest by speaking to Veos in The Sheared Ram in Lumbridge.")
            FIRST -> listOf("Veos gave me a treasure scroll. It tells of a man named Bob in Lumbridge.")
            MAP -> listOf("The next scroll is a map of Lumbridge.")
            ORB_STAGE -> listOf("A mysterious orb tells how close the next clue is. I will feel it from different places.")
            CIPHER -> listOf("The last scroll is a cipher. I need a spade.")
            CASKET_STAGE -> listOf("I dug up an ancient casket. Veos waits at his ship in Port Sarim.")
            else -> listOf("I found the treasure Veos was after.")
        }

    /**
     * The cache's multi-NPCs of Veos (Lumbridge 8632 on varbit 12151, Port Sarim 1063 on varbit 12148) pick the visible variant.
     * OPEN-QUB13: the values are not published; they are set so that the pub Veos shows until the quest ends and the dock Veos
     * from the first scroll on (the cache arrays: 8632 shows 8484 at 2; 1063 shows 8484 at 3 and the travel variant 8630 at 4).
     */
    private fun syncVeos(p: PlayerRef) {
        val done = QuestsB.X_MARKS_THE_SPOT.state(p) == QuestState.FINISHED
        p.setVarbit(VEOS_LUMBRIDGE_VARBIT, if (done) 0 else VEOS_PLAIN)
        p.setVarbit(
            VEOS_SARIM_VARBIT,
            if (done) {
                VEOS_TRAVEL
            } else if (stage(p) >= FIRST) {
                VEOS_DOCK
            } else {
                0
            },
        )
    }

    // ---- Veos

    private suspend fun PlayerScript.veosTalk(veos: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            s == 0 -> veosStart(veos)
            s in FIRST until CASKET_STAGE && !p.has(currentClue(s)) -> veosLost(veos)
            s == FIRST -> veosClue1(veos)
            s == MAP -> veosClue2(veos)
            s == ORB_STAGE -> veosClue3(veos)
            s == CIPHER -> veosClue4(veos)
            s == CASKET_STAGE -> veosFinish(veos)
            else -> npc(veos, "Hello there.")
        }
    }

    private fun currentClue(stage: Int): Int =
        when (stage) {
            FIRST -> CLUE1
            MAP -> CLUE2
            ORB_STAGE -> ORB_ITEM
            else -> CLUE4
        }

    private suspend fun PlayerScript.veosStart(veos: NpcRef) {
        // OPEN-QUB13: whether Great Kourend was visited is not tracked here; the first-visit dialogue is used.
        veosNew(veos)
    }

    /** Wiki "Transcript:X Marks the Spot", section "If the player has not visited Great Kourend before". */
    private suspend fun PlayerScript.veosNew(veos: NpcRef) {
        npc(veos, "Hello there.")
        when (
            choice(
                "Select an Option",
                "Who are you?",
                "I'm looking for a quest.",
                "I have to go.",
            )
        ) {
            1 -> {
                me("Who are you?")
                npc(veos, "The name's Veos. I'm a treasure hunter from the wonderous Kingdom of Great Kourend.")
                me("Great Kourend? Where's that?")
                npc(veos, "Across the sea to the far west. It is a truly magnificent place.")
                me("Interesting. So what brings you to Lumbridge?")
                npc(
                    veos,
                    "I'm here on a bit of a hunt. The hunt for treasure. Back in my home of Great Kourend, I " +
                        "came across a scroll. I believe it will lead me to something of great value.",
                )
                npc(
                    veos,
                    "Alas, I've hit a bit of a blocker. The scroll has led me here but as I don't really know " +
                        "the area, I'm not sure what to do next.",
                )
                when (
                    choice(
                        "Select an Option",
                        "Can I help?",
                        "Well good luck with it.",
                    )
                ) {
                    1 -> {
                        npc(
                            veos,
                            "Hmmm. Maybe you can. You probably know this area better than I do. You might be able to " +
                                "work the scroll out. I'll be able to reward you if you help.",
                        )
                        when (
                            choice(
                                "Select an Option",
                                "Sounds good, what should I do?",
                                "I'm good thanks.",
                            )
                        ) {
                            1 -> {
                                me("Sounds good, what should I do?")
                                if (player.inv(Invs.INV).freeSlots == 0) {
                                    npc(
                                        veos,
                                        "First you should go and make some room in your inventory. Once you've done that, you can " +
                                            "have a look at this scroll.",
                                    )
                                    return
                                }
                                if (player.inv(Invs.INV).freeSlots > 0) {
                                    npc(
                                        veos,
                                        "Take this scroll. It should lead you to the treasure I seek. Once you've found it, meet me " +
                                            "at my ship. It's docked at the northernmost pier in Port Sarim.",
                                    )
                                    giveScroll()
                                    Unit
                                    me("Awesome. Anything else I should know?")
                                    npc(
                                        veos,
                                        "You'll probably want a spade, I find they're pretty much always needed when it comes to " +
                                            "hunting treasure. Check the general store if you don't have one. If you need any extra " +
                                            "help, just let me know.",
                                    )
                                    me("Okay, thanks Veos.")
                                    npc(veos, "Good luck.")
                                }
                            }

                            2 -> {
                                me("I'm good thanks.")
                                npc(veos, "Fair enough. I'll be here if you change your mind.")
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    2 -> {
                        me("Well good luck with it.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                npc(
                    veos,
                    "Hmmm. Well now that you mention it, I could do with some help. The name's Veos. I'm a " +
                        "treasure hunter from the wonderous Kingdom of Great Kourend.",
                )
                me("Great Kourend? Where's that?")
            }

            3 -> {
                me("I have to go.")
            }

            else -> {
                Unit
            }
        }
    }

    private fun PlayerScript.giveScroll() {
        val p = player
        if (p.give(CLUE1, 1, "x marks the spot scroll")) {
            QuestsB.X_MARKS_THE_SPOT.advance(p, FIRST)
            syncVeos(p)
        }
    }

    /** Wiki "Transcript:X Marks the Spot", section "Talking to Veos after losing an item". */
    private suspend fun PlayerScript.veosLost(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        npc(veos, "I came across this [scroll/orb]. Did you lose it by any chance?")
        replaceClue()
        itemBox(CLUE1, "Veos has given you a [scroll/orb].")
        me("I did, thanks Veos.")
        npc(veos, "Not to worry. Just try to be careful with it.")
    }

    private fun PlayerScript.replaceClue() {
        val p = player
        p.giveOrDrop(currentClue(stage(p)), 1, "x marks the spot clue")
    }

    /** Wiki "Transcript:X Marks the Spot", section "Talking to Veos after receiving the first clue". */
    private suspend fun PlayerScript.veosClue1(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        me("Let's talk about my quest.")
        npc(veos, "How's the treasure hunt going?")
        when (
            choice(
                "Select an Option",
                "I could do with some extra help.",
                "I'm still working on it.",
            )
        ) {
            1 -> {
                itemBox(CLUE1, "You show the scroll to Veos.")
                npc(
                    veos,
                    "This is the scroll I gave you. I don't know how to solve it I'm afraid. Maybe look for " +
                        "someone named Bob?",
                )
            }

            2 -> {
                me("I'm still working on it.")
                if (veos.type == Npcs.VEOS_LUMBRIDGE || veos.type == Npcs.VEOS_VISIBLE) {
                    npc(
                        veos,
                        "Well keep at it. Once you've found the treasure, meet me at my ship. It's docked at the " +
                            "northernmost pier in Port Sarim.",
                    )
                    return
                }
                if (veos.type != Npcs.VEOS_LUMBRIDGE && veos.type != Npcs.VEOS_VISIBLE) {
                    npc(veos, "Well keep at it. Once you've found the treasure, bring it to me.")
                }
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:X Marks the Spot", section "Talking to Veos after receiving the second clue". */
    private suspend fun PlayerScript.veosClue2(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        me("Let's talk about my quest.")
        npc(veos, "How's the treasure hunt going?")
        when (
            choice(
                "Select an Option",
                "I could do with some extra help.",
                "I'm still working on it.",
            )
        ) {
            1 -> {
                itemBox(CLUE2, "You show the scroll to Veos.")
                npc(veos, "Looks like a map to me. I'd assume the X marks where you need to go.")
            }

            2 -> {
                me("I'm still working on it.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:X Marks the Spot", section "Talking to Veos after receiving the third clue". */
    private suspend fun PlayerScript.veosClue3(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        me("Let's talk about my quest.")
        npc(veos, "How's the treasure hunt going?")
        when (
            choice(
                "Select an Option",
                "I could do with some extra help.",
                "I'm still working on it.",
            )
        ) {
            1 -> {
                me("I could do with some extra help.")
                itemBox(ORB_ITEM, "You show the orb to Veos.")
                npc(
                    veos,
                    "Interesting, these are quite rare. They work using temperature. The closer you are, the " +
                        "hotter the orb.",
                )
            }

            2 -> {
                me("I'm still working on it.")
                if (veos.type == Npcs.VEOS_LUMBRIDGE || veos.type == Npcs.VEOS_VISIBLE) {
                    npc(
                        veos,
                        "Well keep at it. Once you've found the treasure, meet me at my ship. It's docked at the " +
                            "northernmost pier in Port Sarim.",
                    )
                }
                if (veos.type != Npcs.VEOS_LUMBRIDGE && veos.type != Npcs.VEOS_VISIBLE) {
                    npc(veos, "Well keep at it. Once you've found the treasure, bring it to me.")
                }
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:X Marks the Spot", section "Talking to Veos after receiving the fourth clue". */
    private suspend fun PlayerScript.veosClue4(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        me("Let's talk about my quest.")
        npc(veos, "How's the treasure hunt going?")
        when (
            choice(
                "Select an Option",
                "I could do with some extra help.",
                "I'm still working on it.",
            )
        ) {
            1 -> {
                me("I could do with some extra help.")
                itemBox(CLUE4, "You show the scroll to Veos.")
                npc(
                    veos,
                    "Ah a cipher, I've seen these before. Try shifting the letters to the left or right. That " +
                        "normally works.",
                )
            }

            2 -> {
                me("I'm still working on it.")
                if (veos.type == Npcs.VEOS_LUMBRIDGE || veos.type == Npcs.VEOS_VISIBLE) {
                    npc(
                        veos,
                        "Well keep at it. Once you've found the treasure, meet me at my ship. It's docked at the " +
                            "northernmost pier in Port Sarim.",
                    )
                }
                if (veos.type != Npcs.VEOS_LUMBRIDGE && veos.type != Npcs.VEOS_VISIBLE) {
                    npc(veos, "Well keep at it. Once you've found the treasure, bring it to me.")
                }
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:X Marks the Spot", section "Finishing up". */
    private suspend fun PlayerScript.veosFinish(veos: NpcRef) {
        npc(veos, "Hello there.")
        me("Hello Veos.")
        npc(veos, "How's the treasure hunt going?")
        me("I found the treasure!")
        npc(veos, "Excellent. I'll take it off your hands now.")
        itemBox(CASKET, "You give Veos the Ancient Casket.")
        player.take(CASKET, 1, "x marks the spot casket")
        npc(veos, "Brilliant. This is just what I was looking for. Thank you so much for your help.")
        me("No problem. So what is this treasure anyway?")
        npc(
            veos,
            "Oh err... nothing important. Just something that might be of use to me back in Great " +
                "Kourend.",
        )
        me("If you say so...")
        npc(veos, "Anyway, as promised, a reward for you.")
        // {{Qact|Congratulations! Quest complete!}}
        npc(
            veos,
            "Anyway, I'd best be getting back to Great Kourend. I'm sure I'll be back here before long " +
                "though, there's always more treasure to be found.",
        )
        npc(
            veos,
            "If you ever fancy visiting the kingdom, come find me here. I'm more than happy to take you " +
                "there. Consider it an extra thank you for your help.",
        )
        me("Sounds great, thanks Veos.")
    }

    private fun PlayerScript.completeQuest() {
        QuestCompletion.complete(player, QuestsB.X_MARKS_THE_SPOT, REWARD)
        syncVeos(player)
    }

    // ---- the digs

    /** The four dig spots (Wiki "Map:X Marks the Spot") and what each gives. */
    private fun dig(e: DigEvent) {
        val p = e.player
        val s = stage(p)
        val spot = SPOTS.getOrNull(s - FIRST) ?: return
        if (s !in FIRST..CIPHER || e.at != spot || !p.has(currentClue(s))) return
        e.handled = true
        p.startScript {
            if (s == CIPHER) {
                // Wiki "Digging the last clue".
                p.take(CLUE4, 1, "x marks the spot cipher")
                p.giveOrDrop(CASKET, 1, "x marks the spot casket")
                itemBox(
                    CASKET,
                    "You dig up an Ancient Casket. As you do, you hear a faint whispering. You can't make out what it says though...",
                )
                me("Hmmmm... Must have been the wind.")
                me(
                    "Anyway, this must be the treasure that Veos is after. I should take it to him. If I remember right, he's docked " +
                        "at the northernmost pier in Port Sarim.",
                )
                QuestsB.X_MARKS_THE_SPOT.advance(p, CASKET_STAGE)
            } else {
                // OPEN-QUB13: the Wiki gives only "You will receive the next treasure scroll step."; the message is ours.
                p.take(currentClue(s), 1, "x marks the spot clue")
                p.giveOrDrop(currentClue(s + 1), 1, "x marks the spot clue")
                p.message("You dig up a new clue.")
                QuestsB.X_MARKS_THE_SPOT.advance(p, s + 1)
            }
        }
    }

    /**
     * The mysterious orb says how close the next dig is (Wiki: temperature by the Chebyshev distance to the treasure).
     * OPEN-QUB13: the words and distances are not published; the table is ours ([ORB_BANDS]).
     */
    private fun feelOrb(p: PlayerRef) {
        val s = stage(p)
        val target = SPOTS.getOrNull(s - FIRST) ?: return p.message("The orb is lukewarm.")
        val distance = p.coord.chebyshev(target)
        val word = ORB_BANDS.first { distance <= it.first }.second
        p.message("The orb feels $word.")
    }

    private companion object {
        const val OP_TALK = 1

        /** Quest variable stages (OPEN-QU1): 0 and 8 are the cache's `unstarted` / `endstate`. */
        const val FIRST = 1
        const val MAP = 2
        const val ORB_STAGE = 3
        const val CIPHER = 4
        const val CASKET_STAGE = 5

        const val CLUE1 = Objs.CLUEQUEST_CLUE1
        const val CLUE2 = Objs.CLUEQUEST_CLUE2
        const val ORB_ITEM = Objs.CLUEQUEST_CLUE3
        const val CLUE4 = Objs.CLUEQUEST_CLUE4
        const val CASKET = Objs.CLUEQUEST_CASKET

        /** Veos' location varbits (cache npc 8632 and 1063) and the values this server sets (OPEN-QUB13). */
        const val VEOS_LUMBRIDGE_VARBIT = 12151
        const val VEOS_SARIM_VARBIT = 12148
        const val VEOS_PLAIN = 2
        const val VEOS_DOCK = 3
        const val VEOS_TRAVEL = 4

        /** Wiki "Map:X Marks the Spot": the dig spot of each clue (steps 1 to 4). */
        val SPOTS =
            arrayOf(CoordGrid(3230, 3210, 0), CoordGrid(3203, 3212, 0), CoordGrid(3108, 3264, 0), CoordGrid(3077, 3260, 0))

        /** OPEN-QUB13: orb temperature by distance in tiles (ours). */
        val ORB_BANDS =
            listOf(
                0 to "like it is burning",
                4 to "very hot",
                10 to "hot",
                25 to "warm",
                60 to "cool",
                150 to "cold",
                Int.MAX_VALUE to "ice cold",
            )

        /** Wiki "X Marks the Spot" rewards: 1 quest point (cache), 200 coins, an antique lamp worth 300 experience. */
        val REWARD =
            QuestReward(
                model = Objs.CLUEQUEST_LAMP,
                items = listOf(Objs.COINS to 200, Objs.CLUEQUEST_LAMP to 1),
            )
    }
}
