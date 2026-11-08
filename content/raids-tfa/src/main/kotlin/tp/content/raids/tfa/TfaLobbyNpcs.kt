package tp.content.raids.tfa

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.OpenBankRequest
import tp.plugin.OpenCollectionBox
import tp.plugin.PlayerScript
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.util.CoordGrid

/**
 * The lobby's people and furniture beyond the raid flow (tfa/lobby-nexus-vault.md "Lobby NPCs"):
 *
 * - The lobby Banker 16704 (Talk-to, Bank, Collect). "Bank" and "Collect" are the generic bank and Grand Exchange option
 *   handlers; "Talk-to" is the Wiki transcript with the bank answers wired to the bank plugin's events.
 * - The Record Keeper 16692: the Wiki transcript, and "View Stats?" opens the Alliance records (TfaStatsUi).
 * - The Wizard apprentice 16693 near the ascension rocks (Talk-to is the generic transcript player of content/world).
 * - The lobby bank chest 63275's "Collect" (its "Use" is the generic bank chest handler; 63274 has no ops).
 *
 * The Banker 16704, General Montoya 16705 and the Record Keeper 16692 are already in content/world's `npc-spawns.tsv`
 * (Wiki map pins); General Montoya's Talk-to is the generic transcript player. The Alliance druids, builders, Temple Knights
 * and Guardians of Armadyl (NPCs 16694 / 16695 / 16706-16723) have no ops in the cache and no Wiki page with placements
 * (OPEN-TFA29), so they are not spawned.
 */
public class TfaLobbyNpcs : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart { spawnNpc(Npcs.TFA_WIZARD_APPRENTICE, WIZARD_APPRENTICE_AT, 0) }
        onOpNpc(Npcs.TFA_LOBBY_BANKER, OP1) { npc -> banker(npc) }
        onOpNpc(Npcs.TFA_RECORD_KEEPER, OP1) { npc -> recordKeeper(npc) }
        onOpLoc(Locs.TFA_LOBBY_BANKCHEST_OPEN, OP3) { player.events.publish(OpenCollectionBox(player)) }
    }

    private companion object {
        /**
         * Wiki "Wizard apprentice" map pin (4114, 5539) is a loc tile (collision), so the free tile east of it, in front of the
         * ascension rocks (4110-4117, 5532-5539).
         */
        val WIZARD_APPRENTICE_AT = CoordGrid(4115, 5539)
    }
}

private const val OP1 = 1
private const val OP3 = 3

/** Saved key: the player has had the Record Keeper's introduction (OPEN-TFA27: which branch plays when is not sourced). */
private const val RECORD_KEEPER_MET = "tfa.recordKeeper.met"

private const val ELLIPSIS = "..."

/**
 * Wiki transcript "Banker (The Fractured Archive)". OPEN-B1: "I'd like to buy more bank slots." and OPEN-TFA30: "I'd like to
 * check my PIN settings." are left out, as the plugin-api has no event for the bank slots or the PIN settings panel.
 */
private suspend fun PlayerScript.banker(npc: NpcRef) {
    chatNpc(npc, "Oh, hello. How can I help?")
    when (
        choice(
            "Select an option",
            "I'd like to access my bank account, please.",
            "I'd like to collect items.",
            "What's your function here?",
        )
    ) {
        1 -> player.events.publish(OpenBankRequest(player))
        2 -> player.events.publish(OpenCollectionBox(player))
        3 -> bankerFunction(npc)
    }
}

private suspend fun PlayerScript.bankerFunction(npc: NpcRef) {
    chatPlayer("What's your function here?")
    chatNpc(
        npc,
        "I'm the Alliance's travelling liaison with the Bank of Gielinor. I make sure everybody has everything they need, " +
            "wherever we go.",
    )
    chatPlayer("Must be nice getting to travel around for work.")
    chatNpc(
        npc,
        "I'd enjoy it a lot more if our destinations weren't generally so dangerous, but yes, I like seeing the world.",
    )
    chatPlayer("What's the book for then?")
    chatNpc(
        npc,
        "Once we're settled in, my job involves a lot of standing around. There's only so much you can appreciate a cavern, " +
            "even one as impressive as this.",
    )
    chatPlayer("That makes sense. And what's the book about?")
    chatNpc(npc, "It's a collection of poems believed to be from the Second Age.")
    chatPlayer("Interesting. Could you read me one?")
    chatNpc(npc, "Of course.")
    chatNpc(npc, ELLIPSIS)
    chatNpc(npc, "'Flowers wilt and grow. Pruned carefully as they go. Were it that this flower, could live a life its own.'")
    chatPlayer(ELLIPSIS)
    chatPlayer("I'm not sure I understand.")
    chatNpc(npc, "There's no real need to. It's just a poem.")
    chatPlayer("So it has no meaning?")
    chatNpc(npc, "It means something to me.")
    chatPlayer("Okay, what does it mean to you?")
    chatNpc(
        npc,
        "Hmm... I suppose it's a reflection on the way our surroundings shape us. Or maybe it's arguing that wilder gardens " +
            "are more beautiful.",
    )
    chatPlayer("Maybe... I think I'll leave you to your reading now.")
    chatNpc(npc, "Of course. Let me know if you need anything else.")
}

/** Wiki transcript "Record Keeper": the introduction the first time, the option menu afterwards (OPEN-TFA27). */
private suspend fun PlayerScript.recordKeeper(npc: NpcRef) {
    if (player.saved(RECORD_KEEPER_MET) == null) {
        player.setSaved(RECORD_KEEPER_MET, 1L)
        chatPlayer("Hello.")
        chatNpc(npc, ELLIPSIS)
        chatPlayer("Hello?")
        chatNpc(npc, "Hmm? What what?")
        chatPlayer("I hope I'm not interrupting.")
        chatNpc(npc, "Interrupting who?")
        chatPlayer("You?")
        chatNpc(npc, "What about me?")
        chatPlayer("Am I interrupting you?")
        chatNpc(npc, "I'm not sure... What is it you're interrupting me for?")
        chatPlayer("Well, I was just going to ask what you're doing, but I'm starting to regret it.")
        chatNpc(npc, "Why didn't you say so, my boy!")
        keeperIntroduction(npc)
        viewStats()
        return
    }
    when (choice("Select an option", "What are you doing?", "How are things?", "Goodbye.")) {
        1 -> {
            chatPlayer("What are you doing?")
            keeperIntroduction(npc)
            viewStats()
        }

        2 -> {
            chatPlayer("How are things?")
            chatNpc(
                npc,
                "The Alliance is struggling to hold off the threats to the Archive. And the records are eager to chronicle your " +
                    "deeds in this matter, if you are so inclined.",
            )
            chatNpc(npc, "You may view some of the records, should your curiosity need satisfying.")
            viewStats()
        }

        3 -> {
            chatPlayer("Goodbye.")
            chatNpc(npc, "Fare-thee-well, Aspirant, we watch you with great interest.")
        }
    }
}

private suspend fun PlayerScript.keeperIntroduction(npc: NpcRef) {
    chatNpc(npc, "I... am... the Keeper of the Records!")
    chatPlayer(ELLIPSIS)
    chatNpc(npc, ELLIPSIS)
    chatPlayer("You're a record keeper?")
    chatNpc(npc, "THE Record Keeper!")
    chatNpc(npc, "These sensitive records are under my precise curation!")
    chatNpc(
        npc,
        "The secrets of the Archive, the progress of the Alliance, the very steps you took towards me today, all recorded in " +
            "these... very... pages!",
    )
    chatPlayer("Can I have a loo-")
    chatNpc(npc, "No!")
    chatPlayer("Sorry, I was just interested.")
    chatNpc(npc, "Hmm... I suppose you may view some of the records, should your curiosity need satisfying.")
}

private suspend fun PlayerScript.viewStats() {
    if (choice("View Stats?", "Yes.", "No.") == 1) TfaStatsUi.open(player)
}
