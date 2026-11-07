@file:Suppress(
    "LongMethod",
    "LargeClass",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "ComplexCondition",
)
// ComplexCondition: the Wiki's item checks.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.LocRef
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
import tp.util.CoordGrid

/**
 * Dragon Slayer I (docs/mechanics/quest-dragon-slayer-i.md): the Guildmaster, Oziach, Duke Horacio's anti-dragon shield, the
 * three map pieces (Melzar's Maze, Thalzar's door and chest, Wormbrain), the Lady Lumbridge, Ned's voyage and Elvarg. The quest
 * variable is varp 176; 0 and 10 are the cache's values, the stages between are ours (OPEN-QU1): 1 started, 2 Oziach set the
 * task, 3 the Guildmaster told of the quest, 4 the ship is bought, 5 its hole is patched, 6 Ned has the map, 7 on Crandor,
 * 8 Elvarg is dead. Which lore questions were asked and which map pieces were taken are account flags. Dialogue is the Wiki
 * "Transcript:Dragon Slayer I". The voyage is not staged (OPEN-QUB12) and the sailing cutscene plays as dialogue.
 */
public class DragonSlayer : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.GUILDMASTER, OP_TALK) { guild ->
            player.stopInteraction()
            guildTalk(guild)
        }
        onOpNpc(Npcs.OZIACH, OP_TALK) { oziach ->
            player.stopInteraction()
            oziachTalk(oziach)
        }
        // The Duke's Talk-to is Rune Mysteries'; it hands over to [dukeDragonSlayer] while this quest is under way.
        onOpNpc(Npcs.ORACLE, OP_TALK) { oracle ->
            player.stopInteraction()
            oracleTalk(oracle)
        }
        // The red-armoured Bentnoze (669) is Goblin Diplomacy's; it hands over to [bentnozeDragonSlayer] mid-quest.
        onOpNpc(Npcs.GENERAL_BENTNOZE, OP_TALK) { bentnoze ->
            player.stopInteraction()
            bentnozeTalk(bentnoze)
        }
        onOpNpc(Npcs.WORMBRAIN, OP_TALK) { wormbrain ->
            player.stopInteraction()
            wormbrainTalk(wormbrain)
        }
        onOpNpc(Npcs.KLARENSE, OP_TALK) { klarense ->
            player.stopInteraction()
            klarenseTalk(klarense)
        }
        onOpNpc(Npcs.NED, OP_TALK) { ned ->
            player.stopInteraction()
            nedTalk(ned)
        }
        onOpNpc(Npcs.SARIM_PUB_SITTING_PATRON, OP_TALK) { ahab ->
            player.stopInteraction()
            ahabTalk(ahab)
        }
        onOpNpc(Npcs.SARIM_PUB_DRINKER_1, OP_TALK) { jack ->
            player.stopInteraction()
            jackTalk(jack)
        }
        onOpNpc(Npcs.SARIM_PUB_DRINKER_2, OP_TALK) { ben ->
            player.stopInteraction()
            benTalk(ben)
        }
        // Map pieces
        for ((a, b) in listOf(MELZAR_PIECE to LOZAR_PIECE, MELZAR_PIECE to THALZAR_PIECE, LOZAR_PIECE to THALZAR_PIECE)) {
            onUseOnItem(a, b) { startScript { joinMaps() } }
        }
        for (piece in listOf(MELZAR_PIECE, THALZAR_PIECE, LOZAR_PIECE)) {
            onItemOp(piece, "Study") { startScript { studyPiece(piece) } }
        }
        // Melzar's Maze
        for ((key, door) in KEY_DOORS) {
            onUseOnLoc(key, door) { loc ->
                player.stopInteraction()
                useKey(key, loc)
            }
        }
        onOpLoc(Locs.FUNCHESTSHUT, OP_FIRST) {
            player.stopInteraction()
            searchChest(MELZAR_PIECE, "melzar")
        }
        // Thalzar's door and chest
        for (item in DOOR_ITEMS) {
            onUseOnLoc(item, THALZAR_DOOR) { door ->
                player.stopInteraction()
                useOnThalzarDoor(item, door)
            }
        }
        onOpLoc(THALZAR_DOOR, OP_FIRST) { door ->
            player.stopInteraction()
            if (player.flag(DOOR_OPEN)) player.passDoor(door) else player.message("The door is locked.")
        }
        onOpLoc(Locs.ORACLECHESTSHUT, OP_FIRST) {
            player.stopInteraction()
            searchChest(THALZAR_PIECE, "thalzar")
        }
        // The Lady Lumbridge
        onUseOnLoc(Objs.WOODPLANK, Locs.DRAGONSLAYER_SHIPHOLE) {
            player.stopInteraction()
            patchHole()
        }
        onOpLoc(Locs.DRAGONSLAYER_SHIPHOLE, OP_FIRST) {
            player.stopInteraction()
            patchHole()
        }
        events.on<DeathEvent> { onDeath(it) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.DRAGON_SLAYER) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.DRAGON_SLAYER.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when (stage(p)) {
            0 -> {
                listOf("I can start this quest by speaking to the Guildmaster in the Champions' Guild.")
            }

            STARTED -> {
                listOf("The Guildmaster told me to talk to Oziach in Edgeville about the rune platebody.")
            }

            OZIACH -> {
                listOf("Oziach wants me to slay the dragon of Crandor. I should ask the Guildmaster how.")
            }

            TOLD -> {
                listOf(
                    "I need the three pieces of the map to Crandor, a ship from Port Sarim, a captain and protection from the dragon's " +
                        "breath (Duke Horacio has an anti-dragon shield).",
                )
            }

            SHIP -> {
                listOf("I bought the Lady Lumbridge. I should patch the hole in her hull with planks and steel nails.")
            }

            REPAIRED -> {
                listOf("The ship is seaworthy. Ned of Draynor Village will captain her if I bring him the map.")
            }

            NED -> {
                listOf("Ned has the map. He waits for me on the Lady Lumbridge.")
            }

            CRANDOR -> {
                listOf("I am on Crandor. Elvarg lairs in the volcano.")
            }

            SLAIN -> {
                listOf("I slew Elvarg. I should tell Oziach in Edgeville.")
            }

            else -> {
                listOf("Oziach sells me his rune platebody now that I slew the dragon of Crandor.")
            }
        }

    private fun asked(p: PlayerRef, key: String): Boolean = p.flag(key)

    // ---- Guildmaster

    private suspend fun PlayerScript.guildTalk(guild: NpcRef) {
        val p = player
        when {
            stage(p) == 0 -> {
                guildStart(guild)
            }

            stage(p) == STARTED -> {
                guildBefore(guild)
            }

            stage(p) == OZIACH -> {
                guildAfter(guild)
                guildLore(guild, first = true)
            }

            stage(p) >= SLAIN && stage(p) < END -> {
                guildSlain(guild)
            }

            else -> {
                npc(guild, "Greetings!")
                if (stage(p) < END) guildAgain(guild)
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the Guildmaster". */
    private suspend fun PlayerScript.guildStart(guild: NpcRef) {
        npc(guild, "Greetings!")
        when (
            choice(
                "Select an Option",
                "What is this place?",
                "Can I have a quest?",
            )
        ) {
            1 -> {
                me("What is this place?")
                npc(
                    guild,
                    "This is the Champions' Guild. Only adventurers who have proved themselves worthy by " +
                        "gaining influence from quests are allowed in here.",
                )
            }

            2 -> {
                me("Can I have a quest?")
                npc(guild, "Aha!")
                npc(
                    guild,
                    "Yes! A mighty and perilous quest fit only for the most powerful champions! And, at the end " +
                        "of it, you will earn the right to wear the legendary rune platebody!",
                )
                if (player.combatLevel < RECOMMENDED_COMBAT || player.level(Stats.MAGIC) < RECOMMENDED_MAGIC) {
                    box(
                        "Before starting this quest, be aware that your combat level is lower than the recommended " +
                            "level of 45. One or more of your skill levels are also lower than recommended.",
                    )
                }
                when (
                    choice(
                        "Start the Dragon Slayer I quest?",
                        "Yes.",
                        "No.",
                    )
                ) {
                    1 -> {
                        QuestsB.DRAGON_SLAYER.advance(player, STARTED)
                        me("So, what is this quest?")
                        npc(
                            guild,
                            "You'll have to speak to Oziach, the maker of rune armour. He sets the quests that " +
                                "champions must complete in order to wear it.",
                        )
                        npc(
                            guild,
                            "Oziach lives in a hut, by the cliffs to the west of Edgeville. He can be a " +
                                "little...odd...sometimes, though.",
                        )
                    }

                    2 -> {
                        me("Actually, I'll give it a miss.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the Guildmaster again before talking to Oziach". */
    private suspend fun PlayerScript.guildBefore(guild: NpcRef) {
        npc(guild, "Greetings!")
        when (
            choice(
                "Select an Option",
                "What is this place?",
                "Can I have a quest?",
            )
        ) {
            1 -> {
                me("What is this place?")
            }

            2 -> {
                me("Can I have a quest?")
                npc(guild, "You're already on a quest for me, if I recall correctly. Have you talked to Oziach yet?")
                me("No, not yet.")
                npc(
                    guild,
                    "Well, he's the only one who can grant you the right to wear rune platemail. He lives in a " +
                        "hut, by the cliffs west of Edgeville.",
                )
                me("Okay, I'll go and talk to him.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the Guildmaster after talking to Oziach". */
    private suspend fun PlayerScript.guildAfter(guild: NpcRef) {
        me("I talked to Oziach and he gave me a quest.")
        npc(guild, "Oh? What did he tell you to do?")
        me("Defeat the dragon of Crandor.")
        npc(guild, "The dragon of Crandor?")
        me("Um, yes...")
        npc(guild, "Goodness, he hasn't given you an easy job, has he?")
        me("What's so special about this dragon?")
        npc(
            guild,
            "Thirty years ago, Crandor was a thriving community with a great tradition of mages and " +
                "adventurers. Many Crandorians even earned the right to be part of the Champions' Guild!",
        )
        QuestsB.DRAGON_SLAYER.advance(player, TOLD)
        npc(
            guild,
            "One of their adventurers went too far, however. He descended into the volcano in the " +
                "centre of Crandor and woke the dragon Elvarg.",
        )
        npc(
            guild,
            "He must have fought valiantly against the dragon because they say that, to this day, she " +
                "has a scar down her side,",
        )
        npc(
            guild,
            "but the dragon still won the fight. She emerged and laid waste to the whole of Crandor " +
                "with her fire breath!",
        )
        npc(
            guild,
            "Some refugees managed to escape in fishing boats. They landed on the coast, north of " +
                "Rimmington, and set up camp but the dragon followed them and burned the camp to the " +
                "ground.",
        )
        npc(
            guild,
            "Out of all the people of Crandor there were only three survivors: a trio of wizards who " +
                "used magic to escape. Their names were Thalzar, Lozar and Melzar.",
        )
        npc(
            guild,
            "If you're serious about taking on Elvarg, first you'll need to get to Crandor, and to my " +
                "knowledge, the only way to do that is by sea.",
        )
        me("So I just sail over there? Seems easy enough.")
        npc(
            guild,
            "If only that were true. The island is surrounded by dangerous underwater reefs. You'll " +
                "need a map to guide you through them and a ship capable of doing so.",
        )
        npc(
            guild,
            "And then of course there's the dragon herself. Once you reach Crandor, you'll need some " +
                "kind of protection against her breath.",
        )
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the Guildmaster after slaying Elvarg". */
    private suspend fun PlayerScript.guildSlain(guild: NpcRef) {
        npc(guild, "Greetings!")
        when (
            choice(
                "Select an Option",
                "What is this place?",
                "I killed the dragon!",
            )
        ) {
            1 -> {
                me("What is this place?")
            }

            2 -> {
                me("I killed the dragon!")
                npc(guild, "Really? That's amazing!")
                npc(
                    guild,
                    "You are a true hero. There's no way that Oziach can refuse to sell you rune platemail now. " +
                        "You should tell him at once!",
                )
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.guildAgain(guild: NpcRef) {
        when (choice("Select an option", "What is this place?", "About my quest to kill the dragon...")) {
            1 -> {
                me("What is this place?")
                npc(
                    guild,
                    "This is the Champions' Guild. Only adventurers who have proved themselves worthy by gaining influence from " +
                        "quests are allowed in here.",
                )
            }

            else -> {
                me("About my quest to kill the dragon...")
                npc(
                    guild,
                    "If you're serious about taking on Elvarg, first you'll need to get to Crandor, and to my knowledge, the only way " +
                        "to do that is by sea.",
                )
                me("So I just sail over there? Seems easy enough.")
                npc(
                    guild,
                    "If only that were true. The island is surrounded by dangerous underwater reefs. You'll need a map to guide you " +
                        "through them and a ship capable of doing so.",
                    "And then of course there's the dragon herself. Once you reach Crandor, you'll need some kind of protection " +
                        "against her breath.",
                )
                guildLore(guild, first = false)
            }
        }
    }

    /** The Guildmaster's questions (Wiki "Talking to the Guildmaster after talking to Oziach"); each one unlocks a step. */
    private suspend fun PlayerScript.guildLore(guild: NpcRef, first: Boolean) {
        val p = player
        val special = "What's so special about this dragon?"
        while (true) {
            val options =
                mutableListOf(
                    "Where can I find a map to Crandor?",
                    "What kind of ship do I need?",
                    "How can I protect myself from the dragon's breath?",
                )
            if (first) options.add(0, special)
            options.add("Okay, I'll get going!")
            when (options[choice("Select an Option", *options.toTypedArray()) - 1]) {
                special -> {
                    me(special)
                    npc(
                        guild,
                        "Thirty years ago, Crandor was a thriving community with a great tradition of mages and adventurers. Many " +
                            "Crandorians even earned the right to be part of the Champions' Guild!",
                        "One of their adventurers went too far, however. He descended into the volcano in the centre of Crandor and " +
                            "woke the dragon Elvarg.",
                        "He must have fought valiantly against the dragon because they say that, to this day, she has a scar down her " +
                            "side,",
                        "but the dragon still won the fight. She emerged and laid waste to the whole of Crandor with her fire breath!",
                        "Some refugees managed to escape in fishing boats. They landed on the coast, north of Rimmington, and set up " +
                            "camp but the dragon followed them and burned the camp to the ground.",
                        "Out of all the people of Crandor there were only three survivors: a trio of wizards who used magic to escape. " +
                            "Their names were Thalzar, Lozar and Melzar.",
                    )
                }

                "Where can I find a map to Crandor?" -> {
                    me("Where can I find a map to Crandor?")
                    npc(
                        guild,
                        "After what happened, people were afraid of enraging the dragon. Most maps to the island were destroyed, but " +
                            "I have heard that one survived. It was held by Melzar, Thalzar and Lozar.",
                        "However, the three apparently split the map into three pieces, which each of them taking one piece.",
                    )
                    guildPieces(guild)
                }

                "What kind of ship do I need?" -> {
                    me("What kind of ship do I need?")
                    npc(
                        guild,
                        "Quite a specific one I'm afraid. Even with a map, very few ships will be able to navigate the reefs around " +
                            "the " +
                            "island. You're going to need one of Crandorian design.",
                        "If there's still one in existence, it's probably in Port Sarim. However, a ship will also need a captain, and " +
                            "I'm not sure where you'd find one willing to sail to Crandor!",
                    )
                    p.setFlag(ASKED_SHIP, true)
                }

                "How can I protect myself from the dragon's breath?" -> {
                    me("How can I protect myself from the dragon's breath?")
                    npc(
                        guild,
                        "That part shouldn't be too difficult, actually. I believe the Duke of Lumbridge has a special shield in his " +
                            "armoury that is enchanted against dragon's breath.",
                    )
                    p.setFlag(ASKED_SHIELD, true)
                }

                else -> {
                    me("Okay, I'll get going!")
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.guildPieces(guild: NpcRef) {
        val p = player
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "Where is Melzar's map piece?",
                    "Where is Thalzar's map piece?",
                    "Where is Lozar's map piece?",
                    "Back",
                )
            ) {
                1 -> {
                    me("Where is Melzar's map piece?")
                    npc(
                        guild,
                        "Melzar built a castle on the site of the Crandorian refugee camp, north of Rimmington. He's locked himself " +
                            "in there and no one's seen him for years.",
                        "The inside of his castle is like a maze, and is populated by undead monsters. Maybe, if you could get all " +
                            "the way through the maze, you could find his piece of the map.",
                        "Adventurers sometimes go in there to prove themselves, so I can give you this key to Melzar's Maze.",
                    )
                    p.setFlag(ASKED_MELZAR, true)
                    if (!p.has(Objs.MELZARKEY) && p.giveOrDrop(Objs.MELZARKEY, 1, "dragon slayer maze key")) {
                        itemBox(Objs.MELZARKEY, "The Guildmaster hands you a key.")
                    }
                }

                2 -> {
                    me("Where is Thalzar's map piece?")
                    npc(
                        guild,
                        "Thalzar was the most paranoid of the three wizards. He hid his map piece and took the secret of its location " +
                            "to his grave.",
                        "I don't think you'd be able to find out where it is by ordinary means. You'll need to talk to the Oracle on " +
                            "Ice Mountain.",
                    )
                    p.setFlag(ASKED_THALZAR, true)
                }

                3 -> {
                    me("Where is Lozar's map piece?")
                    npc(
                        guild,
                        "A few weeks ago, I'd have told you to speak to Lozar herself, in her house across the river from Lumbridge.",
                        "Unfortunately, goblin raiders killed her and stole everything. One of the goblins from the Goblin Village " +
                            "probably has the map piece now.",
                    )
                    p.setFlag(ASKED_LOZAR, true)
                }

                else -> {
                    return
                }
            }
        }
    }

    // ---- Oziach

    private suspend fun PlayerScript.oziachTalk(oziach: NpcRef) {
        val p = player
        when {
            stage(p) == STARTED -> {
                oziachFirst(oziach)
            }

            stage(p) in OZIACH until SLAIN -> {
                oziachBefore(oziach)
            }

            stage(p) == SLAIN -> {
                oziachFinal(oziach)
            }

            else -> {
                npc(oziach, "Aye, 'tis a fair day my friend.")
                // Wiki "Talking to Oziach": the rune platebody is sold through the Trade op after the quest.
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Oziach". */
    private suspend fun PlayerScript.oziachFirst(oziach: NpcRef) {
        npc(oziach, "Aye, 'tis a fair day my friend.")
        when (
            choice(
                "Select an Option",
                "Can you sell me a rune platebody?",
                "I'm not your friend.",
                "Yes, it's a very nice day.",
            )
        ) {
            1 -> {
                me("Can you sell me a rune platebody?")
                npc(oziach, "So, how does thee know I 'ave some?")
                when (
                    choice(
                        "Select an Option",
                        "The Guildmaster of the Champions' Guild told me.",
                        "I am a master detective.",
                    )
                ) {
                    1 -> {
                        me("The Guildmaster of the Champions' Guild told me.")
                        npc(
                            oziach,
                            "Yes, I suppose he would, wouldn't he? He's always sending you fancy-pants 'heroes' up to " +
                                "bother me. Telling me I'll give them a quest or sommat like that.",
                        )
                        npc(
                            oziach,
                            "Well, I'm not going to let just anyone wear my rune platemail. It's only for heroes. So, " +
                                "leave me alone.",
                        )
                        when (
                            choice(
                                "Select an Option",
                                "I thought you were going to give me a quest.",
                                "That's a pity, I'm not a hero.",
                            )
                        ) {
                            1 -> {
                                me("I thought you were going to give me a quest.")
                                npc(oziach, "*Sigh*")
                                npc(oziach, "All right, I'll give ye a quest. I'll let ye wear my rune platemail if ye...")
                                npc(oziach, "Slay the dragon of Crandor!")
                                QuestsB.DRAGON_SLAYER.advance(player, OZIACH)
                                when (
                                    choice(
                                        "Select an Option",
                                        "A dragon, that sounds like fun.",
                                        "I may be a champion, but I don't think I'm up to dragon-killing yet.",
                                    )
                                ) {
                                    1 -> {
                                        me("A dragon, that sounds like fun!")
                                        npc(
                                            oziach,
                                            "Hah, yes, you are a typical reckless adventurer, aren't you? Now go kill the dragon and " +
                                                "get out of my face.",
                                        )
                                        me("But how can I defeat the dragon?")
                                        npc(
                                            oziach,
                                            "Go talk to the Guildmaster in the Champions' Guild. He'll help ye out if yer so keen on " +
                                                "doing a quest. I'm not going to be handholding any adventurers.",
                                        )
                                    }

                                    2 -> {
                                        me("I may be a champion, but I don't think I'm up to dragon-killing yet.")
                                        npc(oziach, "Yes, I can understand that. Yer a coward.")
                                    }

                                    else -> {
                                        Unit
                                    }
                                }
                            }

                            2 -> {
                                me("That's a pity, I'm not a hero.")
                                npc(oziach, "Aye, I ken tell!")
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    2 -> {
                        me("I am a master detective.")
                        if (player.questDone("Murder Mystery")) {
                            me("I solved the Sinclair murder mystery by myself!")
                            npc(oziach, "Never 'eard of yer.")
                        }
                        npc(
                            oziach,
                            "Well, however you found out about it, I'm not going to let just anyone wear my rune " +
                                "platemail. It's only for heroes. So leave me alone.",
                        )
                        when (
                            choice(
                                "Select an Option",
                                "I thought you were going to give me a quest.",
                                "That's a pity, I'm not a hero.",
                            )
                        ) {
                            1 -> {
                            }

                            2 -> {
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("I'm not your friend.")
            }

            3 -> {
                me("Yes, it's a very nice day.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Oziach before slaying the dragon". */
    private suspend fun PlayerScript.oziachBefore(oziach: NpcRef) {
        npc(oziach, "Have ye slayed that dragon yet?")
        me("Um... no.")
        npc(oziach, "Be off with ye then.")
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Oziach after slaying Elvarg". */
    private suspend fun PlayerScript.oziachFinal(oziach: NpcRef) {
        npc(oziach, "Have ye slayed that dragon yet?")
        me("Yes, I have!")
        if (player.has(Objs.DRAGON_SLAYER_QIP_ELVARGS_HEAD)) {
            me("I have its head here.")
        }
        npc(oziach, "You actually did it?")
        npc(oziach, "I underestimated ye, adventurer. I apologise.")
        npc(oziach, "Yer a true hero, and I'd be happy to sell ye rune platebodies.")
        QuestCompletion.complete(player, QuestsB.DRAGON_SLAYER, REWARD)
    }

    // ---- Duke Horacio, the Oracle, the goblins

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Duke Horacio". */
    internal suspend fun PlayerScript.dukeTalk(duke: NpcRef) {
        npc(duke, "Greetings. Welcome to my castle.")
        when (
            choice(
                "Select an Option",
                "I seek a shield that will protect me from dragonbreath.",
                "Have you any quests for me?",
                "Where can I find money?",
            )
        ) {
            1 -> {
                me("I seek a shield that will protect me from dragonbreath.")
                npc(duke, "A knight going on a dragon quest, hmm? What dragon do you intend to slay?")
                when (
                    choice(
                        "Select an Option",
                        "Elvarg, the dragon of Crandor island!",
                        "Oh, no dragon in particular",
                    )
                ) {
                    1 -> {
                        me("Elvarg, the dragon of Crandor island!")
                        npc(duke, "Elvarg? Are you sure?")
                        when (
                            choice(
                                "Well, are you sure?",
                                "Yes",
                                "No",
                            )
                        ) {
                            1 -> {
                                me("Yes!")
                                npc(duke, "Well, you're a braver man than I!")
                                me("Why is everyone so scared of this dragon?")
                                npc(
                                    duke,
                                    "Back in my father's day, Crandor was an important city-state. Politically, it was as " +
                                        "important as Falador or Varrock and its ships traded with every port.",
                                )
                                npc(
                                    duke,
                                    "But, one day when I was little, all contact was lost. The trading ships and the diplomatic " +
                                        "envoys just stopped coming.",
                                )
                                npc(
                                    duke,
                                    "I remember my father being very scared. He posted lookouts on the roof to warn if the " +
                                        "dragon was approaching. All the city rulers were worried that Elvarg would devastate the " +
                                        "whole continent.",
                                )
                                when (
                                    choice(
                                        "Select an Option",
                                        "I'd better leave that dragon alone.",
                                        "So, are you going to give me the shield or not?",
                                    )
                                ) {
                                    1 -> {
                                        me("I'd better leave that dragon alone.")
                                        npc(
                                            duke,
                                            "That's a relief. I would hate to see such a promising adventurer cut down in " +
                                                "[his/her/their] prime.",
                                        )
                                    }

                                    2 -> {
                                        me("So, are you going to give me the shield or not?")
                                        npc(
                                            duke,
                                            "If you really think you're up to it then perhaps you are the one who can kill this dragon.",
                                        )
                                        itemBox(Objs.ANTIDRAGONBREATHSHIELD, "The Duke hands you a heavy orange shield.")
                                        player.giveOrDrop(Objs.ANTIDRAGONBREATHSHIELD, 1, "dragon slayer shield")
                                        npc(duke, "Take care out there. If you kill it...")
                                        npc(duke, "If you kill it, for Saradomin's sake make sure it's really dead!")
                                    }

                                    else -> {
                                        Unit
                                    }
                                }
                            }

                            2 -> {
                                me("No.")
                                npc(duke, "Very wise. There are some monsters that are best left alone.")
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    2 -> {
                        me("Oh, no dragon in particular. I just feel like killing a dragon.")
                        npc(
                            duke,
                            "I don't have an infinite supply of these shields, you know. I'll only give one for a truly " +
                                "worthy cause.",
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Have you any quests for me?")
            }

            3 -> {
                me("Where can I find money?")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the Oracle". */
    private suspend fun PlayerScript.oracleMenu(oracle: NpcRef) {
        when (
            choice(
                "Select an Option",
                "I seek a piece of the map to the island of Crandor.",
                "Can you impart your wise knowledge to me, O Oracle?",
            )
        ) {
            1 -> {
                me("I seek a piece of the map to the island of Crandor.")
                npc(
                    oracle,
                    "The map's behind a door below, but entering is rather tough. This is what you need to " +
                        "know: You must use the following stuff.",
                )
                npc(
                    oracle,
                    "First, a drink used by a mage. Next, some worm string, changed to sheet. Then, a small " +
                        "crustacean cage. Last, a bowl that's not seen heat.",
                )
            }

            2 -> {
                me("Can you impart your wise knowledge to me, O Oracle?")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.oracleTalk(oracle: NpcRef) {
        // Wiki: without having asked the Guildmaster about Thalzar's piece the option "I seek a piece of the map" is not offered.
        if (!asked(player, ASKED_THALZAR)) {
            if (choice("Select an Option", "Can you impart your wise knowledge to me, O Oracle?") == 1) {
                me("Can you impart your wise knowledge to me, O Oracle?")
            }
            return
        }
        oracleMenu(oracle)
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to the goblin generals". */
    internal suspend fun PlayerScript.bentnozeMenu(bentnoze: NpcRef) {
        when (
            choice(
                "Select an Option",
                "I've heard that one of your number has got hold of part of a map.",
                "So how is life for the goblins?",
            )
        ) {
            1 -> {
                me("I've heard that one of your number has got hold of part of a map.")
                npc(bentnoze, "Aha, that'd be Wormbrain.")
                me("Where would he be?")
                npc(bentnoze, "Wormbrain steals too much. He got caught. Now he lives in Port Sarim town jail.")
            }

            2 -> {
                me("So how is life for the goblins?")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.bentnozeTalk(bentnoze: NpcRef) {
        bentnozeMenu(bentnoze)
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Wormbrain". */
    private suspend fun PlayerScript.wormbrainMenu(wormbrain: NpcRef) {
        npc(wormbrain, "Whut you want?")
        when (
            choice(
                "Select an Option",
                "I believe you've got a piece of map that I need.",
                "What are you in for?",
                "Sorry, thought this was a zoo.",
            )
        ) {
            1 -> {
                me("I believe you've got piece of a map that I need.")
                npc(wormbrain, "So? Why should I be giving it to you? What you do for Wormbrain?")
                when (
                    choice(
                        "Select an Option",
                        "I'm not going to do anything for you. Forget it.",
                        "I'll let you live. I could just kill you.",
                        "I suppose I could pay you for the map piece...",
                        "Where did you get the map piece from?",
                    )
                ) {
                    1 -> {
                        me("I'm not going to do anything for you. Forget it.")
                        npc(wormbrain, "Be dat way then.")
                    }

                    2 -> {
                        me("I'll let you live. I could just kill you.")
                        npc(wormbrain, "Ha! Me in here and you out dere. You not get map piece.")
                    }

                    3 -> {
                        me("I suppose I could pay you for the map piece. Say, 500 coins?")
                        npc(wormbrain, "Me not stoopid, it worth at least 10,000 coins!")
                        when (
                            choice(
                                "Select an Option",
                                "You must be joking! Forget it.",
                                "Alright then, 10,000 it is.",
                            )
                        ) {
                            1 -> {
                                me("You must be joking! Forget it.")
                                npc(wormbrain, "Fine, you not get map piece.")
                            }

                            2 -> {
                                if (!player.has(Objs.COINS, WORMBRAIN_FEE)) {
                                    me("Alright then, 10,000 coins it is.")
                                    me("Oops, I don't have enough on me.")
                                    npc(wormbrain, "Comes back when you has enough.")
                                    return
                                }
                                if (player.has(Objs.COINS, WORMBRAIN_FEE)) {
                                    me("Alright then, 10,000 coins it is.")
                                    itemBox(LOZAR_PIECE, "You buy the map piece from Wormbrain.")
                                    player.take(Objs.COINS, WORMBRAIN_FEE, "dragon slayer wormbrain")
                                    player.giveOrDrop(LOZAR_PIECE, 1, "dragon slayer map piece")
                                    player.setFlag(GOT_LOZAR, true)
                                    npc(wormbrain, "Fank you very much! Now me can bribe da guards, hehehe.")
                                }
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    4 -> {
                        me("Where did you get the map piece from?")
                        npc(wormbrain, "We rob house of stupid wizard. She very old, not put up much fight at all. Hahaha!")
                        me("Uh... Hahaha.")
                        npc(
                            wormbrain,
                            "Her house full of pictures of a city on island and old pictures of people. Me not " +
                                "recognise island.",
                        )
                        npc(wormbrain, "Me find map piece. Me not know what it is, but it in locked box so me figure it important.")
                        npc(
                            wormbrain,
                            "But, by the time me get box open, other goblins gone. Then me not run fast enough and " +
                                "guards catch me.",
                        )
                        npc(wormbrain, "But now you want map piece so must be special! What you do for me to get it?")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("What are you in for?")
                npc(wormbrain, "Me not sure. Me pick some stuff up and take it away.")
                me("Well, did the stuff belong to you?")
                npc(wormbrain, "Umm...no.")
                me("Well, that would be why then.")
                npc(wormbrain, "Oh, right.")
            }

            3 -> {
                me("Sorry, thought this was a zoo.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.wormbrainTalk(wormbrain: NpcRef) {
        wormbrainMenu(wormbrain)
    }

    // ---- the map pieces

    private suspend fun PlayerScript.joinMaps() {
        val p = player
        if (!(p.has(MELZAR_PIECE) && p.has(THALZAR_PIECE) && p.has(LOZAR_PIECE))) return p.message("You still need one more piece of map.")
        val done =
            p.transaction("dragon slayer map") {
                remove(Invs.INV, MELZAR_PIECE, 1) &&
                    remove(Invs.INV, THALZAR_PIECE, 1) &&
                    remove(Invs.INV, LOZAR_PIECE, 1) &&
                    add(Invs.INV, Objs.DRAGONMAP, 1)
            }
        if (done) {
            itemBox(
                Objs.DRAGONMAP,
                "You put the three pieces together and assemble a map that shows the route through the reefs to Crandor.",
            )
        }
    }

    private suspend fun PlayerScript.studyPiece(piece: Int) {
        val text =
            when (piece) {
                MELZAR_PIECE -> "This is a piece of map that you found in Melzar's Maze."
                THALZAR_PIECE -> "This is a piece of map that you found in a secret chest in the Dwarven Mine."
                else -> "This is a piece of map that you got from Wormbrain, the goblin thief."
            }
        itemBox(piece, "$text You will need to join it to the other two map pieces before you can see the route to Crandor.")
    }

    /** The chests of Melzar and Thalzar (Wiki "Searching the chest"). */
    private suspend fun PlayerScript.searchChest(piece: Int, owner: String) {
        val p = player
        if (stage(p) < TOLD || p.flag("quests.ds1.got_$owner") || p.has(piece) || p.has(Objs.DRAGONMAP)) {
            return p.message("You search the chest but find nothing.")
        }
        if (piece == THALZAR_PIECE) {
            box("As you open the chest, you notice an inscription on the lid:")
            box(
                "Here I rest the map to my beloved home. To whoever finds it, I beg of you, let it be. I was honour-bound not to " +
                    "destroy the map piece, but I have used all my magical skill to keep it from being recovered.",
            )
            box(
                "This map leads to the lair of the beast that destroyed my home, devoured my family, and burned to a cinder all that " +
                    "I love. But revenge would not benefit me now, and to disturb this beast is to risk bringing its wrath down upon " +
                    "another land.",
            )
            box(
                "I cannot stop you from taking this map piece now, but think on this: if you can slay the Dragon of Crandor, you are " +
                    "a greater hero than my land ever produced. There is no shame in backing out now.",
            )
        }
        if (!p.give(piece, 1, "dragon slayer map piece")) return p.message("You don't have enough inventory space.")
        p.setFlag("quests.ds1.got_$owner", true)
        itemBox(piece, "You find a map piece in the chest.")
    }

    // ---- Melzar's Maze

    private suspend fun PlayerScript.useKey(key: Int, door: LocRef) {
        val p = player
        if (!p.take(key, 1, "melzar's maze key")) return
        p.message("The key disintegrates as it unlocks the door.")
        p.passDoor(door)
    }

    /**
     * Wiki "Melzar's Maze": every monster group drops the key of its door. OPEN-QUB10: which variant of a group carries the key
     * (the Wiki names "the skeleton with the round shield" etc.) is not in the cache's names, so [KEY_FROM_ANY_VARIANT] lets every
     * variant drop it; set it false to restrict the drop to the first id of each group.
     */
    private fun onDeath(e: DeathEvent) {
        val npc = e.entity as? NpcRef ?: return
        val killer = e.lootFor ?: return
        if (npc.type == Npcs.ELVARG) {
            if (stage(killer) in CRANDOR until SLAIN) {
                QuestsB.DRAGON_SLAYER.advance(killer, SLAIN)
                killer.giveOrDrop(Objs.DRAGON_SLAYER_QIP_ELVARGS_HEAD, 1, "dragon slayer elvarg's head")
                killer.message("You cut off Elvarg's head as proof of your victory!")
            }
            return
        }
        val key = MAZE_DROPS[npc.type] ?: return
        if (stage(killer) < STARTED || killer.has(key)) return
        if (!KEY_FROM_ANY_VARIANT && npc.type != MAZE_FIRST_VARIANT[key]) return
        killer.transaction("melzar's maze key") { createOnGround(key, 1, npc.coord, killer.username) }
    }

    // ---- Thalzar's door

    private suspend fun PlayerScript.useOnThalzarDoor(item: Int, door: LocRef) {
        val p = player
        val bit = DOOR_ITEMS.indexOf(item)
        val have = (p.saved(DOOR_BITS) ?: 0L).toInt()
        if (p.flag(DOOR_OPEN)) return p.passDoor(door)
        if (!p.take(item, 1, "thalzar's door")) return
        p.message(
            when (item) {
                Objs.SILK -> "You put the silk into the opening in the door."
                Objs.LOBSTER_POT -> "You put the Lobster pot into the opening in the door."
                Objs.BOWL_UNFIRED -> "You put the unfired bowl into the opening in the door."
                else -> "You pour the Wizard's Mind Bomb into the opening in the door."
            },
        )
        val now = have or (1 shl bit)
        p.setSaved(DOOR_BITS, now.toLong())
        if (now == (1 shl DOOR_ITEMS.size) - 1) {
            p.setFlag(DOOR_OPEN, true)
            p.message("The door opens...")
            p.passDoor(door)
        }
    }

    // ---- the Lady Lumbridge

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Klarense". */
    private suspend fun PlayerScript.klarenseSale(klarense: NpcRef) {
        npc(
            klarense,
            "Hello there! Interested in buying this fine ship? I'm sure I can offer her to you for a " +
                "much better price than any of those shipwrights ask for!",
        )
        me("A much better price? What's the catch?")
        npc(klarense, "Well, she's not quite seaworthy right now...")
        me("Not seaworthy? Why would I want to buy her then?")
        npc(
            klarense,
            "Because this isn't just any ship! This is the Lady Lumbridge, one of the last Crandorian " +
                "ships! Despite her size, she's one of the most agile ship{{sic|Should be \"ships\"}} " +
                "around!",
        )
        me("Except she's not seaworthy...")
        npc(klarense, "No, but a bit of work will quickly sort that out!")
        when (
            choice(
                "Select an Option",
                "I'd like to buy her.",
                "Why is she damaged?",
                "I think I'll pass.",
            )
        ) {
            1 -> {
                me("I'd like to buy her.")
                npc(klarense, "Of course! I'm sure the work needed to do on it wouldn't be too expensive.")
                npc(
                    klarense,
                    "How does 2,000 gold sound? I'll even throw in my cabin boy, Jenkins, for free! He'll swab " +
                        "the decks and splice the mainsails for you!",
                )
                when (
                    choice(
                        "Select an Option",
                        "Yep, sounds good.",
                        "I'm not paying that much for a broken boat!",
                    )
                ) {
                    1 -> {
                        if (!player.has(Objs.COINS, SHIP_PRICE)) {
                            me("Yep, sounds good.")
                            me("...except I don't have that kind of money on me...")
                            return
                        }
                        if (player.has(Objs.COINS, SHIP_PRICE)) {
                            me("Yep, sounds good.")
                            npc(klarense, "Okey-dokey! She's all yours!")
                            player.take(Objs.COINS, SHIP_PRICE, "dragon slayer ship")
                            QuestsB.DRAGON_SLAYER.advance(player, SHIP)
                        }
                    }

                    2 -> {
                        me("I'm not paying that much for a broken boat!")
                        npc(klarense, "That's fair enough, I suppose.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Why is she damaged?")
                npc(
                    klarense,
                    "Oh, there was no particular accident, just years of wear and tear. It's been a long time " +
                        "since Crandor was destroyed and she's had several owners since then. Unfortunately, not " +
                        "all of them looked after her.",
                )
            }

            3 -> {
                me("I think I'll pass.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Klarense after purchasing the Lady Lumbridge". */
    private suspend fun PlayerScript.klarenseOwner(klarense: NpcRef) {
        npc(
            klarense,
            "Hello, captain! Here to inspect your new ship? Just a little work and she'll be seaworthy " +
                "again.",
        )
        when (
            choice(
                "Select an Option",
                "So, what needs fixing on the ship?",
                "Would you sail this ship to Crandor for me?",
                "What are you going to do now you don't have a ship?",
                "Can I board the ship now?",
            )
        ) {
            1 -> {
                if (stage(player) < REPAIRED) {
                    me("So, what needs fixing on this ship?")
                    npc(
                        klarense,
                        "Well, the big gaping hole in the hold is the problem. You'll need a few wooden planks " +
                            "hammered in with steel nails to fix it.",
                    )
                }
                if (stage(player) >= REPAIRED) {
                    npc(klarense, "I dunno. It looks fine to me.")
                    me("But you said it needed fix-")
                    npc(klarense, "Yes, but that was before you fixed it!")
                }
            }

            2 -> {
                me("So would you like to sail this ship to Crandor for me?")
                npc(klarense, "Crandor? You're joking, right?")
                when (
                    choice(
                        "Select an Option",
                        "Yes. Ha ha ha!",
                        "No. I want to go to Crandor.",
                    )
                ) {
                    1 -> {
                        me("Yes. Ha ha ha!")
                        npc(
                            klarense,
                            "Crandor's not something we sailors joke about. You can't sail from here to Catherby, or " +
                                "Entrana, or Ardougne without going past that accursed island.",
                        )
                        npc(klarense, "You can't get close to it because of the reefs, but you can always see it.")
                        npc(
                            klarense,
                            "Sometimes you can see a dark shape in the sky, circling the island. That's when you have " +
                                "to sail on as quick as you can and pray it's not hungry. Every year, more ships are lost " +
                                "to that dragon.",
                        )
                    }

                    2 -> {
                        me("No. I want to go to Crandor.")
                        npc(klarense, "Then you must be crazy.")
                        npc(
                            klarense,
                            "That island is surrounded by reefs that would rip this ship to shreds. Even if you found a " +
                                "map, you'd need an experienced captain to stand a chance of getting through",
                        )
                        npc(
                            klarense,
                            "and, even if I could get to it, there's no way I'm going any closer to that dragon than I " +
                                "have to. They say it can destroy whole ships with one bite.",
                        )
                    }

                    else -> {
                        Unit
                    }
                }
            }

            3 -> {
                me("What are you going to do now you don't have a ship?")
                npc(klarense, "Oh, I'll be fine. I've got work as Port Sarim's first lifeguard!")
            }

            4 -> {
                me("Can I board the ship now?")
                npc(klarense, "Sure thing, she's all yours.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.klarenseTalk(klarense: NpcRef) {
        if (stage(player) >= SHIP) klarenseOwner(klarense) else klarenseSale(klarense)
    }

    /** Wiki "Reparing the Lady Lumbridge": three planks, 30 steel nails per plank and a hammer. */
    private suspend fun PlayerScript.patchHole() {
        val p = player
        if (stage(p) != SHIP) return p.message("There is nothing to patch.")
        when {
            !p.has(Objs.WOODPLANK) -> return box("You'll need to use wooden planks on this hole to patch it up.")

            !p.has(
                Objs.NAILS,
                NAILS_PER_PLANK,
            ) && !p.has(Objs.ANY_NAILS, NAILS_PER_PLANK) -> return box("You need 30 steel nails to attach the plank with.")

            !p.has(Objs.HAMMER) -> return box("You need a hammer to force the nails in with.")
        }
        val nails = if (p.has(Objs.NAILS, NAILS_PER_PLANK)) Objs.NAILS else Objs.ANY_NAILS
        if (!p.transaction(
                "dragon slayer plank",
            ) { remove(Invs.INV, Objs.WOODPLANK, 1) && remove(Invs.INV, nails, NAILS_PER_PLANK) }
        ) {
            return
        }
        val done = (p.saved(PLANKS) ?: 0L).toInt() + 1
        p.setSaved(PLANKS, done.toLong())
        p.anim(tp.symbols.Seqs.HUMAN_PICKUPTABLE)
        when (done) {
            1 -> {
                box("You nail a plank over the hole, but you still need more planks to close the hole completely.")
            }

            PLANKS_NEEDED - 1 -> {
                box("You nail a plank over the hole, but you still need one more plank to close the hole completely.")
            }

            else -> {
                box("You nail a final plank over the hole. You have successfully patched the hole in the ship.")
                QuestsB.DRAGON_SLAYER.advance(p, REPAIRED)
            }
        }
    }

    // ---- finding a captain

    /** Wiki "Transcript:Dragon Slayer I", section "Ahab". */
    private suspend fun PlayerScript.ahabTalk(ahab: NpcRef) {
        npc(ahab, "Arrr, matey!")
        when (
            choice(
                "Select an Option",
                "Arrr!",
                "Can you help me get to Crandor Island?",
                "Are you going to sit there all day?",
                "Do you want to trade?",
            )
        ) {
            1 -> {
                me("Arrr!")
            }

            2 -> {
                me("Can you help me get to Crandor Island?")
                npc(ahab, "Arrr, matey, ye canna be serious! That be the home of Elvarg the Dragon!")
                me("Yes, that's right. I 've got to go there and kill the dragon for this quest I'm doing.")
                npc(
                    ahab,
                    "I canna help ye. And right sorry I be to see a fine young [laddie/lassie/'un] like yerself " +
                        "go off to yer doom.",
                )
                me("Thanks, but I'll be fine!")
                npc(ahab, "Arrr...")
            }

            3 -> {
                me("Are you going to sit there all day?")
            }

            4 -> {
                me("Do you want to trade?")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Jack Seagull". */
    private suspend fun PlayerScript.jackTalk(jack: NpcRef) {
        npc(jack, "Arrr, matey!")
        when (
            choice(
                "Select an Option",
                "Have you got any quests I could do?",
                "What are you doing here?",
                "Can you help me get to Crandor Island?",
            )
        ) {
            1 -> {
                me("Have you got any quests I could do?")
            }

            2 -> {
                me("What are you doing here?")
            }

            3 -> {
                me("Can you help me get to Crandor Island?")
                npc(jack, "Crandor Island, ye say?")
                npc(jack, "Arrr, keep yeself away from that accursed place if ye values yer skin!")
                me("So that's a 'No', then?")
                npc(jack, "Aye, that it be.")
                me("Do you know anyone who would take me there?")
                npc(jack, "No, I don't know many idiots. Except for young Ben there.")
                npcAs(Npcs.SARIM_PUB_DRINKER_2, "Longbow Ben", "Arrr, get away with ye, ye scurvy scallywag!")
                me("Okay, thanks anyway.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Longbow Ben". */
    private suspend fun PlayerScript.benTalk(ben: NpcRef) {
        npc(ben, "Arrr, matey!")
        when (
            choice(
                "Select an Option",
                "Have you got any quests I could do?",
                "Why are you called Longbow Ben?",
                "Can you help me get to Crandor Island?",
            )
        ) {
            1 -> {
                me("Have you got any quests I could do?")
            }

            2 -> {
                me("Why are you called Longbow Ben?")
            }

            3 -> {
                me("Can you help me get to Crandor Island?")
                npc(
                    ben,
                    "Arrr, ye may be wanting to go to Crandor Island now, but if ye gets there, ye'll be " +
                        "a-wantin' to get off it again quickly enough!",
                )
                me("So that's a 'No', then?")
                npc(ben, "[Aye, laddie/Aye, lassie/Aye], that it is.")
                me("Do you know anyone who would take me there?")
                npc(
                    ben,
                    "There's only one sailor alive daft enough to go there. Old Ned, the ropemaker in Draynor " +
                        "Village. I can't think of any other fools just now. Except for old Jack Seagull there.",
                )
                npcAs(Npcs.SARIM_PUB_DRINKER_1, "Jack Seagull", "Arrr, who are ye calling old?")
                me("Okay, thanks anyway.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Prince Ali Rescue", section "Talking to Ned". */
    private suspend fun PlayerScript.nedWigMenu(ned: NpcRef) {
        npc(
            ned,
            "Why, hello there, [lad/lass/kid]. Me friends call me Ned. I was a man of the sea, but it's " +
                "past me now. Could I be making or selling you some rope?",
        )
        when (
            choice(
                "Select an Option",
                "Could you make other things apart from rope?",
                "Yes, I would like some rope.",
                "No thanks, Ned. I don't need any.",
            )
        ) {
            1 -> {
                me("Could you make other things apart from rope?")
                npc(ned, "I'm sure I can. What are you thinking of?")
                when (
                    choice(
                        "Select an Option",
                        "Could you knit me a sweater?",
                        "How about some sort of wig?",
                        "Could you repair the arrow holes in the back of my shirt?",
                        "Actually, I don't need anything.",
                    )
                ) {
                    1 -> {
                        me("Could you knit me a sweater?")
                        npc(
                            ned,
                            "Do I look like a member of a sewing circle? Be off wi' you. I have fought monsters that " +
                                "would turn your hair blue.",
                        )
                        npc(ned, "I don't need to be laughed at just 'cos I'm getting a bit old.")
                    }

                    2 -> {
                        me("How about some sort of wig?")
                        npc(
                            ned,
                            "Well... that's an interesting thought. Yes, I think I could do something. Give me three " +
                                "balls of wool and I might be able to do it.",
                        )
                        if (!player.has(Objs.BALL_OF_WOOL, WOOL)) {
                            me("Great, I will get some. I think a wig would be useful.")
                            return
                        }
                        if (player.has(Objs.BALL_OF_WOOL, WOOL)) {
                            when (
                                choice(
                                    "Select an Option",
                                    "I have them here. Please make me a wig.",
                                )
                            ) {
                                1 -> {
                                    me("I have them here. Please make me a wig.")
                                    npc(ned, "Okay, I'll have a go.")
                                    itemBox(Objs.PLAINWIG, "Ned gives you a pretty good wig.")
                                    player.transaction("prince ali wig") {
                                        remove(INV, Objs.BALL_OF_WOOL, WOOL) &&
                                            add(INV, Objs.PLAINWIG, 1)
                                    }
                                    npc(ned, "Here you go. How's that for a quick effort? Not bad I think!")
                                    me("Thanks Ned. There's more to you than meets the eye.")
                                    return
                                }

                                else -> {
                                    Unit
                                }
                            }
                        }
                        when (
                            choice(
                                "Select an Option",
                                "Actually, I don't need one right now.",
                            )
                        ) {
                            1 -> {
                                me("Actually, I don't need one right now.")
                                npc(ned, "Fair enough.")
                            }

                            else -> {
                                Unit
                            }
                        }
                    }

                    3 -> {
                        me("Could you repair the arrow holes in the back of my shirt?")
                        npc(
                            ned,
                            "Ah yes, it's a tough world these days. There's a few brave enough to attack from ten " +
                                "metres away.",
                        )
                        box("Ned pulls out a needle and attacks your shirt.")
                        npc(ned, "There you go, good as new.")
                        me("Thanks Ned. Maybe next time they will attack me face to face.")
                    }

                    4 -> {
                        me("Actually, I don't need anything.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Yes, I would like some rope.")
                player.events.publish(OpenShopRequest(player, Npcs.NED))
            }

            3 -> {
                me("No thanks, Ned. I don't need any.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.nedTalk(ned: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            s in TOLD until NED && p.flag(NED_ASKED) -> nedAgain(ned)
            s in TOLD until NED -> nedFirst(ned)
            s == NED -> nedWaiting(ned)
            else -> nedWigMenu(ned)
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Ned". */
    private suspend fun PlayerScript.nedFirst(ned: NpcRef) {
        npc(
            ned,
            "Why, hello there, [lad/lass/kid]. Me friends call me Ned. I was a man of the sea, but it's " +
                "past me now. Could I be making or selling you some rope?",
        )
        when (
            choice(
                "Select an Option",
                "You're a sailor? Could you take me to Crandor?",
                "Yes, I would like some rope.",
                "No thanks, Ned. I don't need any.",
            )
        ) {
            1 -> {
                me("You're a sailor? Could you take me to the island of Crandor?")
                npc(
                    ned,
                    "Well, I was a sailor. I've not been able to get work at sea these days, though. They say " +
                        "I'm too old.",
                )
                npc(ned, "Sorry, where was it you said you wanted to go?")
                me("To the island of Crandor.")
                npc(ned, "Crandor?")
                npc(
                    ned,
                    "But... It would be a chance to sail a ship once more. I'd sail anywhere if it was a chance " +
                        "to sail again.",
                )
                npc(ned, "Then again, no captain in his right mind would sail to that island...")
                npc(ned, "Ah, you only live once! I'll do it!")
                player.setFlag(NED_ASKED, true)
                npc(ned, "So, where's your ship?")
                if (stage(player) < SHIP) {
                    me("Well, I don't actually have a ship yet.")
                    npc(ned, "Ah, well. Let me know when you do. The place to look would be Port Sarim.")
                    return
                }
                me("It's the Lady Lumbridge, in Port Sarim.")
                npc(ned, "That old pile of junk? Last I heard, she wasn't seaworthy.")
                me("I fixed her up!")
                npc(ned, "You did? Excellent!")
                npc(ned, "Just show me the map and we can get ready to go!")
            }

            2 -> {
                me("Yes, I would like some rope.")
                player.events.publish(OpenShopRequest(player, Npcs.NED))
            }

            3 -> {
                me("No thanks, Ned. I don't need any.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Ned again if the player wasn't ready the first time". */
    private suspend fun PlayerScript.nedAgain(ned: NpcRef) {
        npc(ned, "Hello again, [lad/lass/kid]. Something I can help you with? Want to buy some rope perhaps?")
        when (
            choice(
                "Select an option",
                "Will you take me to Crandor now, then?",
                "Yes, I would like some rope.",
                "No thanks, Ned. I don't need any.",
            )
        ) {
            1 -> {
                me("Will you take me to Crandor now, then?")
                npc(ned, "I said I would and old Ned is a man of his word!")
                npc(ned, "So, where's your ship?")
                if (stage(player) < SHIP) {
                    me("Well, I don't actually have a ship yet.")
                    npc(ned, "Ah, well. Let me know when you do. The place to look would be Port Sarim.")
                    return
                }
                me("It's the Lady Lumbridge, in Port Sarim.")
                npc(ned, "That old pile of junk? Last I heard, she wasn't seaworthy.")
                if (stage(player) < REPAIRED) {
                    me("Yes, good point. I'd better go and fix her up.")
                    return
                }
                me("I fixed her up!")
                npc(ned, "You did? Excellent!")
                npc(ned, "Just show me the map and we can get ready to go!")
            }

            2 -> {
                me("Yes, I would like some rope.")
                player.events.publish(OpenShopRequest(player, Npcs.NED))
            }

            3 -> {
                me("No thanks, Ned. I don't need any.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Ned takes the map (or the three pieces) and waits on the ship (stage [NED]). */
    private fun PlayerScript.giveMapToNed() {
        val p = player
        if (!p.take(Objs.DRAGONMAP, 1, "dragon slayer map to ned")) {
            p.transaction("dragon slayer map pieces to ned") {
                remove(Invs.INV, MELZAR_PIECE, 1) && remove(Invs.INV, THALZAR_PIECE, 1) && remove(Invs.INV, LOZAR_PIECE, 1)
            }
        }
        QuestsB.DRAGON_SLAYER.advance(p, NED)
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Ned after giving him the Crandor map". */
    private suspend fun PlayerScript.nedWaiting(ned: NpcRef) {
        npc(ned, "Hello again, [lad/lass/kid]. Something I can help you with? Want to buy some rope perhaps?")
        when (
            choice(
                "Select an Option",
                "Will you take me to Crandor now, then?",
                "Yes, I would like some rope.",
                "No thanks, Ned. I don't need any.",
            )
        ) {
            1 -> {
                me("Will you take me to Crandor now, then?")
                npc(
                    ned,
                    "I said I would and old Ned is a man of his word! I'll meet you on board the Lady Lumbridge " +
                        "in Port Sarim.",
                )
                nedBoard(ned)
            }

            2 -> {
                me("Yes, I would like some rope.")
                player.events.publish(OpenShopRequest(player, Npcs.NED))
            }

            3 -> {
                me("No thanks, Ned. I don't need any.")
            }

            else -> {
                Unit
            }
        }
    }

    // OPEN-QUB12: the ship scene and the sailing cutscene are not staged; Ned's talk on board and the voyage follow at once.

    /** Wiki "Transcript:Dragon Slayer I", section "Talking to Ned on the Lady Lumbridge". */
    private suspend fun PlayerScript.nedBoard(ned: NpcRef) {
        npc(
            ned,
            "Ah, it's good to be on board a ship again! No matter how long I live on land, a ship will " +
                "always seem better. Are you ready to depart?",
        )
        when (
            choice(
                "Select an Option",
                "Yes, let's go!",
                "No, I'm not quite ready yet.",
            )
        ) {
            1 -> {
                me("Yes, let's go!")
                sailToCrandor(ned)
            }

            2 -> {
                me("No, I'm not quite ready yet.")
                npc(ned, "Well, you go do whatever you need to do. I'll wait here for you until you're ready.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.sailToCrandor(ned: NpcRef) {
        crandorCutscene(ned)
        val p = player
        p.teleport(CRANDOR_BEACH)
        QuestsB.DRAGON_SLAYER.advance(p, CRANDOR)
    }

    /** Wiki "Transcript:Dragon Slayer I", section "Sailing to Crandor". */
    private suspend fun PlayerScript.crandorCutscene(ned: NpcRef) {
        // ACT: The cutscene begins; the camera pans towards the Lady Lumbridge.
        npc(ned, "Ah, it's good to feel the salt spray on my face once again!")
        npc(ned, "And this is a mighty fine ship. She don't look much, but she handles like a dream.")
        me("How much longer until we reach Crandor?")
        npc(
            ned,
            "Not long now! According to the chart, we'd be able to see Crandor if it wasn't for those " +
                "clouds on the horizon.",
        )
        box("Clouds surround the ship.")
        npcAs(
            Npcs.DRAGONSLAYER_JENKINS_THERE,
            "Cabin Boy Jenkins",
            "Looks like there's a storm coming up, cap'n. Soon we won't be able to see anything!",
        )
        npc(ned, "Oh, well. The weather had been so good up until now.")
        // ACT: Elvarg flies over the ship.
        me("Did you see that?")
        npc(ned, "See what?")
        me("I thought I saw something up above us.")
        // ACT: Fire rains down on the ship.
        npc(ned, "It's the dragon!")
        // Elvarg flies over the ship in circles (cutscene, OPEN-QUB12)
        npc(ned, "We're going to sink!")
        me("Look! Land ahead!")
        npc(ned, "We're going to crash!")
        // {{Colour|#800000|CRASH!}}
        box("You are knocked unconscious and later awake on an ash-strewn beach.")
        // ACT: The cutscene ends.
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1

        /** Quest variable stages (OPEN-QU1): 0 and 10 are the cache's `unstarted` / `endstate`. */
        const val STARTED = 1
        const val OZIACH = 2
        const val TOLD = 3
        const val SHIP = 4
        const val REPAIRED = 5
        const val NED = 6
        const val CRANDOR = 7
        const val SLAIN = 8
        const val END = 10

        /** Wiki "Dragon Slayer I": recommended combat level 45 and Magic level 33. */
        const val RECOMMENDED_COMBAT = 45
        const val RECOMMENDED_MAGIC = 33

        /** Wiki: Wormbrain sells Lozar's piece for 10,000 coins; Klarense the ship for 2,000. */
        const val WORMBRAIN_FEE = 10_000
        const val SHIP_PRICE = 2_000

        /** Wiki "Reparing the Lady Lumbridge": three planks, 30 nails each. */
        const val PLANKS_NEEDED = 3
        const val NAILS_PER_PLANK = 30

        const val ASKED_SHIP = "quests.ds1.asked_ship"
        const val ASKED_SHIELD = "quests.ds1.asked_shield"
        const val ASKED_MELZAR = "quests.ds1.asked_melzar"
        const val ASKED_THALZAR = "quests.ds1.asked_thalzar"
        const val ASKED_LOZAR = "quests.ds1.asked_lozar"
        const val GOT_LOZAR = "quests.ds1.got_lozar"
        const val DOOR_BITS = "quests.ds1.door_items"
        const val DOOR_OPEN = "quests.ds1.door_open"
        const val PLANKS = "quests.ds1.planks"
        const val NED_ASKED = "quests.ds1.ned_asked"

        /** Wiki Prince Ali Rescue "Talking to Ned": the wig takes three balls of wool. */
        const val WOOL = 3
        const val INV = Invs.INV

        /** Map pieces (Wiki item ids: Melzar 1535, Lozar 1536, Thalzar 1537). */
        const val MELZAR_PIECE = Objs.MAPPART1
        const val LOZAR_PIECE = Objs.MAPPART2
        const val THALZAR_PIECE = Objs.MAPPART3

        /** The loc of Thalzar's magic door (Dwarven Mine 3050,9839, cache loc 25115) and the four items it takes (Wiki). */
        const val THALZAR_DOOR = 25115
        val DOOR_ITEMS = intArrayOf(Objs.SILK, Objs.LOBSTER_POT, Objs.BOWL_UNFIRED, Objs.WIZARDS_MIND_BOMB)

        /** Melzar's Maze: each coloured key opens the door of its colour (cache locs `reddoor` ... `greendoor`, `melzardoor`). */
        val KEY_DOORS =
            listOf(
                Objs.MELZARKEY to Locs.MELZARDOOR,
                Objs.REDKEY to Locs.REDDOOR,
                Objs.ORANGEKEY to Locs.ORANGEDOOR,
                Objs.YELLOWKEY to Locs.YELLOWDOOR,
                Objs.BLUEKEY to Locs.BLUEDOOR,
                Objs.MAGENTAKEY to Locs.MAGENTADOOR,
                Objs.GREENKEY to Locs.GREENDOOR,
            )

        /** Monster type to the key it drops (npc ids 3969-3982 and Melzar 823, as spawned in the Wiki spawn table). */
        val MAZE_DROPS: Map<Int, Int> =
            buildMap {
                for (id in 3969..3971) put(id, Objs.REDKEY)
                for (id in 3972..3974) put(id, Objs.YELLOWKEY)
                for (id in 3975..3979) put(id, Objs.ORANGEKEY)
                for (id in 3980..3981) put(id, Objs.BLUEKEY)
                put(Npcs.MELZAR_THE_MAD, Objs.MAGENTAKEY)
                put(3982, Objs.GREENKEY)
            }

        /** OPEN-QUB10: see [onDeath]. */
        const val KEY_FROM_ANY_VARIANT = true
        val MAZE_FIRST_VARIANT: Map<Int, Int> =
            mapOf(Objs.REDKEY to 3969, Objs.YELLOWKEY to 3972, Objs.ORANGEKEY to 3975, Objs.BLUEKEY to 3980)

        /** OPEN-QUB12: the beach the voyage ends on (free tile south-west of Crandor's centre, Wiki map 2836,3271). */
        val CRANDOR_BEACH = CoordGrid(2836, 3266, 0)

        /** Wiki "Dragon Slayer I" rewards: 2 quest points (cache), 18,650 Strength and 18,650 Defence experience. */
        val REWARD =
            QuestReward(
                model = Objs.DRAGON_SLAYER_QIP_ELVARGS_HEAD,
                xp = listOf(Stats.STRENGTH to 18_650.0, Stats.DEFENCE to 18_650.0),
                notes = listOf("The ability to wear a rune platebody, green d'hide body and dragon platebody."),
            )
    }
}

/** Dragon Slayer I is under way (started, Elvarg not yet reported): Bentnoze's map-piece menu applies. */
internal fun dragonSlayerActive(p: PlayerRef): Boolean = QuestsB.DRAGON_SLAYER.stage(p) in 1 until DRAGON_SLAYER_SLAIN

/** Elvarg slain stage of Dragon Slayer I (DragonSlayer SLAIN). */
private const val DRAGON_SLAYER_SLAIN = 8

/** Bentnoze's Dragon Slayer I menu, for the Goblin Diplomacy handler that owns the red-armoured Bentnoze. */
internal suspend fun PlayerScript.bentnozeDragonSlayer(bentnoze: NpcRef) {
    with(DragonSlayer()) { bentnozeMenu(bentnoze) }
}

/** The Duke's Dragon Slayer I dialogue (anti-dragon shield), for the Rune Mysteries handler that owns his Talk-to. */
internal suspend fun PlayerScript.dukeDragonSlayer(duke: NpcRef) {
    with(DragonSlayer()) { dukeTalk(duke) }
}
