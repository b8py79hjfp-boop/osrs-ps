@file:Suppress(
    "LongMethod",
    "LargeClass",
    "ReturnCount",
    "CyclomaticComplexMethod",
    "NestedBlockDepth",
    "TooManyFunctions",
    "MagicNumber",
    "ComplexCondition",
)
// MagicNumber: menu option numbers of the transcripts.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttackCheck
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.LocRef
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Shield of Arrav (docs/mechanics/quest-shield-of-arrav.md): Reldo's book, the Phoenix Gang (Baraek, Straven, Jonny the Beard)
 * and the Black Arm Gang (Charlie the Tramp, Katrine, the Weaponsmaster), the Curator's half certificates and King Roald. Two
 * players are needed: one of each gang, who swap half certificates (trade, or "use" on the partner). The cache variable (varbit
 * 13716) only has 0 / 1 / 2, so the progress is the account value `quests.soa.stage` (OPEN-QUB9) with the gang in
 * `quests.soa.gang`; varp 145 (the old quest varp, which the cache's multi-NPC Jonny the Beard still reads) is set to 8 while
 * Jonny is the Phoenix Gang's target and 9 once he is dead. Dialogue is the Wiki "Transcript:Shield of Arrav".
 */
public class ShieldOfArrav : ContentPlugin {
    override fun ContentScope.register() {
        for (reldo in listOf(Npcs.RELDO_NORMAL, Npcs.RELDO_WITHBOOK)) {
            onOpNpc(reldo, OP_TALK) { npc ->
                player.stopInteraction()
                // The Knight's Sword's Imcando question while this quest is not under way.
                val idle = stage(player) == 0 || finished(player)
                if (idle && knightsSwordAsksReldo(player)) reldoKnightsSword(npc) else reldoTalk(npc)
            }
        }
        onOpNpc(Npcs.BARAEK, OP_TALK) { baraek ->
            player.stopInteraction()
            baraekTalk(baraek)
        }
        onOpNpc(Npcs.TRAMPPG, OP_TALK) { charlie ->
            player.stopInteraction()
            charlieTalk(charlie)
        }
        onOpNpc(Npcs.KATRINE, OP_TALK) { katrine ->
            player.stopInteraction()
            katrineTalk(katrine)
        }
        onOpNpc(Npcs.STRAVEN, OP_TALK) { straven ->
            player.stopInteraction()
            stravenTalk(straven)
        }
        for (weaponsmaster in listOf(Npcs.WEAPONSMASTER, Npcs.WEAPONSMASTER_VIS)) {
            onOpNpc(weaponsmaster, OP_TALK) { npc ->
                player.stopInteraction()
                weaponsmasterTalk(npc)
            }
        }
        onOpNpc(Npcs.CURATOR, OP_TALK) { curator ->
            player.stopInteraction()
            curatorTalk(curator)
        }
        onOpNpc(Npcs.KING_ROALD, OP_TALK) { roald ->
            player.stopInteraction()
            roaldTalk(roald)
        }
        onOpLoc(Locs.QUESTBOOKCASE, OP_FIRST) {
            player.stopInteraction()
            searchBookcase()
        }
        onOpLoc(Locs.PHOENIXDOOR, OP_FIRST) { door ->
            player.stopInteraction()
            phoenixDoor(door)
        }
        onOpLoc(Locs.PHOENIXDOOR2, OP_FIRST) { door ->
            player.stopInteraction()
            depotDoor(door)
        }
        onOpLoc(Locs.BLACKARMDOOR, OP_FIRST) { door ->
            player.stopInteraction()
            blackArmDoor(door)
        }
        onOpLoc(Locs.BLACKARMCUPBOARDSHUT, OP_FIRST) {
            player.stopInteraction()
            takeHalf(Objs.ARRAVSHIELD2, BLACK_ARM, "You open the cupboard", "cupboard")
        }
        onOpLoc(Locs.PHOENIXSHUTCHEST, OP_FIRST) {
            player.stopInteraction()
            takeHalf(Objs.ARRAVSHIELD1, PHOENIX, "You open the chest", "chest")
        }
        onOpObj(Objs.PHOENIX_CROSSBOW, OP_TAKE) { crossbow ->
            player.stopInteraction()
            takeCrossbow(crossbow)
        }
        onItemOp(Objs.THE_SHIELD_OF_ARRAV, "Read") {
            startScript { readBook() }
        }
        onItemOp(Objs.ARRAVCERTIFICATE, "Read") {
            message("I can use this to claim a reward from King Roald.")
        }
        for (obj in listOf(Objs.PHOENIXKEY2, Objs.PHOENIX_CROSSBOW)) {
            onItemOp(obj, "Inspect") { startScript { inspect(obj) } }
        }
        for (half in listOf(Objs.ARRAVCERTIFICATE_LFT, Objs.ARRAVCERTIFICATE_RHT)) {
            onItemOp(half, "Inspect") { startScript { inspectHalf(half) } }
        }
        onUseOnItem(Objs.ARRAVCERTIFICATE_LFT, Objs.ARRAVCERTIFICATE_RHT) {
            startScript { combine() }
        }
        onOpPlayerT(Components.Inventory.ITEMS) { target -> giveToPartner(target) }
        events.on<DeathEvent> { onDeath(it) }
        events.on<AttackCheck> { onAttackCheck(it) }
        onNpcSpawn { respawnAsMulti(this) }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.SHIELD_OF_ARRAV) it.lines += journal(it.player)
        }
    }

    // ---- state

    private fun stage(p: PlayerRef): Int = (p.saved(STAGE) ?: 0L).toInt()

    private fun gang(p: PlayerRef): Int = (p.saved(GANG) ?: 0L).toInt()

    /** Moves the progress forward (never back) and keeps the cache variable at "started". */
    private fun advance(p: PlayerRef, to: Int) {
        if (to <= stage(p) || QuestsB.SHIELD_OF_ARRAV.state(p) == QuestState.FINISHED) return
        p.setSaved(STAGE, to.toLong())
        QuestsB.SHIELD_OF_ARRAV.advance(p, 1)
    }

    private fun join(p: PlayerRef, gang: Int, stage: Int) {
        if (gang(p) == 0) p.setSaved(GANG, gang.toLong())
        advance(p, stage)
    }

    private fun finished(p: PlayerRef): Boolean = QuestsB.SHIELD_OF_ARRAV.state(p) == QuestState.FINISHED

    private fun journal(p: PlayerRef): List<String> =
        when {
            stage(p) == 0 && !finished(p) -> {
                listOf("I can start this quest by speaking to Reldo in the Varrock Palace library.")
            }

            finished(p) -> {
                listOf("I returned the Shield of Arrav to the Varrock Museum with a partner and King Roald rewarded us.")
            }

            stage(p) < KNOWN -> {
                listOf("Reldo told me to look for a book about the Shield of Arrav in the palace library.")
            }

            stage(p) < PHOENIX_PAID && gang(p) == 0 -> {
                listOf(
                    "I should join the Phoenix Gang (ask Baraek) or the Black Arm Gang (ask Charlie the Tramp). A partner must join " +
                        "the other.",
                )
            }

            gang(p) == PHOENIX && stage(p) < PHOENIX_JOINED -> {
                listOf(
                    "Straven of the Phoenix Gang wants Jonny the Beard's report from " +
                        "the Blue Moon Inn.",
                )
            }

            gang(p) == PHOENIX && stage(p) < HANDED -> {
                listOf(
                    "I am a Phoenix Gang member. I can take half of the shield from the " +
                        "chest in the hideout.",
                )
            }

            gang(p) == BLACK_ARM && stage(p) < BLACK_JOINED -> {
                listOf(
                    "Katrine of the Black Arm Gang wants two Phoenix crossbows, " +
                        "which my Phoenix partner's key reaches.",
                )
            }

            gang(p) == BLACK_ARM && stage(p) < HANDED -> {
                listOf(
                    "I am a Black Arm Gang member. I can take half of the shield from the " +
                        "cupboard upstairs.",
                )
            }

            else -> {
                listOf(
                    "I gave my shield half to the curator. A half certificate swapped with my partner makes the full " +
                        "certificate for King Roald.",
                )
            }
        }

    // ---- Reldo

    private suspend fun PlayerScript.reldoTalk(reldo: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            finished(p) -> reldoIdle(reldo)
            s == 0 -> reldoStart(reldo)
            s == STARTED -> reldoBefore(reldo)
            s == BOOK -> reldoFound(reldo)
            s == READ -> reldoRead(reldo)
            s in KNOWN until HANDED && hasHalf(p) -> if (gang(p) == PHOENIX) reldoPhoenixShield(reldo) else reldoBlackArmShield(reldo)
            else -> reldoIdle(reldo)
        }
    }

    private suspend fun PlayerScript.reldoIdle(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (choice("Select an option", "Do you have anything to trade?", "I'd better get going.")) {
            1 -> me("Do you have anything to trade?")
            else -> me("I'd better get going.")
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo". */
    private suspend fun PlayerScript.reldoStart(reldo: NpcRef) {
        if (!player.flag(RELDO_MET)) {
            npc(reldo, "Hello, stranger.")
            player.setFlag(RELDO_MET, true)
            me("Hello there! The name's $name. Who are you?")
            npc(reldo, "My name is Reldo. I'm the palace librarian.")
            me("Ah, that explains why you're in the library then.")
            npc(
                reldo,
                "Well, truth be told, I would probably be here even if it wasn't where I worked. I very " +
                    "much like reading, not to mention sharing what I learn with all who are interested.",
            )
            me("Sounds like a very noble goal!")
        }
        if (player.flag(RELDO_MET)) {
            npc(reldo, "Hello there, $name.")
            player.setFlag(RELDO_MET, true)
        }
        when (
            choice(
                "Select an Option",
                "I'm in search of a quest.",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("I'm in search of a quest.")
                npc(reldo, "Hmm... I don't believe there are any here.")
                npc(reldo, "Let me think actually...")
                npc(reldo, "Ah, yes. I think I have something, if you're definitely interested?")
                if (player.combatLevel < RECOMMENDED_COMBAT) {
                    box(
                        "Before starting this quest, be aware that you combat level is lower than the recommended " +
                            "level of 10.",
                    )
                }
                when (
                    choice(
                        "Start the Shield of Arrav quest?",
                        "Yes.",
                        "No.",
                    )
                ) {
                    1 -> {
                        advance(player, STARTED)
                        me("Of course.")
                        npc(
                            reldo,
                            "Well, if you look around for book called The Shield of Arrav, you'll find a quest in " +
                                "there.",
                        )
                        npc(reldo, "I'm not sure where the book is mind you... but I'm sure it's around here somewhere.")
                        me("Great! Thank you.")
                    }

                    2 -> {
                        me("Actually, I don't think I am. Goodbye.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo before finding the book". */
    private suspend fun PlayerScript.reldoBefore(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (
            choice(
                "Select an option",
                "About that book... Where is it again?",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("About that book... Where is it again?")
                npc(reldo, "I'm not sure exactly... but I'm sure it's around here somewhere.")
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo before reading the book". */
    private suspend fun PlayerScript.reldoFound(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (
            choice(
                "Select an option",
                "I found that book.",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("I found that book.")
                npc(reldo, "Very good. I suggest you give it a read.")
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo after reading the book". */
    private suspend fun PlayerScript.reldoRead(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (
            choice(
                "Select an option",
                "Okay, I've read that book about the Shield of Arrav.",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("Okay, I've read that book about the Shield of Arrav.")
                npc(reldo, "Then perhaps you now have your quest?")
                me("I think I do. Do you know where I can find the Phoenix Gang or the Black Arm Gang?")
                npc(
                    reldo,
                    "No, I don't. However, I hear Baraek, the fur trader in the market, has had connections " +
                        "with the Phoenix Gang.",
                )
                me("And the Black Arm Gang?")
                npc(
                    reldo,
                    "There are rumours that they are based in the south west corner of the city. Charlie the " +
                        "Tramp might be able to tell you more. He knows that part of Varrock well.",
                )
                me("Thanks! I'll get to it!")
                advance(player, KNOWN)
                npc(reldo, "Good luck.")
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo with half of the shield". */
    private suspend fun PlayerScript.reldoBlackArmShield(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (
            choice(
                "Select an option",
                "I've found half of the Shield of Arrav!",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("I've found half of the Shield of Arrav!")
                npc(reldo, "Half?")
                me("Yes. It seems it was split in two.")
                npc(reldo, "I see... Where did you find this half?")
                me("The Black Arm Gang had it. I managed to infiltrate their gang and steal it from them.")
                npc(reldo, "A split gang and a split shield. My guess would be that the other gang has the other half.")
                me(
                    "Well, I did have a member of the Phoenix Gang help me... Maybe they can find the other " +
                        "half.",
                )
                npc(
                    reldo,
                    "Let's hope so. In the meantime, you should probably head over to the museum so they can " +
                        "verify the authenticity of this half.",
                )
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Reldo with half of the shield@2". */
    private suspend fun PlayerScript.reldoPhoenixShield(reldo: NpcRef) {
        npc(reldo, "Hello there, $name.")
        when (
            choice(
                "Select an option",
                "I've found half of the Shield of Arrav!",
                "Do you have anything to trade?",
                "I'd better get going.",
            )
        ) {
            1 -> {
                me("I've found half of the Shield of Arrav!")
                npc(reldo, "Half?")
                me("Yes. It seems it was split in two.")
                npc(reldo, "I see... Where did you find this half?")
                me("The Phoenix Gang had it. I managed to infiltrate their gang and steal it from them.")
                npc(reldo, "A split gang and a split shield. My guess would be that the other gang has the other half.")
                me(
                    "Well, I don't think I'll have much luck infiltrating the Black Arm Gang now that I've " +
                        "joined their rivals, but maybe someone else will be able to.",
                )
                npc(
                    reldo,
                    "Let's hope so. In the meantime, you should probably head over to the museum so they can " +
                        "verify the authenticity of this half.",
                )
            }

            2 -> {
                me("Do you have anything to trade?")
            }

            3 -> {
                me("I'd better get going.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.searchBookcase() {
        val p = player
        // Wiki quest page: the book is only found once the quest is started; the quest list of cache items has it one at a time.
        if (stage(p) < STARTED || p.has(Objs.THE_SHIELD_OF_ARRAV) || (stage(p) >= BOOK && stage(p) > READ)) {
            return p.message("You search the bookcase but find nothing of interest.")
        }
        if (!p.give(Objs.THE_SHIELD_OF_ARRAV, 1, "shield of arrav book")) return p.message("You don't have enough inventory space.")
        advance(p, BOOK)
        itemBox(Objs.THE_SHIELD_OF_ARRAV, "You search the bookcase and find a book titled The Shield of Arrav.")
        me("Aha! Exactly what I was looking for.")
    }

    /** Wiki "Book (Shield of Arrav)": reading it makes the quest known to Reldo. */
    private suspend fun PlayerScript.readBook() {
        val p = player
        box("The Shield of Arrav\n\nby A. R. Wright")
        box(
            "Arrav is probably the best known hero of the 4th Age. Many legends are told of his heroics, most notably his role " +
                "in the defence of Avarrocka against the zombie hordes of the Mahjarrat Zemouregal.",
        )
        box(
            "One surviving artefact from the 4th Age is a fabulous shield. This shield once belonged to Arrav and is now indeed " +
                "known as the Shield of Arrav. For over 150 years, it was the prize piece in the Varrock Museum.",
        )
        box(
            "However, in the year 143 of the 5th Age, a group of thieves known as the Phoenix Gang broke into the museum and stole " +
                "the shield in a daring raid. As a result, Misthalin's ruler at the time, King Roald II, put a 1,200 gold bounty " +
                "(a massive sum of money in those days) on the return of the shield, hoping that one of the culprits would betray " +
                "their fellows out of greed. Alas, this tactic did not work.",
        )
        box(
            "Many years after the original theft of the shield, a rift resulted in a splinter group of the Phoenix Gang forming " +
                "their own criminal organisation, the Black Arm Gang.",
        )
        box(
            "Reports on which of the two gangs ended up in possession of the Shield of Arrav were inconsistent, so the location of " +
                "the shield is now sadly unknown. However, King Roald III has recently declared that the reward for its return " +
                "still stands.",
        )
        if (stage(p) == BOOK) advance(p, READ)
    }

    // ---- Phoenix Gang

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Baraek". */
    private suspend fun PlayerScript.baraekMenu(baraek: NpcRef) {
        when (
            choice(
                "Select an option",
                "Can you sell me some furs?",
                "Can you tell me where I can find the Phoenix Gang?",
                "Hello. I am in search of a quest.",
            )
        ) {
            1 -> {
                me("Can you sell me some furs?")
                player.events.publish(OpenShopRequest(player, Npcs.BARAEK))
            }

            2 -> {
                me("Can you tell me where I can find the Phoenix Gang?")
                npc(baraek, "Shh, shh, shh! Not so loud! You don't want to get me in trouble, do you?")
                me("So I take it that you do know where they are.")
                npc(
                    baraek,
                    "I may do... but I don't want to get into trouble for revealing their hideout. Of course, " +
                        "if I was, say 20 coins richer, I may happen to be more inclined to take that sort of " +
                        "risk...",
                )
                when (
                    choice(
                        "Select an Option",
                        "Okay. Have 20 gold coins.",
                        "No, I don't like things like bribery.",
                        "Yes. I'd like to be 20 coins richer too.",
                    )
                ) {
                    1 -> {
                        me("Okay. Have 20 gold coins.")
                        if (player.has(Objs.COINS, BARAEK_FEE)) {
                            player.take(Objs.COINS, BARAEK_FEE, "shield of arrav baraek")
                            join(player, PHOENIX, PHOENIX_PAID)
                            npc(
                                baraek,
                                "Okay, to get to the gang hideout, head to the southern gates of the city and then head " +
                                    "down the alleyway past the Blue Moon Inn.",
                            )
                            npc(
                                baraek,
                                "Down there you'll find a building owned by the VTAM corporation. That's where you'll find " +
                                    "the Phoenix Gang. Just be careful. They ain't the types to be messed about.",
                            )
                            me("Thanks!")
                            return
                        }
                        if (!player.has(Objs.COINS, BARAEK_FEE)) {
                            me("Uh... oops. I don't actually have 20 coins. Silly me.")
                        }
                    }

                    2 -> {
                        me("No, I don't do things like bribery.")
                        npc(
                            baraek,
                            "If you wanna deal with the Phoenix Gang, they're involved in much worse than a bit of " +
                                "bribery.",
                        )
                    }

                    3 -> {
                        me("Yes, I'd like to be 20 coins richer too.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            3 -> {
                me("Hello. I am in search of a quest.")
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.baraekTalk(baraek: NpcRef) {
        // OPEN-QUB9: the Wiki gives no greeting for Baraek; the menu is shown at once. A Black Arm member gets no information.
        if (gang(player) == BLACK_ARM) {
            me("Can you tell me where I can find the Phoenix Gang?")
            return npc(baraek, "Shh, shh, shh! Not so loud! You don't want to get me in trouble, do you?")
        }
        baraekMenu(baraek)
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Straven or attempting to open the door". */
    private suspend fun PlayerScript.stravenIntro(straven: NpcRef) {
        me("What's through that door?")
        npc(
            straven,
            "You can't go in there. Only authorised personnel of the VTAM Corporation are allowed " +
                "beyond this point.",
        )
        when (
            choice(
                "Select an option",
                "I know who you are!",
                "How do I get a job with the VTAM corporation?",
                "Fair enough.",
            )
        ) {
            1 -> {
                me("I know who you are!")
                npc(straven, "Really? Well? Who are we then?")
                me(
                    "This is the headquarters of one of the most dangerous crime syndicates this city has ever " +
                        "seen! The Phoenix Gang!",
                )
                npc(
                    straven,
                    "No, this is a legitimate business run by legitimate individuals. However... supposing we " +
                        "were this crime gang, what would you want with us?",
                )
                when (
                    choice(
                        "Select an option",
                        "I'd like to offer you my services.",
                        "I want nothing. I was just making sure you were them.",
                    )
                ) {
                    1 -> {
                        me("I'd like to offer you my services.")
                        npc(
                            straven,
                            "You mean you'd like to join the Phoenix Gang? Well, obviously I can't speak for them, but " +
                                "the Phoenix Gang doesn't let people join just like that. First, you'd need to prove your " +
                                "loyalty.",
                        )
                        me("How would I go about doing that?")
                        npc(
                            straven,
                            "Obviously, I would have no idea. Although, having said that, a rival gang of ours, er, " +
                                "theirs, called the Black Arm Gang is supposedly meeting a contact from Port Sarim today.",
                        )
                        advance(player, PHOENIX_PAID)
                        npc(
                            straven,
                            "Apparently the meeting is going to take place in the Blue Moon Inn, and supposedly the " +
                                "contact is none other than the notorious mass murderer turned pirate captain, Jonny the " +
                                "Beard.",
                        )
                        npc(
                            straven,
                            "Now, obviously I know nothing about the dealings of the Phoenix Gang, but I bet if " +
                                "somebody were to arrange an accident for Jonny and bring back his intelligence report, the " +
                                "gang would be very pleased.",
                        )
                        me("I see... I'm sure I can help sort something out for them. I'll get right on it.")
                        join(player, PHOENIX, PHOENIX_TASK)
                        player.setVarp(Varps.PHOENIXGANG, ARRAV_JONNY)
                    }

                    2 -> {
                        me("I want nothing. I was just making sure you were them.")
                        npc(straven, "Well then get lost and stop wasting my time.")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            2 -> {
                me("How do I get a job with the VTAM corporation?")
                npc(
                    straven,
                    "Get a copy of the Varrock Herald. If we have any positions right now, they'll be " +
                        "advertised in there.",
                )
            }

            3 -> {
                me("Fair enough.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Straven or opening the door after accepting his task". */
    private suspend fun PlayerScript.stravenTask(straven: NpcRef) {
        npc(straven, "How's your little mission going?")
        if (!player.has(Objs.INTELLIGENCE_REPORT) && !player.inv(Invs.BANK).let { it.total(Objs.INTELLIGENCE_REPORT) > 0L }) {
            npc(straven, "How's your little mission going?")
            me("I don't have it yet...")
            npc(
                straven,
                "Jonny the Beard is the person who has it. I imagine he will be in the Blue Moon Inn... Not " +
                    "that I'd know for sure, with me definitely not being a member of the Phoenix Gang and all.",
            )
            return
        }
        if (player.has(Objs.INTELLIGENCE_REPORT) || player.inv(Invs.BANK).total(Objs.INTELLIGENCE_REPORT) > 0L) {
            me("I have the intelligence report!")
            npc(straven, "Let's see it then.")
            if (!player.has(Objs.INTELLIGENCE_REPORT)) {
                me("Let me go get it from the bank.")
                npc(straven, "I'll wait here.")
                return
            }
            if (player.has(Objs.INTELLIGENCE_REPORT)) {
                itemBox(Objs.INTELLIGENCE_REPORT, "You show the report to Straven.")
                npc(
                    straven,
                    "Very good... Very good... Okay, I'm satisfied. Welcome to the Phoenix Gang! I'm Straven, " +
                        "one of the gang leaders.",
                )
                player.take(Objs.INTELLIGENCE_REPORT, 1, "shield of arrav report")
                join(player, PHOENIX, PHOENIX_JOINED)
                me("Nice to meet you. I'm $name.")
                npc(
                    straven,
                    "As a member of our gang, you now have access to our hideout and our weapons supply depot. " +
                        "The hideout is just through this door and the depot is two doors down from here. You'll " +
                        "need this key to get in.",
                )
                itemBox(Objs.PHOENIXKEY2, "Straven hands you a key.")
                player.giveOrDrop(Objs.PHOENIXKEY2, 1, "shield of arrav key")
                npc(straven, "Once again, welcome to the Phoenix Gang!")
                player.take(Objs.INTELLIGENCE_REPORT, 1, "shield of arrav report")
                join(player, PHOENIX, PHOENIX_JOINED)
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Straven after losing the weapon store key". */
    private suspend fun PlayerScript.stravenKey(straven: NpcRef) {
        npc(straven, "Greetings, fellow gang member.")
        me("I'm afraid I lost that key you gave me...")
        npc(
            straven,
            "You really need to be more careful. We don't want that key falling into the wrong hands. " +
                "Have this spare, but don't lose it this time!",
        )
        itemBox(Objs.PHOENIXKEY2, "Straven hands you a key.")
        player.giveOrDrop(Objs.PHOENIXKEY2, 1, "shield of arrav key")
    }

    private suspend fun PlayerScript.stravenTalk(straven: NpcRef) {
        val p = player
        val s = stage(p)
        when {
            gang(p) == BLACK_ARM -> {
                npc(straven, "Stay away from here! Black Arm dog!")
            }

            gang(p) == PHOENIX && s >= PHOENIX_JOINED -> {
                if (!p.has(Objs.PHOENIXKEY2) && s < HANDED) {
                    stravenKey(straven)
                } else {
                    stravenMember(straven)
                }
            }

            gang(p) == PHOENIX && s >= PHOENIX_TASK -> {
                stravenTask(straven)
            }

            else -> {
                me("What's through that door?")
                stravenIntro(straven)
            }
        }
    }

    /** Straven's menu of a Phoenix member (Wiki "Talking to Straven after joining the Phoenix Gang" and the post-quest text). */
    private suspend fun PlayerScript.stravenMember(straven: NpcRef) {
        npc(straven, "Greetings, fellow gang member.")
        while (true) {
            when (
                choice(
                    "Select an Option",
                    "I've heard you've got some cool treasures in this place.",
                    "Any suggestions for where I can go thieving?",
                    "Where's the Black Arm Gang hideout?",
                    "I'd better get going.",
                )
            ) {
                1 -> {
                    me("I've heard you've got some cool treasures in this place.")
                    npc(
                        straven,
                        "Oh yeah, we've all stolen some stuff in our time. Our candlesticks down here, for example, were quite a " +
                            "challenge to get out of the palace.",
                    )
                    me("And the Shield of Arrav?")
                    if (finished(player)) {
                        npc(straven, "Once one of our most prized possessions.")
                        me("Once?")
                        npc(
                            straven,
                            "It seems the Black Arm Gang weren't content with having one half. They recently stole the other " +
                                "half of the shield from us as well!",
                        )
                        me("Oh no! I'll be sure to keep an eye out for any of their members sneaking around.")
                    } else {
                        npc(straven, "Woah... that's a blast from the past! We stole that years and years ago!")
                        npc(
                            straven,
                            "Truth be told, we don't even have the whole shield anymore. A few years ago there was a big fight within " +
                                "the gang and the shield got broken in half as a result.",
                            "After the fight, some gang members split off and formed their own group. They took one half of the shield " +
                                "with them.",
                        )
                        me("This other group? The Black Arm Gang?")
                        npc(straven, "Indeed.")
                    }
                }

                2 -> {
                    me("Any suggestions for where I can go thieving?")
                    npc(straven, "You could always try the marketplace over in Ardougne. Lots of opportunity there!")
                }

                3 -> {
                    me("Where's the Black Arm Gang hideout?")
                    npc(
                        straven,
                        "Interested in spying on the enemy, are you? It will be tricky. Their security is pretty good. Not as good as " +
                            "ours obviously, but still good.",
                        "Still, if you really want to go there, it's down an alleyway near the southern gate. One of our contacts, a " +
                            "tramp going by the name of Charlie, keeps an eye on it. He may be able to help you.",
                    )
                    me("I see. Thanks for the help!")
                }

                else -> {
                    me("I'd better get going.")
                    return
                }
            }
        }
    }

    /** Straven's door and the depot door (Wiki "Talking to Straven or attempting to open the door"). */
    private suspend fun PlayerScript.phoenixDoor(door: LocRef) {
        val p = player
        if (gang(p) == PHOENIX && stage(p) >= PHOENIX_JOINED) return p.passDoor(door)
        val straven = p.npcsWithin(door.coord, STRAVEN_RADIUS).firstOrNull { it.type == Npcs.STRAVEN }
        if (straven == null) return p.message("The door is securely locked.")
        stravenTalk(straven)
    }

    private suspend fun PlayerScript.depotDoor(door: LocRef) {
        val p = player
        if (!p.has(Objs.PHOENIXKEY2)) return box("The door is securely locked.")
        p.passDoor(door)
    }

    private suspend fun PlayerScript.blackArmDoor(door: LocRef) {
        val p = player
        when {
            gang(p) == BLACK_ARM && stage(p) >= BLACK_JOINED -> p.passDoor(door)
            gang(p) == PHOENIX -> box("The door is securely locked.")
            else -> p.message("This door seems to be locked from the inside.")
        }
    }

    // ---- Black Arm Gang

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Charlie the Tramp". */
    private suspend fun PlayerScript.charlieIntro(charlie: NpcRef) {
        npc(charlie, "Spare some change, guv?")
        when (
            choice(
                "Select an Option",
                "Who are you?",
                "I'm looking for the Black Arm Gang.",
                "Sorry, I haven't got any.",
                "Go get a job!",
            )
        ) {
            1 -> {
                me("Who are you?")
            }

            2 -> {
                me("I'm looking for the Black Arm Gang.")
                if (!player.flag(CHARLIE_ASKED)) {
                    npc(
                        charlie,
                        "Funny you should mention that... Rumour has it that they have their headquarters right " +
                            "down this alleyway. Though if I were you, I'd be careful about messing with them...",
                    )
                    player.setFlag(CHARLIE_ASKED, true)
                }
                if (player.flag(CHARLIE_ASKED)) {
                    npc(
                        charlie,
                        "Like I told you, the rumour is that they have their headquarters right down this alleyway. " +
                            "Though if I were you, I'd be careful about messing with them...",
                    )
                }
                me("Do you think they would let me join?")
                npc(
                    charlie,
                    "You never know. You'll find a lady down there called Katrine. Speak to her. But whatever " +
                        "you do, don't upset her.",
                )
            }

            3 -> {
                me("Sorry, I haven't got any.")
            }

            4 -> {
                me("Go get a job!")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Charlie the Tramp after accepting Katrine's task". */
    private suspend fun PlayerScript.charlieTask(charlie: NpcRef) {
        npc(charlie, "Spare some change, guv?")
        when (
            choice(
                "Select an option",
                "Who are you?",
                "Any idea where the Phoenix Gang are based?",
            )
        ) {
            1 -> {
                me("Who are you?")
            }

            2 -> {
                me("Any idea where the Phoenix Gang are based?")
            }

            else -> {
                Unit
            }
        }
        if (!player.flag(CHARLIE_PAID)) {
            me("Any idea where the Phoenix Gang are based?")
            npc(
                charlie,
                "Sure, I know everything there is to know about this city. I'd be happy to tell you for ten " +
                    "coins.",
            )
            when (
                choice(
                    "Select an option",
                    "Okay, that sounds fair.",
                    "Ten coins? That's too much! I'll give you five.",
                    "Never mind. I'll find it myself.",
                )
            ) {
                1 -> {
                    if (!player.has(Objs.COINS, CHARLIE_FEE)) {
                        player.message("You don't have enough coins.")
                        return
                    }
                    me("Okay, that sounds fair.")
                    itemBox(Objs.COINS, "You give ten coins to Charlie.")
                    player.take(Objs.COINS, CHARLIE_FEE, "shield of arrav charlie")
                    player.setFlag(CHARLIE_PAID, true)
                    npc(
                        charlie,
                        "Perfect! Now listen closely. The Phoenix Gang can be found east of here. Take the southern " +
                            "alley past the Blue Moon Inn and look for a building owned by the VTAM corporation.",
                    )
                    npc(
                        charlie,
                        "Their main hideout is in that building, with their weapon stash being two buildings east " +
                            "of it. Good luck getting into either without being a member of their gang though.",
                    )
                    me("Without being a member of their gang? That might be problematic.")
                    npc(
                        charlie,
                        "Well, your best bet would probably be to find someone who isn't a member of the Black Arm " +
                            "Gang and get them to infiltrate the ranks of the Phoenix Gang for you.",
                    )
                    me("Someone? Like who?")
                    npc(
                        charlie,
                        "There's plenty of other adventurers about besides yourself. I'm sure if you asked one of " +
                            "them nicely they would help you.",
                    )
                    me("I see...")
                    return
                }

                2 -> {
                    if (!player.has(Objs.COINS, CHARLIE_HALF)) {
                        player.message("You don't have enough coins.")
                        return
                    }
                    me("Ten coins? That's too much! I'll give you five.")
                    npc(charlie, "I guess that'll have to do then.")
                    itemBox(Objs.COINS, "You give five coins to Charlie.")
                    chargeCharlie()
                    npc(charlie, "Great, thanks.")
                    me("So where can I find the Phoenix Gang then?")
                    npc(charlie, "If you pay me five more coins, I'll tell you.")
                    when (
                        choice(
                            "Select an option",
                            "That's not fair, but I guess I don't have a choice.",
                            "You thieving gutter-scum! You'd better watch your back!",
                        )
                    ) {
                        1 -> {
                            me("That's not fair, but I guess I don't have a choice.")
                            itemBox(Objs.COINS, "You give five coins to Charlie.")
                            chargeCharlie()
                            npc(
                                charlie,
                                "Perfect! Now listen closely. The Phoenix Gang can be found east of here. Take the southern " +
                                    "alley past the Blue Moon Inn and look for a building owned by the VTAM corporation.",
                            )
                            npc(
                                charlie,
                                "Their main hideout is in that building, with their weapon stash being two buildings east " +
                                    "of it. Good luck getting into either without being a member of their gang though.",
                            )
                            me("Without being a member of their gang? That might be problematic.")
                            npc(
                                charlie,
                                "Well, your best bet would probably be to find someone who isn't a member of the Black Arm " +
                                    "Gang and get them to infiltrate the ranks of the Phoenix Gang for you.",
                            )
                            me("Someone? Like who?")
                            npc(
                                charlie,
                                "There's plenty of other adventurers about besides yourself. I'm sure if you asked one of " +
                                    "them nicely they would help you.",
                            )
                            me("I see...")
                            return
                        }

                        2 -> {
                            me("You thieving gutter-scum! You'd better watch your back!")
                            npc(charlie, "Yeah, sure, whatever. You don't frighten me, pal.")
                            return
                        }

                        else -> {
                            Unit
                        }
                    }
                }

                3 -> {
                    me("Never mind. I'll find it myself.")
                    npc(charlie, "Well, you know where to find me if you get lost!")
                    return
                }

                else -> {
                    Unit
                }
            }
        }
        if (player.flag(CHARLIE_PAID)) {
            me("Any idea where the Phoenix Gang are based?")
            npc(
                charlie,
                "Well, given you paid up, I guess I can remind you. The Phoenix Gang can be found east of " +
                    "here. Take the southern alley past the Blue Moon Inn and look for a building owned by the " +
                    "VTAM corporation.",
            )
            npc(
                charlie,
                "Their main hideout is in that building, with their weapon stash being two buildings east " +
                    "of it. Good luck getting into either without being a member of their gang though.",
            )
            me("Without being a member of their gang? That might be problematic.")
            npc(
                charlie,
                "Well, your best bet would probably be to find someone who isn't a member of the Black Arm " +
                    "Gang and get them to infiltrate the ranks of the Phoenix Gang for you.",
            )
            me("Someone? Like who?")
            npc(
                charlie,
                "There's plenty of other adventurers about besides yourself. I'm sure if you asked one of " +
                    "them nicely they would help you.",
            )
            me("I see...")
            return
        }
        when (
            choice(
                "Select an Option",
                "Okay, here you go.",
                "Sorry, I haven't got any.",
            )
        ) {
            1 -> {
                me("Okay, here you go.")
            }

            2 -> {
                me("Sorry, I haven't got any.")
            }

            else -> {
                Unit
            }
        }
    }

    private fun PlayerScript.chargeCharlie() {
        val p = player
        p.take(Objs.COINS, CHARLIE_HALF, "shield of arrav charlie")
        // The second five coins complete the payment (Wiki "Talking to Charlie the Tramp after accepting Katrine's task").
        if (p.flag(CHARLIE_HALF_PAID)) p.setFlag(CHARLIE_PAID, true) else p.setFlag(CHARLIE_HALF_PAID, true)
    }

    /** Wiki "Transcript:Shield of Arrav", section "Learning the location of the Black Arm gang from Charlie the Tramp". */
    private suspend fun PlayerScript.charliePhoenix(charlie: NpcRef) {
        npc(charlie, "Spare some change, guv?")
        when (
            choice(
                "Select an Option",
                "Who are you?",
                "I'm looking for the Black Arm Gang.",
                "Go get a job!",
                "Sorry, I haven't got any.",
            )
        ) {
            1 -> {
                me("Who are you?")
            }

            2 -> {
                me("I'm looking for the Black Arm Gang.")
                npc(
                    charlie,
                    "Like I told you, the rumour is that they have their headquarters right down this alleyway. " +
                        "Though if I were you, I'd be careful about messing with them...",
                )
                me("Do you think they would let me join?")
                npc(charlie, "No. You're a collaborator with the Phoenix Gang. There's no way they'll let you join.")
                when (
                    choice(
                        "Select an option",
                        "How did you know I was in the Phoenix Gang?",
                        "Any ideas how I could get in there then?",
                    )
                ) {
                    1 -> {
                        me("How did you know I was in the Phoenix Gang?")
                        npc(
                            charlie,
                            "I've been known to have dealings with the Phoenix Gang. I wouldn't be very good at my job " +
                                "if I didn't know their members.",
                        )
                        me("I see. So, any ideas how I could get in there?")
                        npc(
                            charlie,
                            "Your best bet would probably be to find someone who isn't a member of the Phoenix Gang and " +
                                "get them to infiltrate the ranks of the Black Arm Gang for you.",
                        )
                        me("Someone? Like who?")
                        npc(
                            charlie,
                            "There's plenty of other adventurers about besides yourself. I'm sure if you asked one of " +
                                "them nicely they would help you.",
                        )
                        me("I see...")
                    }

                    2 -> {
                        me("Any ideas how I could get in there then?")
                        npc(
                            charlie,
                            "Your best bet would probably be to find someone who isn't a member of the Phoenix Gang and " +
                                "get them to infiltrate the ranks of the Black Arm Gang for you.",
                        )
                        me("Someone? Like who?")
                        npc(
                            charlie,
                            "There's plenty of other adventurers about besides yourself. I'm sure if you asked one of " +
                                "them nicely they would help you.",
                        )
                        me("I see...")
                    }

                    else -> {
                        Unit
                    }
                }
            }

            3 -> {
                me("Go get a job!")
            }

            4 -> {
                me("Sorry, I haven't got any.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Below Ice Mountain", section "Talking to Charlie the Tramp". */
    private suspend fun PlayerScript.charlieMarley(charlie: NpcRef) {
        npc(charlie, "Spare some change guv?")
        when (
            choice(
                "Select an Option",
                "Who are you?",
                "Sorry, I haven't got any.",
                "Go get a job!",
                "Ok. Here you go.",
                "Is there anything down this alleyway?",
                "I'm looking for a man named Marley. Have you seen him?",
            )
        ) {
            1 -> {
                me("Who are you?")
            }

            2 -> {
                me("Sorry, I haven't got any.")
            }

            3 -> {
                me("Go get a job!")
            }

            4 -> {
                me("Ok. Here you go.")
            }

            5 -> {
                me("Is there anything down this alleyway?")
            }

            6 -> {
                me("I'm looking for a man named Marley. Have you seen him?")
                npc(charlie, "Ah Marley, my cousin. He's always getting himself into trouble!")
                me("Oh really? Do you know where I could find him?")
                npc(
                    charlie,
                    "Hmm... it's been a while since I've seen him. Last I heard he got caught in the middle of " +
                        "a robbery and had to flee Varrock.",
                )
                npc(charlie, "Something to do with trying to steal a set of Wizard robes from the museum.")
                me("Great, so I'm dealing with a criminal!")
                npc(
                    charlie,
                    "Afraid so. Marley is no stranger to the authorities... Knowing him, he's probably hanging " +
                        "out in Edgeville ruins till the attention dies down. I'd try there.",
                )
                me("Perfect. I'll go look for him.")
                player.setFlag(BIM_CHARLIE, true)
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.charlieTalk(charlie: NpcRef) {
        val p = player
        // Below Ice Mountain: Charlie's cousin Marley (the menu of that quest's transcript replaces the Arrav menu until asked).
        if (QuestsB.BELOW_ICE_MOUNTAIN.stage(p) > 0 && !p.flag(BIM_CHARLIE)) return charlieMarley(charlie)
        when {
            gang(p) == PHOENIX && stage(p) >= PHOENIX_JOINED -> charliePhoenix(charlie)
            gang(p) == BLACK_ARM && stage(p) >= BLACK_TASK -> charlieTask(charlie)
            else -> charlieIntro(charlie)
        }
    }

    /** Wiki "Talking to Katrine": the long first conversation. */
    private suspend fun PlayerScript.katrineIntro(katrine: NpcRef) {
        me("What is this place?")
        npc(katrine, "It's a private business. Can I help you at all?")
        when (
            choice(
                "Select an Option",
                "I've heard you're the Black Arm Gang.",
                "What sort of business?",
                "I'm looking for fame and riches.",
            )
        ) {
            1 -> {
                me("I've heard you're the Black Arm Gang.")
                npc(katrine, "Who told you that?")
                when (
                    choice(
                        "Select an Option",
                        "I'd rather not reveal my sources.",
                        "It was Charlie, the tramp outside.",
                        "Everyone knows. It's no great secret.",
                    )
                ) {
                    1 -> {
                        me("I'd rather not reveal my sources.")
                        npc(
                            katrine,
                            "Hmm... I suppose I can understand that. So, say we were the Black Arm Gang, what would you want " +
                                "with us?",
                        )
                    }

                    2 -> {
                        me("It was Charlie, the tramp outside.")
                        npc(
                            katrine,
                            "Is that guy still out there? He's getting to be a nuisance. Might be time for him to have a " +
                                "little accident...",
                        )
                        npc(katrine, "Anyway, say we were the Black Arm Gang, what would you want with us?")
                    }

                    else -> {
                        me("Everyone knows. It's no great secret.")
                        npc(katrine, "What?! You must be joking!")
                        me("Oh no, not at all... It's so obvious! Even the town guard have caught on...")
                        npc(katrine, "I see... Well... say we were the Black Arm Gang, what would you want with us?")
                    }
                }
                when (
                    choice(
                        "Select an Option",
                        "I want to become a member of your gang.",
                        "I want some hints on becoming a thief.",
                        "I'm looking for the door out of here.",
                    )
                ) {
                    1 -> {
                        me("I want to become a member of your gang.")
                        katrineTest(katrine)
                    }

                    2 -> {
                        me("I want some hints on becoming a thief.")
                        npc(katrine, "Do you now? And what makes you think I'll provide anything like that?")
                        me("Well, how about I become a member of your gang in return?")
                        katrineTest(katrine)
                    }

                    else -> {
                        me("I'm looking for the door out of here.")
                        npc(katrine, "Try... the one you just came through?")
                    }
                }
            }

            2 -> {
                me("What sort of business?")
                npc(katrine, "A small, family business. We give financial advice to other companies. Now, if you'll excuse me.")
            }

            else -> {
                me("I'm looking for fame and riches.")
                npc(katrine, "And you expect to find that up the back streets of Varrock? Keep dreaming.")
            }
        }
    }

    private suspend fun PlayerScript.katrineTest(katrine: NpcRef) {
        npc(
            katrine,
            "How unusual... Okay, let's say we are who you believe we are. Do you think we'd be recruiting people who just waltz in " +
                "here saying 'hello, I'd like to play'?",
        )
        me("I don't know. What would be the alternative?")
        npc(
            katrine,
            "Watching local thugs and thieves in action, perhaps? How do I know I can just trust someone who's wandered in here.",
        )
        if (choice("Select an option", "Well, you can give me a try can't you?", "Well, people tell me I have an honest face.") == 1) {
            me("Well, you can give me a try can't you?")
            npc(katrine, "I'm not so sure...")
        } else {
            me("Well, people tell me I have an honest face.")
            npc(katrine, "Someone honest wanting to join a gang of thieves? Excuse me if I remain unconvinced...")
        }
        npc(
            katrine,
            "Thinking about it... I may have a solution actually. You may have heard of the Phoenix Gang. I hear they have a very " +
                "nice weapons stash not too far from here.",
            "We're fresh out of crossbows, but apparently they have plenty. If someone were to acquire a couple for us, it would be " +
                "very much appreciated.",
        )
        me("Sounds simple enough. Any particular reason you need them?")
        npc(
            katrine,
            "Let's just say there's a local merchant who is refusing to pay some very reasonable 'keep-your-life-pleasant' " +
                "insurance rates.",
        )
        me("So you're planning to murder him?")
        npc(
            katrine,
            "Him? No. However, I hear that the local law might soon be finding a crossbow belonging to the Phoenix Gang in " +
                "his home...",
        )
        npc(katrine, "I also hear that a similar type of crossbow might soon be used in a murder on the other side of the city...")
        npc(katrine, "Now, are you going to keep asking questions, or are you going to go get those crossbows?")
        var accepted = choice("Select an option", "Okay, no problem.", "Sounds a little tricky. Got anything easier?") == 1
        if (accepted) {
            me("Okay, no problem.")
        } else {
            me("Sounds a little tricky. Got anything easier?")
            npc(katrine, "If you're not up for a little bit of danger, I don't think you've got anything to offer us.")
            if (choice("Select an option", "Okay, I'll do it.", "It's a no from me then.") == 1) {
                me("Okay, I'll do it.")
                accepted = true
            } else {
                me("It's a no from me then.")
                return npc(katrine, "Then get lost!")
            }
        }
        if (accepted) {
            join(player, BLACK_ARM, BLACK_TASK)
            npc(
                katrine,
                "Great! Apparently the Phoenix Gang's weapons stash is somewhere to the east. Ask that tramp outside. I'm " +
                    "sure he'll know more.",
            )
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Katrine again after accepting her task". */
    private suspend fun PlayerScript.katrineTask(katrine: NpcRef) {
        npc(katrine, "Have you got those crossbows for me yet?")
        if (!player.has(Objs.PHOENIX_CROSSBOW)) {
            me("No, I haven't found them yet. Do you know where I should be looking.")
            npc(
                katrine,
                "Apparently the Phoenix Gang's weapons stash is somewhere to the east. Ask that tramp " +
                    "outside. I'm sure he'll know more.",
            )
            return
        }
        if (player.has(Objs.PHOENIX_CROSSBOW) && !player.has(Objs.PHOENIX_CROSSBOW, CROSSBOWS)) {
            me("I have one...")
            npc(katrine, "Well, I need two. Come back when you have them.")
            return
        }
        if (player.has(Objs.PHOENIX_CROSSBOW, CROSSBOWS)) {
            me("Yes, I have.")
            itemBox(Objs.PHOENIX_CROSSBOW, "You give the crossbows to Katrine.")
            player.take(Objs.PHOENIX_CROSSBOW, CROSSBOWS, "shield of arrav crossbows")
            join(player, BLACK_ARM, BLACK_JOINED)
            npc(
                katrine,
                "It seems you're more skilled than I originally gave you credit for. Well done. Consider " +
                    "yourself a member of the Black Arm Gang. I'd suggest you now go and familiarise yourself " +
                    "with the rest of the ganghouse.",
            )
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Katrine after joining the Black Arm Gang". */
    private suspend fun PlayerScript.katrineMember(katrine: NpcRef) {
        me("Hey.")
        npc(katrine, "Hey.")
        when (
            choice(
                "Select an Option",
                "So I'm a part of the gang now?",
                "Who are all those people in there?",
                "Teach me to be a top class criminal!",
            )
        ) {
            1 -> {
                me("So I'm part of the gang now?")
                npc(katrine, "You are. Feel free to familiarise yourself with the rest of the ganghouse.")
                me("Will do! Thanks!")
            }

            2 -> {
                me("Who are all those people in there?")
                npc(katrine, "They're just various rogues and thieves.")
                me("They don't say a lot...")
                npc(katrine, "Nope.")
            }

            3 -> {
                me("Teach me to be a top class criminal.")
                npc(katrine, "Teach yourself.")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Katrine as a member of the Black Arm Gang". */
    private suspend fun PlayerScript.katrineAfter(katrine: NpcRef) {
        me("Hey.")
        npc(katrine, "Hey.")
        when (
            choice(
                "Select an Option",
                "So I'm a part of the gang now?",
                "Who are all those people in there?",
                "Teach me to be a top class criminal!",
            )
        ) {
            1 -> {
                me("So I'm part of the gang now?")
                npc(
                    katrine,
                    "You are, but don't make me regret it. In case you didn't hear, someone recently stole " +
                        "something very important from us. We need to be more careful about who we let in.",
                )
                me("Imagine doing something like that!")
            }

            2 -> {
            }

            3 -> {
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.katrineTalk(katrine: NpcRef) {
        val p = player
        when {
            gang(p) == PHOENIX && stage(p) >= PHOENIX_JOINED -> {
                npc(
                    katrine,
                    "You've got some guts coming here, Phoenix scum! Get " +
                        "lost, or I'll make sure you haven't got those guts anymore!",
                )
            }

            gang(p) == BLACK_ARM && stage(p) >= BLACK_JOINED && finished(p) -> {
                katrineAfter(katrine)
            }

            gang(p) == BLACK_ARM && stage(p) >= BLACK_JOINED -> {
                katrineMember(katrine)
            }

            gang(p) == BLACK_ARM && stage(p) >= BLACK_TASK -> {
                katrineTask(katrine)
            }

            else -> {
                katrineIntro(katrine)
            }
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to the Weaponsmaster". */
    private suspend fun PlayerScript.weaponsmasterHostile(smith: NpcRef) {
        me("Hello.")
        npc(smith, "Hey! Who are you? I'm gonna teach you not to stick your nose where it don't belong!")
        smith.huntRange = SMITH_HUNT
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to the Weaponsmaster after joining the Phoenix Gang". */
    private suspend fun PlayerScript.weaponsmasterFriendly(smith: NpcRef) {
        me("Hello.")
        npc(smith, "Hello fellow Phoenix! What are you after?")
        when (
            choice(
                "Select an Option",
                "I'm after a weapon or two.",
                "I'm looking for treasure.",
            )
        ) {
            1 -> {
                me("I'm after a weapon or two.")
                npc(smith, "No problem. Feel free to look around.")
            }

            2 -> {
                me("I'm looking for treasure.")
                npc(
                    smith,
                    "Aren't we all? We've not got any up here. Go mug someone somewhere if you want some " +
                        "treasure.",
                )
            }

            else -> {
                Unit
            }
        }
    }

    private suspend fun PlayerScript.weaponsmasterTalk(smith: NpcRef) {
        if (gang(player) == PHOENIX && stage(player) >= PHOENIX_JOINED) weaponsmasterFriendly(smith) else weaponsmasterHostile(smith)
    }

    /** Wiki "Attempting to take a Phoenix crossbow": non-members are attacked; killing the Weaponsmaster clears the way. */
    private suspend fun PlayerScript.takeCrossbow(crossbow: tp.plugin.ObjRef) {
        val p = player
        val smith =
            p.npcsWithin(crossbow.coord, SMITH_RADIUS).firstOrNull {
                it.type == Npcs.WEAPONSMASTER ||
                    it.type == Npcs.WEAPONSMASTER_VIS
            }
        val guarded = smith != null && p.varbit(Varbits.SOA_WEAPONMASTER_DEAD) == 0
        if (guarded && gang(p) == PHOENIX && stage(p) >= PHOENIX_JOINED) {
            return npcAs(Npcs.WEAPONSMASTER_VIS, "Weaponsmaster", "Hey! That's Straven's! He won't like you messing with that!")
        }
        if (guarded) {
            npcAs(Npcs.WEAPONSMASTER_VIS, "Weaponsmaster", "Stop! Thief!")
            smith?.huntRange = SMITH_HUNT
            return
        }
        if (!p.transaction("take phoenix crossbow") { take(crossbow, Invs.INV) }) {
            p.message("You don't have enough inventory space to hold that item.")
        }
    }

    // ---- the shield halves, the curator, King Roald

    private fun hasHalf(p: PlayerRef): Boolean = p.has(Objs.ARRAVSHIELD1) || p.has(Objs.ARRAVSHIELD2)

    /** Wiki "Searching the Phoenix Gang's chest" / "Searching the Black Arm Gang's " +
     "cupboard": one half at a time, none after the quest. */
    private suspend fun PlayerScript.takeHalf(half: Int, gang: Int, opened: String, what: String) {
        val p = player
        if (gang(p) != gang || stage(p) < (if (gang == PHOENIX) PHOENIX_JOINED else BLACK_JOINED) || finished(p) || stage(p) >= HANDED) {
            return box("You search the $what but find nothing of interest.")
        }
        if (hasHalf(p)) return box("$opened, but don't find anything of interest inside.")
        if (p.give(half, 1, "shield of arrav half")) {
            itemBox(half, "$opened and find half of a shield inside.")
            if (gang == BLACK_ARM) {
                me(
                    "This must be the Shield of Arrav! Well... half of it at least. I wonder where the other half " +
                        "is...",
                )
            }
        } else {
            itemBox(half, "$opened and find half of a shield inside, but you don't have enough room to take it.")
        }
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to Curator Haig Halen". */
    private suspend fun PlayerScript.curatorHandIn(curator: NpcRef) {
        me("Hello there. I'm here about the Shield of Arrav.")
        npc(
            curator,
            "The Shield of Arrav? The Museum has been searching for that for years! The late King Roald " +
                "II offered a generous reward for its return!",
        )
        me("Well, I'm here to claim it.")
        npc(curator, "You've found the shield? Let's have a look!")
        itemBox(Objs.ARRAVSHIELD1, "You show the shield half to the curator.")
        npc(curator, "This is incredible! But where's the other half?")
        me(
            "I obtained this half from the [Phoenix Gang/Black Arm Gang]. The [Black Arm Gang/Phoenix " +
                "Gang] might have the other half.",
        )
        npc(
            curator,
            "That does sound plausible. We will have to hope that another adventurer can recover that " +
                "half.",
        )
        me("So will I be rewarded for recovering half of the shield?")
        npc(
            curator,
            "I'm afraid the reward is for the recovery of the full shield. However, what I'll do is " +
                "write you out two half- certificates. If another adventurer returns the other shield half, " +
                "I'll do the same for them.",
        )
        npc(
            curator,
            "The two of you can then swap one of the half- certificates to make a full one each. Take " +
                "that to the King and he will grant you your reward.",
        )
        itemBox(Objs.ARRAVCERTIFICATE_LFT, "The curator gives you two half-certificates.")
        giveHalves()
    }

    /** Wiki "Transcript:Shield of Arrav", section "Talking to the Curator again after giving him the shield half". */
    private suspend fun PlayerScript.curatorAgain(curator: NpcRef) {
        npc(curator, "Welcome to the museum of Varrock.")
        me("Hello there. I'm here about the Shield of Arrav.")
        npc(
            curator,
            "I gave you two half-certificates for the half you returned. If another adventurer returns " +
                "the other shield half, I'll do the same for them.",
        )
        npc(
            curator,
            "The two of you can then swap one of the half-certificates to make a full one each. Take " +
                "that to the King and he will grant you your reward.",
        )
        me("Would it be okay to get more half-certificates?")
        npc(curator, "I suppose so. It's not like you can do anything with them without the other half.")
        itemBox(Objs.ARRAVCERTIFICATE_LFT, "The curator gives you two half-certificates.")
        giveHalves()
    }

    /** Takes the shield half and gives two half certificates of the player's gang (Wiki "Talking to Curator Haig Halen"). */
    private fun PlayerScript.giveHalves() {
        val p = player
        if (stage(p) < HANDED) {
            val half = if (gang(p) == PHOENIX) Objs.ARRAVSHIELD1 else Objs.ARRAVSHIELD2
            p.take(half, 1, "shield of arrav half")
            advance(p, HANDED)
        }
        p.giveOrDrop(if (gang(p) == PHOENIX) Objs.ARRAVCERTIFICATE_LFT else Objs.ARRAVCERTIFICATE_RHT, HALVES, "shield of arrav halves")
    }

    private suspend fun PlayerScript.curatorTalk(curator: NpcRef) {
        val p = player
        val handed = stage(p) >= HANDED
        val bring = hasHalf(p) && gang(p) != 0
        when {
            finished(p) -> npc(curator, "Welcome to the museum of Varrock.")
            handed && gang(p) != 0 -> curatorAgain(curator)
            bring -> curatorHandIn(curator)
            else -> npc(curator, "Welcome to the museum of Varrock.")
        }
    }

    private suspend fun PlayerScript.combine() {
        val p = player
        if (choice("Combine the two half-certificates?", "Yes.", "No.") != 1) return
        val done =
            p.transaction("shield of arrav certificate") {
                remove(Invs.INV, Objs.ARRAVCERTIFICATE_LFT, 1) &&
                    remove(Invs.INV, Objs.ARRAVCERTIFICATE_RHT, 1) &&
                    add(Invs.INV, Objs.ARRAVCERTIFICATE, 1)
            }
        if (done) itemBox(Objs.ARRAVCERTIFICATE, "You combine the two halves to make a complete certificate.")
    }

    private suspend fun PlayerScript.inspect(obj: Int) {
        val text =
            if (obj == Objs.PHOENIXKEY2) {
                "It's the key to the Phoenix Gang's weapons store. If you're working with a player who's restricted from trading, " +
                    "you can still give them this item by using it on them."
            } else {
                "If you need to give this item to a player who's restricted from trading, you can give them the item by using it " +
                    "on them."
            }
        itemBox(obj, text)
    }

    private suspend fun PlayerScript.inspectHalf(half: Int) {
        val p = player
        if (p.has(Objs.ARRAVCERTIFICATE_LFT) && p.has(Objs.ARRAVCERTIFICATE_RHT)) return combine()
        itemBox(
            half,
            "It's half of a certificate for claiming a reward from King Roald. If you're working with a player who's restricted " +
                "from trading, you can still give them this item by using it on them.",
        )
    }

    /** Wiki "Using a tradeable quest item on another player": the item is dropped for the partner (account-restricted players). */
    private suspend fun PlayerScript.giveToPartner(target: PlayerRef) {
        val p = player
        val obj = used?.obj ?: return
        if (obj !in PARTNER_ITEMS || target == p) return
        val label =
            if (obj == Objs.PHOENIXKEY2) {
                "key"
            } else if (obj == Objs.PHOENIX_CROSSBOW) {
                "crossbow"
            } else {
                "certificate"
            }
        if (choice("Drop your $label for ${target.username} to take?", "Yes", "No") != 1) return
        val done =
            p.transaction("shield of arrav give") {
                dropAt(Invs.INV, used?.slot ?: return@transaction false, obj, 1, p.coord, target.username)
            }
        if (done) target.message("<col=ff0000>${p.username} has dropped something for you to take.</col>")
    }

    private suspend fun PlayerScript.roaldTalk(roald: NpcRef) {
        val p = player
        me("Greetings, your majesty.")
        npc(roald, "Yes, citizen. Do you need something?")
        if (finished(p)) return
        if (choice("Select an option", "I'm here about the Shield of Arrav.", "Not really.") != 1) return me("Not really.")
        me("I'm here about the Shield of Arrav.")
        if (p.has(Objs.ARRAVCERTIFICATE)) {
            itemBox(Objs.ARRAVCERTIFICATE, "You show the certificate to the king.")
            npc(
                roald,
                "My goodness! This is for the reward offered by my father many years ago! I never thought I would live to see the day " +
                    "when someone came forward to claim it!",
            )
            me("It was quite the undertaking. I wouldn't have been able to do it without help from a fellow adventurer.")
            npc(
                roald,
                "Well, in that case, it's only right that you both be rewarded. I will grant half of the reward to you and half to " +
                    "your companion. That means 600 coins for you. Enjoy it! You've very much earned it!",
            )
            p.take(Objs.ARRAVCERTIFICATE, 1, "shield of arrav certificate")
            QuestCompletion.complete(p, QuestsB.SHIELD_OF_ARRAV, REWARD)
        } else if (hasHalf(p)) {
            npc(roald, "The Shield of Arrav? Yes, I do recall my father put a reward out for that.")
            me("Well, I've managed to find half the shield!")
            npc(
                roald,
                "You have? Well, before we go any further, you'll need to get the authenticity of the shield verified by the " +
                    "curator at the museum.",
            )
            me("Okay. I'll head over there.")
        } else if (p.has(Objs.ARRAVCERTIFICATE_LFT) || p.has(Objs.ARRAVCERTIFICATE_RHT)) {
            itemBox(Objs.ARRAVCERTIFICATE_LFT, "You show the certificate half to the king.")
            npc(
                roald,
                "I'm afraid that's only half the reward certificate. You'll have to get the other half and join them together " +
                    "if you want to claim the reward.",
            )
        }
    }

    // ---- Jonny the Beard and the Weaponsmaster as multi-NPCs

    /**
     * The cache's multi-NPCs (Jonny the Beard 5213 on varp 145, the Weaponsmaster 5211 on varbit 11513) decide per player what is
     * seen; the spawn table places their visible variants, so those are turned into the parent types here.
     */
    private fun respawnAsMulti(npc: NpcRef) {
        when (npc.type) {
            Npcs.JONNY_THE_BEARD_2OP -> npc.transform(Npcs.JONNY_THE_BEARD, restoreStats = false)
            Npcs.WEAPONSMASTER_VIS -> npc.transform(Npcs.WEAPONSMASTER, restoreStats = false)
        }
    }

    /** Wiki quest page: Jonny drops the intel report when killed on Straven's task; the Weaponsmaster's death opens the depot. */
    private fun onDeath(e: DeathEvent) {
        val npc = e.entity as? NpcRef ?: return
        val killer = e.lootFor ?: return
        when (npc.type) {
            Npcs.JONNY_THE_BEARD, Npcs.JONNY_THE_BEARD_2OP -> {
                if (killer.varp(Varps.PHOENIXGANG) != ARRAV_JONNY) return
                killer.setVarp(Varps.PHOENIXGANG, ARRAV_JONNY_DEAD)
                killer.transaction("shield of arrav report") { createOnGround(Objs.INTELLIGENCE_REPORT, 1, npc.coord, killer.username) }
            }

            Npcs.WEAPONSMASTER, Npcs.WEAPONSMASTER_VIS -> {
                if (gang(killer) == BLACK_ARM) killer.setVarbit(Varbits.SOA_WEAPONMASTER_DEAD, 1)
            }
        }
    }

    /** Wiki "Changes" 6 November 2024: Jonny the Beard is only attackable when Straven has tasked the player. */
    private fun onAttackCheck(e: AttackCheck) {
        val player = e.attacker as? PlayerRef ?: return
        val npc = e.target as? NpcRef ?: return
        if ((npc.type == Npcs.JONNY_THE_BEARD || npc.type == Npcs.JONNY_THE_BEARD_2OP) && player.varp(Varps.PHOENIXGANG) != ARRAV_JONNY) {
            e.deny("You can't attack him right now.")
        }
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1

        /** Ground op 3 = "Take". */
        const val OP_TAKE = 3

        const val STAGE = "quests.soa.stage"
        const val GANG = "quests.soa.gang"
        const val RELDO_MET = "npc.reldo.met"
        const val CHARLIE_ASKED = "quests.soa.charlie_asked"
        const val CHARLIE_PAID = "quests.soa.charlie_paid"
        const val CHARLIE_HALF_PAID = "quests.soa.charlie_half"
        const val BIM_CHARLIE = "quests.bim.charlie"

        const val PHOENIX = 1
        const val BLACK_ARM = 2

        /** Progress stages (OPEN-QUB9, ours): the cache variable only has 0 / 1 / 2. */
        const val STARTED = 1
        const val BOOK = 2
        const val READ = 3
        const val KNOWN = 4
        const val PHOENIX_PAID = 10
        const val PHOENIX_TASK = 11
        const val PHOENIX_JOINED = 12
        const val BLACK_TASK = 21
        const val BLACK_JOINED = 22
        const val HANDED = 30

        /** Varp 145 (the old quest varp, read by the multi-NPC Jonny the Beard): 8 attackable, 9 and up gone (cache npc 5213). */
        const val ARRAV_JONNY = 8
        const val ARRAV_JONNY_DEAD = 9

        /** Wiki quest page: recommended combat level 10 (Reldo's warning box). */
        const val RECOMMENDED_COMBAT = 10

        /** Wiki "Talking to Baraek": 20 coins. */
        const val BARAEK_FEE = 20

        /** Wiki "Talking to Charlie the Tramp after accepting Katrine's task": ten coins, or five and five. */
        const val CHARLIE_FEE = 10
        const val CHARLIE_HALF = 5

        /** Wiki "Talking to Katrine again": two crossbows. */
        const val CROSSBOWS = 2

        /** Wiki "Talking to Curator Haig Halen": two half certificates per hand-in. */
        const val HALVES = 2

        /** OPEN-QUB9: the Weaponsmaster starts attacking from this range (the NPC hunt) and the crossbows lie within this radius of him. */
        const val SMITH_HUNT = 6
        const val SMITH_RADIUS = 8

        /** Radius around Straven's door in which Straven is looked for. */
        const val STRAVEN_RADIUS = 10

        val PARTNER_ITEMS = setOf(Objs.PHOENIXKEY2, Objs.PHOENIX_CROSSBOW, Objs.ARRAVCERTIFICATE_LFT, Objs.ARRAVCERTIFICATE_RHT)

        /** Wiki "Shield of Arrav" rewards: 1 quest point (cache), 600 coins; the antique lamp has no obj in cache 2757 (OPEN-QUB9). */
        val REWARD = QuestReward(model = Objs.ARRAVCERTIFICATE, items = listOf(Objs.COINS to 600))
    }
}
