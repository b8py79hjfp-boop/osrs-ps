package tp.content.raids.tfa

import tp.content.raids.RaidEvent
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.chatNpcAs
import tp.plugin.chatPlayer
import tp.plugin.mesbox
import tp.symbols.Invs
import tp.symbols.Jingles
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.util.CoordGrid

/**
 * The miniquest "The Fractured Archive" (Wiki "The Fractured Archive (miniquest)" and its transcript; tfa.md TFA1; client
 * contract 1.2). State is varbit `TFA_MINIQUEST` (16426): the cache quest row 17027 reads it with 0 not started and 8 finished.
 *
 * - [STARTED] (1): the first time the player goes through the lobby opening, Ivy Sophista, Armacus Selles and Eva Cashien
 *   brief them ("Entering the archive" scene).
 * - [RAID_DONE] (6): the player's first completed run. The multi NPCs 16701-16703 show the three leaders in the lobby at
 *   exactly this value (cache `multiNpcs` has only index 6 set).
 * - [FINISHED] (8): the "Finishing up" scene with any of the three. Reward: none beyond the story ("Access to further runs").
 *
 * OPEN-TFA20: the in-between values 2-5 and 7 are unused; the Wiki lists the in-raid conversations (navigator, Armacus, Eva,
 * Ivy, Ocellus) but not which value each one sets, and the quest journal text is a client script.
 */
public class TfaMiniquestPlugin : ContentPlugin {
    override fun ContentScope.register() {
        var world: WorldRef? = null
        onWorldStart {
            world = this
            spawnAll(this)
        }
        onOpNpc(Npcs.TFA_MESSENGER, OP1) { npc -> messenger(npc) }
        // Talk-to on the leaders' lobby NPCs (the multi 16701-16703 resolves to these while the varbit is 6).
        for (leader in intArrayOf(Npcs.ARMACUS_SELLES, Npcs.EVA_CASHIEN, Npcs.TFA_IVY_SOPHISTA)) {
            onOpNpc(leader, OP1) { finishingUp() }
        }
        // OPEN-TFA23: the note's text is not captured.
        onItemOp(Objs.TFA_START_MESSAGE, "Read") { startScript { mesbox(MESSAGE_TEXT) } }
        events.on<RaidEvent.Completed> { e ->
            val w = world
            if (w != null && e.run.raid.id == TfaRaid.ID) {
                for (m in e.run.members.values) {
                    if (m.inside) w.onlinePlayer(m.name)?.let(TfaMiniquest::raidDone)
                }
            }
        }
    }

    private fun spawnAll(world: WorldRef) {
        world.spawnNpc(Npcs.TFA_MESSENGER, TfaMiniquest.MESSENGER_AT, 0)
        for ((type, at) in TfaMiniquest.ENDING_NPCS) world.spawnNpc(type, at, 0)
    }
}

private const val OP1 = 1

/** Miniquest state helpers (all through the one varbit). */
internal object TfaMiniquest {
    const val NOT_STARTED: Int = 0
    const val STARTED: Int = 1

    /** Client-contract 1.2: value 6 of `TFA_MINIQUEST` makes the NPC multis 16701-16703 show Armacus / Ivy / Eva. */
    const val RAID_DONE: Int = 6

    /** Quest table row 17027 (`quest-states.tsv`): the finished value of the quest list. */
    const val FINISHED: Int = 8

    /** Saved key: the player took the Alliance's message from the messenger (the entry scene has a line for not having it). */
    const val MESSAGE_KEY: String = "tfa.miniquest.message"

    /**
     * OPEN-TFA1: the Wiki pins the start "near the entrance of the Ancient Guthixian Temple" (map 4052, 4562); the 20 Oct
     * update has a messenger greet players on login; the main page says the miniquest starts on the first attempt to enter
     * the lobby. All three are kept: the messenger stands at the pinned tile and hands out the note (optional), the
     * miniquest starts at the lobby opening.
     */
    val MESSENGER_AT = CoordGrid(4052, 4562)

    /**
     * Where the three leaders stand in the lobby while the state is [RAID_DONE]: a row of free tiles (collision) south of the
     * Archive Door's exit tile (4122, 5559), in sight of players who come out of the raid.
     */
    val ENDING_NPCS =
        listOf(
            Npcs.TFA_IVY_SOPHISTA_ENDING to CoordGrid(4122, 5557),
            Npcs.TFA_ARMACUS_SELLES_ENDING to CoordGrid(4124, 5557),
            Npcs.TFA_EVA_CASHIEN_ENDING to CoordGrid(4126, 5557),
        )

    fun stage(p: PlayerRef): Int = p.varbit(Varbits.TFA_MINIQUEST)

    /** Starts the miniquest; false when it was started already or the entry requirement (TFA1) is not met. */
    fun begin(p: PlayerRef): Boolean {
        if (stage(p) != NOT_STARTED || TfaConfig.requirement(p) != null) return false
        p.setVarbit(Varbits.TFA_MINIQUEST, STARTED)
        return true
    }

    /** The first completed run (or any run before the miniquest was finished): the leaders wait in the lobby. */
    fun raidDone(p: PlayerRef) {
        if (stage(p) < RAID_DONE) p.setVarbit(Varbits.TFA_MINIQUEST, RAID_DONE)
    }

    /** The miniquest is over: the varbit takes the quest list's finished value. */
    fun finish(p: PlayerRef) {
        p.setVarbit(Varbits.TFA_MINIQUEST, FINISHED)
        p.message(COMPLETED_TEXT)
        p.jingle(Jingles.QUEST_COMPLETE_1)
    }

    /**
     * OPEN-TFA25: the quest framework (content/quests) is internal to its module, so the completion line is the one it uses
     * for quests; the real miniquest line and the reward scroll are not captured.
     */
    private const val COMPLETED_TEXT = "Congratulations, you've completed a quest: <col=0000ff>The Fractured Archive</col>"
}

// OPEN-TFA23: the note's text is a placeholder built from the entry scene (no source for the real one).
private const val MESSAGE_TEXT =
    "Ivy Sophista of Crux Eqal asks for your help: the Fractured Archive, below the Ancient Guthixian Temple, is under threat."

// OPEN-TFA28: the messenger's lines for a player who already has the note are not captured.
private const val HAS_MESSAGE_TEXT = "You already have the Alliance's message."
private const val BACKPACK_FULL_TEXT = "You don't have enough inventory space."

/** Ticks to wait after the lobby teleport before the scene opens (the arrival is processed first). */
private const val ARRIVAL_TICKS = 2

/**
 * The lobby opening (from the Guthixian temple cave): moves the player in and, the first time, plays the entry scene
 * (TFA1 / OPEN-TFA1: the miniquest starts here).
 */
internal suspend fun PlayerScript.enterLobby() {
    player.teleport(TfaRaid.LOBBY_FROM_OUTSIDE)
    if (!TfaMiniquest.begin(player)) return
    delay(ARRIVAL_TICKS)
    val received = player.saved(TfaMiniquest.MESSAGE_KEY) != null
    play(if (received) ENTRY_COMMON + ENTRY_SITUATION else ENTRY_COMMON + ENTRY_NO_MESSAGE + ENTRY_SITUATION + ENTRY_REST)
}

private suspend fun PlayerScript.messenger(npc: NpcRef) {
    TfaConfig.requirement(player)?.let {
        player.message(it)
        return
    }
    val held = player.inv(Invs.INV).total(Objs.TFA_START_MESSAGE) + player.inv(Invs.BANK).total(Objs.TFA_START_MESSAGE)
    if (held > 0) {
        player.message(HAS_MESSAGE_TEXT)
        return
    }
    chatNpc(npc, "Hello there. I have a message for you.")
    if (player.transaction("tfa alliance message") { add(Invs.INV, Objs.TFA_START_MESSAGE, 1) }) {
        player.setSaved(TfaMiniquest.MESSAGE_KEY, 1L)
        mesbox("You've been given a message.")
    } else {
        mesbox(BACKPACK_FULL_TEXT)
    }
}

/** "Finishing up": only while the leaders stand in the lobby ([TfaMiniquest.RAID_DONE]). */
private suspend fun PlayerScript.finishingUp() {
    if (TfaMiniquest.stage(player) != TfaMiniquest.RAID_DONE) return
    play(FINISHING_UP)
    mesbox("The others depart.")
    TfaMiniquest.finish(player)
}

// ---- dialogue (Wiki "Transcript:The Fractured Archive (miniquest)", sections "Entering the archive" and "Finishing up")

private enum class Who(val type: Int, val title: String) {
    PLAYER(-1, ""),
    IVY(Npcs.TFA_IVY_SOPHISTA, "Ivy Sophista"),
    ARMACUS(Npcs.ARMACUS_SELLES, "Armacus Selles"),
    EVA(Npcs.EVA_CASHIEN, "Eva Cashien"),
}

private class Line(val who: Who, val text: String)

private fun p(text: String) = Line(Who.PLAYER, text)

private fun ivy(text: String) = Line(Who.IVY, text)

private fun armacus(text: String) = Line(Who.ARMACUS, text)

private fun eva(text: String) = Line(Who.EVA, text)

/** The scene's `[player name]` token. */
private const val NAME = "[player name]"

private suspend fun PlayerScript.play(lines: List<Line>) {
    for (line in lines) {
        val text = line.text.replace(NAME, player.username)
        if (line.who == Who.PLAYER) chatPlayer(text) else chatNpcAs(line.who.type, line.who.title, text)
    }
}

private val ENTRY_COMMON =
    listOf(
        ivy("Hello agai-"),
        armacus("Greetings, adventurer! Have you come to join the ranks of the Alliance?"),
        ivy("Armacus, this is $NAME, the adventurer I told you about."),
        armacus("Aha! Well met, young $NAME."),
        eva("I have heard good things from my uncle. It is good to meet you, $NAME."),
        p("Well met, and likewise. I received your message and came as soon as I could."),
        ivy(
            "Thank you. This is Eva Cashien and Armacus Selles. They are leading the Temple Knights and the Guardians of " +
                "Armadyl who have mobilised to defend the Archive.",
        ),
    )

private val ENTRY_NO_MESSAGE =
    listOf(
        p("The Archive?"),
        ivy("You didn't receive my message? Concerning..."),
        ivy(
            "It seems the Stone of Jas wasn't the only thing Guthix hid down here. This Archive was built to house various " +
                "other powerful artefacts, to stop them falling into the wrong hands.",
        ),
        ivy(
            "However, thanks to Lucien, it is now at risk. His followers are preparing to attack the Archive. Not only that, " +
                "but it seems various other groups are also mobilising their forces.",
        ),
    )

private val ENTRY_SITUATION =
    listOf(p("What's the current situation? Have your soldiers entered the Archive already?"))

private val ENTRY_REST =
    listOf(
        eva("No, they are still on their way. We came ahead of time to scout out the situation."),
        p("And what do you make of it?"),
        armacus(
            "It's quite the pickle we're in, I'm afraid! Our enemies approach from all sides. We have " +
                "identified six vulnerable chambers altogether.",
        ),
        eva(
            "What's more, the central chamber we intended to use to establish fortifications appears to be " +
                "guarded by some sort of hostile creature.",
        ),
        ivy("It is likely one of Guthix's creations, left behind to defend the Archive from intruders."),
        p("Could we leverage it to help us?"),
        ivy(
            "I'm afraid not... It might hold off some of the intruders, but it won't manage all of them. And without getting in " +
                "there to establish defences of our own, we won't stand a much better of a chance.",
        ),
        p("I see... That's unfortunate."),
        armacus(
            "Indeed! But not all is bad news. Several of our enemies have clashed on their way to the " +
                "Archive, and it's bought us some time.",
        ),
        armacus(
            "If a brave young adventurer were to appear and clear the way for us to set up our defences, " +
                "whilst we gathered our troops...",
        ),
        armacus("But wait! We have one right here!"),
        eva("Armacus' jesting aside, we're hoping you might be able to help us establish a foothold."),
        p("I see. And do we know anything specific about what artefacts are hidden here?"),
        ivy(
            "It seems to be all manner of things. Most of them likely aren't too valuable to our foes, but " +
                "the Archive has an inner vault.",
        ),
        ivy(
            "We're not sure exactly what's inside, but we can't take any risks. We need to assume that the contents could have " +
                "the potential to be on par with the Stone of Jas itself.",
        ),
        p("And our foes? What do we know about them?"),
        eva(
            "Lucien's forces are being led by someone called Moia. Our records have little information on her, but if Lucien has " +
                "trusted her with this task, we must assume she is very dangerous.",
        ),
        armacus(
            "However, luckily for us, there does seem to have been some splintering within Lucien's ranks. The Dagon'hai no " +
                "longer appear to be working with him. Instead, they seem intent on raiding the Archive for themselves.",
        ),
        ivy(
            "There is also a goblin warband that has somehow found out about this place. I fear we may have " +
                "other foes as well that have not yet revealed themselves.",
        ),
        p("Sounds like we'd best not waste any time then."),
        eva("Thank you, $NAME. Once you've cleared the central chamber, we'll split up and tackle our enemies one at a time."),
        ivy(
            "We'll be leaning heavily on you once again, $NAME. Our forces will do what they can to lift your burden, but our " +
                "enemies are powerful, and you have more experience than most.",
        ),
        p("Thank you, I'll-"),
        armacus(
            "Indeed! They are powerful. But! You need not face them alone. I have no doubt that an enterprising young adventurer " +
                "such as yourself has many capable and jolly allies.",
        ),
        armacus("Now is the time to unite, and leverage the strength of our co-operation against the weakness of our foes' greed."),
        p("I'll see what I can do."),
        eva("We must go and ready our forces. We will see you inside."),
        ivy("Good luck, $NAME."),
    )

private val FINISHING_UP =
    listOf(
        ivy("$NAME! What happened?"),
        p("It's hard to put into words... There was someone in the inner vault... but he was Guthixian!"),
        armacus("Ah, a new ally for our cause!"),
        p("I wish that were so..."),
        ivy("They opposed you? Who were they?"),
        p("He called himself Ocellus."),
        eva("Ocellus? I'm not familiar with this name."),
        ivy("Surely it can't be so..."),
        armacus("You know who this Ocellus is, Ivy?"),
        ivy(
            "I have heard only stories... and very few at that. It is said that when the God Wars ended, Guthix tasked a select " +
                "few with guarding items and areas of great power.",
        ),
        ivy(
            "There is Juna of course, but also Valluta... Aeternam... These are just some of the names " +
                "passed down through our order. Ocellus was another.",
        ),
        ivy("It is never said exactly what it was he was tasked with, but the stories call him the Guardian of Ascension."),
        eva("And he was guarding this vault? But then... what was in it?"),
        p("It was... a sword of some sorts. It had been shattered, but Ocellus was still able to use it. Its power was... extraordinary."),
        eva("Fascinating... and terrifying..."),
        armacus("If you know of Ocellus, Ivy, then perhaps you are familiar with this blade as well?"),
        ivy("I'm afraid not, but for Ocellus to be guarding it, I fear it is of a power far beyond our understanding."),
        p(
            "Ocellus said none could resist its power. In truth, I think that might have included him. He claimed none were " +
                "worthy of claiming it, and yet was happy to try and use it himself.",
        ),
        eva("Hmm... What was Ocellus' fate, $NAME?"),
        p("He's dead."),
        ivy(
            "Forgive me, $NAME, but I suspect that is unlikely. Ocellus will have been granted power by " +
                "Guthix himself. He will not die easily.",
        ),
        armacus("Then we must consider him another enemy."),
        eva("And our other foes will soon regroup. I fear our defence of the Archive has only just begun."),
        ivy(
            "I suspect you're right. Thank you, $NAME, for all of your help here, but it seems we must prepare to resume our " +
                "defence. Any help you can continue to provide would be greatly appreciated.",
        ),
        p("I'll do what I can."),
        ivy("That is all we can ever ask. Now, best we begin our preparations for the next wave of attacks."),
        armacus("Yes! Time to beat back our foes once more!"),
    )
