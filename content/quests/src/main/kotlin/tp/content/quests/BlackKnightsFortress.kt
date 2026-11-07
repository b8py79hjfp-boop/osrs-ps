@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Black Knights' Fortress (docs/mechanics/quest-black-knights-fortress.md): Sir Amik Varze sends the player into the Black Knights'
 * fortress in disguise to learn about, and ruin, their invincibility potion with a cabbage. The quest variable (varp 130) is 0 /
 * 1 started / 2 overheard the witch (ours, OPEN-QU1) / 3 potion ruined (ours) / 4 finished (cache `endstate`). The fortress
 * doors and walls are passed by moving the player across ([stepAcross]); the Black Knights, the witch and the cutscene stages
 * are staged with [Scene] where the Wiki describes them (the witch's room, OPEN-QUA6). Dialogue is the Wiki
 * "Transcript:Black Knights' Fortress".
 */
public class BlackKnightsFortress : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.SIR_AMIK_VARZE, OP_TALK) { amik ->
            player.stopInteraction()
            amikTalk(amik)
        }
        onItemOp(Objs.YAMA_DOSSIER, "Open") { readDossier() }
        onOpLoc(Locs.WITCHGRILL, OP_LISTEN) {
            player.stopInteraction()
            listen()
        }
        onOpLoc(STURDY_DOOR_SOUTH, OP_OPEN) { door ->
            player.stopInteraction()
            southDoor(door)
        }
        onOpLoc(MEETING_DOOR, OP_OPEN) { door ->
            player.stopInteraction()
            meetingDoor(door)
        }
        for (wall in FAKE_WALLS) {
            onOpLoc(wall, OP_PUSH) { loc ->
                player.stopInteraction()
                player.message("You push against the wall. You find a secret passage.")
                player.stepAcross(loc)
            }
        }
        onUseOnLoc(Objs.CABBAGE, HOLE) {
            player.stopInteraction()
            cabbageOnHole()
        }
        onUseOnLoc(Objs.MAGIC_CABBAGE, HOLE) {
            player.stopInteraction()
            if (stage < OVERHEARD) whyWouldI() else wrongCabbage()
        }
        events.on<JournalRequest> {
            if (it.quest == Quests.BLACK_KNIGHTS_FORTRESS) it.lines += journal(it.quest.stage(it.player))
        }
    }

    private val PlayerScript.stage: Int get() = Quests.BLACK_KNIGHTS_FORTRESS.stage(player)

    private fun journal(stage: Int): List<String> =
        when (stage) {
            0 -> {
                listOf("I can start this quest by speaking to Sir Amik Varze in the White Knights' Castle in Falador (12 quest points).")
            }

            STARTED -> {
                listOf(
                    "Sir Amik Varze wants me to find out the Black Knights' secret weapon and sabotage it.",
                    "I need an iron chainbody and a bronze medium helmet to pass as a guard, and a cabbage.",
                )
            }

            OVERHEARD -> {
                listOf(
                    "The Black Knights' secret weapon is a potion of invincibility. Only a Draynor Manor cabbage fits it.",
                    "I should drop an ordinary cabbage into the potion through the hole in the fortress's upper floor.",
                )
            }

            RUINED -> {
                listOf("The potion is ruined. I should tell Sir Amik Varze.")
            }

            else -> {
                listOf("I ruined the Black Knights' invincibility potion and Sir Amik Varze paid me 2,500 coins.")
            }
        }

    // ---- Sir Amik Varze

    private suspend fun PlayerScript.amikTalk(amik: NpcRef) {
        val p = player
        when (stage) {
            0 -> {
                npc(amik, "I am the leader of the White Knights of Falador. Why do you seek my audience?")
                when (choice("Select an Option", "I seek a quest!", "I don't, I'm just looking around.")) {
                    1 -> {
                        me("I seek a quest.")
                        // OPEN-QUA14: the Wiki lists 12 quest points to start; Sir Amik's refusal line is not published.
                        if (p.varp(Varps.QP) < QUEST_POINTS_REQUIRED) {
                            npc(amik, "I'm afraid I can only trust somebody who has proven themselves with 12 quest points.")
                            return
                        }
                        offer(amik)
                    }

                    2 -> {
                        me("I don't, I'm just looking around.")
                        npc(amik, "Ok. Please don't break anything.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            STARTED -> {
                npc(amik, "How's the mission going?")
                me("I haven't managed to find what the secret weapon is yet...")
                npc(amik, "Well, keep at it! Falador's future is at stake!")
                if (!p.has(Objs.YAMA_DOSSIER) && !p.flag(FLAG_DOSSIER_READ)) dossier(amik)
            }

            OVERHEARD -> {
                npc(amik, "How's the mission going?")
                me("I've found out what the Black Knight's secret weapon is. It's a potion of invincibility.")
                npc(
                    amik,
                    "A potion of invincibility? That is grave news indeed. If you can sabotage it somehow, you will be very well paid.",
                )
                me("So when I finish this mission for you can I be a white knight? Can I wear your armour too?")
                npc(
                    amik,
                    "I am afraid I cannot authorise you to become a white knight unless under a situation of dire circumstance. " +
                        "Assisting us on a freelance basis will be well worth your time however and you will have my personal gratitude.",
                )
                me("I can't buy stuff with personal gratitude though...")
                npc(amik, "There is of course also the financial recompense as I said.")
                me("Ok! I'll get on to sabotaging that potion.")
            }

            RUINED -> {
                me(
                    "I have ruined the Black Knights' invincibility potion. That should put a stop to your problem and an end to their " +
                        "little schemes.",
                )
                npc(
                    amik,
                    "Yes, we have just received a message from the Black Knights saying they withdraw their demands, which would " +
                        "seem to confirm your story.",
                )
                me("Now I believe there was some talk of a cash reward...")
                npc(amik, "Absolutely right. Please accept this reward.")
                box("Sir Amik hands you 2500 coins.")
                QuestCompletion.complete(p, Quests.BLACK_KNIGHTS_FORTRESS, REWARD)
            }

            else -> {
                // OPEN-QUA15: the post-quest dialogue is not published (the Wiki marks it incomplete).
                npc(amik, "Hello, friend! Thank you again for your help with the Black Knights.")
            }
        }
    }

    private suspend fun PlayerScript.offer(amik: NpcRef) {
        npc(
            amik,
            "Well, I need some spy work doing but it's quite dangerous. It will involve going into the Black Knights' fortress.",
        )
        when (choice("Select an Option", "I laugh in the face of danger!", "I go and cower in a corner at the first sign of danger!")) {
            1 -> {
                me("I laugh in the face of danger!")
                npc(amik, "Well that's good. Don't get too overconfident though.")
            }

            2 -> {
                me("I go and cower in a corner at the first sign of danger!")
                npc(amik, "Err....", "Well.", "I... suppose spy work DOES involve a little hiding in corners.")
                if (choice("Select an Option", "Oh. I suppose I'll give it a go then.", "No, I'm not ready to do that.") != 1) {
                    me("No, I'm not ready to do that.")
                    npc(amik, "Come see me again if you change your mind.")
                    return
                }
                me("Oh. I suppose I'll give it a go then.")
            }

            else -> {
                return
            }
        }
        npc(
            amik,
            "You've come along at just the right time actually. All of my knights are already known to the Black Knights.",
            "Subtlety isn't exactly our strong point.",
        )
        me("Can't you just take your White Knights' armour off? They wouldn't recognise you then!")
        npc(amik, "I am afraid our charter prevents us using espionage in any form, that is the domain of the Temple Knights.")
        me("Temple Knights? Who are they?")
        npc(amik, "That information is classified. I am forbidden to share it with outsiders.")
        me("So... what do you need doing?")
        npc(
            amik,
            "Well, the Black Knights have started making strange threats to us; demanding large amounts of money and land, and " +
                "threatening to invade Falador if we don't pay them.",
            "Now, NORMALLY this wouldn't be a problem...",
            "But they claim to have a powerful new secret weapon.",
            "Your mission, should you decide to accept it, is to infiltrate their fortress, find out what their secret weapon is, " +
                "and then sabotage it.",
        )
        if (choice("Start the Black Knights' Fortress quest?", "Yes.", "No.") != 1) {
            me("No, I'm not ready to do that.")
            npc(amik, "Come see me again if you change your mind.")
            return
        }
        me("Ok, I'll do my best.")
        npc(amik, "Good luck! Let me know how you get on. Here's the dossier for the case, I've already given you the details.")
        Quests.BLACK_KNIGHTS_FORTRESS.advance(player, STARTED)
        dossier(amik)
    }

    /** Gives the dossier (it needs a free slot). */
    private suspend fun PlayerScript.dossier(amik: NpcRef) {
        if (!player.give(Objs.YAMA_DOSSIER, 1, "black knights fortress dossier")) {
            npc(amik, "Oh. You don't appear to have any room for the dossier in your inventory. Come back when you do.")
        }
    }

    /** "Open" on the dossier: it counts down and destroys itself (Wiki "Speaking to Sir Amik Varze again"). */
    private fun PlayerRef.readDossier() {
        message("Infiltrate fortress... sabotage secret weapon... self destruct in 3...")
        message("Infiltrate fortress... sabotage secret weapon... self destruct in 2...")
        message("Infiltrate fortress... sabotage secret weapon... self destruct in 1...")
        if (take(Objs.YAMA_DOSSIER, 1, "black knights fortress dossier read")) setFlag(FLAG_DOSSIER_READ, true)
    }

    // ---- the fortress

    /** Whether the player passes as a Fortress Guard (iron chainbody and bronze med helm) or a Black Knight (full black). */
    private fun PlayerRef.disguised(): Boolean {
        val worn = inv(Invs.WORN)
        val guard = worn.total(Objs.IRON_CHAINBODY) > 0 && worn.total(Objs.BRONZE_MED_HELM) > 0
        val black =
            worn.total(Objs.BLACK_FULL_HELM) > 0 &&
                worn.total(Objs.BLACK_PLATEBODY) > 0 &&
                (worn.total(Objs.BLACK_PLATELEGS) > 0 || worn.total(Objs.BLACK_PLATESKIRT) > 0)
        return guard || black
    }

    private suspend fun PlayerScript.southDoor(door: LocRef) {
        val p = player
        if (p.disguised()) {
            p.stepAcross(door)
            return
        }
        guard("Hey! You can't come in here! This is a high security military installation!")
        when (
            choice(
                "Select an Option",
                "Yes, but I work here!",
                "Oh, sorry.",
                "So who does it belong to?",
            )
        ) {
            1 -> {
                me("Yes, but I work here!")
                guard(
                    "Well, this is the guards' entrance. I might be new here but I can tell you're not a guard.",
                    "You're not even wearing proper guards uniform!",
                )
                if (choice("Select an Option", "Oh pleeeaaase let me in!", "So what is this uniform?") == 1) {
                    me("Oh pleeeaaase let me in!")
                    guard("Go away. You're getting annoying.")
                } else {
                    me("So what is this uniform?")
                    guard("Well you can see me wearing it. It's an iron chainbody and a medium bronze helm.")
                    me("Hmmm... I wonder if I can make that or get some in the local towns....")
                    guard("What was that you muttered?")
                    me("Oh, nothing important!")
                }
            }

            2 -> {
                me("Oh, sorry.")
                guard("Don't let it happen again.")
            }

            3 -> {
                me("So who does it belong to?")
                guard("This fortress belongs to the order of Black Knights known as the Kinshra.")
                me("Oh. Okay, thanks.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.meetingDoor(door: LocRef) {
        guard(
            "I wouldn't go in there if I were you. Those Black Knights are in an important meeting. They said they'd kill " +
                "anyone who went in there!",
        )
        if (choice("Select an Option", "Okay, I won't.", "I don't care. I'm going in anyway.") == 1) {
            me("Okay, I won't.")
            guard("Wise move.")
            return
        }
        me("I don't care. I'm going in anyway.")
        player.stepAcross(door)
    }

    private suspend fun PlayerScript.guard(vararg lines: String) = npcAs(Npcs.FORTRESSGUARD, "Fortress Guard", *lines)

    /** "Listen-at" on the grill: the conversation of the Black Knight Captain, the witch and Greldo (Wiki transcript). */
    private suspend fun PlayerScript.listen() {
        val p = player
        if (stage >= RUINED) {
            witch("It's RUINED! All that work, FIVE years down the drain, who was it! I'll find them and when I do....")
            captain("Erm.. maybe we can save some of it?")
            witch(
                "SAVE some of it? Are you mad!? That cabbage was a normal cabbage, it spoilt the whole thing and now I have to " +
                    "start all over again!",
            )
            captain("No need to... get upset... you're scar ing the cat.")
            witch("'The CAT'? She's not 'THE CAT'!", "She's Maemi... aren't you dear....")
            return
        }
        scene { scene ->
            stageWitchRoom(scene, GRILL_CAMERA)
            captain("So... how's the secret weapon coming along?")
            witch(
                "The invincibility potion is almost ready...",
                "It's taken me FIVE YEARS, but it's almost ready.",
                "Greldo the Goblin here is just going to fetch the last ingredient for me.",
                "It's a specially grown cabbage grown by my cousin Helda who lives in Draynor Manor.",
                "The soil there is slightly magical and it gives the cabbages slight magical properties....",
                "...not to merntion the trees!",
                "Now remember Greldo, only a Draynor Manor cabbage will do! Don't get lazy and bring any old cabbage, THAT would " +
                    "ENTIRELY wreck the potion!",
            )
            npcAs(Npcs.GRELDO, "Greldo", "Yeth, Mithreth.")
        }
        Quests.BLACK_KNIGHTS_FORTRESS.advance(p, OVERHEARD)
    }

    /**
     * The room under the hole where the witch brews (Wiki "Listen-at Grill", "Using a normal cabbage on the hole"): the witch, the
     * Black Knight Captain, Greldo and the cat stand round the cauldron; the camera looks at it from [camera] (OPEN-QUC5).
     */
    private fun stageWitchRoom(scene: Scene, camera: CoordGrid) {
        scene.shot(camera, WITCH_AT, CamHeight.HIGH, CamHeight.LOW)
        scene.actor(Npcs.FORTWITCH, WITCH_AT)
        scene.actor(Npcs.GRILLKNIGHT, WITCH_AT.translate(1, 1))
        scene.actor(Npcs.GRELDO, WITCH_AT.translate(-1, -1))
        scene.actor(Npcs.BKF_CAT, WITCH_AT.translate(2, 0))
    }

    private suspend fun PlayerScript.captain(vararg lines: String) = npcAs(Npcs.GRILLKNIGHT, "Black Knight Captain", *lines)

    private suspend fun PlayerScript.witch(vararg lines: String) = npcAs(Npcs.FORTWITCH, "Witch", *lines)

    private suspend fun PlayerScript.whyWouldI() {
        me("Why exactly would I want to do that?")
    }

    private suspend fun PlayerScript.wrongCabbage() {
        box("This is the wrong sort of cabbage!")
        me("I'm not supposed to be HELPING the witch you know...")
    }

    private suspend fun PlayerScript.cabbageOnHole() {
        val p = player
        if (stage < OVERHEARD) return whyWouldI()
        if (stage >= RUINED) return
        if (!p.take(Objs.CABBAGE, 1, "black knights fortress cabbage")) return
        scene { scene ->
            stageWitchRoom(scene, HOLE_CAMERA)
            box("The player throws the cabbage through the hole.")
            witch("Where has Greldo got to with that magic cabbage!")
            captain("What's that noise?")
            witch("Hopefully Greldo with the cabbage... yes, look here it co....NOOOOOoooo!")
            box("The cabbage falls through the hole in the ceiling and lands in the potion, destroying it.")
            witch("My potion!")
            captain("Oh boy, this doesn't look good!")
            npcAs(Npcs.BKF_CAT, "Black Cat", "Meow!")
            me("Looks like my work here is done. Seems like that's successfully sabotaged their little secret weapon plan.")
        }
        Quests.BLACK_KNIGHTS_FORTRESS.advance(p, RUINED)
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_OPEN = 1
        const val OP_LISTEN = 1
        const val OP_PUSH = 1

        /** Wiki "Black Knights' Fortress": 12 quest points to start. */
        const val QUEST_POINTS_REQUIRED = 12

        /** Quest stages (ours, OPEN-QU1); 4 is the cache's `endstate`. */
        const val STARTED = 1
        const val OVERHEARD = 2
        const val RUINED = 3

        const val FLAG_DOSSIER_READ = "quests.bkf.dossier"

        /**
         * The witch's room: level 0 under the hole at 3031,3507 (cache map 47 54; the grill is at 3026,3507). The NPC positions in it
         * and the camera placements are ours (OPEN-QUC5).
         */
        val WITCH_AT = CoordGrid(3031, 3507, 0)
        val GRILL_CAMERA = CoordGrid(3027, 3505, 0)
        val HOLE_CAMERA = CoordGrid(3034, 3505, 0)

        /**
         * Cache locs of the fortress (placements from `cache-dump find-locs`): sturdy door 3016,3514, door 3020,3515, hole 3031,3507
         * level 1.
         */
        const val STURDY_DOOR_SOUTH = 2337
        const val MEETING_DOOR = 2338
        const val HOLE = 2336
        val FAKE_WALLS = intArrayOf(2341)

        /** Wiki "Black Knights' Fortress" rewards: 3 quest points, 2,500 coins (the 3 November 2026 antique lamps are OPEN-QUA5). */
        val REWARD = QuestReward(model = Objs.COINS, items = listOf(Objs.COINS to 2500))
    }
}
