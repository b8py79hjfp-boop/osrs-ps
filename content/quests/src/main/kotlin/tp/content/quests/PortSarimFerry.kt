@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * The Port Sarim - Musa Point ferry of Pirate's Treasure and Dragon Slayer I (docs/mechanics/quest-pirates-treasure.md PT9):
 * Captain Tobias, Seaman Lorris and Seaman Thresnor take the player to Karamja for 30 coins; the customs officer on Musa Point
 * searches the player for rum before the return trip. The boat ride itself is not staged (OPEN-QUB1): the player is moved to
 * the other dock. Free with the Ring of Charos(a) (Wiki "Pirate's Treasure").
 */
public class PortSarimFerry : ContentPlugin {
    override fun ContentScope.register() {
        for (sailor in SAILORS) {
            onOpNpc(sailor, OP_TALK) { npc ->
                player.stopInteraction()
                sailorTalk(npc)
            }
            onOpNpc(sailor, OP_TRAVEL) { _ ->
                player.stopInteraction()
                board(MUSA_POINT, "Musa Point")
            }
        }
        onOpNpc(Npcs.CUSTOMS_OFFICER, OP_TALK) { officer ->
            player.stopInteraction()
            customsTalk(officer)
        }
        onOpNpc(Npcs.CUSTOMS_OFFICER, OP_TRAVEL) { officer ->
            player.stopInteraction()
            customsTravel(officer)
        }
    }

    private suspend fun PlayerScript.sailorTalk(sailor: NpcRef) {
        npc(sailor, "Hello there. Do you want to go on a trip to Karamja? We can take you to Musa Point for only 30 coins.")
        val crandor = QuestsB.DRAGON_SLAYER.stage(player) >= DS_SHIP
        val pick =
            if (crandor) {
                choice("Select an Option", "I'd rather go to Crandor.", "Yes please.", "No, thank you.")
            } else {
                choice("Select an Option", "Yes please.", "No, thank you.") + 1
            }
        when (pick) {
            1 -> {
                me("I'd rather go to Crandor.")
                npc(sailor, "Crandor? No chance! You may have a death wish, but we don't!")
            }

            2 -> {
                me("Yes please.")
                board(MUSA_POINT, "Musa Point")
            }

            else -> {
                me("No, thank you.")
            }
        }
    }

    /** Pays the fare (30 coins, free with the Ring of Charos(a)) and moves the player to [to]. */
    private suspend fun PlayerScript.board(to: CoordGrid, place: String) {
        val p = player
        if (!p.wears(Objs.RING_OF_CHAROS_UNLOCKED) && !p.take(Objs.COINS, FARE, "ferry fare")) {
            return me("Oh dear, I don't seem to have enough money.")
        }
        // OPEN-QUB1: the message and the ship scene are not published; the player is moved to the other dock.
        p.message("You board the ship.")
        p.teleport(to)
        p.message("The ship arrives at $place.")
    }

    /** Wiki "Pirate's Treasure", section "Talking to the Customs officer" (the charm options need the Ring of Charos(a)). */
    private suspend fun PlayerScript.customsTalk(officer: NpcRef) {
        val p = player
        npc(officer, "Can I help you?")
        when (choice("Select an Option", "Can I journey on this ship?", "Does Karamja have unusual customs then?")) {
            1 -> {
                me("Can I journey on this ship?")
                npc(officer, "You need to be searched before you can board.")
                val charos = p.wears(Objs.RING_OF_CHAROS_UNLOCKED)
                val options = mutableListOf("Why?", "Search away. I have nothing to hide.", "You're not putting your hands on my things!")
                if (charos) options += "(Charm) You don't need to look through my things."
                var pick = choice("Select an Option", *options.toTypedArray())
                if (pick == 1) {
                    me("Why?")
                    npc(officer, "Because Asgarnia has banned the import of intoxicating spirits.")
                    pick = choice("Select an Option", *options.drop(1).toTypedArray()) + 1
                }
                when (pick) {
                    2 -> {
                        me("Search away. I have nothing to hide.")
                        customsSearch(officer)
                    }

                    3 -> {
                        me("You're not putting your hands on my things!")
                        npc(officer, "You're not getting on this ship then.")
                    }

                    else -> {
                        me("You don't need to look through my things.")
                        npc(
                            officer,
                            "Oh, I think I do. If you don't want to let me search your possessions, then you're not coming " +
                                "aboard this ship.",
                        )
                    }
                }
            }

            else -> {
                me("Does Karamja have any unusual customs then?")
                npc(officer, "I'm not that sort of customs officer.")
            }
        }
    }

    /** The search: rum is confiscated (the Ring of Charos(a) offers a charm that fails), otherwise the player pays and boards. */
    private suspend fun PlayerScript.customsSearch(officer: NpcRef) {
        val p = player
        if (!p.has(Objs.KARAMJA_RUM)) {
            npc(officer, "Well you've got some odd stuff, but it's all legal. Now you need to pay a boarding charge of 30 coins.")
            if (choice("Select an Option", "Okay.", "Oh, I'll not bother then.") == 1) {
                me("Okay.")
                board(PORT_SARIM, "Port Sarim")
            } else {
                me("Oh, I'll not bother then.")
            }
            return
        }
        npc(officer, "Aha, trying to smuggle rum are we?")
        val charm =
            p.wears(Objs.RING_OF_CHAROS_UNLOCKED) &&
                choice(
                    "Select an option",
                    "Umm... it's for personal use?",
                    "(Charm) This is not the Karamja rum you are looking " +
                        "for.",
                ) == 2
        if (!charm) {
            me("Umm... it's for personal use?")
            p.take(Objs.KARAMJA_RUM, 1, "customs officer")
            p.message("The customs officer confiscates your rum.")
            p.message("You will need to find some way to smuggle it off the island...")
            return
        }
        me("This is not the Karamja rum you are looking for.")
        npc(officer, "This is not the Karamja rum we are looking for.")
        me("Well that was easy!")
        p.anim(Seqs.EMOTE_DANCE)
        p.message("You dance for joy...")
        p.message("...and accidentally drop the rum.")
        p.take(Objs.KARAMJA_RUM, 1, "customs officer")
        me("Drat...")
        p.message("You will need to try again.")
    }

    /** The "Travel" op of the officer: the search of the dialogue without the questions. */
    private suspend fun PlayerScript.customsTravel(officer: NpcRef) {
        val p = player
        if (p.has(Objs.KARAMJA_RUM)) {
            npc(officer, "Aha, trying to smuggle rum are we?")
            me("Umm... it's for personal use?")
            p.take(Objs.KARAMJA_RUM, 1, "customs officer")
            p.message("The customs officer confiscates your rum.")
            p.message("You will need to find some way to smuggle it off the island...")
            return
        }
        board(PORT_SARIM, "Port Sarim")
    }

    private companion object {
        const val OP_TALK = 1

        /** Op 3 of the sailors ("Travel", "Musa Point") and of the customs officer ("Travel", "Port Sarim"): cache npc ops. */
        const val OP_TRAVEL = 3

        /** Wiki "Pirate's Treasure": the boarding charge is 30 coins. */
        const val FARE = 30

        /** The three sailors of Port Sarim (multi-NPCs on varbit 18314, resolved per player). */
        val SAILORS = intArrayOf(Npcs.CAPTAIN_TOBIAS, Npcs.SEAMAN_LORRIS, Npcs.SEAMAN_THRESNOR)

        /** OPEN-QUB1: Musa Point dock, the customs officer's Wiki spawn tile (map 2950,3151). */
        val MUSA_POINT = CoordGrid(2950, 3151, 0)

        /** OPEN-QUB1: Port Sarim dock, the sailors' Wiki spawn tile (map 3028,3218). */
        val PORT_SARIM = CoordGrid(3028, 3218, 0)

        /** Dragon Slayer I stage from which the sailors are asked about Crandor (the player owns the ship, see DragonSlayer). */
        const val DS_SHIP = 6
    }
}
