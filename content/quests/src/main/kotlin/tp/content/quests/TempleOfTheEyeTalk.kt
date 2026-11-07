@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.chatNpcAs
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * Stages of varbit 13738 (docs/mechanics/quest-temple-of-the-eye.md TE1; the cache fixes only 0, the NPC ranges and
 * 130).
 */
internal object Tote {
    const val GOT_AMULET = 10
    const val APPRAISED = 20
    const val TEA_DRUNK = 30
    const val WASHED = 40
    const val INCANTATION = 50
    const val SHOWN_PERSTEN = 60
    const val ANALYSING = 70
    const val RIDDLE_SOLVED = 85
    const val IN_TEMPLE = 91
    const val TUTORIAL = 100
    const val CLOSED = 120
    const val END = 130

    /**
     * Cache loc / NPC multi tables: the apprentices stand in the tower from 70 to 90, Persten in Al Kharid up to 60
     * and in the tower from
     * 85 to 90.
     */
    const val PUZZLE_ANSWER = 11

    val ABYSS_CENTRE = CoordGrid(3039, 4836, 0)
    val TOWER_ARRIVAL = CoordGrid(3109, 3162, 0)
    val SEDRIDOR_ROOM = CoordGrid(3104, 9573, 0)
    val TEMPLE_LOBBY = CoordGrid(2461, 5696, 0)

    fun stage(p: tp.plugin.PlayerRef): Int = Quests.TEMPLE_OF_THE_EYE.stage(p)

    fun set(p: tp.plugin.PlayerRef, to: Int) {
        Quests.TEMPLE_OF_THE_EYE.advance(p, to)
    }

    /** The three apprentices' riddles (Wiki walkthrough "Help from Some Wizards"): first and second sentence. */
    val RIDDLES =
        mapOf(
            "Cordelia" to ("Fire is four and Body is seven." to "A thingummywut is less than three Water and more than three Air."),
            "Felix" to ("Water is six and Mind is eight." to "A thingummywut is less than two Body and more than two Earth."),
            "Tamara" to ("Earth is five and Air is three." to "A thingummywut is less than three Fire and more than one Mind."),
        )
}

/** Dialogue helper: a line starting with "> " is the player's, any other is [npc]'s. */
internal suspend fun PlayerScript.say(npc: NpcRef, vararg lines: String) {
    for (l in lines) if (l.startsWith("> ")) me(l.substring(2)) else npc(npc, l)
}

/** As [say] for a speaker who is not clicked (a cutscene): NPC type [type] under [who]. */
internal suspend fun PlayerScript.sayAs(type: Int, who: String, vararg lines: String) {
    for (l in lines) if (l.startsWith("> ")) me(l.substring(2)) else chatNpcAs(type, who, l)
}

/**
 * The Mage of Zamorak's, Sedridor's and Traiborn's extra options while the quest is under way (they are merged into
 * the NPCs' own menus).
 */
internal fun templeMageOptions(p: tp.plugin.PlayerRef): List<Pair<String, suspend PlayerScript.(NpcRef) -> Unit>> {
    val stage = Tote.stage(p)
    val out = ArrayList<Pair<String, suspend PlayerScript.(NpcRef) -> Unit>>()
    when {
        stage == Tote.GOT_AMULET -> {
            out += "I need your help with an amulet." to { mage -> mageAmulet(mage) }
        }

        stage in Tote.APPRAISED until Tote.END -> {
            out += "Could you help me with that amulet now?" to { mage -> mageAmulet(mage) }
        }

        stage >= Tote.END && !p.flag(MAGE_POST) -> {
            out +=
                "About that incantation your initiate gave me..." to { mage -> mageIncantation(mage) }
        }
    }
    return out
}

internal const val MAGE_POST = "quests.tote.magepost"

// ---- Herbert (the Mage of Zamorak in Varrock)

internal suspend fun PlayerScript.mageAmulet(mage: NpcRef) {
    val p = player
    when (Tote.stage(p)) {
        Tote.GOT_AMULET -> {
            say(mage, "> I need your help with an amulet.", "An amulet? What amulet?")
            if (!p.has(Objs.TOTE_AMULET)) return say(mage, "> Oh, I don't actually have it with me. I'll be back.", "Right...")
            box("You show the amulet to the Mage of Zamorak.")
            say(
                mage,
                "That eye on it... That is the symbol of the Order of Wizards. What's all this about?",
                "> The amulet is giving off abyssal energy. I was hoping you could help me work out where it came " +
                    "from.",
                "Why should I?",
                "> Well I did help you gain access to the Rune Essence Mine.",
                "And we gave you knowledge on the Abyss in return. We owe you no further favours.",
                "> Are you not even slightly curious about how an amulet with the symbol of the Order of Wizards " +
                    "on it is giving off " +
                    "abyssal energy? After all, they don't have access to the Abyss.",
                "I hope you're not about to tell me that you shared our secrets with the Saradominists?",
                "> Of course not.",
                "Then where did you get the amulet?",
                "> I... bought it from an archaeologist.",
                "There's something off about all of this. Show it to me again.",
            )
            box("You show the amulet to the Mage of Zamorak again. As you do, he casts a spell upon it.")
            say(
                mage,
                "> What was that?",
                "Just a spell to tell me more about it. It seems this amulet is very unusual.",
                "> So you'll help?",
                "Very well, but first you must do something for me. Bring me a bucket of water and a strong cup " +
                    "of tea.",
                "> A strong cup of tea?",
                "That's right. The tea seller just north east of here can give you one. He owes me, in fact, so " +
                    "just tell him Herbert " +
                    "sent you.",
                "> Wait... Your name is Herbert?",
                "Is that a problem?",
                "> I guess not. I just never had you down as a Herbert.",
            )
            Tote.set(p, Tote.APPRAISED)
        }

        Tote.APPRAISED -> {
            say(mage, "> Could you help me with that amulet now?", "Do you have a bucket of water and a strong cup of tea?")
            if (!p.has(Objs.TOTE_CUP_OF_TEA_STRONG) || !p.has(Objs.BUCKET_WATER)) {
                return say(
                    mage,
                    "> Not yet.",
                    "I will not help you until you bring them to me. The tea seller just north east of here can " +
                        "give you a strong cup " +
                        "of tea. Just tell him Herbert sent you.",
                )
            }
            say(mage, "> Here you go.")
            p.take(Objs.TOTE_CUP_OF_TEA_STRONG, 1, "tote tea")
            box("The Mage of Zamorak takes the strong cup of tea from you and immediately drinks it.")
            say(
                mage,
                "Aaah, nothing like a nice cuppa tea!",
                "Right, a deal is a deal, but the spell to determine the origin of that amulet will only work " +
                    "from the Abyss, so you'll " +
                    "need to have the initiate there perform it for you.",
                "Meet me in the Wilderness when you're ready to be teleported there.",
                "> Can't you just teleport me from here?",
                "From the middle of Varrock? What if someone is watching?",
                "> If someone was watching, they'd know you have a secret Chaos Temple here in Varrock. I reckon " +
                    "you're pretty safe.",
                "Fine, but don't expect me to make a habit of this. I will offer you a single teleport from here " +
                    "directly to the " +
                    "initiate in the Abyss. Make sure you are definitely ready.",
                "> Hang on... what was the bucket of water for?",
                "Ah, that. While time passes slower in the Abyss, the initiate maintaining the rift has still " +
                    "been there for quite a " +
                    "while. He needs a good bath.",
                "> So you just want me to throw it over him?",
                "Exactly. Now, are you ready to be teleported? Don't forget, this is a one-time offer. In future, " +
                    "you'll need to meet " +
                    "me in the Wilderness to access the Abyss.",
            )
            Tote.set(p, Tote.TEA_DRUNK)
            abyssOffer(mage)
        }

        else -> {
            if (Tote.stage(p) == Tote.TEA_DRUNK) {
                say(
                    mage,
                    "> Could you help me with that amulet now?",
                    "The spell to determine the origin of that amulet will only work from the Abyss, so you'll " +
                        "need to have the " +
                        "initiate there perform it for you.",
                )
                if (p.varbit(Varbits.TOTE_ABYSS_TELEPORT_USED) == 1) {
                    return say(
                        mage,
                        "I already gave you your one-time teleport to the Abyss from here, so you'll need to meet " +
                            "me in the Wilderness " +
                            "if you want teleporting there.",
                        "> Alright.",
                    )
                }
                say(
                    mage,
                    "Are you ready to be teleported there? Don't forget, this is a one-time offer. In future, " +
                        "you'll need to meet me in " +
                        "the Wilderness to access the Abyss.",
                )
                abyssOffer(mage)
            } else {
                say(
                    mage,
                    "> Could you help me with that amulet now?",
                    "The spell to determine the origin of that amulet will only work from the Abyss, so you'll " +
                        "need to have the " +
                        "initiate there perform it for you.",
                    "> Oh, he already did that.",
                    "Then why are you talking to me? You have what you need.",
                )
            }
        }
    }
}

private suspend fun PlayerScript.abyssOffer(mage: NpcRef) {
    val p = player
    if (!p.has(Objs.TOTE_AMULET)) return say(mage, "> I should probably get the amulet first.", "That would be wise.")
    if (!p.has(Objs.BUCKET_WATER)) return say(mage, "> I should probably get that bucket of water first.", "That would be wise.")
    if (choice("Teleport to the Abyss? You can only teleport from here once.", "Yes.", "No.") == 1) {
        mage.say("Veniens! Sallakar! Rinnesset!")
        p.setVarbit(Varbits.TOTE_ABYSS_TELEPORT_USED, 1)
        p.teleport(abyssLanding(p))
    } else {
        say(mage, "> I'm not ready yet.", "Well then return to me when you are, but try not to waste too much of my time.")
    }
}

private fun abyssLanding(p: tp.plugin.PlayerRef): CoordGrid =
    (0..LANDING_RADIUS)
        .flatMap { r ->
            (-r..r).flatMap { dx -> (-r..r).map { dz -> Tote.ABYSS_CENTRE.translate(dx, dz) } }
        }.firstOrNull { p.isWalkable(it) }
        ?: Tote.ABYSS_CENTRE

private const val LANDING_RADIUS = 4

internal suspend fun PlayerScript.mageIncantation(mage: NpcRef) {
    say(
        mage,
        "> About that incantation your initiate gave me...",
        "Ah yes, the one to trace that amulet. Don't think I didn't notice that you modified the transmission " +
            "spell I put on it. Next " +
            "time you need help, we might not be so willing.",
        "> Well maybe you should have asked first! Besides, you wouldn't have got anything useful out of it. In " +
            "tracing that amulet, we " +
            "accidently managed to open an uncontrollable rift to the Abyss.",
        "And does this 'we' include the Order of Wizards?",
        "> Maybe...",
        "I knew it! It's just like them to play with magic they don't understand. First they burnt down the " +
            "Wizards' Tower, now they're " +
            "opening uncontrollable abyssal rifts!",
        "Perhaps the fools should leave the dangerous magic to us next time!",
        "> Oh, that reminds me. That incantation didn't appear to be written on parchment. What was it?",
        "Hmm... probably the flesh of an abyssal creature. Why do you ask?",
        "> Oh, I just thought it might be human flesh. I'm relieved it wasn't!",
        "You've been spending too much time with your friends in the Order of Wizards! I know that we Zamorakians " +
            "have a bad " +
            "reputation, but do you really think we'd be so barbaric?",
    )
    player.setFlag(MAGE_POST, true)
    player.setVarbit(Varbits.TOTE_ZMI_POSTQUEST_CHAT, 1)
}

// ---- the Tea Seller

internal suspend fun PlayerScript.teaSellerTalk(tea: NpcRef) {
    val p = player
    npc(tea, "Greetings! Are you in need of refreshment?")
    val wants = Tote.stage(p) >= Tote.APPRAISED && Tote.stage(p) < Tote.END
    val labels = ArrayList<String>()
    labels += "Yes please."
    if (wants) labels += "Could I have a strong cup of tea?"
    labels += "No thanks."
    val pick = choice("Select an option", *labels.toTypedArray())
    when {
        pick == 1 -> {
            me("Yes please.")
            // The Tea Seller's stall is the shops plugin's (its "Trade" op).
            tea.say("Here you are.")
        }

        wants && pick == 2 -> {
            me("Could I have a strong cup of tea?")
            if (p.varbit(Varbits.TOTE_RECEIVED_TEA_BEFORE) == 0) {
                npc(tea, "A strong cup of tea? What do you mean?")
                me("Herbert sent me for one.")
                npc(
                    tea,
                    "Ah, well why didn't you say that? Between you and me, Herbert's strong cups of tea are " +
                        "actually just my regular " +
                        "cups of tea, but if it makes him happy...",
                )
            } else if (p.has(Objs.TOTE_CUP_OF_TEA_STRONG)) {
                npc(tea, "For Herbert? I already gave you one for him.")
                return me("Ah yes, how silly of me.")
            } else {
                npc(tea, "For Herbert? I already gave you one for him.")
                me("Sorry, but I need another.")
                npc(tea, "Hmm... alright, but I can't keep making these for free you know.")
            }
            if (p.give(Objs.TOTE_CUP_OF_TEA_STRONG, 1, "tote tea")) {
                p.setVarbit(Varbits.TOTE_RECEIVED_TEA_BEFORE, 1)
                itemBox(Objs.TOTE_CUP_OF_TEA_STRONG, "The Tea Seller hands you a strong cup of tea.")
                npc(tea, "I hope Herbert enjoys it.")
            } else {
                itemBox(
                    Objs.TOTE_CUP_OF_TEA_STRONG,
                    "The Tea Seller tries to hand you a strong cup of tea, but you don't have enough room to take " +
                        "it.",
                )
            }
        }

        else -> {
            me("No thanks.")
        }
    }
}

// ---- the Dark Mage in the Abyss

internal suspend fun PlayerScript.darkMageTalk(dark: NpcRef) {
    val p = player
    val stage = Tote.stage(p)
    me("Hello there.")
    npc(dark, "Quiet! You must not break my concentration!")
    val labels = ArrayList<String>()
    if (stage == Tote.TEA_DRUNK || stage == Tote.APPRAISED) labels += "I need your help with an amulet."
    if (stage == Tote.WASHED) labels += "What do I need to do with this energy?"
    if (stage >= Tote.INCANTATION && stage < Tote.ANALYSING) labels += "About that incantation..."
    labels += "Sorry, I'll go."
    val pick = labels[choice("Select an option", *labels.toTypedArray()) - 1]
    when (pick) {
        "I need your help with an amulet." -> {
            darkMageAmulet(dark)
        }

        "What do I need to do with this energy?" -> {
            say(dark, "> What do I need to do with this energy?", "Work out which order it goes in!", "> Right...")
        }

        "About that incantation..." -> {
            say(dark, "> About that incantation...", "What about it?")
            if (p.has(Objs.TOTE_INCANTATION)) {
                say(dark, "> Er... Nothing.", "Nothing! Would you leave me alone!")
            } else {
                say(dark, "> I lost it.", "Oh the things I would do to you if I wasn't maintaining this rift! Here, take this you fool!")
                if (p.give(
                        Objs.TOTE_INCANTATION,
                        1,
                        "tote incantation",
                    )
                ) {
                    itemBox(Objs.TOTE_INCANTATION, "The Dark Mage hands you an incantation.")
                }
            }
        }

        else -> {
            me("Sorry, I'll go.")
        }
    }
}

internal suspend fun PlayerScript.darkMageAmulet(dark: NpcRef) {
    val p = player
    say(dark, "> I need your help with an amulet.", "Amulet?")
    if (!p.has(
            Objs.TOTE_AMULET,
        )
    ) {
        return say(dark, "> Yes, but I don't seem to have it with me. I'll be back soon.", "What? Are you trying to waste my time?")
    }
    if (!p.has(
            Objs.BUCKET_WATER,
        )
    ) {
        return say(dark, "> Yes, but I also need to go and get a bucket of water.", "What? Are you trying to waste my time?")
    }
    say(dark, "Oh, hang on. Someone is trying to contact me...")
    box("The Dark Mage speaks with someone unseen.")
    say(
        dark,
        "What do you want? Can't you see I'm busy?",
        "Fine. A simple transfiguration spell should resolve things for you.",
        "Now can you leave me alone? I can't keep affording these distractions!",
    )
    say(dark, "What did you want? Something about an amulet?", "> Yes, but first...")
    p.take(Objs.BUCKET_WATER, 1, "tote bucket")
    p.give(Objs.BUCKET_EMPTY, 1, "tote bucket")
    box("You throw the bucket of water over the Dark Mage.")
    say(
        dark,
        "What in the actual...",
        "> Sorry, but Herbert said you needed a wash.",
        "Are you serious? What if I'd lost concentration and let this rift collapse? Do you like having a brain " +
            "that isn't made of " +
            "mush? Because mush is all that would have been left!",
        "> I like my brain how it is, thanks. Now, could you help me?",
        "Why should I? I'm soaked thanks to you!",
        "> Herbert said you would.",
        "Of course he did! Alright then, what do you want?",
        "> This amulet is giving off abyssal energy. I need you to cast a spell to tell me where it came from.",
        "Let's have a look then.",
    )
    box("You show the amulet to the Dark Mage.")
    say(dark, "Right, just give me a moment.")
    box("The Dark Mage casts a spell on the amulet.")
    say(
        dark,
        "Okay, this spell will allow me to triangulate the origin of the amulet. To make things easier, I'm using " +
            "this rift to compare " +
            "energy from that origin with energy from the runic altars.",
        "Of course, thanks to the ways time and space work in this place, the information won't necessarily come " +
            "in the right order.",
        "> Meaning?",
        "Meaning I'll need you to help, you fool! You see that energy that's just emerged from the rift? Work out " +
            "which order it goes in!",
        "> Right...",
    )
    Tote.set(p, Tote.WASHED)
    AbyssEnergy.reset(p)
}

// ---- the abyssal energy puzzle (Wiki walkthrough: the order is random per player; OPEN-TE2: a wrong touch resets
// the run)

internal object AbyssEnergy {
    /** Cache varbits `tote_abyssal_energy_*` (13747..13752): 0 hidden, 1 touchable, 2 lit white, 3 gone. */
    val VARBITS =
        intArrayOf(
            Varbits.TOTE_ABYSSAL_ENERGY_EARTH,
            Varbits.TOTE_ABYSSAL_ENERGY_COSMIC,
            Varbits.TOTE_ABYSSAL_ENERGY_DEATH,
            Varbits.TOTE_ABYSSAL_ENERGY_NATURE,
            Varbits.TOTE_ABYSSAL_ENERGY_LAW,
            Varbits.TOTE_ABYSSAL_ENERGY_FIRE,
        )
    const val ORDER = "quests.tote.order"
    const val PROGRESS = "quests.tote.progress"
    private const val SHOWN = 1
    private const val LIT = 2
    private const val GONE = 3

    /** Shows the six energies in a fresh random order (a base-6 number kept on the account). */
    fun reset(p: tp.plugin.PlayerRef) {
        val order = (0 until VARBITS.size).toMutableList()
        for (i in order.size - 1 downTo 1) {
            val j = p.random(i + 1)
            val t = order[i]
            order[i] = order[j]
            order[j] = t
        }
        p.setSaved(ORDER, order.fold(0L) { acc, v -> acc * VARBITS.size + v })
        p.setSaved(PROGRESS, 0L)
        for (v in VARBITS) p.setVarbit(v, SHOWN)
    }

    private fun order(p: tp.plugin.PlayerRef): IntArray {
        var v = p.saved(ORDER) ?: 0L
        val out = IntArray(VARBITS.size)
        for (i in out.indices.reversed()) {
            out[i] = (v % VARBITS.size).toInt()
            v /= VARBITS.size
        }
        return out
    }

    /** Touching energy [which] (an index of [VARBITS]). */
    fun touch(p: tp.plugin.PlayerRef, which: Int) {
        if (Tote.stage(p) != Tote.WASHED) return
        val order = order(p)
        val k = (p.saved(PROGRESS) ?: 0L).toInt()
        if (order[k] == which) {
            p.setVarbit(VARBITS[which], LIT)
            p.setSaved(PROGRESS, (k + 1).toLong())
            if (k + 1 == VARBITS.size) {
                for (v in VARBITS) p.setVarbit(v, GONE)
                p.message("The runic energy settles into a pattern.")
            }
        } else {
            for (v in VARBITS) p.setVarbit(v, SHOWN)
            p.setSaved(PROGRESS, 0L)
            if (order[0] == which) {
                p.setVarbit(VARBITS[which], LIT)
                p.setSaved(PROGRESS, 1L)
            }
        }
    }

    fun solved(p: tp.plugin.PlayerRef): Boolean = (p.saved(PROGRESS) ?: 0L) >= VARBITS.size
}

internal fun templeDarkMageSolved(p: tp.plugin.PlayerRef): Boolean = Tote.stage(p) == Tote.WASHED && AbyssEnergy.solved(p)

/** "I've worked out which order the energy goes in." (the Dark Mage's talk once the puzzle is solved). */
internal suspend fun PlayerScript.darkMageIncantation(dark: NpcRef) {
    val p = player
    say(
        dark,
        "> I've worked out which order the energy goes in.",
        "Yes, I can see that. Now, hang on.",
    )
    box("The Dark Mage pauses for a moment, as if lost in thought. He then hastily pulls out a scroll and scribbles something on it.")
    say(
        dark,
        "I've noted the details on this scroll. A competent enough wizard should be able to use it to cast a " +
            "teleportation spell to " +
            "take you right there.",
        "> Can you tell me anything about the place?",
        "With all the time I've spent here, I'd like to consider myself an expert on the Abyss, but there's " +
            "something very strange " +
            "about the place this amulet came from.",
        "> Strange how?",
        "Well if I knew that, it wouldn't be strange, would it? Now, if you're done bothering me, I need to stay " +
            "focused on this rift!",
    )
    if (p.give(Objs.TOTE_INCANTATION, 1, "tote incantation")) {
        Tote.set(p, Tote.INCANTATION)
        p.setVarbit(Varbits.TOTE_RECEIVED_INCANTATION_BEFORE, 1)
        itemBox(Objs.TOTE_INCANTATION, "The Dark Mage hands you an incantation.")
    } else {
        p.message("You don't have enough inventory space.")
    }
}

internal val TOTE_REWARD_XP = Stats.RUNECRAFT to 5000.0
