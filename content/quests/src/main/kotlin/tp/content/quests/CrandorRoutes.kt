@file:Suppress("LongMethod", "ReturnCount", "CyclomaticComplexMethod")
// The long function is a Wiki transcript: straight-line dialogue.

package tp.content.quests

import tp.plugin.AttackCheck
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.LethalHitEvent
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * The side routes of Dragon Slayer I (docs/mechanics/quest-dragon-slayer-i.md DS9-DS11): Lozar's map piece by beating Wormbrain up
 * and Telekinetic Grab, the sawmill operator's nine planks, and the secret wall between the Crandor and Karamja dungeons. Sources:
 * OSRS Wiki "Dragon Slayer I", "Transcript:Dragon Slayer I" (sections "Defeating Wormbrain", "Interacting with the sawmill operator
 * after buying the Lady Lumbridge"), "Transcript:Sawmill operator", "Wormbrain", "Wall (Crandor and Karamja Dungeon)".
 */
public class CrandorRoutes : ContentPlugin {
    override fun ContentScope.register() {
        // The Wiki: "If you didn't ask the Guildmaster about Lozar's map piece, you will not be able to attack Wormbrain."
        events.on<AttackCheck> { check ->
            val target = check.target as? NpcRef ?: return@on
            val p = check.attacker as? PlayerRef ?: return@on
            if (target.type != Npcs.WORMBRAIN) return@on
            when {
                p.has(Objs.MAPPART2) || p.has(Objs.DRAGONMAP) -> {
                    check.deny("You have already taken Wormbrain's map piece. There is no use in beating him up further.")
                }

                (p[BEATEN_UNTIL] ?: 0L) > p.tick -> {
                    check.deny("You have just beaten Wormbrain up. Give the poor goblin a break.")
                }

                // OPEN-QUC3: the Wiki gives no message for this refusal.
                !p.flag(ASKED_LOZAR) -> {
                    check.deny("There is no reason to attack Wormbrain.")
                }
            }
        }
        // Wiki "Wormbrain": at 0 hitpoints he says "Ow!" and drops the map part, which Telekinetic Grab must pick up.
        events.on<LethalHitEvent> { e ->
            if (e.npc.type != Npcs.WORMBRAIN) return@on
            e.surviveWith = e.npc.maxHitpoints
            val p = e.source as? PlayerRef ?: return@on
            if (!p.flag(ASKED_LOZAR) || p.has(Objs.MAPPART2) || p.has(Objs.DRAGONMAP)) return@on
            e.npc.say("Ow!")
            p.transaction("dragon slayer wormbrain map piece") { createOnGround(Objs.MAPPART2, 1, e.npc.coord, p.username) }
            p.message("Wormbrain drops a map piece on the floor.")
            p[BEATEN_UNTIL] = p.tick + BEATEN_TICKS
        }
        onOpNpc(Npcs.POH_SAWMILL_OPP, OP_TALK) { operator ->
            player.stopInteraction()
            sawmillTalk(operator)
        }
        onOpLoc(Locs.DRAGONSECRETDOOR, OP_OPEN) { wall ->
            player.stopInteraction()
            val p = player
            val found = p.varbit(Varbits.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR) != 0
            if (!found && QuestsB.DRAGON_SLAYER.stage(p) < CRANDOR) {
                // OPEN-QUC3: the Wiki gives no refusal message; the wall's examine text is "There is something strange about this wall."
                p.message("Nothing interesting happens.")
                return@onOpLoc
            }
            p.setVarbit(Varbits.DRAGONSLAYER_CRANDOR_FOUND_SECRET_DOOR, 1)
            p.stepAcross(wall)
        }
    }

    private suspend fun PlayerScript.sawmillTalk(operator: NpcRef) {
        val p = player
        val stage = QuestsB.DRAGON_SLAYER.stage(p)
        if (stage in SHIP until SLAIN) return sawmillQuest(operator)
        // Wiki "Transcript:Sawmill operator", standard dialogue (the Plank-making screen is the "Buy-plank" option, CO1).
        npc(
            operator,
            "Hello there. Do you want me to make some planks for you? I can make planks from wood, oak, teak and mahogany logs. " +
                "Or would you like to buy some other housing supplies?",
        )
        when (choice("Select an option", "Yes, please make me some planks.", "Can I buy some housing supplies?", "I'm good, thanks.")) {
            1 -> {
                me("Yes, please make me some planks.")
                p.interact(operator, OP_BUY_PLANK)
            }

            2 -> {
                me("Can I buy some housing supplies?")
                npc(operator, "Of course!")
                p.events.publish(OpenShopRequest(p, Npcs.POH_SAWMILL_OPP))
            }

            3 -> {
                me("I'm good, thanks.")
                npc(operator, "Well come back when you want some. You'll struggle to find quality planks anywhere but here!")
            }

            else -> {
                Unit
            }
        }
    }

    /** Wiki "Interacting with the sawmill operator after buying the Lady Lumbridge". */
    private suspend fun PlayerScript.sawmillQuest(operator: NpcRef) {
        val p = player
        npc(operator, "Hello there. Are you in need of planks or construction supplies?")
        me("As it so happens, I do need some wooden planks!")
        npc(
            operator,
            "Fortunately for you, you've come to the right place!",
            "Less fortunately for you, I'm on a legally mandated break right now.",
        )
        me("Could you make an exception? I need the planks to go and fight a dragon.")
        npc(operator, "A dragon? How are planks going to help you with that?")
        me("I'm repairing a boat to sail to Crandor so that I can slay Elvarg.")
        npc(operator, "Crandor? Elvarg? Well, far be it from me to stop you trying.")
        me("So you'll make some planks for me?")
        npc(
            operator,
            "I will, but I can't make you too many. It'll be just my luck if King Roald decides to audit this place and I've been making " +
                "planks off the clock.",
        )
        me("What do you need from me to make some planks?")
        val made = (p.saved(PLANKS_MADE) ?: 0L).toInt()
        if (made >= PLANK_LIMIT) {
            npc(
                operator,
                "Unfortunately I can't make any more planks for you without drawing attention to myself. You're going to have to source " +
                    "them elsewhere.",
            )
            return
        }
        npc(
            operator,
            "You'll have to provide the logs and 100 coins for each plank. Just basic logs will do for what you need.",
            "As a reminder, I can't make you very many without risking an audit, so I'll have to stop at nine planks.",
        )
        val logs = p.inv(Invs.INV).total(Objs.LOGS).toInt()
        val coins = p.inv(Invs.INV).total(Objs.COINS)
        if (logs == 0) return npc(operator, "You'll need to bring me some more logs.")
        if (coins < PLANK_FEE) return npc(operator, "Those planks cost $PLANK_FEE coins. You don't have enough money for all of them.")
        // OPEN-QUC3: the transcript ends after these checks; the planks are made at once, as many as logs, coins and the limit allow.
        val count = minOf(logs, (coins / PLANK_FEE).toInt(), PLANK_LIMIT - made)
        val ok =
            p.transaction("dragon slayer sawmill") {
                remove(Invs.INV, Objs.COINS, count * PLANK_FEE) && remove(Invs.INV, Objs.LOGS, count) &&
                    add(Invs.INV, Objs.WOODPLANK, count)
            }
        if (!ok) return p.message("You don't have enough inventory space.")
        p.setSaved(PLANKS_MADE, (made + count).toLong())
        p.message("The sawmill operator makes you $count plank${if (count == 1) "" else "s"}.")
    }

    private companion object {
        const val OP_TALK = 1
        const val OP_OPEN = 1

        /** Op 3 "Buy-plank" of the sawmill operator (Construction). */
        const val OP_BUY_PLANK = 3

        /** Dragon Slayer I stages (DragonSlayer): 4 the ship is bought, 7 on Crandor, 8 Elvarg slain. */
        const val SHIP = 4
        const val CRANDOR = 7
        const val SLAIN = 8

        /** Account flags of DragonSlayer: the Guildmaster told of Lozar's piece. */
        const val ASKED_LOZAR = "quests.ds1.asked_lozar"
        const val PLANKS_MADE = "quests.ds1.planks_made"

        /** Wiki: the operator makes at most nine planks for the quest, 100 coins and one normal log each. */
        const val PLANK_LIMIT = 9
        const val PLANK_FEE = 100

        /** OPEN-QUC3: how long Wormbrain "has just been beaten up" (ticks); the Wiki gives no time. */
        const val BEATEN_TICKS = 100L
        val BEATEN_UNTIL = AttrKey<Long>("quests.ds1.wormbrain_beaten")
    }
}
