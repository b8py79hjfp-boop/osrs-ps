@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions")
// The long functions are Wiki transcripts and cutscenes: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttrKey
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

// Temple of the Eye part 2 (docs/mechanics/quest-temple-of-the-eye.md TE3-TE8): the tower, the cutscenes and the
// tutorial's talk.

private val BUSY = AttrKey<Boolean>("tote.busy")

/** Account values written by the tutorial floor of `content/skills` `GuardiansOfTheRift` (its `T_*` constants). */
private object Fact {
    const val CELL = "gotr.t.cell"
    const val BARRIER = "gotr.t.barrier"
    const val ELEMENTAL = "gotr.t.elemental"
    const val CATALYTIC = "gotr.t.catalytic"
    const val FRAGMENTS = "gotr.t.fragments"
    const val ESSENCE = "gotr.t.essence"
    const val IMBUES = "gotr.t.imbues"
    const val POWERS = "gotr.t.powers"
    const val MEDIUM = "gotr.t.medium"
    const val STRENGTHENED = "gotr.t.strengthened"
    val ALL = listOf(CELL, BARRIER, ELEMENTAL, CATALYTIC, FRAGMENTS, ESSENCE, IMBUES, POWERS, MEDIUM, STRENGTHENED)

    /**
     * Wiki walkthrough: "Tamara ... will ask you to collect guardian fragments" (the transcript's "After mining 5
     * fragments").
     */
    const val FRAGMENTS_NEEDED = 5

    /** The Great Guardian closes the rift on the third feeding (Mind, Water, Water). */
    const val FEEDS = 3L

    /**
     * Cache map: the tutorial floor is the minigame's floor shifted by (-1152, -3776); its tile of the one inactive
     * cell tile.
     */
    val TILE = CoordGrid(2463, 5734, 0)
}

private fun PlayerRef.fact(key: String): Long = saved(key) ?: 0L

internal fun landing(p: PlayerRef, at: CoordGrid): CoordGrid =
    (0..LANDING_RADIUS)
        .flatMap { r ->
            (-r..r).flatMap { dx -> (-r..r).map { dz -> at.translate(dx, dz) } }
        }.firstOrNull { p.isWalkable(it) }
        ?: at

private const val LANDING_RADIUS = 4
private val PERSTEN = Npcs.TOTE_PERSTEN_TEMPLE
private val SEDRIDOR = Npcs.HEAD_WIZARD_1OP

/** True while the player stands on the tutorial floor (cache map: x 2432..2495, z 5680..5759). */
private fun onTutorialFloor(p: PlayerRef): Boolean = p.coord.level == 0 && p.coord.x in TUTORIAL_X && p.coord.z in TUTORIAL_Z

private val TUTORIAL_X = 2432..2495
private val TUTORIAL_Z = 5680..5759

// ---- the polled finish of the tutorial (OPEN-TE4: no event for "the tutorial guardian was fully powered")

internal fun poll(p: PlayerRef) {
    if (Tote.stage(p) != Tote.TUTORIAL || p[BUSY] == true || !onTutorialFloor(p)) return
    if (p.fact(Fact.POWERS) < Fact.FEEDS) return
    p[BUSY] = true
    p.startScript {
        try {
            closeRift()
        } finally {
            p[BUSY] = null
        }
    }
}

// ---- Sedridor

internal fun templeSedridorBusy(p: PlayerRef): Boolean = Tote.stage(p) in Tote.RIDDLE_SOLVED until Tote.IN_TEMPLE

/** Sedridor with Persten present (stage 85 to 90): the Wiki's "Talking to Sedridor or Persten". */
internal suspend fun PlayerScript.readyConversation(who: NpcRef) {
    val p = player
    if (!templeSedridorBusy(p)) return npc(who, "Hello there.")
    sayAs(
        Npcs.TOTE_PERSTEN_TOWER_CHILD,
        "Wizard Persten",
        "Hello again! Ready to uncover a magical secret that could change the world forever?",
    )
    sayAs(SEDRIDOR, "Archmage Sedridor", "Slow down, Persten! I am still inspecting this amulet of yours.")
    me("You're inspecting the amulet now? Does that mean the incantation is safe?")
    sayAs(
        Npcs.TOTE_PERSTEN_TOWER_CHILD,
        "Wizard Persten",
        "That's right! The amulet is as well, of course, but Sedridor here likes to be " + "careful.",
    )
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "Not all of us are so carefree when it comes to the rules of our order, Persten. I " +
            "mean really, Zamorakian incantations! Be glad I've even let you back in the tower!",
    )
    sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "The unfortunate price of progress, Sedridor.")
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "I can think of a few other unfortunate things... Anyway, the amulet seems to be safe, " +
            "and as much as I hate to admit it, your assessment seems to be correct.",
    )
    me("So we can go ahead?")
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "Yes. I'm still not happy about this, but we can't throw away this chance. I assume " + "Traiborn's apprentices are ready?",
    )
    me("That's right.")
    sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "Well then what are we waiting for? Let's do this!")
    sayAs(SEDRIDOR, "Archmage Sedridor", "$name, are you ready?")
    if (choice("Select an option", "Let's do it.", "Not yet.") == 1) {
        me("Let's do it.")
        incantation()
    } else {
        me("Not yet.")
        sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "Don't take too long.")
    }
}

/** Extra options on Archmage Sedridor's menu ([RuneMysteries]). */
internal fun templeSedridorOptions(p: PlayerRef): List<Pair<String, suspend PlayerScript.(NpcRef) -> Unit>> {
    val stage = Tote.stage(p)
    val out = ArrayList<Pair<String, suspend PlayerScript.(NpcRef) -> Unit>>()
    when {
        stage == Tote.SHOWN_PERSTEN -> {
            out += "I need your help with an incantation." to { sed -> sedridorIncantation(sed) }
        }

        stage == Tote.ANALYSING -> {
            out +=
                "Can you help me with that incantation?" to
                { sed ->
                    say(
                        sed,
                        "> Can you help me with that incantation?",
                        "I haven't finished my analysis of it yet. While you wait, I suggest you speak with " +
                            "Traiborn. He has three new apprentices who could help you, assuming I agree to let " +
                            "things go ahead.",
                    )
                }
        }

        stage in Tote.RIDDLE_SOLVED until Tote.IN_TEMPLE -> {
            out +=
                "So we're ready to perform the incantation?" to { sed -> readyConversation(sed) }
        }

        stage in Tote.IN_TEMPLE until Tote.TUTORIAL -> {
            out +=
                "Are the others through the portal?" to
                { sed -> say(sed, "> Are the others through the portal?", "I assume so. You should probably go through and join them.") }
        }

        stage in Tote.TUTORIAL until Tote.CLOSED -> {
            out +=
                "Sedridor! Something's gone wrong!" to
                { sed ->
                    say(
                        sed,
                        "> Sedridor! Something's gone wrong!",
                        "What do you mean?",
                        "> Through the portal! We've opened some sort of rift to the Abyss!",
                        "Well you'd better get back there and fix it!",
                    )
                }
        }

        stage == Tote.CLOSED -> {
            out += "Sedridor! It all went wrong!" to { _ -> finale() }
        }

        stage >= Tote.END -> {
            out += "How are things at the Temple of the Eye?" to { sed -> postQuestTemple(sed) }
        }
    }
    return out
}

private suspend fun PlayerScript.sedridorIncantation(sed: NpcRef) {
    val p = player
    say(
        sed,
        "> I need your help with an incantation.",
        "An incantation, hmm? What incantation?",
        "> A teleport that could potentially take us to an ancient teleportation matrix. Persten reckons you " +
            "might be able to use it to " +
            "gain easier access to the runic altars.",
        "Wait... Persten sent you?",
        "> That's right. Oh, I should also mention that this teleportation matrix seems to have some connection " +
            "to your order, despite " +
            "predating it by thousands of years.",
        "So Persten disappears for all this time, and then shows up again with knowledge on an ancient " +
            "teleportation matrix? I suppose " +
            "I shouldn't be surprised. It's probably not the strangest thing she's done.",
        "Anyway, what do you need my help with exactly?",
        "> Persten needs three other wizards to be able to perform the incantation.",
        "Hmm... can I see it?",
    )
    if (!p.has(
            Objs.TOTE_INCANTATION,
        )
    ) {
        return say(sed, "> Oh, I don't have it with me.", "I'd very much like to see it. Come back to me when you have it.")
    }
    say(sed, "> Sure.")
    p.take(Objs.TOTE_INCANTATION, 1, "tote incantation")
    box("You give the incantation to Sedridor.")
    say(
        sed,
        "Hmm... this scroll is very interesting. Did you get it from a Zamorakian wizard, perhaps? Don't lie to " +
            "me, $name.",
        "> Well since you asked... I... well...",
        "It's clear from your response that my suspicions are correct. This incantation is written on very thin " +
            "flesh rather than " +
            "parchment, a horrific practice only used by Zamorakians.",
        "Need I remind you that it was the Zamorakians who burnt down the first Wizards' Tower?",
        "> Er...",
        "$name, I'm going to be frank with you. Persten hasn't been seen here for almost a year. My last meeting " +
            "with her was to " +
            "discuss changing her line of study due to her lack of results.",
        "She was given one more year to produce something of note. Now, with time almost up, she sends you here " +
            "with Zamorakian " +
            "incantations and odd claims about an ancient teleportation matrix.",
        "> But what if it's true? That teleportation matrix could change everything!",
        "> Okay, I admit we went to some Zamorakians for help with the incantation, but they've had no other " +
            "involvement. Persten has " +
            "even made sure they won't get anything beneficial out of this.",
        "Hmm... You have been a great help to us in the past. For that, I will consider it. First though, I will " +
            "do a thorough arcane " +
            "analysis of this incantation to determine if it is safe to cast.",
        "Now, as for your three wizards, I can't be wasting the others' time with this sort of thing, especially " +
            "when I haven't even " +
            "agreed to allow it yet.",
        "However, Traiborn has three new apprentices who could help. This might be a good learning opportunity " +
            "for them. Speak to him " +
            "while I inspect this incantation.",
    )
    Tote.set(p, Tote.ANALYSING)
}

private suspend fun PlayerScript.postQuestTemple(sed: NpcRef) {
    say(
        sed,
        "> How are things at the Temple of the Eye?",
        "Between the Guardians of the Rift and ourselves, we are managing to keep the Abyss at bay.",
        "However, despite all our research, we are no closer to working out how to close the rift permanently.",
        "Gaining a better understanding of the artefact that was once there is key, but we have found little " +
            "mention of this 'Eye'.",
        "I'm sure the wizards of the first tower had plenty of research on it. After all, they knew enough about " +
            "it to use its symbol " +
            "as the symbol for our order.",
        "All that knowledge lost because of those accursed Zamorakians!",
        "Anyway, if you wish to help defend the temple, just head through the portal.",
    )
}

// ---- Traiborn

/** Traiborn's part of the quest, after his greeting. Returns false to carry on with his other conversation. */
internal suspend fun PlayerScript.templeTraiborn(traiborn: NpcRef): Boolean {
    val p = player
    val stage = Tote.stage(p)
    if (stage !in Tote.ANALYSING until Tote.IN_TEMPLE) return false
    val asked = p.flag("quests.tote.traiborn")
    val label =
        when {
            stage >= Tote.RIDDLE_SOLVED -> "So your apprentices can help with the incantation?"
            asked -> "I think I know what a thingummywut is!"
            else -> "I need your apprentices to help with an incantation."
        }
    if (choice("Select an option", label, "Talk about something else.") != 1) return false
    me(label)
    when {
        stage >= Tote.RIDDLE_SOLVED -> npc(traiborn, "That's right, young thingummywut. Now, don't keep Sedridor waiting!")
        !asked -> traibornAsk(traiborn)
        else -> traibornGuess(traiborn)
    }
    return true
}

private suspend fun PlayerScript.traibornAsk(t: NpcRef) {
    say(
        t,
        "Right now? But we're having a party!",
        "> A party?",
        "Party? What party?",
        "> You said you were having a party?",
        "Well I suppose it is a party of sorts. A study party! Would you like to join in? You look like you could " +
            "use one!",
        "> I'll pass... Anyway, Sedridor said your apprentices could help me with an incantation.",
        "What kind of incantation?",
        "> One that could lead us to an ancient teleportation matrix.",
        "Teleportation matrix? Doesn't sound like the most comfortable place for a party... I bet it's full of " +
            "thingummywuts as well!",
        "> I'm not suggesting we move the party there. I'm suggesting that we cancel the party and discover the " +
            "secrets of this " +
            "teleportation matrix instead.",
        "Cancel the party? You're certainly a party pooper!",
        "> Alright, how about instead of cancelling the party, we finish it with a bang? What's currently on the " +
            "agenda?",
        "Finish it with a bang? Sedridor won't be happy if we burn the place down again, will he? Still, I like it!",
        "I've currently tasked each of the apprentices with solving a problem. If they all solve it, we can " +
            "celebrate together!",
        "> Sounds good. So, what's the problem?",
        "Problem? There's no problem! Everything is fine here. We're having a party!",
        "> I mean what's the problem they have to solve?",
        "Oh! It's trivial! They just have to figure out what a thingummywut is!",
        "> A thingummy... what?",
        "Precisely!",
        "> Right... I'll speak to the apprentices. Perhaps together we can figure this out.",
        "Good luck!",
    )
    player.setFlag("quests.tote.traiborn", true)
}

private suspend fun PlayerScript.traibornGuess(t: NpcRef) {
    val p = player
    npc(t, "Oh? Do tell.")
    val seenAll =
        listOf(Varbits.TOTE_SPOKEN_TO_TAMARA, Varbits.TOTE_SPOKEN_TO_FELIX, Varbits.TOTE_SPOKEN_TO_CORDELIA).all {
            p.varbit(it) ==
                1
        }
    if (!seenAll) {
        val wrong = arrayOf("A slayer creature!", "A great wizard, lost to history!", "You!", "I don't know.")
        val pick = choice("Select an option", *wrong)
        me(wrong[pick - 1])
        val reply =
            when (pick) {
                1 -> {
                    "A slayer creature? That doesn't make any sense at all! Besides, not even the slayer " +
                        "masters know how to kill a thingummywut!"
                }

                2 -> {
                    "Lost to history? That doesn't make any sense at all! Besides, a thingummywut is not " +
                        "lost to history. It's very well documented inside my head!"
                }

                3 -> {
                    "Me? That doesn't make any sense at all! If I were a thingummywut, I wouldn't be stood here " +
                        "teaching!"
                }

                else -> {
                    "Oh dear, how embarrassing."
                }
            }
        return say(t, reply, "Perhaps my apprentices could teach you a thing or three. You should talk to them.")
    }
    p.countDialog("Enter amount") { answer ->
        startScript {
            me("$answer!")
            if (answer != Tote.PUZZLE_ANSWER) return@startScript npc(t, "No, that's not right at all. You should think harder about this!")
            say(
                t,
                "Well done! That's correct!",
                "See, it's easier to solve problems when you put aside what's best for you and instead focus on " +
                    "what's best for the team!",
                "Congratulations, you all pass!",
            )
            sayAs(Npcs.TOTE_FELIX_WIZARD_TOWER_CHILD, "Apprentice Felix", "But you said that only the first of us to solve it would pass!")
            say(
                t,
                "Oh? Did I? I forgot. Either way, I hope you've all now learnt a valuable lesson in " +
                    "teamwork! You have this adventurer here to thank for that.",
            )
            sayAs(
                Npcs.TOTE_CORDELIA_WIZARD_TOWER_CHILD,
                "Apprentice Cordelia",
                "Wait, but we all had different puzzles written down?",
                "Of course! We needed to combine our puzzles to solve it. Wow, that's genius! What a " +
                    "great way to teach us all teamwork!",
            )
            say(t, "Now, finish with a bang did you say?")
            t.say("Boom!")
            say(
                t,
                "> What the - what was that for?!",
                "Well, it was your idea after all. Now why don't you go tell Sedridor that my " + "apprentices are free to help?",
            )
            Tote.set(p, Tote.RIDDLE_SOLVED)
        }
    }
}

// ---- the cutscenes

private fun spot(p: PlayerRef, dx: Int, dz: Int): CoordGrid = landing(p, p.coord.translate(dx, dz))

/** Persten, Sedridor and the apprentices cast the incantation; the five step through the portal into the temple. */
private suspend fun PlayerScript.incantation() {
    val p = player
    scene { sc ->
        sc.shot(p.coord.translate(CAM_BACK, CAM_BACK), p.coord, CamHeight.HIGH, CamHeight.EYE)
        val traiborn = sc.actor(Npcs.TOTE_TRAIBORN_CUTSCENE, spot(p, 2, 1))
        sc.actor(Npcs.TOTE_TAMARA_CUTSCENE, spot(p, 2, -1))
        sc.actor(Npcs.TOTE_CORDELIA_CUTSCENE, spot(p, 3, 0))
        sc.actor(Npcs.TOTE_FELIX_CUTSCENE, spot(p, 3, 2))
        sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "Everyone ready?")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "We won't let you down, Persten.")
        sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "Alright, with me!")
        p.say("Saris! Val! Curak! Belito!")
        p.spotAnim(tp.symbols.SpotAnims.TOTE_PORTAL_CAST)
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "It worked!", "Well what are we waiting for? Let's go in!")
        sayAs(
            SEDRIDOR,
            "Archmage Sedridor",
            "Go in? You three aren't going through that portal. We have no idea what's on the " + "other side!",
            "I have given Persten and $name permission to go through. Until we know it's safe, no " + "one else may enter.",
        )
        sayAs(
            Npcs.TOTE_CORDELIA_TEMPLE,
            "Apprentice Cordelia",
            "You can't be serious!",
            "Please let us help! We've all worked so hard for an opportunity like this!",
        )
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "And we have Persten to look after us!")
        sayAs(
            SEDRIDOR,
            "Archmage Sedridor",
            "No. You are only apprentices. You were here to help with the incantation, and you've " +
                "done that now. You should go back to your studies.",
        )
        sayAs(
            Npcs.TOTE_PERSTEN_TOWER_CHILD,
            "Wizard Persten",
            "Come now, Sedridor. The young thingummywuts have proven themselves to be more than " + "capable. Let them have their party!",
        )
        traiborn.say("It's going to be quite something! You really should let them help!")
        sayAs(
            SEDRIDOR,
            "Archmage Sedridor",
            "Quiet, Persten!",
            "Let it be known that I consider this a bad idea. That said, they are your " +
                "apprentices, Traiborn. If they have your permission, I won't stop them.",
        )
        sayAs(Npcs.TOTE_PERSTEN_TOWER_CHILD, "Wizard Persten", "Alright! Ready to make history?")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "Let's go!")
    }
    Tote.set(p, Tote.IN_TEMPLE)
    p.teleport(landing(p, Tote.TEMPLE_LOBBY))
    p.spawnNpc(PERSTEN, landing(p, Tote.TEMPLE_LOBBY.translate(2, 3)), 1)
    scene { sc ->
        sc.shot(p.coord.translate(CAM_BACK, CAM_BACK), p.coord.translate(0, LOOK_NEAR), CamHeight.HIGH, CamHeight.LOW)
        sayAs(PERSTEN, "Wizard Persten", "Well this is... unexpected.")
        sayAs(Npcs.TOTE_CORDELIA_TEMPLE, "Apprentice Cordelia", "Where are we?")
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "Are we... underwater?")
        sc.shot(p.coord.translate(CAM_BACK, 0), p.coord.translate(0, LOOK_FAR), CamHeight.HIGH, CamHeight.LOW)
        sayAs(
            Npcs.TOTE_TAMARA_TEMPLE,
            "Apprentice Tamara",
            "Let me cast a locator spell!",
            "It seems we're deep below the sea, just south of Morytania!",
        )
        sayAs(PERSTEN, "Wizard Persten", "Fascinating... Come, let's look around.")
    }
}

private const val CAM_BACK = -5

/** Cutscene camera targets: tiles ahead of the player (OPEN-QUC5: ours). */
private const val LOOK_NEAR = 6
private const val LOOK_FAR = 12

/**
 * Talking to Persten in the temple (stage 91): she sends the player to the apprentices, then the vision and the rift.
 */
internal suspend fun PlayerScript.perstenTemple(per: NpcRef) {
    val p = player
    val stage = Tote.stage(p)
    if (stage != Tote.IN_TEMPLE) return npc(per, "Hello!")
    val all = listOf(Varbits.TOTE_TEMPLE_TAMARA, Varbits.TOTE_TEMPLE_FELIX, Varbits.TOTE_TEMPLE_CORDELIA).all { p.varbit(it) == 1 }
    if (!all) {
        return npc(
            per,
            "This place is brilliant! The apprentices are looking around to see what they can " + "discover. You should join them!",
        )
    }
    say(
        per,
        "> Hey!",
        "...",
        "> Hello?",
        "Oh, sorry, I didn't see you there. I was lost in thought!",
        "> What were you thinking about?",
        "Hmm? Oh, I was looking at these markings here. They're very interesting! They seem to be radiating a " +
            "magical energy I've never " +
            "encountered before!",
        "Take a closer look! Maybe you'll feel the energy as well!",
    )
    box("You begin to experience a vision, and walk towards a Mysterious Stone.")
    sayAs(Npcs.TOTE_VOICE_1, "Justiciar Zachariah", "Saradomin be with you.")
    sayAs(Npcs.TOTE_VOICE_2, "Justiciar Aliya", "And with you, Zachariah. So, it's really true?")
    sayAs(Npcs.TOTE_VOICE_1, "Justiciar Zachariah", "Indeed it is, Aliya, and he has given us the task of protecting it.")
    sayAs(Npcs.TOTE_VOICE_2, "Justiciar Aliya", "What would he have me do?")
    sayAs(
        Npcs.TOTE_VOICE_1,
        "Justiciar Zachariah",
        "You must build a sanctuary for the Eye. The forces of chaos already seek to reobtain " + "it, as do countless others.",
    )
    sayAs(Npcs.TOTE_VOICE_2, "Justiciar Aliya", "I will keep it safe, no matter the cost. And you?")
    sayAs(
        Npcs.TOTE_VOICE_1,
        "Justiciar Zachariah",
        "Zamorak has tasked his collector with finding the Eye. I will hunt him down before he " +
            "does, along with any others who would try to claim it.",
    )
    sayAs(Npcs.TOTE_VOICE_2, "Justiciar Aliya", "Then I wish you luck, brother.")
    sayAs(
        Npcs.TOTE_VOICE_1,
        "Justiciar Zachariah",
        "There is one last thing, Aliya. You must keep the Eye secret from all others, even " +
            "our own. The fewer people that know the truth, the better.",
    )
    sayAs(Npcs.TOTE_VOICE_2, "Justiciar Aliya", "On my honour as a justiciar, it will be done.")
    sayAs(Npcs.TOTE_VOICE_1, "Justiciar Zachariah", "Good. Saradomin be with you, sister.")
    say(
        per,
        "> What the...",
        "$name?",
        "> Jas!",
        "What did you say?",
        "> I... I don't know.",
        "Did you just say... 'Jas'?",
        "> I can't remember. When I looked at the marks on the floor I had this... vision, but " + "it's all unclear.",
        "Curious... Well, time to see what the others have found!",
    )
    riftCutscene(per)
}

private suspend fun PlayerScript.riftCutscene(per: NpcRef) {
    val p = player
    scene { sc ->
        sc.shot(p.coord.translate(CAM_BACK, CAM_BACK), p.coord.translate(0, 8), CamHeight.HIGH, CamHeight.LOW)
        sayAs(PERSTEN, "Wizard Persten", "Right, what have we discovered?")
        sayAs(
            Npcs.TOTE_CORDELIA_TEMPLE,
            "Apprentice Cordelia",
            "That large hole behind you is radiating a strange energy. I'm still trying to work " + "out what it is.",
        )
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "There's some statues of an old Saradominist Justiciar called Aliya!")
        sayAs(
            PERSTEN,
            "Wizard Persten",
            "Remarkable! The justiciars haven't been around for thousands of years. That confirms " +
                "my suspicions about the age of this place.",
        )
        sayAs(
            Npcs.TOTE_TAMARA_TEMPLE,
            "Apprentice Tamara",
            "That's not all I found. Apparently, this place was once called the Temple of the Eye! " +
                "I remember reading about something called the Eye back at the Wizards' Tower! " +
                "Apparently it was a powerful artefact made by Saradomin himself!",
        )
        sayAs(PERSTEN, "Wizard Persten", "Sorry... made by Saradomin, you say?")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "That's what the book said. Why?")
        sayAs(PERSTEN, "Wizard Persten", "Oh, just checking! I get all caught up in the moment and mishear things sometimes.")
        sayAs(
            Npcs.TOTE_CORDELIA_TEMPLE,
            "Apprentice Cordelia",
            "Of course! I've worked out what the energy from that hole is. It's abyssal energy!",
        )
        sayAs(
            PERSTEN,
            "Wizard Persten",
            "Hmm... Yes... Brilliant! I think I've just about worked it all out! The wizards here " +
                "must have used power from the Abyss to create portals to the runic altars.",
        )
        sayAs(
            PERSTEN,
            "Wizard Persten",
            "Then, with the power from this 'Eye' and support from their rune guardians, they'd " +
                "have been able to create runes at lightning speed!",
        )
        sayAs(
            PERSTEN,
            "Wizard Persten",
            "In a moment. I'm just going to cast a simple revealing spell first, just in case " + "there's anything else hidden here.",
        )
        p.camShake(0, 2, SHAKE_AMPLITUDE, SHAKE_RATE)
        sayAs(Npcs.TOTE_CORDELIA_TEMPLE, "Apprentice Cordelia", "What's happening?")
        sayAs(PERSTEN, "Wizard Persten", "Don't worry! I'm sure everything is fine. Let me handle this. Huh... now that is a problem.")
        per.despawn()
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "Persten!")
        val walker = sc.actor(Npcs.TOTE_ABYSSAL_WALKER_CUTSCENE, spot(p, 0, 4))
        sc.actor(Npcs.TOTE_ABYSSAL_PYRAMID_CUTSCENE, spot(p, 2, 4))
        sayAs(Npcs.TOTE_CORDELIA_TEMPLE, "Apprentice Cordelia", "You don't belong in this temple, accursed filth!")
        sc.remove(walker)
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "And so the rift to the Scar is open once more. Humans, fight with us. Let us hold " + "back the Abyss!",
        )
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "What do we do now? What if more of those creatures come through that rift?")
        sayAs(
            Npcs.TOTE_CORDELIA_TEMPLE,
            "Apprentice Cordelia",
            "Well don't think I'll be stopping them all again! I may be powerful, but that spell " + "still took a lot out of me.",
        )
        sayAs(Npcs.TOTE_GREAT_GUARDIAN, "The Great Guardian", "Humans, use the cells. Protect me while I close the rift.")
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "Tamara! $name! I think I've found something over here!")
        me("On my way!")
    }
    for (key in Fact.ALL) p.setSaved(key, null)
    Tote.set(p, Tote.TUTORIAL)
}

private const val SHAKE_AMPLITUDE = 6
private const val SHAKE_RATE = 10

/**
 * The tutorial's last feeding: the Great Guardian closes the rift, the group stays and the player returns to
 * Sedridor.
 */
private suspend fun PlayerScript.closeRift() {
    val p = player
    scene { sc ->
        sc.shot(p.coord.translate(CAM_BACK, CAM_BACK), p.coord.translate(0, LOOK_NEAR), CamHeight.HIGH, CamHeight.LOW)
        sayAs(Npcs.TOTE_GREAT_GUARDIAN, "The Great Guardian", "Humans, I now have the power to close the rift. Brace yourselves!")
        p.say("Whoa!")
        sayAs(Npcs.TOTE_GREAT_GUARDIAN, "The Great Guardian", "We are safe, for now.")
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "What was all that? What happened?")
        sayAs(Npcs.TOTE_GREAT_GUARDIAN, "The Great Guardian", "That was a rift into an area of the Abyss known as the Scar.")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "The Scar? But I thought this was meant to be a teleportation matrix.")
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "You are mistaken. While it is true that teleportation magic is strong here, that is " +
                "only a side effect of this temple's history.",
        )
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "This temple was built to house a powerful artefact. Many sought to claim this " +
                "artefact for themselves, including servants of the Fallen Empire.",
        )
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "In their attempt to claim the artefact, they used magic they did not understand. The " +
                "rift to the Scar was created as a result.",
        )
        me("This artefact... you mean the Eye?")
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "You are correct. The Eye was removed after the battle, but not before the Justiciar of the Lion used " +
                "it to seal the rift " +
                "once more.",
            "She then created us, the Guardians of the Rift, to protect this place in the event the rift was ever " +
                "reopened.",
        )
        sayAs(Npcs.TOTE_CORDELIA_TEMPLE, "Apprentice Cordelia", "And we reopened it.")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "But it's been sealed again now! The danger is gone.")
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "Incorrect. Without the power of the Eye, we have no way to seal the rift permanently. " +
                "It will soon reopen once more. You should leave before that happens.",
        )
        sayAs(Npcs.TOTE_CORDELIA_TEMPLE, "Apprentice Cordelia", "No. This was our mistake. I'm staying to help fix it.")
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "Me too!")
        sayAs(Npcs.TOTE_TAMARA_TEMPLE, "Apprentice Tamara", "Well if you two are staying, I guess I will as well.")
        sayAs(Npcs.TOTE_GREAT_GUARDIAN, "The Great Guardian", "Your support will be invaluable.")
        sayAs(
            Npcs.TOTE_CORDELIA_TEMPLE,
            "Apprentice Cordelia",
            "$name, could you go back to the Wizards' Tower and let Sedridor know what happened " + "here?",
        )
        me("Alright, if you're absolutely sure about this.")
        sayAs(Npcs.TOTE_FELIX_TEMPLE, "Apprentice Felix", "What about Persten? We have to help her.")
        sayAs(
            Npcs.TOTE_GREAT_GUARDIAN,
            "The Great Guardian",
            "No part of the Abyss is more dangerous than the Scar. Your friend will have already " + "been consumed.",
            "Now, the power of the Abyss grows. The rift will soon reopen once more. Those of you " +
                "staying should prepare yourselves.",
        )
        me("Then I guess I'd better go and see Sedridor. Good luck.")
    }
    Tote.set(p, Tote.CLOSED)
    p.teleport(landing(p, Tote.SEDRIDOR_ROOM.translate(1, -1)))
    finale()
}

/** Sedridor's cutscene and the end of the quest. */
private suspend fun PlayerScript.finale() {
    val p = player
    me("Sedridor! It all went wrong!")
    sayAs(SEDRIDOR, "Archmage Sedridor", "What do you mean? Was there no teleportation matrix?")
    me(
        "Kind of, but not really. The portal took us to the Temple of the Eye, an ancient " +
            "Saradominist temple built to house a powerful artefact.",
        "Long ago, a rift to the Abyss was created in the temple, but it was sealed. At least " +
            "it was, until Persten accidently opened it again...",
        "She was sucked into the Abyss, and all these abyssal creatures came out!",
    )
    sayAs(SEDRIDOR, "Archmage Sedridor", "Abyssal creatures? We have to get in there and stop this!")
    me(
        "It's okay again, for now. Some rune guardians called the Guardians of the Rift were " +
            "built to protect the temple from the Abyss. They helped us close the rift again, but " +
            "they say it will soon reopen.",
    )
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "This is a disaster! I knew that teleportation matrix was too good to be true. I " +
            "should have stopped this, but I was blinded by my own desires for that matrix!",
        "What of Traiborn's apprentices?",
    )
    me("They're safe. They chose to stay and help the Guardians of the Rift keep the rift closed.")
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "Then I will ensure they have all the help they need. I will also begin researching " +
            "this rift at once. Maybe we can still find a way to close it permanently.",
        "In the meantime, I suggest you do what you can to help them as well. After all, this " + "is partially your doing.",
    )
    me("I know. I'll help wherever I'm needed.")
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "Thank you, $name. I appreciate that you did not mean for things to go this way. You " +
            "should have this. It belonged to Persten. I don't want to think about where she got " +
            "it.",
    )
    box("Sedridor shows you a pouch.")
    me("Do you think she's still alive?")
    sayAs(
        SEDRIDOR,
        "Archmage Sedridor",
        "Sadly, that seems unlikely. Still, if anyone can survive the Abyss, it's her.",
        "Now, we'd better get to work.",
    )
    val owned =
        listOf(Invs.INV, Invs.BANK).any {
            p.inv(it).total(Objs.RCU_POUCH_MEDIUM) + p.inv(it).total(Objs.RCU_POUCH_MEDIUM_DEGRADE) >
                0
        }
    QuestCompletion.complete(p, Quests.TEMPLE_OF_THE_EYE, rewardFor(owned))
    for (key in Fact.ALL) p.setSaved(key, null)
}

/**
 * Wiki "Temple of the Eye" Rewards: 1 quest point, 5,000 Runecraft xp, access to the minigame and a medium pouch (if
 * not already owned).
 */
private fun rewardFor(ownsMedium: Boolean): QuestReward =
    QuestReward(
        model = Objs.RCU_POUCH_MEDIUM,
        xp = listOf(TOTE_REWARD_XP),
        items = if (ownsMedium) emptyList() else listOf(Objs.RCU_POUCH_MEDIUM to 1),
        notes = listOf("Access to the Guardians of the Rift minigame"),
    )

// ---- the apprentices in the temple

internal suspend fun PlayerScript.templeApprentice(a: NpcRef, who: String) {
    val p = player
    val stage = Tote.stage(p)
    if (stage in Tote.IN_TEMPLE until Tote.TUTORIAL) return discovery(a, who)
    if (stage in Tote.TUTORIAL until Tote.CLOSED) return tutorialTalk(a, who)
    npc(a, "Hello, $name. Between us and the Great Guardian, we're managing to hold back the Abyss.")
}

private suspend fun PlayerScript.discovery(a: NpcRef, who: String) {
    val p = player
    when (who) {
        "Cordelia" -> {
            say(
                a,
                "> Found anything interesting?",
                "There's something strange about this big hole here. It's giving out the tiniest " +
                    "amount of magical energy. I can't work out what it is though.",
                "> You know, those plants look similar to stuff I've seen in the...",
                "Quiet! I'm more than capable of working this out. Maybe you should go help the others " + "instead.",
            )
            p.setVarbit(Varbits.TOTE_TEMPLE_CORDELIA, 1)
        }

        "Felix" -> {
            say(
                a,
                "> That's an interesting statue.",
                "See the plaque on it? It reads: 'Aliya, Justiciar of the Lion and guardian of the Temple of the " +
                    "Eye.'",
                "> Temple of the Eye?",
                "That must be where we are right now! I've read about the justiciars before, in a book back at " +
                    "the Wizards' Tower.",
                "> What did the book say?",
                "They were powerful warriors in service to Saradomin himself, back when the gods still walked " +
                    "among us! This temple " +
                    "must be ancient. Imagine if Saradomin himself once stood here!",
            )
            p.setVarbit(Varbits.TOTE_TEMPLE_FELIX, 1)
        }

        else -> {
            say(
                a,
                "> What are you looking at?",
                "Look! Rune guardians, like the ones at the Mage Training Arena! If this place was once a " +
                    "teleportation matrix, these " +
                    "rune guardians might have helped the wizards that used it.",
                "> Anything different about them?",
                "Well, despite their age, they're actually of a remarkably similar design to the ones used by " +
                    "modern wizards. Isn't " +
                    "that amazing?",
            )
            p.setVarbit(Varbits.TOTE_TEMPLE_TAMARA, 1)
        }
    }
}

/** The tutorial's apprentices answer by what the player has done on the tutorial floor (the facts of [Fact]). */
private suspend fun PlayerScript.tutorialTalk(a: NpcRef, who: String) {
    val p = player
    val cell = p.fact(Fact.CELL) > 0
    val barrier = p.fact(Fact.BARRIER) > 0
    val guardians = p.fact(Fact.ELEMENTAL) > 0 && p.fact(Fact.CATALYTIC) > 0
    val someGuardian = p.fact(Fact.ELEMENTAL) > 0 || p.fact(Fact.CATALYTIC) > 0
    val fragments = p.fact(Fact.FRAGMENTS) >= Fact.FRAGMENTS_NEEDED || p.has(Objs.GOTR_GUARDIAN_FRAGMENT, Fact.FRAGMENTS_NEEDED)
    val essence = p.fact(Fact.ESSENCE) > 0
    val imbues = p.fact(Fact.IMBUES)
    val powers = p.fact(Fact.POWERS)
    if (who == "Cordelia") {
        return say(
            a,
            "If you're hoping I can help in some way, you're out of luck. I may be powerful, but " +
                "that spell still took a lot out of me. You'll have to hope the others can do " +
                "something.",
        )
    }
    if (who == "Felix") {
        when {
            !cell -> {
                say(
                    a,
                    "> What have you found?",
                    "Well... I can't say for sure, but I think the containers on this table contain small " + "amounts of runic energy.",
                    "> Right, I'll take one to Tamara.",
                    "Just make sure you don't take more than one! I tried to do that already and the " + "energy within them exploded!",
                    "> Alright, thanks for the warning.",
                )
            }

            !barrier -> {
                say(
                    a,
                    "You should take one of these containers to Tamara. Just don't take more than one or " + "they'll explode!",
                    "> Alright, thanks for the warning.",
                )
            }

            !someGuardian -> {
                say(
                    a,
                    "> Felix, we need to place cells in some imbued rocks. Any idea what that means?",
                    "I think the essence piles here are actually remains of old guardians. They seem to " +
                        "contain elemental and catalytic energies. Perhaps you could try infusing each one " +
                        "with a cell.",
                    "You'll probably need a chisel to shape the essence first. Looks like there's one on " + "the floor there.",
                    "> Right, I'll get to work.",
                )
            }

            !guardians -> {
                say(a, "Try infusing the other essence pile with a cell like you did with the first one.", "> Will do.")
            }

            !fragments -> {
                say(
                    a,
                    "The guardians should be strong enough to fend off those creatures. Maybe Tamara will " + "know what to do next.",
                )
            }

            !essence -> {
                say(
                    a,
                    "> We need to power up this big guardian somehow, but I need a way to capture runic " +
                        "energy. Tamara said you might be able to help.",
                    "Hmm... I reckon this old workbench here might have been used for just that. There's " +
                        "still plenty of pure essence on it for you to use, but that might not be enough... " +
                        "What if you combined the pure essence with fragments from the fallen guardians?",
                    "> I'll give it a go.",
                )
            }

            powers == 0L -> {
                say(a, "If you think you have enough, you should take it to Tamara.")
            }

            else -> {
                say(
                    a,
                    "You could use the workbench here to combine pure essence with fragments from the " +
                        "nearby fallen guardians. The essence would then be perfect for capturing runic " +
                        "energy.",
                    "> I'll give it a go.",
                )
            }
        }
        return
    }
    when {
        !cell -> {
            say(a, "Felix has found something on the table over there. You should speak to him.")
        }

        !barrier -> {
            box("You show the cell to Tamara.")
            say(
                a,
                "> This is what Felix found.",
                "That must be one of the cells we need! Try placing it in the tile next to me.",
                "> Alright.",
            )
            activateTile(p)
        }

        !guardians && !someGuardian -> {
            say(
                a,
                "It worked! This barrier should protect us against those creatures! And just in time " + "too. Here come more of them!",
                "> What do we do now?",
                "I'm not sure. The barrier won't hold out for long!",
            ).also {
                sayAs(
                    Npcs.TOTE_GREAT_GUARDIAN,
                    "The Great Guardian",
                    "Humans, we need more support. Restore my brethren! Place cells within imbued rocks " + "and they will do the rest.",
                )
            }.also { say(a, "Imbued rocks? What does that mean?", "> I'll see if Felix can help.", "Be careful!") }
        }

        !guardians -> {
            say(a, "Try creating another one using the other essence pile.")
        }

        !fragments -> {
            say(a, "You made some rune guardians!", "> And they seem to be holding back those creatures.")
            sayAs(
                Npcs.TOTE_GREAT_GUARDIAN,
                "The Great Guardian",
                "Humans, I lack the power needed to close the rift! I require runic energy!",
            )
            say(
                a,
                "> Runic energy? Do we need more cells?",
                "Hmm... I don't think they'll work for this. We'll need something else... Of course! We can use " +
                    "the rune guardians! See " +
                    "all the fallen guardians around here? They'd have once been powered using runic energy!",
                "If you were to obtain some fragments from them, you might be able to make something to capture " +
                    "the runic energy needed.",
                "> So mine the fallen guardians?",
                "Exactly! Hopefully you can find a pickaxe somewhere. Once you have a few fragments, go and speak " +
                    "to Felix. I'm sure " +
                    "he'll be able to help you use them.",
            )
        }

        !essence -> {
            say(
                a,
                "Do you have anything to capture some runic energy with?",
                "> I have some fragments from the fallen guardians.",
                "Take them to Felix. He'll hopefully know what to do with them.",
            )
        }

        imbues == 0L -> {
            box("You show the guardian essence to Tamara.")
            say(a, "> Do you think this will work?", "Perfect! Now we just need to capture some runic energy with it.", "> How?")
            sayAs(
                Npcs.TOTE_GREAT_GUARDIAN,
                "The Great Guardian",
                "I shall now use power from the Abyss to open a portal to the Runic Altar of Mind.",
            )
            say(
                a,
                "Well... I guess that's your answer. Head to the Mind Altar and craft some runes. That " +
                    "essence you have should capture some of the runic energy released. Take an uncharged " +
                    "cell with you as well.",
                "> Why?",
                "I have a theory to test.",
                "> Fair enough. I'll be back soon.",
            )
        }

        powers == 0L -> {
            say(
                a,
                "> What do we do now?",
                "First, you should use that new cell to replace the one powering the barrier here. I " +
                    "don't think the current one will last much longer.",
                "> And then?",
                "Then just use those stones to power up this big guardian.",
                "> I'll get right on it.",
            )
        }

        powers == 1L && imbues < 2 -> {
            say(
                a,
                "Looks like the portal to the Mind Altar has closed. I guess the guardian only keeps " +
                    "them open for so long. You'll need to use the new portal to the Water Altar instead. " +
                    "Make sure you take another uncharged cell with you.",
            )
        }

        !(p.fact(Fact.MEDIUM) > 0) -> {
            box("You show the cell and the talisman to Tamara.")
            say(
                a,
                "> Something odd happened at the Water Altar. The energy in this cell seems different " +
                    "to before. The essence also formed this strange talisman.",
                "Oh, fascinating! I guess it makes sense that the stronger the runic energy, the more " +
                    "powerful the cell will be. After all, water is more powerful than air or mind.",
                "Try making another rune guardian using that cell. In the meantime, I'll see if I can " +
                    "work out what this talisman does.",
            )
        }

        !(p.fact(Fact.STRENGTHENED) > 0) -> {
            say(
                a,
                "Great! We're almost there! I think we just need to power up this guardian one more " +
                    "time for it to be able to close that rift.",
                "I think the talisman you got can be used to return to the Water Altar without the " +
                    "portal being open. Just present that talisman to the statue where the portal to the " +
                    "Water Altar was. Make sure you take more essence and another cell with you.",
            )
        }

        else -> {
            say(
                a,
                "Look! The barrier changed! Just as I thought! We can create better barriers by using " + "stronger cells!",
                "Now, let's charge the guardian with those stones you created and close this rift!",
            )
        }
    }
}

/**
 * Tamara makes the inactive cell tile usable ("Place-cell"): the cache has it as the op-less
 * `gotr_cell_tile_inactive_noop`.
 */
private fun activateTile(p: PlayerRef) {
    val old =
        (0..SHAPES).firstNotNullOfOrNull { s -> p.locAt(Fact.TILE, s)?.takeIf { it.id == Locs.GOTR_CELL_TILE_INACTIVE_NOOP } } ?: return
    p.addLoc(Locs.GOTR_CELL_TILE_INACTIVE, Fact.TILE, old.shape, old.angle)
}

private const val SHAPES = 22
