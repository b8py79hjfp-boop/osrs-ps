@file:Suppress("MagicNumber") // data table: Wiki "Servant" (level, wage, capacity, trip time)

package tp.content.skills

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.Expression
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Npcs
import tp.symbols.Objs

/**
 * A servant (Wiki "Servant", Types of servants): hired at [level] Construction for [wage] coins, which is due after every eight
 * services; carries [capacity] items per trip, a trip takes [trip] ticks; the cook and better also take logs to the [sawmill].
 */
internal class ServantType(
    val name: String,
    val npc: Int,
    val level: Int,
    val wage: Int,
    val capacity: Int,
    val trip: Int,
    val sawmill: Boolean,
)

/** What the servant is doing for the player: fetch [obj] from the bank, or turn logs [obj] into planks, [amount] of them. */
internal class ServantTask(val sawmill: Boolean, val obj: Int, val amount: Int) {
    var ready = false
}

/**
 * Servants in the player-owned house (docs/mechanics/construction.md SV1-SV5): hire one at the Servants' Guild in East Ardougne,
 * ask it to fetch building materials from the bank or to take logs to the sawmill, pay its wage after eight services, dismiss it
 * at home or at the chief servant. A request is only recorded: the bank and backpack change in one item transaction when the
 * servant is back and spoken to, so nothing can be lost while it is away.
 */
public class PohServants : ContentPlugin {
    override fun ContentScope.register() {
        for (type in TYPES) onOpNpc(type.npc, OP_TALK) { talk(it, type) }
        onOpNpc(Npcs.POH_CHIEF_SERVANT, OP_TALK) { chief(it) }
    }

    // ---- hiring and dismissing

    private suspend fun PlayerScript.talk(npc: NpcRef, type: ServantType) {
        val p = player
        val house = p[House.CURRENT]
        if (house == null || house.servant?.index != npc.index) return hire(npc, type)
        if (house.owner != p.username.lowercase()) return chatNpc(npc, "I only work for my master.", Expression.NEUTRAL)
        servicesMenu(npc, house, type)
    }

    /** SV1: the guild's servants offer themselves; the player needs the level, two bedrooms with beds and no servant. */
    private suspend fun PlayerScript.hire(npc: NpcRef, type: ServantType) {
        val p = player
        chatNpc(
            npc,
            "Greetings. I am ${type.name.lowercase()}; I can work in your house for ${type.wage} coins per eight tasks.",
            Expression.NEUTRAL,
        )
        if (choice("Hire ${type.name}?", "Yes", "No") != YES) return
        when {
            hired(p) != null -> {
                chatNpc(npc, "You already have a servant. Dismiss them first.", Expression.NEUTRAL)
            }

            p.level(Stats.CONSTRUCTION) < type.level -> {
                chatNpc(npc, "You need a Construction level of ${type.level}.", Expression.NEUTRAL)
            }

            !hasBeds(p) -> {
                chatNpc(npc, "You need two bedrooms, each with a bed, in your house.", Expression.NEUTRAL)
            }

            else -> {
                p.setSaved(TYPE_KEY, (TYPES.indexOf(type) + 1).toLong())
                p.setSaved(USES_KEY, null)
                chatNpc(npc, "Very well, I will be in your house the next time you enter it.", Expression.HAPPY)
            }
        }
    }

    private suspend fun PlayerScript.chief(npc: NpcRef) {
        val p = player
        val type = hired(p) ?: return chatNpc(npc, "Welcome to the Servants' Guild.", Expression.NEUTRAL)
        chatNpc(npc, "Welcome to the Servants' Guild. Do you want to dismiss your ${type.name.lowercase()}?", Expression.NEUTRAL)
        if (choice("Dismiss ${type.name}?", "Yes", "No") == YES) dismiss(p)
    }

    private fun dismiss(p: PlayerRef) {
        p.setSaved(TYPE_KEY, null)
        p.setSaved(USES_KEY, null)
        p[House.CURRENT]?.let { Servants.despawn(it) }
        p.message("You dismiss your servant.")
    }

    /** SV1: the player's house needs at least two bedrooms, each with a bed built (Wiki "Hiring servants"). */
    private fun hasBeds(p: PlayerRef): Boolean {
        val layout = PohSave.load(p) ?: return false
        val bedrooms = layout.rooms.filter { it.template.name == BEDROOM }
        val withBed =
            bedrooms.count { room ->
                val cell = HouseLayout.cellKey(room.floor, room.x, room.z)
                layout.furniture.any { (key, loc) ->
                    HouseLayout.cellOf(key) == cell &&
                        PohFurniture
                            .byLoc(loc)
                            ?.name
                            ?.lowercase()
                            ?.let { "bed" in it || "4-poster" in it } == true
                }
            }
        return withBed >= MIN_BEDROOMS
    }

    // ---- services

    private suspend fun PlayerScript.servicesMenu(npc: NpcRef, house: House, type: ServantType) {
        val p = player
        val task = house.task
        if (task != null) return collect(npc, house, task)
        if ((p.saved(USES_KEY) ?: 0L) >= SERVICES_PER_WAGE) return wage(npc, type)
        val labels = mutableListOf("Fetch from the bank")
        if (type.sawmill) labels += "Take logs to the sawmill"
        labels += "Dismiss"
        labels += "Nothing"
        val pick = choice("What can I do for you?", *labels.toTypedArray())
        val options = if (type.sawmill) listOf(FETCH, SAWMILL, DISMISS) else listOf(FETCH, DISMISS)
        when (options.getOrNull(pick - 1)) {
            FETCH -> fetch(npc, house, type)
            SAWMILL -> sawmill(npc, house, type)
            DISMISS -> dismiss(p)
        }
    }

    /** SV3: the wage is due after eight services; the servant does nothing else until it is paid. */
    private suspend fun PlayerScript.wage(npc: NpcRef, type: ServantType) {
        val p = player
        chatNpc(npc, "Master, I have worked eight times: my wage of ${type.wage} coins is due.", Expression.NEUTRAL)
        when (choice("Pay ${type.wage} coins?", "Pay", "Dismiss", "Not now")) {
            1 -> {
                if (p.transaction("servant wage") {
                        remove(Invs.INV, Objs.COINS, type.wage)
                    }
                ) {
                    p.setSaved(USES_KEY, null)
                } else {
                    p.message("You don't have enough coins.")
                }
            }

            2 -> {
                dismiss(p)
            }
        }
    }

    private suspend fun PlayerScript.fetch(npc: NpcRef, house: House, type: ServantType) {
        val p = player
        var start = 0
        var obj = -1
        while (obj < 0) {
            val page = FETCHABLE.drop(start).take(PAGE)
            val more = start + PAGE < FETCHABLE.size
            val pick = choice("Fetch what?", *page.map { p.objName(it) }.toTypedArray(), if (more) "More..." else "Cancel")
            when {
                pick == 0 -> return
                pick <= page.size -> obj = page[pick - 1]
                more -> start += PAGE
                else -> return
            }
        }
        val item = obj
        p.countDialog(
            "How many (up to ${type.capacity})?",
        ) { n -> request(this, npc, house, type, ServantTask(false, item, minOf(n, type.capacity))) }
    }

    private suspend fun PlayerScript.sawmill(npc: NpcRef, house: House, type: ServantType) {
        val p = player
        val labels = ConstructionData.PLANKS.take(PAGE).map { "${it.name} (${it.cost} gp)" }
        val pick = choice("Which planks?", *labels.toTypedArray(), "Cancel")
        val plank = ConstructionData.PLANKS.getOrNull(pick - 1) ?: return
        p.countDialog(
            "How many (up to ${type.capacity})?",
        ) { n -> request(this, npc, house, type, ServantTask(true, plank.log, minOf(n, type.capacity))) }
    }

    /** SV2: records the task and sends the servant away for the trip time; one more service is counted for the wage. */
    private fun request(p: PlayerRef, npc: NpcRef, house: House, type: ServantType, task: ServantTask) {
        if (task.amount <= 0 || house.task != null || house.instance.destroyed) return
        house.task = task
        p.setSaved(USES_KEY, (p.saved(USES_KEY) ?: 0L) + 1)
        npc.say("Very good, master.")
        p.weakQueue(type.trip) {
            if (house.task === task) {
                task.ready = true
                npc.say("Master, I have returned.")
            }
        }
    }

    private suspend fun PlayerScript.collect(npc: NpcRef, house: House, task: ServantTask) {
        val p = player
        if (!task.ready) return chatNpc(npc, "I am still on my way, master.", Expression.NEUTRAL)
        house.task = null
        val done = if (task.sawmill) deliverPlanks(p, task) else deliverFromBank(p, task)
        chatNpc(npc, if (done > 0) "Here you are, master." else "I could not do that, master.", Expression.NEUTRAL)
    }

    /** SV4: moves up to the task's amount from the bank to the backpack in one transaction; returns how many moved. */
    private fun deliverFromBank(p: PlayerRef, task: ServantTask): Int {
        val room = if (p.objStackable(task.obj)) task.amount else p.inv(Invs.INV).freeSlots
        val n = minOf(task.amount.toLong(), p.inv(Invs.BANK).total(task.obj), room.toLong()).toInt()
        if (n <= 0) return 0
        return if (p.transaction("servant bank") { remove(Invs.BANK, task.obj, n) && add(Invs.INV, task.obj, n) }) n else 0
    }

    /** SV5: logs and the sawmill fee become planks in one transaction (Wiki "Sawmill operator": unlogged logs only, a fee per plank). */
    private fun deliverPlanks(p: PlayerRef, task: ServantTask): Int {
        val type = ConstructionData.PLANKS.first { it.log == task.obj }
        val n = minOf(task.amount.toLong(), p.inv(Invs.INV).total(type.log), p.inv(Invs.INV).total(Objs.COINS) / type.cost).toInt()
        if (n <= 0) return 0
        val ok =
            p.transaction("servant sawmill") {
                remove(Invs.INV, Objs.COINS, n * type.cost) && remove(Invs.INV, type.log, n) && add(Invs.INV, type.plank, n)
            }
        return if (ok) n else 0
    }

    internal companion object {
        const val OP_TALK = 1
        const val YES = 1
        const val PAGE = 4
        const val BEDROOM = "Bedroom"
        const val MIN_BEDROOMS = 2

        /** Wiki "Servant": the wage is due after eight uses. */
        const val SERVICES_PER_WAGE = 8

        private const val FETCH = 1
        private const val SAWMILL = 2
        private const val DISMISS = 3

        const val TYPE_KEY = "poh.servant"
        const val USES_KEY = "poh.servant.uses"

        /** Wiki "Servant", Types of servants (npc ids: cache 2757 `poh_servant_dogsbody` .. `poh_servant_demon`). */
        val TYPES =
            listOf(
                ServantType("Rick", Npcs.POH_SERVANT_DOGSBODY, 20, 500, 6, 100, false),
                ServantType("Maid", Npcs.POH_SERVANT_WAITER_WOMAN, 25, 1000, 10, 50, false),
                ServantType("Cook", Npcs.POH_SERVANT_COOK_WOMAN, 30, 3000, 16, 28, true),
                ServantType("Butler", Npcs.POH_SERVANT_MAITRE_D_MAN, 40, 5000, 20, 20, true),
                ServantType("Demon butler", Npcs.POH_SERVANT_DEMON, 50, 10000, 26, 12, true),
            )

        /** Wiki "Servant" (Services): the building materials a servant fetches from the bank. */
        val FETCHABLE =
            listOf(
                Objs.WOODPLANK,
                Objs.PLANK_OAK,
                Objs.PLANK_TEAK,
                Objs.PLANK_MAHOGANY,
                Objs.SOFTCLAY,
                Objs.LIMESTONEBRICK,
                Objs.STEEL_BAR,
                Objs.CLOTH,
                Objs.GOLD_LEAF,
                Objs.MARBLE_BLOCK,
                Objs.POH_MAGIC_CRYSTAL,
            )

        fun hired(p: PlayerRef): ServantType? = TYPES.getOrNull((p.saved(TYPE_KEY) ?: return null).toInt() - 1)
    }
}

/** Placing the hired servant in a live house and taking it out again; called by [PlayerOwnedHouse]. */
internal object Servants {
    /** Puts the owner's servant beside the exit portal when the owner has entered their own house. */
    fun spawn(p: PlayerRef, house: House) {
        if (house.servant != null || house.owner != p.username.lowercase()) return
        val type = PohServants.hired(p) ?: return
        house.servant = p.spawnNpc(type.npc, HouseBuilder.arrival(p, house), 0)
    }

    /** Removes the servant; the bank is never touched by a pending task, so nothing is held that could be lost. */
    fun despawn(house: House) {
        house.servant?.despawn()
        house.servant = null
        house.task = null
    }
}
