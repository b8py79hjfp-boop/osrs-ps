@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttackCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TimerId
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Demon Slayer (docs/mechanics/quest-demon-slayer.md): Aris foresees Delrith; Sir Prysin keeps Silverlight in a case with three keys
 * (his own in the palace drain, Captain Rovin's, Wizard Traiborn's for 25 bones); at the Varrock stone circle Delrith is summoned,
 * Silverlight must be wielded to kill him, and the incantation banishes his weakened form. The quest variable (varbit 2561) is
 * 0 / 1 started / 2 Silverlight given (ours, OPEN-QU1) / 3 finished (cache `endstate`). The steps in between live in account
 * flags `quests.ds.*`. Dialogue is the Wiki "Transcript:Demon Slayer", "Transcript:Sir Prysin" and "Transcript:Captain Rovin".
 */
public class DemonSlayer : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.ARIS, OP_TALK) { aris ->
            player.stopInteraction()
            arisTalk(aris)
        }
        for (prysin in intArrayOf(Npcs.SIR_PRYSIN, Npcs.SIR_PRYSIN_SILVERLIGHT)) {
            onOpNpc(prysin, OP_TALK) { npc ->
                player.stopInteraction()
                prysinTalk(npc)
            }
        }
        onOpNpc(Npcs.CAPTAIN_ROVIN, OP_TALK) { rovin ->
            player.stopInteraction()
            rovinTalk(rovin)
        }
        onOpNpc(Npcs.TRAIBORN, OP_TALK) { traiborn ->
            player.stopInteraction()
            traibornTalk(traiborn)
        }
        for (drain in DRAINS) {
            onUseOnLoc(Objs.BUCKET_WATER, drain) {
                player.stopInteraction()
                washKey()
            }
            onOpLoc(drain, OP_SEARCH) {
                player.stopInteraction()
                searchDrain()
            }
        }
        onOpNpc(Npcs.DELRITH_WEAKENED, OP_BANISH) { delrith ->
            player.stopInteraction()
            banish(delrith)
        }
        // Delrith can only be defeated with Silverlight wielded (Wiki "Delrith"); the check is the plugin API's attack gate.
        events.on<AttackCheck> { check ->
            val target = check.target as? NpcRef ?: return@on
            val attacker = check.attacker as? PlayerRef ?: return@on
            if (target.type == Npcs.DELRITH && attacker.inv(Invs.WORN).total(Objs.SILVERLIGHT) == 0L) {
                check.deny("I'd better wield Silverlight first.")
            }
        }
        // Delrith's defeat: he is weakened instead of dying for good (Wiki "Demon Slayer" > Banishing Delrith).
        events.on<DeathEvent> { e ->
            val delrith = e.entity as? NpcRef ?: return@on
            if (delrith.type != Npcs.DELRITH) return@on
            val at = delrith.coord
            delrith.despawn()
            val weak = delrith.spawnNpc(Npcs.DELRITH_WEAKENED, at, 0)
            weak.queueHit(WEAK_LIFETIME, strong = false) { if (weak.isInWorld) weak.despawn() }
        }
        // Entering the stone circle with Silverlight summons Delrith (no area-entry event in the plugin API, OPEN-QUA19).
        onLogin { timer(CIRCLE_TIMER, CIRCLE_CHECK, true) { circle() } }
        events.on<JournalRequest> {
            if (it.quest == Quests.DEMON_SLAYER) it.lines += journal(it.player)
        }
    }

    private val PlayerRef.stage: Int get() = Quests.DEMON_SLAYER.stage(this)

    private fun journal(p: PlayerRef): List<String> =
        when (p.stage) {
            0 -> {
                listOf("I can start this quest by speaking to Aris, the fortune-teller in Varrock Square.")
            }

            STARTED -> {
                val keys =
                    listOf(
                        "Captain Rovin's key: ${if (p.flag(FLAG_ROVIN) || p.has(Objs.SILVERLIGHT_KEY_2)) "have it" else "not yet"}",
                        "Wizard Traiborn's key: ${if (p.has(Objs.SILVERLIGHT_KEY_1)) "have it" else "${p.bones()}/$BONES bones given"}",
                        "Sir Prysin's key: ${if (p.has(Objs.SILVERLIGHT_KEY_3)) "have it" else "washed down the palace drain"}",
                    )
                listOf(
                    "Delrith is being summoned in the stone circle south of Varrock. I need Silverlight from Sir Prysin, who keeps it " +
                        "in a case with three keys.",
                ) + keys
            }

            SILVERLIGHT -> {
                listOf("I have Silverlight. I must kill Delrith in the stone circle and banish him with the incantation.")
            }

            else -> {
                listOf("I banished the demon Delrith with Silverlight.")
            }
        }

    // ---- the incantation

    /** The five words of Wally's incantation (Wiki "Demon Slayer"); their order is random for every player. */
    private fun PlayerRef.words(): List<String> {
        var code = saved(KEY_WORDS)
        if (code == null) {
            val order = WORDS.indices.toMutableList()
            for (i in order.size - 1 downTo 1) {
                val j = random(i + 1)
                val t = order[i]
                order[i] = order[j]
                order[j] = t
            }
            code = order.fold(0L) { acc, w -> acc * WORDS.size + w }
            setSaved(KEY_WORDS, code)
        }
        val digits = ArrayList<Int>()
        var rest: Long = code
        repeat(WORDS.size) {
            digits.add(0, (rest % WORDS.size).toInt())
            rest /= WORDS.size
        }
        return digits.map { WORDS[it] }
    }

    private fun PlayerRef.bones(): Int = (saved(KEY_BONES) ?: 0L).toInt()

    // ---- Aris

    private suspend fun PlayerScript.arisTalk(aris: NpcRef) {
        val p = player
        when (p.stage) {
            0 -> {
                npc(aris, "Hello young one. Cross my palm with silver and the future will be revealed to you.")
                if (!p.has(Objs.COINS)) {
                    me("Oh dear. I don't have any money.")
                    return
                }
                if (choice("Select an Option", "Yes.", "No.") != 1) {
                    me("No, I don't believe in that stuff.")
                    npc(aris, "Ok suit yourself.")
                    return
                }
                me("Okay, here you go.")
                npc(
                    aris,
                    "Come closer, and listen carefully to what the future holds for you, as I peer into the swirling mists of the " +
                        "crystal ball.",
                    "I can see images forming. I can see you.",
                    "You are holding a very impressive looking sword. I'm sure I recognise that sword...",
                    "There is a big dark shadow appearing now.",
                    "Aaargh!",
                )
                me("Are you all right?")
                npc(aris, "It's Delrith! Delrith is coming!")
                me("Who's Delrith?")
                npc(
                    aris,
                    "Delrith...",
                    "Delrith is a powerful demon.",
                    "Oh! I really hope he didn't see me looking at him through my crystal ball!",
                    "He tried to destroy this city 150 years ago. He was stopped just in time by the great hero Wally.",
                    "Using his magic sword Silverlight, Wally managed to trap the demon in the stone circle just south of this city.",
                    "Ye gods! Silverlight was the sword you were holding in my vision! You are the one destined to stop the demon " +
                        "this time.",
                )
                firstMenu(aris)
            }

            STARTED -> {
                npc(aris, "Greetings. How goes thy quest?")
                if (p.flag(FLAG_PRYSIN)) {
                    me("I found Sir Prysin. Unfortunately I haven't got the sword yet. He's made it complicated for me.")
                    npc(aris, "Ok, hurry, we haven't much time.")
                } else {
                    me("I'm still working on it.")
                    npc(aris, "Well if you need any advice I'm always here, young one.")
                }
                questionsMenu(aris, withDemon = false)
            }

            SILVERLIGHT -> {
                npc(aris, "How goes the quest?")
                me("I have the sword now. I just need to kill the demon, I think.")
                npc(aris, "Yep, that's right.")
                questionsMenu(aris, withDemon = true)
            }

            else -> {
                npc(aris, "Greetings young one.", "You're a hero now. That was a good bit of demonslaying.")
                when (choice("What would you like to say?", "How do you know I killed it?", "Thanks.", "Stop calling me that!")) {
                    1 -> {
                        me("How do you know I killed it?")
                        npc(aris, "You forget. I'm good at knowing things.")
                    }

                    2 -> {
                        me("Thanks.")
                    }

                    3 -> {
                        stopCalling(aris)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }
    }

    private suspend fun PlayerScript.stopCalling(aris: NpcRef) {
        me("Stop calling me that!")
        npc(aris, "In the scheme of things you are very young.")
        if (choice("What would you like to say?", "Ok but how old are you?", "Oh if it's in the scheme of things that's ok.") == 1) {
            me("Ok, but how old are you?")
            npc(aris, "Count the number of legs on the stools in the Blue Moon inn, and multiply that number by seven.")
            me("Er, yeah, whatever.")
        } else {
            me("Oh if it's in the scheme of things that's ok.")
            npc(aris, "You show wisdom for one so young.")
        }
    }

    /** The menu after the first vision (Wiki "Starting off"): the warnings, the Wally scene, and the way to start the quest. */
    private suspend fun PlayerScript.firstMenu(aris: NpcRef) {
        var wally = false
        while (true) {
            val options = ArrayList<String>()
            options += "How am I meant to fight a demon who can destroy cities?"
            options += "Okay, where is he? I'll kill him for you!"
            if (!wally) {
                options += "Wally doesn't sound like a very heroic name."
                options += "So how did Wally kill Delrith?"
            } else {
                options += "What is the magical incantation?"
                options += "Where can I find Silverlight?"
                options += "Okay, thanks. I'll do my best to stop the demon."
            }
            when (options[choice("What would you like to say?", *options.toTypedArray()) - 1]) {
                "How am I meant to fight a demon who can destroy cities?" -> {
                    me("How am I meant to fight a demon who can destroy cities?!")
                    npc(
                        aris,
                        "If you face Delrith while he is still weak from being summoned, and use the correct weapon, you will not " +
                            "find the task too arduous.",
                        "Do not fear. If you follow the path of the great hero Wally, then you are sure to defeat the demon.",
                    )
                }

                "Okay, where is he? I'll kill him for you!" -> {
                    me("Okay, where is he? I'll kill him for you!")
                    npc(
                        aris,
                        "Ah, the overconfidence of the young!",
                        "Delrith can't be harmed by ordinary weapons. You must face him using the same weapon that Wally used.",
                    )
                }

                "Wally doesn't sound like a very heroic name." -> {
                    me("Wally doesn't sound a very heroic name.")
                    npc(
                        aris,
                        "Yes I know. Maybe that is why history doesn't remember him. However he was a very great hero.",
                        "Who knows how much pain and suffering Delrith would have brought forth without Wally to stop him!",
                        "It looks like you are going to need to perform similar heroics.",
                    )
                }

                "So how did Wally kill Delrith?" -> {
                    me("So how did Wally kill Delrith?")
                    box("A cutscene showing Wally defeating Delrith plays.")
                    npc(
                        aris,
                        "Wally managed to arrive at the stone circle just as Delrith was summoned by a cult of chaos druids...",
                    )
                    npcAs(Npcs.QIP_DS_WALLY, "Wally", "Die, foul demon!", "Now, what was that incantation again?")
                    npcAs(Npcs.QIP_DS_WALLY, "Wally", player.words().joinToString("... ") + "!")
                    npcAs(Npcs.QIP_DS_WALLY, "Wally", "I am the greatest demon slayer EVER!")
                    npc(
                        aris,
                        "By reciting the correct magical incantation, and thrusting Silverlight into Delrith while he was newly " +
                            "summoned, Wally was able to imprison Delrith in the stone block in the centre of the circle.",
                    )
                    box("Cutscene ends.")
                    npc(
                        aris,
                        "Delrith will come forth from the stone circle again.",
                        "I would imagine an evil sorcerer is already starting on the rituals to summon Delrith as we speak.",
                    )
                    wally = true
                }

                "What is the magical incantation?" -> {
                    incantation(aris)
                }

                "Where can I find Silverlight?" -> {
                    silverlightWhere(aris)
                }

                else -> {
                    me("Ok thanks. I'll do my best to stop the demon.")
                    npc(aris, "Good luck, and may Guthix be with you!")
                    Quests.DEMON_SLAYER.advance(player, STARTED)
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.incantation(aris: NpcRef) {
        me("What is the magical incantation?")
        npc(aris, "Oh yes, let me think a second...")
        npc(aris, "Alright, I think I've got it now, it goes.... " + player.words().joinToString("... ") + ". Have you got that?")
        me("I think so, yes.")
    }

    private suspend fun PlayerScript.silverlightWhere(aris: NpcRef) {
        me("Where can I find Silverlight?")
        npc(
            aris,
            "Silverlight has been passed down through Wally's descendants. I believe it is currently in the care of one of the " +
                "King's knights called Sir Prysin.",
            "He shouldn't be too hard to find. He lives in the royal palace in this city. Tell him Aris sent you.",
        )
    }

    /** The menu of Aris during the quest. */
    private suspend fun PlayerScript.questionsMenu(aris: NpcRef, withDemon: Boolean) {
        while (true) {
            val options = ArrayList<String>()
            options += "What is the magical incantation?"
            if (!withDemon) options += "Where can I find Silverlight?" else options += "Where can I find the demon?"
            options += "Stop calling me that!"
            options += "Well I'd better press on with it."
            when (options[choice("What would you like to say?", *options.toTypedArray()) - 1]) {
                "What is the magical incantation?" -> {
                    incantation(aris)
                }

                "Where can I find Silverlight?" -> {
                    silverlightWhere(aris)
                }

                "Where can I find the demon?" -> {
                    me("Where can I find the demon?")
                    npc(aris, "Just head south and you should find the stone circle just outside the city gate.")
                    return
                }

                "Stop calling me that!" -> {
                    stopCalling(aris)
                    return
                }

                else -> {
                    me("Well I'd better press on with it.")
                    npc(aris, "See you anon.")
                    return
                }
            }
        }
    }

    // ---- Sir Prysin

    private suspend fun PlayerScript.prysinTalk(prysin: NpcRef) {
        val p = player
        when {
            p.stage >= FINISHED -> {
                npc(prysin, "Hello. I've heard you stopped the demon, well done.")
                when (choice("Select an Option", "Yes, that's right.", "Yes, although I'm afraid I've lost Silverlight.")) {
                    1 -> {
                        me("Yes, that's right.")
                        npc(prysin, "A good job well done then.")
                        me("Thank you.")
                    }

                    2 -> {
                        reclaim(prysin)
                    }

                    else -> {
                        Unit
                    }
                }
            }

            p.stage == SILVERLIGHT -> {
                npc(prysin, "Have you sorted that demon out yet?")
                if (p.has(Objs.SILVERLIGHT) || p.inv(Invs.WORN).total(Objs.SILVERLIGHT) > 0) {
                    me("No, not yet.")
                    npc(prysin, "Well get on with it. He'll be pretty powerful when he gets to full strength.")
                } else {
                    me("Not yet. And I, um, lost Silverlight.")
                    npc(
                        prysin,
                        "Yes, I know, someone returned it to me. Take better care of it this time. With full inventory it drops to " +
                            "the ground.",
                    )
                    p.giveOrDrop(Objs.SILVERLIGHT, 1, "demon slayer silverlight")
                }
            }

            p.stage == STARTED && p.flag(FLAG_PRYSIN) -> {
                npc(prysin, "So how are you doing with getting the keys?")
                val keys = KEYS.filter { p.has(it) }
                if (keys.size == KEYS.size) {
                    me("I've got all three keys!")
                    npc(prysin, "Excellent! Now I can give you Silverlight.")
                    val ok =
                        p.transaction("demon slayer silverlight") {
                            KEYS.all { remove(Invs.INV, it, 1) } && addOrDrop(Invs.INV, Objs.SILVERLIGHT, 1)
                        }
                    if (!ok) return
                    box("Sir Prysin walks to the sword case, unlocks it and hands you Silverlight.")
                    box("Sir Prysin hands you a very shiny sword.")
                    npc(
                        prysin,
                        "That sword belonged to my great-grandfather. Make sure you treat it with respect!",
                        "Now go kill that demon!",
                    )
                    Quests.DEMON_SLAYER.advance(p, SILVERLIGHT)
                    return
                }
                if (keys.isEmpty()) {
                    me("I haven't found any of them yet.")
                    if (choice("Select an Option", "Can you remind me where all the keys were again?", "I'm still looking.") == 1) {
                        me("Can you remind me where all the keys were again?")
                        npc(
                            prysin,
                            "I kept one of the keys. I gave the other two to other people for safe keeping.",
                            "One I gave to Rovin, the captain of the palace guard.",
                            "I gave the other to the wizard Traiborn.",
                        )
                        keyQuestions(prysin)
                    } else {
                        me("I'm still looking.")
                        npc(prysin, "Ok, tell me when you've got them all.")
                    }
                    return
                }
                me(keyLine(keys))
                npc(prysin, "Ok, tell me when you've got them all.")
            }

            else -> {
                npc(prysin, "Hello, who are you?")
                val options = ArrayList<String>()
                options += "I am a mighty adventurer. Who are you?"
                options += "I'm not sure, I was hoping you could tell me."
                if (p.stage >= STARTED) options += "Aris said I should come and talk to you."
                when (options[choice("Select an Option", *options.toTypedArray()) - 1]) {
                    "I am a mighty adventurer. Who are you?" -> {
                        me("I am a mighty adventurer, who are you?")
                        npc(prysin, "I am Sir Prysin. A bold and famous knight of the realm.")
                    }

                    "I'm not sure, I was hoping you could tell me." -> {
                        me("I was hoping you could tell me.")
                        npc(prysin, "Well I've never met you before.")
                    }

                    else -> {
                        me("Aris said I should come and talk to you.")
                        npc(
                            prysin,
                            "Aris? Is she still alive? I remember her from when I was pretty young. Well what do you need to talk to " +
                                "me about?",
                        )
                        me("I need to find Silverlight.")
                        npc(prysin, "What do you need to find that for?")
                        me("I need it to fight Delrith.")
                        npc(prysin, "Delrith? I thought the world was rid of him, thanks to my great-grandfather.")
                        if (choice(
                                "Select an Option",
                                "Well, Aris' crystal ball seems to think otherwise.",
                                "He's back and unfortunately I've got to deal with him.",
                            ) ==
                            1
                        ) {
                            me("Well Aris' crystal ball seems to think otherwise.")
                            npc(prysin, "Well if the ball says so, I'd better help you.")
                        } else {
                            me("He's back and unfortunately I've got to deal with him.")
                            npc(prysin, "You don't look up to much. I suppose Silverlight may be good enough to carry you through though.")
                        }
                        npc(prysin, "The problem is getting Silverlight.")
                        me("You mean you don't have it?")
                        npc(
                            prysin,
                            "Oh I do have it, but it is so powerful that the king made me put it in a special box which needs three " +
                                "different keys to open it. That way it won't fall into the wrong hands.",
                        )
                        me("So give me the keys!")
                        npc(
                            prysin,
                            "Um, well, it's not so easy.",
                            "I kept one of the keys. I gave the other two to other people for safe keeping.",
                            "One I gave to Rovin, the captain of the palace guard.",
                            "I gave the other to the wizard Traiborn.",
                        )
                        p.setFlag(FLAG_PRYSIN, true)
                        keyQuestions(prysin)
                    }
                }
            }
        }
    }

    private fun keyLine(keys: List<Int>): String =
        when {
            keys.size == 2 -> "I've got ${keys.joinToString(" and ") { KEY_NAMES.getValue(it) }}."
            else -> "I've got ${KEY_NAMES.getValue(keys.first())}."
        }

    /** The key questions Sir Prysin answers (Wiki "Sir Prysin"). */
    private suspend fun PlayerScript.keyQuestions(prysin: NpcRef) {
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "Can you give me your key?",
                    "Where can I find Captain Rovin?",
                    "Where does the wizard live?",
                    "Well I'd better go key hunting.",
                )
            ) {
                1 -> {
                    me("Can you give me your key?")
                    npc(
                        prysin,
                        "Um.... ah....",
                        "Well there's a problem there as well.",
                        "I managed to drop the key in the drain just outside the palace kitchen. It is just inside and I can't reach it.",
                    )
                    if (choice("Select an Option", "So what does the drain lead to?", "Well I'd better go key hunting.") == 1) {
                        me("So what does the drain connect to?")
                        npc(prysin, "It is the drain for the drainpipe running from the sink in the kitchen down to the palace sewers.")
                    } else {
                        me("Well I'd better go key hunting.")
                        npc(prysin, "Ok, goodbye.")
                        return
                    }
                }

                2 -> {
                    me("Where can I find Captain Rovin?")
                    npc(prysin, "Captain Rovin lives at the top of the guards' quarters in the north-west wing of this palace.")
                }

                3 -> {
                    me("Where does the wizard live?")
                    npc(
                        prysin,
                        "Wizard Traiborn?",
                        "He is one of the wizards who lives in the tower on the little island just off the south coast. I believe " +
                            "his quarters are on the first floor of the tower.",
                    )
                }

                else -> {
                    me("Well I'd better go key hunting.")
                    npc(prysin, "Ok, goodbye.")
                    return
                }
            }
        }
    }

    /** After the quest: Silverlight can be bought back (Wiki "Transcript:Sir Prysin"; the quest page says 500 coins, OPEN-QUA21). */
    private suspend fun PlayerScript.reclaim(prysin: NpcRef) {
        me("Yes, although I'm afraid I've lost Silverlight.")
        npc(
            prysin,
            "Yes, news of your carelessness is almost as widespread as knowledge of your victory. Fortunately for you, Silverlight has " +
                "come back into my possession.",
        )
        if (choice("Select an Option", "Phew, that's a relief.", "Is there any chance of me borrowing it again?") != 2) {
            me("Phew, that's a relief.")
            return
        }
        me("Is there any chance of me borrowing it again?")
        npc(
            prysin,
            "I'm not going to give it away that easily again, it's far too important to be treated so disrespectfully. Especially " +
                "now its powers have increased after your fight with Agrith-Naar!",
            "If you wish to make use of Silverlight again, it will cost you 1000 gold pieces. Maybe that will encourage you to look " +
                "after it.",
        )
        if (choice("Select an Option", "No way, it's not worth that much.", "Ok, I'll pay.") != 2) {
            me("No way, it's not worth that much.")
            return
        }
        me("Ok, I'll pay.")
        val p = player
        if (!p.has(Objs.COINS, SILVERLIGHT_PRICE)) {
            me("But I don't have that much money.")
            return
        }
        if (p.inv(Invs.INV).freeSlots == 0) {
            npc(prysin, "You don't even have enough free space to carry it. Stop wasing my time!")
            return
        }
        val ok =
            p.transaction("demon slayer silverlight") {
                remove(Invs.INV, Objs.COINS, SILVERLIGHT_PRICE) &&
                    add(Invs.INV, Objs.SILVERLIGHT, 1)
            }
        if (ok) npc(prysin, "May you make good use of it.")
    }

    // ---- Captain Rovin

    private suspend fun PlayerScript.rovinTalk(rovin: NpcRef) {
        val p = player
        npc(rovin, "What are you doing up here? Only the palace guards are allowed up here.")
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "I am one of the palace guards.",
                    "What about the King?",
                    "Yes I know, but this is important.",
                )
            ) {
                1 -> {
                    me("I am one of the palace guards.")
                    npc(rovin, "No, you're not! I know all the palace guards.")
                    if (choice("Select an Option", "I'm a new recruit.", "I've had extensive plastic surgery.") == 1) {
                        me("I'm a new recruit.")
                        npc(rovin, "I interview all the new recruits. I'd know if you were one of them.")
                        me("That blows that story out of the water then.")
                        npc(rovin, "Get out of my sight.")
                    } else {
                        me("I've had extensive plastic surgery.")
                        npc(
                            rovin,
                            "What sort of surgery is that? I've never heard of it. Besides, you look reasonably healthy.",
                            "Why is this relevant anyway? You still shouldn't be here.",
                        )
                    }
                    return
                }

                2 -> {
                    me("What about the King? Surely you'd let him up here.")
                    npc(
                        rovin,
                        "Well, yes, I suppose we'd let him up. He doesn't generally want to come up here, but if he did want to, he " +
                            "could.",
                        "Anyway, you're not the King either. So get out of my sight.",
                    )
                    return
                }

                else -> {
                    me("Yes, I know, but this is important.")
                    npc(rovin, "Ok, I'm listening. Tell me what's so important.")
                    if (choice("Select an Option", "There's a demon who wants to invade this city.", "Erm I forgot.") != 1) {
                        me("Erm I forgot.")
                        return
                    }
                    me("There's a demon who wants to invade the city.")
                    if (p.flag(FLAG_ROVIN) || p.has(Objs.SILVERLIGHT_KEY_2)) {
                        npc(rovin, "Yes, you said before, haven't you killed it yet?")
                        me("Well I'm going to use the powerful sword Silverlight, which I believe you have one of the keys for?")
                        npc(rovin, "I already gave you my key. Check your pockets.")
                        return
                    }
                    demonTalk(rovin)
                    return
                }
            }
        }
    }

    private suspend fun PlayerScript.demonTalk(rovin: NpcRef) {
        val p = player
        npc(rovin, "Is it a powerful demon?")
        if (choice("Select an Option", "Not really.", "Yes, very.") != 2) {
            me("Not really.")
            npc(rovin, "Well, I'm sure the palace guards can deal with it, then. Thanks for the information.")
            return
        }
        me("Yes, very.")
        npc(rovin, "As good as the palace guards are, I don't know if they're up to taking on a very powerful demon.")
        if (choice(
                "Select an Option",
                "Yeah, the palace guards are rubbish!",
                "It's not them who are going to fight the demon, it's me.",
            ) !=
            2
        ) {
            me("Yeah, the palace guards are rubbish!")
            npc(rovin, "Yeah, they're--", "Wait! How dare you insult the palace guards? Get out of my sight!")
            return
        }
        me("It's not them who are going to fight the demon, it's me.")
        npc(rovin, "What, all by yourself? How are you going to do that?")
        me("I'm going to use the powerful sword Silverlight, which I believe you have one of the keys for?")
        npc(rovin, "Yes, I do. But why should I give it to you?")
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "Fortune-teller Aris said I was destined to kill the demon.",
                    "Otherwise the demon will destroy the city!",
                    "Sir Prysin said you would give me the key.",
                )
            ) {
                1 -> {
                    me("Fortune-teller Aris said I was destined to kill the demon.")
                    npc(
                        rovin,
                        "A fortune-teller? Destiny? I don't believe in that stuff. I got where I am today by hard work, not by " +
                            "destiny! Why should I care what that mad old fortune-teller says?",
                    )
                }

                2 -> {
                    me("Otherwise the demon will destroy the city!")
                    npc(rovin, "You can't fool me! How do I know you haven't just made that story up to get my key?")
                }

                else -> {
                    me("Sir Prysin said you would give me the key.")
                    npc(
                        rovin,
                        "Oh, he did, did he? Well I don't report to Sir Prysin, I report directly to the king!",
                        "I didn't work my way up through the ranks of the palace guards so I could take orders from an ill-bred moron " +
                            "who only has his job because his great- grandfather was a hero with a silly name!",
                    )
                    if (choice(
                            "Select an Option",
                            "Why did he give you one of the keys then?",
                            "Aris said I was destined to kill the demon.",
                        ) !=
                        1
                    ) {
                        me("Fortune-teller Aris said I was destined to kill the demon.")
                        continue
                    }
                    me("Why did he give you one of the keys then?")
                    npc(
                        rovin,
                        "Only because the king ordered him to! The king couldn't get Sir Prysin to part with his precious ancestral " +
                            "sword, but he made him lock it up so he couldn't lose it.",
                        "I got one key and I think some wizard got another. Now what happened to the third one?",
                    )
                    me("Sir Prysin dropped it down a drain!")
                    npc(
                        rovin,
                        "Ha ha ha! The idiot!",
                        "Okay, I'll give you the key, just so that it's you that kills the demon and not Sir Prysin!",
                    )
                    if (p.giveOrDrop(Objs.SILVERLIGHT_KEY_2, 1, "demon slayer rovin key")) {
                        p.setFlag(FLAG_ROVIN, true)
                        box("Captain Rovin hands you a key.")
                    }
                    return
                }
            }
        }
    }

    // ---- Wizard Traiborn

    private suspend fun PlayerScript.traibornTalk(traiborn: NpcRef) {
        val p = player
        npc(traiborn, "Ello young thingummywut.")
        // OPEN-QUA22: only the Demon Slayer part of his conversation is transcribed; the rest ("Talk about something else") is not.
        if (p.stage != STARTED) return
        if (choice("Select an Option", "Talk about Demon Slayer.", "Talk about something else.") != 1) return
        when {
            p.has(Objs.SILVERLIGHT_KEY_1) -> {
                npc(traiborn, "Don't you have somewhere to be, young thingummywut? You still have that key you asked me for.")
                me("You're right. I've got a demon to slay.")
            }

            p.flag(FLAG_TRAIBORN) -> {
                bones(traiborn)
            }

            else -> {
                askKey(traiborn)
            }
        }
    }

    private suspend fun PlayerScript.askKey(traiborn: NpcRef) {
        me("I need to get a key given to you by Sir Prysin.")
        npc(traiborn, "Sir Prysin? Who's that? What would I want his key for?")
        if (choice("Select an Option", "He told me you were looking after it for him.", "He's one of the King's knights.") == 1) {
            me("He told me you were looking after it for him.")
            npc(
                traiborn,
                "That wasn't very clever of him. I'd lose my head if it wasn't screwed on. Go and tell him to find someone else to " +
                    "look after his valuables in future.",
            )
        } else {
            me("He's one of the King's knights.")
            npc(
                traiborn,
                "Say, I remember one of the King's knights. He had nice shoes...",
                "...and didn't like my homemade spinach rolls. Would you like a spinach roll?",
            )
            if (choice("Select an Option", "Yes please.", "Just tell me if you have the key.") == 1) {
                me("Yes please.")
                if (player.giveOrDrop(Objs.SPINACH_ROLL, 1, "demon slayer spinach roll")) {
                    box(
                        "Traiborn digs around in the pockets of his robes. After a few moments he triumphantly presents you with a " +
                            "spinach roll.",
                    )
                }
                me("Thank you very much.")
            } else {
                me("Just tell me if you have the key.")
                npc(
                    traiborn,
                    "The key? The key to what?",
                    "There's more than one key in the world don't you know? Would be a bit odd if there was only the one.",
                )
                if (choice("Select an Option", "It's the key to get a sword called Silverlight.", "You've lost it, haven't you?") == 1) {
                    me("It's the key to get a sword called Silverlight.")
                    npc(
                        traiborn,
                        "Silverlight? Never heard of that. Sounds a good name for a ship. Are you sure it's not the name of a ship " +
                            "rather than a sword?",
                    )
                    me("Yeah, pretty sure.")
                    npc(traiborn, "That's a pity, waste of a name.")
                } else {
                    me("You've lost it haven't you?")
                    npc(traiborn, "Me? Lose things? That's a nasty accusation.")
                }
            }
        }
        me("Well, have you got any keys knocking around?")
        npc(
            traiborn,
            "Now you come to mention it, yes I do have a key. It's in my special closet of valuable stuff. Now how do I get into that?",
            "I sealed it using one of my magic rituals. So it would make sense that another ritual would open it again.",
        )
        me("So do you know what ritual to use?")
        npc(
            traiborn,
            "Let me think a second.",
            "Yes a simple drazier style ritual should suffice. Hmm, main problem with that is I'll need 25 sets of bones. Now where " +
                "am I going to get hold of something like that?",
        )
        if (choice("Select an Option", "Hmm, that's too bad. I really need that key.", "I'll get the bones for you.") == 1) {
            me("Hmm, that's too bad. I really need that key.")
            npc(traiborn, "Ah well, sorry I couldn't be any more help.")
            return
        }
        me("I'll help get the bones for you.")
        npc(traiborn, "Ooh that would be very good of you.")
        me("Okay, I'll speak to you when I've got some bones.")
        player.setFlag(FLAG_TRAIBORN, true)
    }

    /** Bones are handed in by the set; the 25th set gives the key. */
    private suspend fun PlayerScript.bones(traiborn: NpcRef) {
        val p = player
        npc(traiborn, "How are you doing finding bones?")
        val have = p.inv(Invs.INV).total(Objs.BONES).toInt()
        val owed = BONES - p.bones()
        if (have == 0) {
            me("I haven't got any at the moment.")
            npc(traiborn, "Nevermind, keep working on it.")
            return
        }
        me("I have some bones.")
        npc(traiborn, "Give 'em here then.")
        val give = minOf(have, owed)
        if (!p.take(Objs.BONES, give, "demon slayer bones")) return
        p.setSaved(KEY_BONES, (p.bones() + give).toLong())
        if (p.bones() < BONES) {
            box("You give Traiborn ${if (give == 1) "1 set" else "$give sets"} of bones.")
            me("That's all of them.")
            npc(traiborn, "I still need ${BONES - p.bones()} more.")
            me("Ok, I'll keep looking.")
            return
        }
        box("You give Traiborn ${if (give == 1) "1 set" else "$give sets"} of bones.")
        npc(
            traiborn,
            "Hurrah! That's all 25 sets of bones.",
            "Wings of dark and colour too, Spreading in the morning dew; Locked away I have a key; Return it now, please, unto me.",
        )
        if (p.giveOrDrop(Objs.SILVERLIGHT_KEY_1, 1, "demon slayer traiborn key")) {
            p.setSaved(KEY_BONES, null)
            p.setFlag(FLAG_TRAIBORN, false)
            box("Traiborn hands you a key.")
            me("Thank you very much.")
            npc(traiborn, "Not a problem for a friend of Sir What's-his-face.")
        }
    }

    // ---- the drain

    /** A bucket of water on the palace drain washes Sir Prysin's key into the sewer (Wiki "Sir Prysin's key"). */
    private suspend fun PlayerScript.washKey() {
        val p = player
        if (p.stage != STARTED || !p.flag(FLAG_PRYSIN) || p.flag(FLAG_DRAIN)) {
            p.message("You pour the liquid down the drain.")
            return
        }
        if (!p.transaction(
                "demon slayer drain water",
            ) { remove(Invs.INV, Objs.BUCKET_WATER, 1) && add(Invs.INV, Objs.BUCKET_EMPTY, 1) }
        ) {
            return
        }
        p.message("You hear a clink.")
        p.setFlag(FLAG_DRAIN, true)
        me("OK, I think I've washed the key down into the sewer. I'd better go down and get it!")
    }

    /**
     * "Search" on the drain. Before the water: the key is out of reach. The real key lies in the sewers by a muddy patch whose loc
     * the cache does not name; the key is handed over here once it was washed down (OPEN-QUA20).
     */
    private suspend fun PlayerScript.searchDrain() {
        val p = player
        if (p.stage != STARTED || !p.flag(FLAG_PRYSIN)) {
            p.message("Nothing interesting seems to have been dropped down here today.")
            return
        }
        if (!p.flag(FLAG_DRAIN)) {
            me("That must be the key Sir Prysin dropped.")
            me("I don't seem to be able to reach it. I wonder if I can dislodge it somehow. That way it may go down into the sewers.")
            return
        }
        if (p.has(Objs.SILVERLIGHT_KEY_3)) {
            p.message("Nothing interesting seems to have been dropped down here today.")
            return
        }
        if (p.giveOrDrop(Objs.SILVERLIGHT_KEY_3, 1, "demon slayer prysin key")) box("You pick up an old rusty key.")
    }

    // ---- the stone circle

    /** Polled while logged in: entering the stone circle with Silverlight starts the summoning (Wiki "The final battle"). */
    private fun PlayerRef.circle() {
        if (stage != SILVERLIGHT || coord.level != 0) return
        if (coord.x !in CIRCLE_X || coord.z !in CIRCLE_Z) return
        if (inv(Invs.WORN).total(Objs.SILVERLIGHT) == 0L && !has(Objs.SILVERLIGHT)) return
        if (npcsWithin(CIRCLE_CENTRE, CIRCLE_SEARCH).any { it.type == Npcs.DELRITH || it.type == Npcs.DELRITH_WEAKENED }) return
        startScript { summon() }
    }

    private suspend fun PlayerScript.summon() {
        val p = player
        npcAs(Npcs.QIP_DS_DARK_WIZARD_DENATH, "Denath", "Arise, O mighty Delrith! Bring destruction to this soft, weak city!")
        npcAs(Npcs.QIP_DS_DARK_WIZARD_DENATH, "Dark wizards", "Arise, Delrith!")
        box("The wizards cast an evil spell...")
        npcAs(
            Npcs.QIP_DS_DARK_WIZARD_DENATH,
            "Denath",
            "Ha ha ha! At last you are free, my demonic brother! Rest now, and then have your revenge on this pitiful city!",
        )
        npcAs(Npcs.QIP_DS_DARK_WIZARD_DENATH, "Dark wizard", "Who's that?")
        npcAs(Npcs.QIP_DS_DARK_WIZARD_DENATH, "Denath", "Noo! Not Silverlight! Delrith is not ready yet!", "I've got to get out of here...")
        val delrith = p.spawnNpc(Npcs.DELRITH, CIRCLE_CENTRE, 0)
        delrith.huntRange = CIRCLE_SEARCH
    }

    // ---- Delrith's end

    private suspend fun PlayerScript.banish(delrith: NpcRef) {
        val p = player
        if (p.stage != SILVERLIGHT) {
            p.message("Nothing interesting happens.")
            return
        }
        box("Delrith is weakened and no longer attacking.")
        me("Now what was that incantation again?")
        val remaining = WORDS.toMutableList()
        val said = ArrayList<String>()
        repeat(WORDS.size) {
            val pick = choice("Select an Option", *remaining.toTypedArray())
            if (pick !in 1..remaining.size) return
            said += remaining.removeAt(pick - 1)
            me(said.last() + if (said.size == WORDS.size) "!" else "...")
        }
        if (said != p.words()) {
            box("The vortex collapses. That was the wrong incantation.")
            val at = delrith.coord
            delrith.despawn()
            p.spawnNpc(Npcs.DELRITH, at, 0).huntRange = CIRCLE_SEARCH
            return
        }
        box("Delrith is sucked into the vortex...")
        box("...back into the dark dimension from which he came.")
        delrith.despawn()
        p.message("You scurry away from the enraged dark wizards having defeated the demon.")
        QuestCompletion.complete(p, Quests.DEMON_SLAYER, REWARD)
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_SEARCH = 1

        /** Cache npc 5080 (Weakened Delrith) ops: op 1 "Banish". */
        const val OP_BANISH = 1

        /** Quest stages (ours, OPEN-QU1): 1 started, 2 Silverlight given; 3 is the cache's `endstate`. */
        const val STARTED = 1
        const val SILVERLIGHT = 2
        const val FINISHED = 3

        const val FLAG_PRYSIN = "quests.ds.prysin"
        const val FLAG_ROVIN = "quests.ds.rovin"
        const val FLAG_TRAIBORN = "quests.ds.traiborn"
        const val FLAG_DRAIN = "quests.ds.drain"
        const val KEY_BONES = "quests.ds.bones"
        const val KEY_WORDS = "quests.ds.words"

        /** Wiki "Demon Slayer": Wizard Traiborn wants 25 sets of bones. */
        const val BONES = 25

        /** Wiki "Transcript:Sir Prysin": buying Silverlight back (the quest page says 500 coins, the dialogue 1,000; OPEN-QUA21). */
        const val SILVERLIGHT_PRICE = 500

        /** Wiki "Demon Slayer": the five words of the incantation. */
        val WORDS = listOf("Aber", "Gabindo", "Purchai", "Camerinthum", "Carlem")

        val KEYS = intArrayOf(Objs.SILVERLIGHT_KEY_1, Objs.SILVERLIGHT_KEY_2, Objs.SILVERLIGHT_KEY_3)
        val KEY_NAMES =
            mapOf(
                Objs.SILVERLIGHT_KEY_1 to "the key from Wizard Traiborn",
                Objs.SILVERLIGHT_KEY_2 to "the key from Captain Rovin",
                Objs.SILVERLIGHT_KEY_3 to "the key which you dropped down the drain",
            )

        /** Wiki "Drain (Demon Slayer)": locs 17423 and 17424, option "Search". */
        val DRAINS = intArrayOf(17423, 17424)

        /** Weakened Delrith stays this many ticks (ours, like the other quest NPCs). */
        const val WEAK_LIFETIME = 500

        /** The stone circle: the centre of the dark wizards' spawns in `npc-spawns.tsv` (3223..3232, 3363..3374); OPEN-QUA19. */
        val CIRCLE_CENTRE = CoordGrid(3227, 3368, 0)
        val CIRCLE_X = 3225..3229
        val CIRCLE_Z = 3366..3370
        const val CIRCLE_SEARCH = 8
        const val CIRCLE_CHECK = 3
        val CIRCLE_TIMER = TimerId("demon_slayer_circle")

        /** Wiki "Demon Slayer" rewards: 3 quest points; Silverlight (given during the quest). */
        val REWARD = QuestReward(model = Objs.SILVERLIGHT, notes = listOf("Silverlight"))
    }
}
