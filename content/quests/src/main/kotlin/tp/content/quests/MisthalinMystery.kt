@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod", "NestedBlockDepth", "TooManyFunctions", "UnusedParameter")
// UnusedParameter: generated dialogue signatures.
// The long functions are Wiki transcripts: straight-line dialogue.

package tp.content.quests

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LocRef
import tp.plugin.NpcRef
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
 * Misthalin Mystery (docs/mechanics/quest-misthalin-mystery.md), first part: Abigale and Hewey, the rowboat, the bucket, Sid's
 * murder, the barrel with the manor key and the knife. The quest variable is varbit 3468; 0 and 135 are the cache's values, and
 * the multi-NPCs fix more (Abigale and Hewey are gone above 15, Sid above 19, Mandy's visible forms switch at 89, 130). Ours
 * (OPEN-QU1): 10 started, 16 on the island, 20 Sid dead, 30 manor key found, 130 the killers are beaten, 135 finished. The riddles
 * that follow (Tayten's note, the painting, the fuse, Lacey, the piano, Mandy's kitchen, the gem switches) and the mirror showdown
 * need cutscenes, the piano and gem interfaces and a boss room and are OPEN-QUB16: the quest cannot be completed until they exist.
 * Dialogue is the Wiki "Transcript:Misthalin Mystery".
 */
public class MisthalinMystery : ContentPlugin {
    override fun ContentScope.register() {
        for (npc in listOf(Npcs.MISTMYST_ABIGALE, Npcs.MISTMYST_HEWEY, Npcs.MISTMYST_ABIGALE_LUM_VIS, Npcs.MISTMYST_HEWEY_LUM_VIS)) {
            onOpNpc(npc, OP_TALK) { who ->
                player.stopInteraction()
                abigaleTalk(who)
            }
        }
        for (mandy in listOf(Npcs.MISTMYST_MANDY_POST, Npcs.MISTMYST_MANDY_POST_VIS)) {
            onOpNpc(mandy, OP_TALK) { who ->
                player.stopInteraction()
                mandyTalk(who)
            }
        }
        onOpLoc(Locs.MISTMYST_BOAT_LUMBRIDGE, OP_FIRST) {
            player.stopInteraction()
            row(ISLAND_LANDING, "You row through the fog to the nearby island.")
        }
        onOpLoc(Locs.MISTMYST_BOAT_ISLAND, OP_FIRST) {
            player.stopInteraction()
            row(SWAMP_LANDING, "You row through the fog back to Lumbridge Swamp.")
        }
        onOpLoc(Locs.MISTMYST_EMPTY_BUCKET, OP_FIRST) {
            player.stopInteraction()
            takeBucket()
        }
        onOpLoc(Locs.MISTMYST_BARREL_WATER, OP_FIRST) {
            player.stopInteraction()
            searchBarrel(drained = false)
        }
        onOpLoc(Locs.MISTMYST_BARREL_EMPTIED, OP_FIRST) {
            player.stopInteraction()
            searchBarrel(drained = true)
        }
        onUseOnLoc(Objs.BUCKET_EMPTY, Locs.MISTMYST_BARREL_WATER) {
            player.stopInteraction()
            fillBucket()
        }
        for (door in listOf(Locs.MISTMYST_FRONT_DOORL, Locs.MISTMYST_FRONT_DOORR)) {
            onOpLoc(door, OP_FIRST) { loc ->
                player.stopInteraction()
                frontDoor(loc)
            }
        }
        onOpLoc(Locs.MISTMYST_TABLE_KNIFE, OP_FIRST) {
            player.stopInteraction()
            takeKnife()
        }
        onOpLoc(Locs.MISTMYST_SHELVES_TINDERBOX, OP_FIRST) {
            player.stopInteraction()
            takeTinderbox()
        }
        events.on<JournalRequest> {
            if (it.quest == QuestsB.MISTHALIN_MYSTERY) it.lines += journal(it.player)
        }
    }

    private fun stage(p: PlayerRef): Int = QuestsB.MISTHALIN_MYSTERY.stage(p)

    private fun journal(p: PlayerRef): List<String> =
        when {
            stage(p) == 0 -> listOf("I can start this quest by speaking to Abigale or Hewey in the south-east corner of Lumbridge Swamp.")

            stage(
                p,
            ) < ARRIVED -> listOf("Abigale and Hewey were attacked at a house party on an island. I should take their rowboat there.")

            stage(p) < SID_DEAD -> listOf("I am on the island with the manor. I should look around.")

            stage(p) < KEY -> listOf("Sid was murdered. Something shiny fell into the barrel of rainwater.")

            stage(p) < END -> listOf("I found the manor key. The killer is still inside.")

            else -> listOf("I unmasked the killers of the manor and Mandy rewarded me.")
        }

    private suspend fun PlayerScript.abigaleTalk(who: NpcRef) {
        val p = player
        if (stage(p) >= ARRIVED) return npc(who, "Please, you need to make sure they don't get away with this!")
        if (stage(p) == 0) mysteryStart(who) else mysteryAgain(who)
    }

    /** Wiki "Transcript:Misthalin Mystery", section "Talking to Abigale or Hewey". */
    private suspend fun PlayerScript.mysteryStart(abigale: NpcRef) {
        npc(abigale, "Help! Help!")
        when (
            choice(
                "Start the Misthalin Mystery quest?",
                "Yes.",
                "No.",
            )
        ) {
            1 -> {
                QuestsB.MISTHALIN_MYSTERY.advance(player, STARTED)
                me("What has happened here?")
                npc(
                    abigale,
                    "We were invited to a house party on an island not far from here... Something felt wrong " +
                        "about the whole thing but we went anyway...",
                )
                npc(
                    abigale,
                    "The house seemed pretty creepy, but everything was going fine, we were all having a good " +
                        "time...",
                )
                npc(abigale, "...and that's when we got attacked...")
                npcAs(Npcs.MISTMYST_HEWEY_LUM_VIS, "Hewey", "I... I tried to save her...")
                npc(
                    abigale,
                    "Hewey bought me enough time to find an old boat. I went back to get him but it was too " +
                        "late. I'm scared that he won't make it.",
                )
                npc(
                    abigale,
                    "You have to do something... they can't get away with this! <!--got interrupted around this " +
                        "point and it started from this point when talking to Abigale again-->",
                )
                me("What do you want me to do?")
                npc(abigale, "That psycho is still out there, you have to bring them to justice.")
                npc(abigale, "The boat we escaped on is just over there, you can use it to get to the island.")
                me("Okay, I'll see what I can do.")
            }

            2 -> {
                me("I'm not getting involved in this!")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Transcript:Misthalin Mystery", section "Talking to Abigale or Hewey again". */
    private suspend fun PlayerScript.mysteryAgain(abigale: NpcRef) {
        npc(
            abigale,
            "Please, you need to make sure they don't get away with this! You can use the boat over " +
                "there to get to the island we escaped from.",
        )
    }

    private fun PlayerScript.row(to: CoordGrid, text: String) {
        val p = player
        if (stage(p) < STARTED) return p.message("I should probably talk to whoever left this boat here before rowing off in it...")
        // OPEN-QUB16: the fog row is a plain move.
        p.message(text)
        p.teleport(to)
        if (to == ISLAND_LANDING) QuestsB.MISTHALIN_MYSTERY.advance(p, ARRIVED)
    }

    private fun PlayerScript.takeBucket() {
        val p = player
        when {
            p.has(Objs.BUCKET_EMPTY) || p.has(Objs.BUCKET_WATER) -> {
                p.message("You already have a bucket.")
            }

            p.inv(Invs.INV).freeSlots == 0 -> {
                p.message("You need space in your inventory to take the bucket.")
            }

            p.give(Objs.BUCKET_EMPTY, 1, "misthalin mystery bucket") -> {
                p.startScript {
                    // The murder of Sid starts when the player walks on north; here it follows the bucket (OPEN-QUB16).
                    if (stage(p) in ARRIVED until SID_DEAD) sidDemise()
                }
            }
        }
    }

    /** Wiki "Transcript:Misthalin Mystery", section "Sid's demise". */
    private suspend fun PlayerScript.sidDemise(unused: Int = 0) {
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "Woo, party on bro!")
        // cutscene (OPEN-QUB16)
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "Woah, that wind totally just felt like someone breathing on my neck!")
        // cutscene (OPEN-QUB16)
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "Whoah, not cool bro...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...urgh...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...I'm feeling woozy...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...gargh...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...I see the light...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...urrggghhh...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...it's not my time...glurgh")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...gargle...urgh...")
        npcAs(Npcs.MISTMYST_KILLER_BACKGROUND_VIS, "Killer", "...")
        me("...")
        // cutscene (OPEN-QUB16)
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...glargh...help...me...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...urgh...hergle...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...flergh.")
        box(
            "Sid collapses to the ground in a crumpled mess. As he falls down you spot something shiny " +
                "fall out of his shirt pocket and into the barrel of water.",
        )
        me("Gosh, that was horrible and so drawn out!")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...urgh...")
        me("Oh wow, he's still going...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...")
        npcAs(Npcs.MISTMYST_SID_VIS, "Sid", "...")
        me("I think he is actually dead this time.")
        me("Who are you? Why are you doing this!?")
        npcAs(Npcs.MISTMYST_KILLER_BACKGROUND_VIS, "Killer", "...")
        // cutscene (OPEN-QUB16)
        me("Gah! I've got to find a way into that house and stop this maniac!")
        me("I wonder what it was that I saw fall into that barrel?")
        QuestsB.MISTHALIN_MYSTERY.advance(player, SID_DEAD)
    }

    private suspend fun PlayerScript.fillBucket() {
        val p = player
        if (stage(p) < SID_DEAD) return me("The barrel is full of rainwater, I can't see what's in there.")
        if (!p.transaction(
                "misthalin mystery water",
            ) { remove(Invs.INV, Objs.BUCKET_EMPTY, 1) && add(Invs.INV, Objs.BUCKET_WATER, 1) }
        ) {
            return
        }
        itemBox(Objs.BUCKET_WATER, "You fill the bucket from the barrel.")
    }

    private suspend fun PlayerScript.searchBarrel(drained: Boolean) {
        val p = player
        if (!drained) return me("The barrel is full of rainwater, I can't see what's in there.")
        if (p.has(Objs.MISTMYST_FRONTDOOR_KEY)) return p.message("You have already found the manor key.")
        if (stage(p) < SID_DEAD) return
        if (p.inv(Invs.INV).freeSlots == 0) return box("You don't have enough space in your inventory.")
        p.give(Objs.MISTMYST_FRONTDOOR_KEY, 1, "misthalin mystery key")
        QuestsB.MISTHALIN_MYSTERY.advance(p, KEY)
        itemBox(Objs.MISTMYST_FRONTDOOR_KEY, "You find a key at the bottom of the barrel.")
    }

    private suspend fun PlayerScript.frontDoor(door: LocRef) {
        val p = player
        if (!p.has(Objs.MISTMYST_FRONTDOOR_KEY)) {
            box("The door is securely locked.")
            return me("I'll have to find the key...")
        }
        p.message("You use the manor key to unlock the door.")
        p.passDoor(door)
    }

    private fun PlayerScript.takeKnife() {
        val p = player
        when {
            p.has(Objs.KNIFE) -> p.message("You already have a knife.")
            p.inv(Invs.INV).freeSlots == 0 -> p.message("You need space in your inventory to take the knife.")
            p.give(Objs.KNIFE, 1, "misthalin mystery knife") -> p.message("You take the knife from the table.")
        }
    }

    private fun PlayerScript.takeTinderbox() {
        val p = player
        when {
            p.has(Objs.TINDERBOX) -> p.message("You already have a tinderbox.")
            p.inv(Invs.INV).freeSlots == 0 -> p.message("You need space in your inventory to take the tinderbox.")
            p.give(Objs.TINDERBOX, 1, "misthalin mystery tinderbox") -> p.message("You take a tinderbox from the shelves.")
        }
    }

    /** Wiki "Transcript:Misthalin Mystery", section "Finishing up". */
    private suspend fun PlayerScript.mandyFinish(mandy: NpcRef) {
        npc(mandy, "Oh hi, $name.")
        me("Hi Mandy. How are you feeling now?")
        npc(mandy, "Much better thanks, and lucky to be alive, thanks to you in no small part.")
        me("Well we saved the day as a team, so thank you too.")
        if (player.inv(Invs.INV).freeSlots < REWARD_SLOTS) {
            npc(
                mandy,
                "I found some stuff in the wardrobe when I was seeing to my wound. You need [3 more " +
                    "spaces/2 more spaces/another space] in your inventory to take it all.",
            )
            return
        }
        if (player.inv(Invs.INV).freeSlots >= REWARD_SLOTS) {
            npc(
                mandy,
                "Here, I found these in the wardrobe when I was seeing to my wound. I don't want them so " +
                    "please, take them...",
            )
            // {{Qact|Congratulations! Quest complete!}}
        }
    }

    private suspend fun PlayerScript.mandyTalk(mandy: NpcRef) {
        if (stage(player) in SHOWDOWN_WON until END) mandyFinish(mandy) else npc(mandy, "Thank you for your help.")
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_FIRST = 1

        /** Quest variable stages (OPEN-QU1): 0 and 135 are the cache's; the multi-NPCs fix 15 / 19 / 89 / 129. */
        const val STARTED = 10
        const val ARRIVED = 16
        const val SID_DEAD = 20
        const val KEY = 30
        const val SHOWDOWN_WON = 130
        const val END = 135

        /** Wiki "Finishing up": the three uncut gems need three empty slots. */
        const val REWARD_SLOTS = 3

        /**
         * OPEN-QUB16: the rowboat landings. The boats are the locs at 3238,3139 (swamp) and 1638,4800 (island); the landings are the
         * NPC spawn tiles of the Wiki start (3236,3155) and of the post-quest Mandy outside the manor (1636,4817).
         */
        val SWAMP_LANDING = CoordGrid(3236, 3155, 0)
        val ISLAND_LANDING = CoordGrid(1636, 4817, 0)

        /** Wiki "Misthalin Mystery" rewards: 1 quest point (cache), uncut ruby, emerald and sapphire, 600 Crafting experience. */
        val REWARD =
            QuestReward(
                model = Objs.MISTMYST_FRONTDOOR_KEY,
                xp = listOf(Stats.CRAFTING to 600.0),
                items = listOf(Objs.UNCUT_RUBY to 1, Objs.UNCUT_EMERALD to 1, Objs.UNCUT_SAPPHIRE to 1),
            )
    }
}
