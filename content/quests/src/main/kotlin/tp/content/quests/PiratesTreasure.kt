@file:Suppress("LongMethod", "LargeClass", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions")
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.choice
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.util.CoordGrid

/**
 * Pirate's Treasure (docs/mechanics/quest-pirates-treasure.md): Redbeard Frank, Luthas' banana crate, Wydin's back room,
 * Hector's chest and the buried casket. The quest variable (varp 71) is 0 / 1 / 4 in the cache; the stages between are ours
 * (OPEN-QU1): 1 started, 2 Frank gave the chest key, 3 the message is taken from the chest. Dialogue is the Wiki
 * "Transcript:Pirate's Treasure". The crate, the job at Luthas and at Wydin and the rum's whereabouts are account flags.
 */
public class PiratesTreasure : ContentPlugin {
    override fun ContentScope.register() {
        onOpNpc(Npcs.REDBEARD_FRANK, OP_TALK) { frank ->
            player.stopInteraction()
            frankTalk(frank)
        }
        onUseOnNpc(Objs.KARAMJA_RUM, Npcs.REDBEARD_FRANK) { frank ->
            player.stopInteraction()
            if (stage(player) == STARTED) {
                me("Frank, I have some Karamja Rum.")
                frankDeliver(frank)
            } else {
                frankTalk(frank)
            }
        }
        for (rum in listOf(Objs.TBWT_BANANA_IN_KARAMJA_RUM, Objs.TBWT_SLICED_BANANA_IN_KARAMJA_RUM)) {
            onUseOnNpc(rum, Npcs.REDBEARD_FRANK) { frank ->
                player.stopInteraction()
                npc(frank, "Arr, Matey!", "Have ye brought some rum for yer ol' mate Frank?")
                me("Yes, I've got some.")
                if (rum == Objs.TBWT_BANANA_IN_KARAMJA_RUM) {
                    npc(frank, "Arr - this here rum's got a banana stuck in it!")
                } else {
                    npc(frank, "Arr - I don't likes banana in me rum!")
                }
            }
        }
        onUseOnNpc(Objs.PIRATE_CASKET, Npcs.REDBEARD_FRANK) { frank ->
            player.stopInteraction()
            if (stage(player) >= END) frankShare(frank)
        }
        onOpNpc(Npcs.LUTHAS, OP_TALK) { luthas ->
            player.stopInteraction()
            luthasTalk(luthas)
        }
        onOpNpc(Npcs.WYDIN, OP_TALK) { wydin ->
            player.stopInteraction()
            wydinTalk(wydin)
        }
        onOpLoc(Locs.BANANACRATE, OP_FIRST) {
            player.stopInteraction()
            searchCrate()
        }
        onOpLoc(Locs.BANANACRATE, OP_SECOND) {
            player.stopInteraction()
            fillCrate()
        }
        onUseOnLoc(Objs.BANANA, Locs.BANANACRATE) {
            player.stopInteraction()
            packBanana()
        }
        onUseOnLoc(Objs.KARAMJA_RUM, Locs.BANANACRATE) {
            player.stopInteraction()
            stashRum()
        }
        onUseOnLoc(Objs.TBWT_BANANA_IN_KARAMJA_RUM, Locs.BANANACRATE) {
            player.stopInteraction()
            box("Pirates don't usually take banana with their Karamjan rum.")
        }
        onUseOnLoc(Objs.TBWT_SLICED_BANANA_IN_KARAMJA_RUM, Locs.BANANACRATE) {
            player.stopInteraction()
            box("Pirates don't usually take sliced banana with their Karamjan rum.")
        }
        for (tree in BANANA_TREES) {
            onOpLoc(tree, OP_FIRST) { loc ->
                player.stopInteraction()
                pickBanana(loc)
            }
        }
        onOpLoc(Locs.GROCERYCRATE, OP_FIRST) {
            player.stopInteraction()
            searchGroceryCrate()
        }
        onOpLoc(Locs.WYDINDOOR, OP_FIRST) { door ->
            player.stopInteraction()
            wydinDoor(door)
        }
        onOpLoc(Locs.PIRATECHEST, OP_FIRST) {
            player.stopInteraction()
            player.message("The chest is locked.")
        }
        onUseOnLoc(Objs.CHEST_KEY, Locs.PIRATECHEST) {
            player.stopInteraction()
            openChest()
        }
        onItemOp(Objs.PIRATEMESSAGE, "Read") {
            startScript { box("Visit the city of the White Knights. In the park, Saradomin points to the X which marks the spot.") }
        }
        onItemOp(Objs.PIRATE_CASKET, "Open") {
            openCasket(this)
        }
        events.on<DigEvent> { dig(it) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.PIRATES_TREASURE) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.PIRATES_TREASURE.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when (stage(p)) {
            0 -> listOf("I can start this quest by speaking to Redbeard Frank at Port Sarim.")
            STARTED -> listOf("Redbeard Frank wants a bottle of Karamjan rum before he will tell me where the treasure is.")
            KEY -> listOf("Frank gave me the key to One-Eyed Hector's chest in the Blue Moon Inn, Varrock.")
            else -> listOf("The message from the chest says the treasure is buried in Falador Park.")
        }

    // ---- Redbeard Frank

    private suspend fun PlayerScript.frankTalk(frank: NpcRef) {
        val p = player
        when {
            stage(p) == 0 -> frankStart(frank)
            stage(p) == STARTED -> frankRum(frank)
            stage(p) < END -> frankAfter(frank)
            else -> npc(frank, "Arr, Matey!")
        }
    }

    private suspend fun PlayerScript.frankAfter(frank: NpcRef) {
        val p = player
        npc(frank, "Arr, Matey!")
        if (p.has(Objs.CHEST_KEY) || p.has(Objs.PIRATEMESSAGE) || stage(p) > KEY) return
        if (choice("Select an Option", "I seem to have lost my chest key...", "Arr!") == 1) frankKey(frank)
    }

    private fun rumDealDone(p: PlayerRef): Boolean = QuestsB.RUM_DEAL.state(p) == QuestState.FINISHED

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Redbeard Frank". */
    private suspend fun PlayerScript.frankStart(frank: NpcRef) {
        npc(frank, "Arr, Matey!")
        when (
            choice(
                "Select an Option",
                "I'm in search of treasure.",
                "Arr!",
                "Do you have anything for trade?",
            )
        ) {
            1 -> {
                me("I'm in search of treasure.")
                npc(
                    frank,
                    "Arr, treasure you be after eh? Well I might be able to tell you where to find some... For " +
                        "a price...",
                )
                me("What sort of price?")
                npc(frank, "Well for example if you can get me a bottle of rum... Not just any rum mind...")
                npc(frank, "I'd like some rum made on Karamja Island. There's no rum like Karamja Rum!")
                if (rumDealDone(player)) {
                    me("Would some Braindeath 'Rum' do?")
                    npc(frank, "Gadzooks, that swill! Not a chance!")
                    npc(frank, "It's Karamja Rum or no deal.")
                }
                when (
                    choice(
                        "Start the Pirate's Treasure quest?",
                        "Yes.",
                        "No.",
                    )
                ) {
                    1 -> {
                        QuestsB.PIRATES_TREASURE.advance(player, STARTED)
                        me("Ok, I will bring you some rum.")
                        npc(frank, "Yer a saint, although it'll take a miracle to get it off Karamja.")
                        me("What do you mean?")
                        npc(
                            frank,
                            "The Customs office has been clampin' down on the export of spirits. You seem like a " +
                                "resourceful young lad, I'm sure ye'll be able to find a way to slip the stuff past them.",
                        )
                        me("Well I'll give it a shot.")
                        npc(frank, "Arr, that's the spirit!")
                    }

                    2 -> {
                        me("Not right now.")
                        npc(frank, "Fair enough. I'll still be here and thirsty whenever you feel like helpin' out.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                Unit
            }

            3 -> {
                Unit
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Redbeard Frank@2". */
    private suspend fun PlayerScript.frankRum(frank: NpcRef) {
        npc(frank, "Arr, Matey!")
        npc(frank, "Have ye brought some rum for yer ol' mate Frank?")
        if (!player.has(Objs.KARAMJA_RUM)) {
            me("No, not yet.")
            npc(frank, "Not surprising, tis no easy task to get it off Karamja.")
            me("What do you mean?")
            npc(
                frank,
                "The Customs office has been clampin' down on the export of spirits. You seem like a " +
                    "resourceful young lad, I'm sure ye'll be able to find a way to slip the stuff past them.",
            )
            me("Well I'll give it another shot.")
            return
        }
        me("Yes, I've got some.")
        npc(
            frank,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a pirate " +
                "captain called One-Eyed Hector.",
        )
        npc(
            frank,
            "Hector were very successful and became very rich. But about a year ago we were boarded by " +
                "the Customs and Excise Agents.",
        )
        npc(
            frank,
            "Hector were killed along with many of the crew, I were one of the few to escape and I " +
                "escaped with this.",
        )
        itemBox(Objs.CHEST_KEY, "Frank happily takes the rum... and hands you a key.")
        giveKey()
        npc(
            frank,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue Moon Inn in " +
                "Varrock.",
        )
        npc(frank, "With any luck his treasure will be in there.")
        when (
            choice(
                "Select an Option",
                "Ok thanks, I'll go and get it.",
                "So why didn't you ever get it?",
            )
        ) {
            1 -> {
                me("Ok thanks, I'll go and get it.")
            }

            2 -> {
                me("So why didn't you ever get it?")
                npc(frank, "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Redbeard Frank@2". */
    private suspend fun PlayerScript.frankDeliver(frank: NpcRef) {
        npc(
            frank,
            "Now a deal's a deal, I'll tell ye about the treasure. I used to serve under a pirate " +
                "captain called One-Eyed Hector.",
        )
        npc(
            frank,
            "Hector were very successful and became very rich. But about a year ago we were boarded by " +
                "the Customs and Excise Agents.",
        )
        npc(
            frank,
            "Hector were killed along with many of the crew, I were one of the few to escape and I " +
                "escaped with this.",
        )
        itemBox(Objs.CHEST_KEY, "Frank happily takes the rum... and hands you a key.")
        giveKey()
        npc(
            frank,
            "This be Hector's key. I believe it opens his chest in his old room in the Blue Moon Inn in " +
                "Varrock.",
        )
        npc(frank, "With any luck his treasure will be in there.")
        when (
            choice(
                "Select an Option",
                "Ok thanks, I'll go and get it.",
                "So why didn't you ever get it?",
            )
        ) {
            1 -> {
                me("Ok thanks, I'll go and get it.")
            }

            2 -> {
                me("So why didn't you ever get it?")
                npc(frank, "I'm not allowed in the Blue Moon Inn. Apparently I'm a drunken trouble maker.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Reclaiming the key". */
    private suspend fun PlayerScript.frankKey(frank: NpcRef) {
        npc(frank, "Arr, Matey!")
        me("I seem to have lost my chest key...")
        npc(frank, "Arr, silly you. Fortunately I took the precaution to have another one made.")
        itemBox(Objs.CHEST_KEY, "Frank hands you a chest key.")
        player.giveOrDrop(Objs.CHEST_KEY, 1, "pirate's treasure key")
        when (
            choice(
                "Select an Option",
                "Arr!",
                "Do you have anything for trade?",
            )
        ) {
            1 -> {
                Unit
            }

            2 -> {
                Unit
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Using the chest on Redbeard Frank after the quest". */
    private suspend fun PlayerScript.frankShare(frank: NpcRef) {
        me("I have the treasure, would you like a share?")
        npc(frank, "No lad, you got it fair and square.")
        npc(frank, "You enjoy it. It's what Hector would have wanted.")
    }

    /** Frank takes the rum and gives Hector's chest key (stage [KEY]); a full backpack drops the key at the player's feet. */
    private fun PlayerScript.giveKey() {
        val p = player
        p.take(Objs.KARAMJA_RUM, 1, "pirate's treasure rum")
        p.giveOrDrop(Objs.CHEST_KEY, 1, "pirate's treasure key")
        QuestsB.PIRATES_TREASURE.advance(p, KEY)
    }

    // ---- Luthas and the banana crate

    private suspend fun PlayerScript.luthasTalk(luthas: NpcRef) {
        val p = player
        if (p.flag(JOB_LUTHAS) && p.saved(CRATE_BANANAS) == MAX_BANANAS.toLong()) {
            luthasPaid(luthas)
        } else {
            luthasMenu(luthas)
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Luthas". */
    private suspend fun PlayerScript.luthasMenu(luthas: NpcRef) {
        npc(luthas, "Hello I'm Luthas, I run the banana plantation here.")
        when (
            choice(
                "Select an Option",
                "Could you offer me employment on your plantation?",
                "That customs officer is annoying isn't she?",
            )
        ) {
            1 -> {
                player.setFlag(JOB_LUTHAS, true)
                me("Could you offer me employment on your plantation?")
                npc(luthas, "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.")
                npc(
                    luthas,
                    "You wouldn't believe the demand for bananas from Wydin's shop over in Port Sarim. I think " +
                        "this is the third crate I've shipped him this month..",
                )
                npc(luthas, "If you could fill it up with bananas, I'll pay you 30 gold.")
            }

            2 -> {
                me("That customs officer is annoying isn't she?")
                npc(luthas, "Well I know her pretty well. She doesn't cause me any trouble any more.")
                npc(luthas, "She doesn't even search my export crates any more. She knows they only contain bananas.")
                me("Really? How interesting. Whereabouts do you send those to?")
                npc(
                    luthas,
                    "There is a little shop over in Port Sarim that buys them up by the crate. I believe it is " +
                        "run by a man called Wydin.",
                )
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Luthas after filling up the crate". */
    private suspend fun PlayerScript.luthasPaid(luthas: NpcRef) {
        me("I've filled a crate with bananas.")
        npc(luthas, "Well done, here's your payment.")
        player.message("Luthas hands you 30 coins.")
        player.giveOrDrop(Objs.COINS, LUTHAS_WAGES, "luthas wages")
        shipCrate(player)
        when (
            choice(
                "Select an Option",
                "Will you pay me for another crate full?",
                "Thank you, I'll be on my way",
                "So where are these bananas going to be delivered to?",
                "That customs officer is annoying isn't she?",
            )
        ) {
            1 -> {
                me("Will you pay me for another crate full?")
                npc(luthas, "Yes certainly.")
                npc(
                    luthas,
                    "If you go outside you should see the old crate has been loaded on to the ship, and there " +
                        "is another empty crate in its place.",
                )
            }

            2 -> {
                me("Thank you, I'll be on my way.")
            }

            3 -> {
                me("So where are these bananas going to be delivered to?")
                npc(luthas, "I sell them to Wydin who runs a grocery store in Port Sarim.")
            }

            4 -> {
            }

            else -> {
                Unit
            }
        }
    }

    /**
     * Luthas pays for the full crate; it is loaded onto the ship and an empty one takes its place (Wiki "Talking to Luthas after
     * filling up the crate"). Rum hidden in it travels to Wydin's food store.
     */
    private fun shipCrate(p: PlayerRef) {
        p.setSaved(CRATE_BANANAS, null)
        if (p.flag(CRATE_RUM)) {
            p.setFlag(CRATE_RUM, false)
            p.setFlag(RUM_AT_WYDIN, true)
        }
    }

    private fun crateBananas(p: PlayerRef): Int = (p.saved(CRATE_BANANAS) ?: 0L).toInt()

    private fun PlayerScript.searchCrate() {
        val p = player
        val n = crateBananas(p)
        // Wiki "Searching the crate": a count line below 10 bananas, a box at 10, and a second box for the stashed rum.
        p.startScript {
            if (n >=
                MAX_BANANAS
            ) {
                box("The crate is full of bananas.")
            } else {
                p.message("The crate has $n banana${if (n == 1) "" else "s"} inside.")
            }
            if (p.flag(CRATE_RUM)) box("There is also some rum stashed in here too.")
        }
    }

    private fun PlayerScript.fillCrate() {
        val p = player
        // OPEN-QUB3: "Why would I want to do that?" is the Wiki quest page's reply of a player Luthas has not employed.
        if (!p.flag(JOB_LUTHAS)) return p.message("Why would I want to do that?")
        val n = crateBananas(p)
        if (n >= MAX_BANANAS) return p.message("The crate is already full.")
        val have = p.inv(INV).total(Objs.BANANA).toInt()
        // OPEN-QUB3: the reply when no banana is carried is not published.
        if (have == 0) return p.message("You have no bananas to pack.")
        val put = minOf(have, MAX_BANANAS - n)
        if (!p.take(Objs.BANANA, put, "banana crate")) return
        p.setSaved(CRATE_BANANAS, (n + put).toLong())
        p.message("You pack all your bananas into the crate.")
    }

    private fun PlayerScript.packBanana() {
        val p = player
        if (!p.flag(JOB_LUTHAS)) return p.message("Why would I want to do that?")
        val n = crateBananas(p)
        if (n >= MAX_BANANAS) return p.message("The crate is already full.")
        if (!p.take(Objs.BANANA, 1, "banana crate")) return
        p.setSaved(CRATE_BANANAS, (n + 1).toLong())
        p.message("You pack a banana into the crate.")
    }

    private suspend fun PlayerScript.stashRum() {
        val p = player
        if (!p.flag(JOB_LUTHAS)) return p.message("Why would I want to do that?")
        if (p.flag(CRATE_RUM)) return p.message("There's already some rum in here...")
        if (!p.take(Objs.KARAMJA_RUM, 1, "stash rum")) return
        p.setFlag(CRATE_RUM, true)
        box("You stash the rum in the crate.")
    }

    /**
     * Picks a banana from a Karamja banana tree. OPEN-QUB4: the Wiki gives no pick message, count or regrowth for these trees;
     * a tree is picked clean after [BANANA_PICKS] bananas and regrows after [BANANA_REGROW] ticks.
     */
    private fun PlayerScript.pickBanana(tree: tp.plugin.LocRef) {
        val p = player
        val next = BANANA_TREES.indexOf(tree.id).let { if (it < 0 || it >= BANANA_TREES.size - 1) -1 else BANANA_TREES[it + 1] }
        if (next < 0) return p.message("There are no bananas left on the tree.")
        if (!p.give(Objs.BANANA, 1, "pick banana")) return p.message("You don't have enough inventory space.")
        p.anim(Seqs.HUMAN_PICKUPFLOOR)
        p.message("You pick a banana.")
        p.deleteLoc(tree.coord, tree.shape, BANANA_REGROW)
        p.addLoc(next, tree.coord, tree.shape, tree.angle, BANANA_REGROW)
    }

    // ---- Wydin

    private suspend fun PlayerScript.wydinTalk(wydin: NpcRef) {
        if (player.flag(JOB_WYDIN)) wydinEmployee(wydin) else wydinMenu(wydin)
    }

    private fun wearsApron(p: PlayerRef): Boolean = p.wears(Objs.WHITE_APRON) || p.wears(Objs.PIRATETREASURE_APRON)

    private fun hasApron(p: PlayerRef): Boolean = p.hasAny(Objs.WHITE_APRON, Objs.PIRATETREASURE_APRON) || wearsApron(p)

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Wydin". */
    private suspend fun PlayerScript.wydinMenu(wydin: NpcRef) {
        npc(wydin, "Welcome to my food store! Would you like to buy anything?")
        when (
            choice(
                "Select an Option",
                "Yes please.",
                "No, thank you.",
                "What can you recommend?",
                "Can I get a job here?",
            )
        ) {
            1 -> {
                me("Yes please.")
                player.events.publish(OpenShopRequest(player, Npcs.WYDIN))
            }

            2 -> {
                me("No, thank you.")
            }

            3 -> {
                Unit
            }

            4 -> {
                me("Can I get a job here?")
                npc(
                    wydin,
                    "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your own " +
                        "white apron?",
                )
                if (hasApron(player)) {
                    me("Yes, I have one right here.")
                    npc(
                        wydin,
                        "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, " +
                            "please.",
                    )
                    player.setFlag(JOB_WYDIN, true)
                    if (!wearsApron(player)) {
                        npc(wydin, "You need to put your white apron on first though.")
                    }
                    return
                }
                if (!hasApron(player)) {
                    me("No, I haven't.")
                    npc(
                        wydin,
                        "Well, you can't work here unless you have a white apron. Health and safety regulations, " +
                            "you understand.",
                    )
                    me("Where can I get one of those?")
                    npc(wydin, "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap there.")
                    npc(
                        wydin,
                        "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store somewhere. It's " +
                            "the little place just north of here.",
                    )
                }
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Wydin after getting the job". */
    private suspend fun PlayerScript.wydinEmployee(wydin: NpcRef) {
        npc(wydin, "Is it nice and tidy round the back now?")
        when (
            choice(
                "Select an Option",
                "Yes, can I work out front now?",
                "Yes, are you going to pay me yet?",
                "No, it's a complete mess",
                "Can I buy something please?",
            )
        ) {
            1 -> {
                me("Yes, can I work out front now?")
                npc(wydin, "No, I'm the one who works here.")
            }

            2 -> {
                me("Yes, are you going to pay me yet?")
                npc(wydin, "Umm... No, not yet.")
            }

            3 -> {
                me("No, it's a complete mess.")
                npc(wydin, "Ah well, it'll give you something to do, won't it.")
            }

            4 -> {
                me("Can I buy something please?")
                npc(wydin, "Yes, of course.")
                player.events.publish(OpenShopRequest(player, Npcs.WYDIN))
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.wydinDoor(door: tp.plugin.LocRef) {
        val p = player
        val wydin = p.npcsWithin(door.coord, WYDIN_RADIUS).firstOrNull { it.type == Npcs.WYDIN }
        when {
            !p.flag(JOB_WYDIN) -> {
                // Wiki "Trying to open the door in the Food Store before getting the job".
                if (wydin == null) return p.message("The door is locked.")
                wydinBlocked(wydin)
            }

            !wearsApron(p) -> {
                npcAs(Npcs.WYDIN, "Wydin", "Can you put your white apron on before going in there, please?")
            }

            else -> {
                p.passDoor(door)
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Trying to open the door in the Food Store before getting the job". */
    private suspend fun PlayerScript.wydinBlocked(wydin: NpcRef) {
        npc(wydin, "Hey, you can't go in there. Only employees of the grocery store can go in.")
        when (
            choice(
                "Select an Option",
                "Well, can I get a job here?",
                "Sorry, I didn't realise.",
            )
        ) {
            1 -> {
                wydinJob(wydin)
            }

            2 -> {
                me("Sorry, I didn't realise.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Pirate's Treasure", section "Talking to Wydin". */
    private suspend fun PlayerScript.wydinJob(wydin: NpcRef) {
        me("Can I get a job here?")
        npc(
            wydin,
            "Well, you're keen, I'll give you that. Okay, I'll give you a go. Have you got your own " +
                "white apron?",
        )
        if (hasApron(player)) {
            me("Yes, I have one right here.")
            npc(
                wydin,
                "Wow - you are well prepared! You're hired. Go through to the back and tidy up for me, " +
                    "please.",
            )
            player.setFlag(JOB_WYDIN, true)
            if (!wearsApron(player)) {
                npc(wydin, "You need to put your white apron on first though.")
            }
            return
        }
        if (!hasApron(player)) {
            me("No, I haven't.")
            npc(
                wydin,
                "Well, you can't work here unless you have a white apron. Health and safety regulations, " +
                    "you understand.",
            )
            me("Where can I get one of those?")
            npc(wydin, "Well, I get all of mine over at the clothing shop in Varrock. They sell them cheap there.")
            npc(
                wydin,
                "Oh, and I'm sure that I've seen a spare one over in Gerrant's fish store somewhere. It's " +
                    "the little place just north of here.",
            )
        }
    }

    private suspend fun PlayerScript.searchGroceryCrate() {
        val p = player
        if (p.flag(RUM_AT_WYDIN) && stage(p) == STARTED) {
            p.message("There are a lot of bananas in the crate.")
            if (!p.give(Objs.KARAMJA_RUM, 1, "rum from wydin's crate")) return p.message("You don't have enough inventory space.")
            p.setFlag(RUM_AT_WYDIN, false)
            p.message("You find your bottle of rum in amongst the bananas.")
        } else {
            // OPEN-QUB4: the reply of an ordinary search is not published.
            p.message("You search the crate but find nothing interesting.")
        }
    }

    // ---- the chest and the casket

    private suspend fun PlayerScript.openChest() {
        val p = player
        if (stage(p) < KEY || p.has(Objs.PIRATEMESSAGE)) return p.message("The chest is locked.")
        p.message("You unlock the chest.")
        p.message("All that's in the chest is a message...")
        if (p.give(Objs.PIRATEMESSAGE, 1, "pirate message")) {
            QuestsB.PIRATES_TREASURE.advance(p, MESSAGE)
            p.message("You take the message from the chest.")
        } else {
            p.message("You don't have enough inventory space.")
        }
    }

    /** Wiki "Casket (Pirate's Treasure)": 450 coins, a gold ring and an emerald. */
    private fun openCasket(p: PlayerRef) {
        val done =
            p.transaction("pirate's treasure casket") {
                remove(INV, Objs.PIRATE_CASKET, 1) &&
                    addOrDrop(INV, Objs.COINS, CASKET_COINS) &&
                    addOrDrop(INV, Objs.GOLD_RING, 1) &&
                    addOrDrop(INV, Objs.EMERALD, 1)
            }
        if (done) p.startScript { box("You open the casket, and find One-Eyed Hector's treasure.") }
    }

    // ---- digging in Falador Park

    private fun dig(e: DigEvent) {
        val p = e.player
        if (stage(p) < MESSAGE || stage(p) >= END || e.at != TREASURE_SPOT) return
        e.handled = true
        val gardener = p[GARDENER]
        if (gardener != null && gardener.isInWorld) {
            p.message("I can't dig up anything with him attacking me!")
            return
        }
        if (p[DUG_ONCE] != true) {
            // The first dig calls the gardener (Wiki "Digging in Falador Park"); the second one finds the casket.
            p[DUG_ONCE] = true
            val g = p.spawnNpc(Npcs.PIRATE_IRATE_GARDENER, e.at.translate(1, 0), 0)
            g.huntRange = GARDENER_HUNT
            g.say("First moles, now this! Take this, vandal!")
            g.queueHit(GARDENER_LIFETIME, strong = false) { g.despawn() }
            p[GARDENER] = g
            return
        }
        p.startScript {
            p.message("and find a little chest of treasure.")
            p.giveOrDrop(Objs.PIRATE_CASKET, 1, "pirate's treasure casket")
            QuestCompletion.complete(p, QuestsB.PIRATES_TREASURE, REWARD)
        }
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val INV = tp.symbols.Invs.INV

        /** Wiki "Casket (Pirate's Treasure)" contents: 450 coins always. */
        const val CASKET_COINS = 450

        /** Quest variable stages (OPEN-QU1): 0 and 4 are the cache's `unstarted` / `endstate`. */
        const val STARTED = 1
        const val KEY = 2
        const val MESSAGE = 3
        const val END = 4

        /** Account flags and values of the quest (`saved`). */
        const val JOB_LUTHAS = "quests.pt.luthas_job"
        const val JOB_WYDIN = "quests.pt.wydin_job"
        const val CRATE_BANANAS = "quests.pt.crate_bananas"
        const val CRATE_RUM = "quests.pt.crate_rum"
        const val RUM_AT_WYDIN = "quests.pt.rum_at_wydin"

        /** Wiki "Pirate's Treasure": a crate takes ten bananas. */
        const val MAX_BANANAS = 10

        /** Wiki "Talking to Luthas after filling up the crate": 30 coins for a full crate. */
        const val LUTHAS_WAGES = 30

        /** OPEN-QUB4: the Karamja banana tree sequence is `banana tree` (full) down to empty; five bananas per tree is ours. */
        const val BANANA_PICKS = 5

        /** OPEN-QUB4: ticks a picked tree takes to regrow (not published). */
        const val BANANA_REGROW = 100

        /** Banana tree locs, full to empty (symbols `bananatreefull` ... `bananatreeempty`). */
        val BANANA_TREES =
            intArrayOf(
                Locs.BANANATREEFULL,
                Locs.BANANATREEFOUR,
                Locs.BANANATREETHREE,
                Locs.BANANATREETWO,
                Locs.BANANATREEONE,
                Locs.BANANATREEEMPTY,
            )

        /** Radius around Wydin's door in which his NPC is looked for. */
        const val WYDIN_RADIUS = 10

        /** Wiki "Pirate's Treasure" walkthrough: the "X" of dirt east of Saradomin's statue in Falador Park (map 2999,3383). */
        val TREASURE_SPOT = CoordGrid(2999, 3383, 0)

        /**
         * OPEN-QUB5: the gardener hunts within this range and goes away after [GARDENER_LIFETIME] ticks (Wiki: "run out of the
         * park to despawn him").
         */
        const val GARDENER_HUNT = 8
        const val GARDENER_LIFETIME = 300

        val GARDENER = AttrKey<NpcRef>("quests.pt.gardener")
        val DUG_ONCE = AttrKey<Boolean>("quests.pt.dug")

        /** Wiki "Pirate's Treasure" rewards: 2 quest points (cache), 800 Cooking experience (change of 3 November 2026). */
        val REWARD = QuestReward(model = Objs.PIRATE_CASKET, xp = listOf(Stats.COOKING to 800.0))
    }
}
