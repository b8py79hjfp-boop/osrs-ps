package tp.content.skills

import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.Stats
import tp.plugin.WorldRef
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Invs
import tp.symbols.Locs
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Seqs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid
import tp.content.skills.GiantsFoundryData as D

/** What a player is doing at a station this tick; [beat] is the last tick the interaction ran (it re-runs every tick while it lasts). */
internal class FoundryWork(val kind: Int, val since: Long) {
    var beat: Long = since
    var counter: Int = 0

    /** Tick until which a sweet spot window is open (0 = none), whether the player clicked inside it, and a pending over-click miss. */
    var sweetUntil: Long = 0
    var sweetArmed: Boolean = false
    var missNext: Boolean = false
}

/** Per-sword sweet spot schedule: the progress at which each window opens, and how many have opened. Not saved (OPEN-GF7). */
internal class FoundrySweet(val thresholds: IntArray) {
    var opened: Int = 0
}

/**
 * Giants' Foundry (docs/mechanics/giants-foundry.md): Kovac's commissions, the crucible and mould jig, the three machines, the lava
 * and waterfall, the preform storage, hand-in and the reputation shop. The sword lives in the player's varbits (the HUD, interface
 * 754, reads them); this class holds only the transient station state.
 */
public class GiantsFoundry : ContentPlugin {
    /** Metal items the crucible accepts: obj -> (metal index, bars). Filled at world start from the cache names. */
    private val metalItems = HashMap<Int, IntArray>()

    /** Players holding a preform inside the foundry, advanced every tick; players whose jig is cooling, with the due tick. */
    private val holders = ArrayList<PlayerRef>()
    private val cooling = ArrayList<Pair<PlayerRef, Long>>()

    /** Cache enum 4372: commission descriptor value -> word (client scripts 6101 and 6102). */
    private val wordValues = IntArray(D.WORD_NAMES.size) { -1 }

    override fun ContentScope.register() {
        onWorldStart {
            resolve(this)
            if (System.getProperty("tp.worldSpawns") != "false") spawnNpc(Npcs.GIANTS_FOUNDRY_KOVAC, KOVAC_AT, 0)
        }
        onWorldTick { drive(this) }
        onLogin { arrive(this) }
        onLogout { depart(this) }
        onOpLoc(Locs.GIANTS_FOUNDRY_ENTRANCE, OP_FIRST) { enter(player) }
        onOpLoc(Locs.GIANTS_FOUNDRY_EXIT, OP_FIRST) { leave(player) }
        registerKovac()
        registerCrucible()
        registerStations()
    }

    private fun ContentScope.registerKovac() {
        onOpNpc(Npcs.GIANTS_FOUNDRY_KOVAC, OP_FIRST) { npc -> talk(npc) }
        onOpNpc(Npcs.GIANTS_FOUNDRY_KOVAC, OP_SECOND) { npc -> handIn(npc) }
        onOpNpc(Npcs.GIANTS_FOUNDRY_KOVAC, OP_THIRD) { commission(player) }
        onOpNpc(Npcs.GIANTS_FOUNDRY_KOVAC, OP_FOURTH) { shop(player) }
        for (reward in D.REWARDS.filter { it.refund }) onUseOnNpc(reward.obj, Npcs.GIANTS_FOUNDRY_KOVAC) { sellBack(player, reward) }
    }

    private fun ContentScope.registerCrucible() {
        for (loc in listOf(Locs.GIANTS_FOUNDRY_CRUCIBLE_MULTI, Locs.GIANTS_FOUNDRY_CRUCIBLE_EMPTY, Locs.GIANTS_FOUNDRY_CRUCIBLE_FULL)) {
            onOpLoc(loc, OP_FIRST) { fillOrPour(player) }
            onOpLoc(loc, OP_SECOND) { checkCrucible(player) }
            onOpLoc(loc, OP_THIRD) { emptyCrucible(player) }
            // The used item decides which metal goes in; the engine hands the loc id placed on the map (the multi loc).
            onUseOnLoc(-1, loc) { addUsed(player, used?.obj ?: -1) }
        }
        for (loc in listOf(Locs.GIANTS_FOUNDRY_MOULD_JIG, Locs.GIANTS_FOUNDRY_JIG_EMPTY, Locs.GIANTS_FOUNDRY_JIG_MOULD)) {
            onOpLoc(loc, OP_FIRST) { jig(player) }
        }
        for (loc in listOf(Locs.GIANTS_FOUNDRY_PREFORM_STORAGE, Locs.GIANTS_FOUNDRY_PREFORM_STORAGE_EMPTY)) {
            onOpLoc(loc, OP_FIRST) { storage(player) }
        }
        onOpLoc(Locs.GIANTS_FOUNDRY_SUPPLY_BOX_MULTI, OP_FIRST) { supplyBucket(player) }
    }

    private fun ContentScope.registerStations() {
        onOpLoc(Locs.GIANTS_FOUNDRY_TRIP_HAMMER, OP_FIRST) { loc -> work(player, D.HAMMER, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_GRINDSTONE, OP_FIRST) { loc -> work(player, D.GRINDSTONE, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_POLISHING_WHEEL, OP_FIRST) { loc -> work(player, D.POLISH, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_LAVA_POOL, OP_FIRST) { loc -> work(player, KIND_HEAT_SLOW, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_LAVA_POOL, OP_SECOND) { loc -> work(player, KIND_HEAT_FAST, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_WATERFALL, OP_FIRST) { loc -> work(player, KIND_COOL_SLOW, loc.id) }
        onOpLoc(Locs.GIANTS_FOUNDRY_WATERFALL, OP_SECOND) { loc -> work(player, KIND_COOL_FAST, loc.id) }
    }

    // ---- set-up

    private fun resolve(world: WorldRef) {
        for ((metal, name) in D.METAL_NAMES.withIndex()) {
            metalItems[D.BARS[metal]] = intArrayOf(metal, 1)
            for ((suffix, bars) in D.EQUIPMENT_VALUES) {
                for (id in world.objIdsNamed("$name $suffix")) metalItems[id] = intArrayOf(metal, bars)
            }
        }
        for ((value, text) in world.enumStrings(WORD_ENUM)) {
            val word = D.WORD_NAMES.indexOf(text)
            if (word >= 0) wordValues[word] = value
        }
    }

    // ---- entering, leaving, login

    private fun inFoundry(c: CoordGrid): Boolean = c.level == 0 && c.x in FOUNDRY_X && c.z in FOUNDRY_Z

    private fun enter(p: PlayerRef) {
        p.teleport(ARRIVAL)
        arrive(p)
    }

    private fun leave(p: PlayerRef) {
        depart(p)
        p.teleport(OUTSIDE)
    }

    /** Opens the HUD and, for a sword in the player's hands, adds them to the per-tick list. */
    private fun arrive(p: PlayerRef) {
        if (!inFoundry(p.coord)) {
            if (holdsPreform(p)) store(p, announce = false)
            return
        }
        p.openOverlay(Components.Toplevel.OVERLAY_HUD, Interfaces.GIANTS_FOUNDRY_HUD)
        if (p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_STORED) == 1) {
            p.message("You notice your current commission is being kept warm in the preform storage.")
        } else if (holdsPreform(p) && p !in holders) {
            holders += p
        }
    }

    /** Leaving the cave or logging out puts the sword in the storage (Wiki "Preform storage"). */
    private fun depart(p: PlayerRef) {
        holders.remove(p)
        cooling.removeAll { it.first == p }
        p[WORK] = null
        if (holdsPreform(p)) store(p, announce = false)
        if (!inFoundry(p.coord)) p.closeOverlay(Components.Toplevel.OVERLAY_HUD)
    }

    private fun holdsPreform(p: PlayerRef): Boolean =
        p.inv(Invs.WORN).total(Objs.GIANTS_FOUNDRY_PREFORM) > 0 || p.inv(Invs.INV).total(Objs.GIANTS_FOUNDRY_PREFORM) > 0

    // ---- Kovac

    private suspend fun PlayerScript.talk(npc: NpcRef) {
        chatNpc(npc, "What do you want, small one?")
        when (choice("Select an Option", "I want a commission.", "I have a sword to hand in.", "Show me the shop.", "Nothing.")) {
            1 -> commission(player)
            2 -> handIn(npc)
            3 -> shop(player)
        }
    }

    private fun commissioned(p: PlayerRef): Boolean = (p.saved(COMMISSION_KEY) ?: 0L) != 0L

    /** Gives a commission: two descriptors, never both Heavy or both Light (Wiki "Giants' Foundry" > Preform creation). */
    private fun commission(p: PlayerRef) {
        if (commissioned(p)) return p.message("Finish or hand in your current commission first.")
        var first: Int
        var second: Int
        do {
            first = D.FIRST_WORDS[p.random(D.FIRST_WORDS.size)]
            second = D.SECOND_WORDS[p.random(D.SECOND_WORDS.size)]
        } while (first == second && (first == D.HEAVY || first == D.LIGHT))
        p.setVarbit(Varbits.GIANTS_FOUNDRY_COMMISSION_WORD_1, wordValue(first))
        p.setVarbit(Varbits.GIANTS_FOUNDRY_COMMISSION_WORD_2, wordValue(second))
        p.setSaved(COMMISSION_KEY, 1L)
        p.setSaved(START_KEY, p.epochSeconds)
        resetSword(p)
        p.message("Kovac commissions a ${D.WORD_NAMES[first]} ${D.WORD_NAMES[second]} sword.")
    }

    private fun wordValue(word: Int): Int = wordValues[word].coerceAtLeast(0)

    private fun wordOf(p: PlayerRef, varbit: Int): Int = wordValues.indexOf(p.varbit(varbit)).coerceAtLeast(0)

    private fun firstWord(p: PlayerRef): Int = wordOf(p, Varbits.GIANTS_FOUNDRY_COMMISSION_WORD_1)

    private fun secondWord(p: PlayerRef): Int = wordOf(p, Varbits.GIANTS_FOUNDRY_COMMISSION_WORD_2)

    /** Clears the sword and the crucible and jig for a new commission or after a hand-in. */
    private fun resetSword(p: PlayerRef) {
        for (v in SWORD_VARBITS) p.setVarbit(v, 0)
        for (v in COUNT_VARBITS) p.setVarbit(v, 0)
        for (v in SEED_VARBITS) p.setVarbit(v, 0)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_RICASSO, 0)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_BLADE, 0)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_TIP, 0)
        p[SWEET] = null
        p[WORK] = null
        holders.remove(p)
    }

    /** Hands in a finished or ruined sword: xp, coins to the bank, reputation, run energy (Wiki "Giants' Foundry" > Handing in). */
    private suspend fun PlayerScript.handIn(npc: NpcRef) {
        val p = player
        if (!commissioned(p)) return chatNpc(npc, "You have no commission from me.")
        val made = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_DIFICULTY) > 0
        val quality = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY)
        val finished = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION) >= D.SCALE
        if (!made || (quality > 0 && !finished)) return chatNpc(npc, "That sword is not finished.")
        val q = if (quality > 0) quality else 0
        val xp = D.handInXp(q)
        val coins = if (q > 0) D.handInCoins(q) else RUINED_COINS
        removePreformItem(p)
        p.addXp(Stats.SMITHING, xp.toDouble())
        if (!p.transaction("giants foundry coins") { add(Invs.BANK, Objs.COINS, coins) }) {
            p.transaction("giants foundry coins") { addOrDrop(Invs.INV, Objs.COINS, coins) }
        }
        p.setVarp(
            Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS,
            (p.varp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS) + q).coerceAtMost(Int.MAX_VALUE),
        )
        p.runEnergy = (p.runEnergy + D.RUN_RESTORE).coerceAtMost(RUN_MAX)
        val swords = (p.saved(SWORDS_KEY) ?: 0L) + 1
        p.setSaved(SWORDS_KEY, swords)
        val seconds = (p.epochSeconds - (p.saved(START_KEY) ?: p.epochSeconds)).coerceAtLeast(0)
        val better =
            q > (p.saved(PB_QUALITY_KEY) ?: -1L) ||
                (q.toLong() == p.saved(PB_QUALITY_KEY) && seconds < (p.saved(PB_SECONDS_KEY) ?: Long.MAX_VALUE))
        if (better) {
            p.setSaved(PB_QUALITY_KEY, q.toLong())
            p.setSaved(PB_SECONDS_KEY, seconds)
        }
        p.message(
            "Sword quality: $q, time: ${seconds / SECONDS_PER_MINUTE}:${"%02d".format(
                seconds % SECONDS_PER_MINUTE,
            )}, personal best quality: ${p.saved(PB_QUALITY_KEY)}.",
        )
        p.message("You have made $swords swords so far; $coins coins were sent to your bank.")
        p.setSaved(COMMISSION_KEY, 0L)
        resetSword(p)
        p.anim(Seqs.GIANTS_FOUNDRY_FINISH_SWORD)
    }

    // ---- the crucible

    private fun counts(p: PlayerRef): IntArray = IntArray(COUNT_VARBITS.size) { p.varbit(COUNT_VARBITS[it]) }

    private fun total(counts: IntArray): Int = counts.sum()

    private fun setCounts(p: PlayerRef, counts: IntArray) {
        for (i in counts.indices) p.setVarbit(COUNT_VARBITS[i], counts[i])
        val sum = total(counts)
        p.setVarbit(
            Varbits.GIANTS_FOUNDRY_CRUCIBLE_STATE,
            if (sum == 0) {
                0
            } else if (sum >= D.CRUCIBLE_CAPACITY) {
                2
            } else {
                1
            },
        )
    }

    /** Whether the preform in the jig or the player's hands is still unfinished business: no new metal until it is handed in. */
    private fun swordExists(p: PlayerRef): Boolean = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_DIFICULTY) > 0

    private fun fillOrPour(p: PlayerRef) {
        if (!commissioned(p)) return p.message("You need a commission from Kovac first.")
        val c = counts(p)
        if (total(c) >= D.CRUCIBLE_CAPACITY) return pour(p, c)
        for (obj in p.inv(Invs.INV).let { inv -> (0 until inv.size).map { inv.obj(it) }.distinct() }) addUsed(p, obj)
    }

    /** Adds every unit of [obj] in the backpack that fits (OPEN-GF1: the Wiki's amount prompt is not used). */
    private fun addUsed(p: PlayerRef, obj: Int) {
        val info = metalItems[obj] ?: return
        if (!commissioned(p)) return p.message("You need a commission from Kovac first.")
        if (swordExists(p)) return p.message("You can't pour a second preform before handing in the first.")
        if (p.level(Stats.SMITHING) < D.METAL_LEVELS[info[0]]) {
            return p.message(
                "You need a Smithing level of ${D.METAL_LEVELS[info[0]]} to add ${D.METAL_NAMES[info[0]].lowercase()} to the crucible.",
            )
        }
        val c = counts(p)
        val room = (D.CRUCIBLE_CAPACITY - total(c)) / info[1]
        val have =
            p
                .inv(Invs.INV)
                .total(obj)
                .coerceAtMost(room.toLong())
                .toInt()
        if (have <= 0) return
        if (!p.transaction("giants foundry crucible") { remove(Invs.INV, obj, have) }) return
        c[info[0]] += have * info[1]
        setCounts(p, c)
    }

    private fun checkCrucible(p: PlayerRef) {
        val c = counts(p)
        val parts = c.indices.filter { c[it] > 0 }.joinToString(", ") { "${c[it]} ${D.METAL_NAMES[it].lowercase()}" }
        p.message(if (parts.isEmpty()) "The crucible is empty." else "The crucible holds $parts (${total(c)} of ${D.CRUCIBLE_CAPACITY}).")
    }

    /** Emptying destroys all the metal (Wiki "Crucible"). */
    private fun emptyCrucible(p: PlayerRef) {
        if (total(counts(p)) == 0) return
        setCounts(p, IntArray(COUNT_VARBITS.size))
        p.message("You empty the crucible; the metal is lost.")
    }

    // ---- the mould jig

    private fun moulds(p: PlayerRef): IntArray =
        intArrayOf(
            p.varbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_RICASSO),
            p.varbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_BLADE),
            p.varbit(Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_TIP),
        )

    private fun mouldScore(p: PlayerRef): Int {
        val sel = moulds(p)
        var sum = 0
        for (type in 0..2) if (sel[type] > 0) sum += D.MOULDS[type][sel[type] - 1].score(firstWord(p), secondWord(p))
        return sum
    }

    private fun unlocked(p: PlayerRef, type: Int, index: Int): Boolean =
        index < D.DEFAULT_MOULDS || p.varbit(UNLOCK_VARBITS[type][index - D.DEFAULT_MOULDS]) == 1

    private suspend fun PlayerScript.jig(p: PlayerRef) {
        val state = p.varbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE)
        if (state >= JIG_POURED) return pickUp(p, state)
        if (!commissioned(p)) return p.message("You need a commission from Kovac first.")
        if (swordExists(p)) return p.message("You can't pour a second preform before handing in the first.")
        val first = firstWord(p)
        val second = secondWord(p)
        for (type in 0..2) {
            val options =
                D.MOULDS[type]
                    .withIndex()
                    .filter { (i, m) -> unlocked(p, type, i) && p.level(Stats.SMITHING) >= m.level }
                    .sortedByDescending { (_, m) -> m.score(first, second) }
            val labels = options.map { (_, m) -> "${m.name} (+${m.score(first, second)})" }
            val pick = pick("${D.TYPE_NAMES[type]} for ${D.WORD_NAMES[first]} ${D.WORD_NAMES[second]}", labels)
            if (pick < 0) return
            val index = options[pick].index + 1
            p.setVarbit(MOULD_SELECTED[type], index)
        }
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE, JIG_SET)
        p.message("Mould score: ${mouldScore(p)}.")
    }

    /** A paged option menu (menus hold five choices): the picked 0-based index, or -1. */
    private suspend fun PlayerScript.pick(title: String, labels: List<String>): Int {
        var page = 0
        while (true) {
            val start = page * PAGE
            val slice = labels.drop(start).take(PAGE)
            val more = start + PAGE < labels.size
            val answer = choice(title, *(slice + (if (more) "More..." else "Cancel")).toTypedArray())
            when {
                answer in 1..slice.size -> return start + answer - 1
                more && answer == slice.size + 1 -> page++
                else -> return -1
            }
        }
    }

    private fun pour(p: PlayerRef, c: IntArray) {
        if (p.varbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE) != JIG_SET) return p.message("Set up the mould in the mould jig first.")
        val sel = moulds(p)
        for (type in 0..2) {
            if (p.level(Stats.SMITHING) <
                D.MOULDS[type][sel[type] - 1].level
            ) {
                return p.message("Your Smithing level is too low for the mould you chose.")
            }
        }
        val metal = D.metalScore(c)
        val start = metal + mouldScore(p)
        val sections = D.sections(metal)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_DIFICULTY, metal)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY, start)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_START_QUALITY, start)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, D.START_TEMPERATURE)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION, 0)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_STORED, 0)
        // OPEN-GF2: the Wiki fixes the first tool (trip hammer) but not the rest; later sections draw a tool uniformly, not
        // repeating the last.
        var last = D.HAMMER
        for (s in 1 until sections) {
            var t: Int
            do {
                t = p.random(TOOLS)
            } while (t == last)
            p.setVarbit(SEED_VARBITS[s - 1], t)
            last = t
        }
        p[SWEET] = sweetSchedule(p, sections)
        setCounts(p, IntArray(COUNT_VARBITS.size))
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE, JIG_POURED)
        cooling += p to p.tick + D.COOLING_TICKS
        p.anim(Seqs.GIANTS_FOUNDRY_CRUCIBLE_POUR01)
        p.message("You pour the molten metal into the mould.")
    }

    /** OPEN-GF7: sweet spot i opens at a random progress inside the i-th equal slice of the sword. */
    private fun sweetSchedule(p: PlayerRef, sections: Int): FoundrySweet {
        val n = D.sweetSpots(sections)
        val slice = D.SCALE / n
        return FoundrySweet(IntArray(n) { it * slice + p.random(slice) })
    }

    private fun pickUp(p: PlayerRef, state: Int) {
        if (state != JIG_COOLED) return p.message("The metal is still cooling.")
        val gloves = p.inv(Invs.WORN).let { it.total(Objs.ICE_GLOVES) + it.total(Objs.SMITHING_UNIFORM_GLOVES_ICE) } > 0
        if (!gloves && p.inv(Invs.INV).total(Objs.BUCKET_WATER) == 0L) {
            return p.message("You need a bucket of water or ice gloves to pick up the hot preform.")
        }
        val weapon = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT)
        val ok =
            p.transaction("giants foundry preform") {
                if (weapon >= 0) {
                    move(Invs.WORN, Skilling.WEAPON_SLOT, weapon, 1, Invs.INV)
                } else {
                    true
                }
            } &&
                p.transaction("giants foundry preform") { setAt(Invs.WORN, Skilling.WEAPON_SLOT, Objs.GIANTS_FOUNDRY_PREFORM, 1) }
        if (!ok) return p.message("You need a free inventory slot to pick up the preform.")
        p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE, 0)
        holders += p
        p.message("You pick up the preform.")
    }

    private fun removePreformItem(p: PlayerRef) {
        p.transaction("giants foundry preform") {
            val worn = p.inv(Invs.WORN).total(Objs.GIANTS_FOUNDRY_PREFORM)
            val held = p.inv(Invs.INV).total(Objs.GIANTS_FOUNDRY_PREFORM)
            (worn == 0L || remove(Invs.WORN, Objs.GIANTS_FOUNDRY_PREFORM, worn.toInt())) &&
                (held == 0L || remove(Invs.INV, Objs.GIANTS_FOUNDRY_PREFORM, held.toInt()))
        }
    }

    // ---- storage, supplies

    private fun storage(p: PlayerRef) {
        if (holdsPreform(p)) return store(p, announce = true)
        if (p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_STORED) != 1) return
        val weapon = p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT)
        val ok =
            p.transaction("giants foundry storage") {
                if (weapon >=
                    0
                ) {
                    move(Invs.WORN, Skilling.WEAPON_SLOT, weapon, 1, Invs.INV)
                } else {
                    true
                }
            } &&
                p.transaction("giants foundry storage") { setAt(Invs.WORN, Skilling.WEAPON_SLOT, Objs.GIANTS_FOUNDRY_PREFORM, 1) }
        if (!ok) return p.message("You need a free inventory slot to take the preform.")
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_STORED, 0)
        if (p !in holders) holders += p
    }

    private fun store(p: PlayerRef, announce: Boolean) {
        removePreformItem(p)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_STORED, 1)
        p[WORK] = null
        holders.remove(p)
        if (announce) p.message("You place the preform in the storage.")
    }

    private fun supplyBucket(p: PlayerRef) {
        if (p.inv(Invs.INV).total(Objs.BUCKET_WATER) > 0 || p.inv(Invs.INV).total(Objs.BUCKET_EMPTY) > 0) return
        p.transaction("giants foundry bucket") { add(Invs.INV, Objs.BUCKET_WATER, 1) }
    }

    // ---- stations

    private fun PlayerScript.work(p: PlayerRef, kind: Int, locId: Int) {
        if (!holdsPreform(p) || p.inv(Invs.WORN).obj(Skilling.WEAPON_SLOT) != Objs.GIANTS_FOUNDRY_PREFORM) {
            return p.message("You need to be holding a preform to use this.")
        }
        if (p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY) <= 0) {
            return p.message("This sword is too damaged to keep working. You should give it to Kovac and try again.")
        }
        if (kind <= D.POLISH && p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION) >= D.SCALE) {
            return p.message("You have successfully finished the sword. You should give it to Kovac for a reward.")
        }
        val now = p.tick
        val clock = Skilling.clock(p, Skilling.locKey(locId, kind))
        var w = p[WORK]
        if (w == null || w.kind != kind || w.beat < now - 1) {
            w = FoundryWork(kind, now)
            p[WORK] = w
            if (kind <= D.POLISH) p.anim(Seqs.GIANTS_FOUNDRY_WORK_PREFORM_LOOP)
        } else if (clock.fresh && kind <= D.POLISH) {
            // OPEN-GF12: a click while the machine runs is a sweet spot if its window is open, otherwise it overworks the preform.
            if (w.sweetUntil >= now && !w.sweetArmed) w.sweetArmed = true else w.missNext = true
        }
        w.beat = now
        if (p !in holders) holders += p
        keepInteraction()
    }

    // ---- the per-tick drive

    private fun drive(world: WorldRef) {
        val now = world.tick
        if (cooling.isNotEmpty()) {
            val it = cooling.iterator()
            while (it.hasNext()) {
                val (p, due) = it.next()
                if (now < due) continue
                p.setVarbit(Varbits.GIANTS_FOUNDRY_MOULD_STATE, JIG_COOLED)
                it.remove()
            }
        }
        var i = 0
        while (i < holders.size) {
            val p = holders[i]
            if (!inFoundry(p.coord) || !holdsPreform(p)) {
                holders.removeAt(i)
                continue
            }
            step(p, now)
            i++
        }
    }

    private fun step(p: PlayerRef, now: Long) {
        var temp = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_TEMPERATURE)
        val w = p[WORK]?.takeIf { it.beat == now }
        if (w == null) {
            p[WORK] = null
            temp -= D.PASSIVE_COOLING
        } else if (w.kind > D.POLISH) {
            val fast = w.kind == KIND_HEAT_FAST || w.kind == KIND_COOL_FAST
            val rate =
                ((if (fast) D.FAST_START else D.SLOW_START) + D.RAMP_GAIN * (now - w.since))
                    .coerceAtMost(
                        D.RAMP_CAP.toLong(),
                    ).toInt()
            temp += if (w.kind == KIND_HEAT_SLOW || w.kind == KIND_HEAT_FAST) rate else -rate
        } else {
            temp -= D.PASSIVE_COOLING
            w.counter++
            if (w.counter % D.PERIOD[w.kind] == 0) temp += action(p, w, now, temp)
        }
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_TEMPERATURE, temp.coerceIn(0, D.SCALE))
    }

    /** One machine action at the temperature [temp]; returns the change it makes to the temperature. */
    private fun action(p: PlayerRef, w: FoundryWork, now: Long, temp: Int): Int {
        val tool = w.kind
        val difficulty = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_DIFICULTY)
        if (w.missNext) {
            w.missNext = false
            p.message("Your attempt to overwork the preform causes you to miss the action altogether.")
            return 0
        }
        val sections = D.sections(difficulty)
        val progress = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION)
        val section = (progress * sections / D.SCALE).coerceAtMost(sections - 1)
        val required = if (section == 0) D.HAMMER else p.varbit(SEED_VARBITS[section - 1])
        val change = D.TEMPERATURE[tool]
        if (tool != required || !D.inRange(tool, temp, difficulty)) {
            damage(p, w)
            return change
        }
        val sweet = p[SWEET]
        var gain = D.PROGRESS[tool]
        var xp = D.ACTION_XP[tool]
        if (w.sweetArmed) {
            w.sweetArmed = false
            gain *= D.SWEET_PROGRESS_MULTIPLIER
            xp *= 2
            restore(p)
        }
        if (p.random(D.SCALE / ONE_PERCENT_OF_SCALE) < uniformPercent(p)) gain += D.UNIFORM_BONUS_PROGRESS
        p.addXp(Stats.SMITHING, xp)
        val done = (progress + gain).coerceAtMost(D.SCALE)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION, done)
        if (done >= D.SCALE) {
            p.message("You have successfully finished the sword. You should give it to Kovac for a reward.")
            p[WORK] = null
            p.stopInteraction()
            p.anim(-1)
        } else if (sweet != null && sweet.opened < sweet.thresholds.size && done >= sweet.thresholds[sweet.opened]) {
            sweet.opened++
            w.sweetUntil = now + D.SWEET_WINDOW_TICKS
        }
        return change
    }

    private fun uniformPercent(p: PlayerRef): Int {
        val worn = p.inv(Invs.WORN)
        val pieces = D.UNIFORM.count { worn.total(it) > 0 } + (if (D.UNIFORM_GLOVES.any { worn.total(it) > 0 }) 1 else 0)
        return if (pieces == UNIFORM_PIECES) FULL_PERCENT else pieces * D.UNIFORM_PIECE_PERCENT
    }

    private fun damage(p: PlayerRef, w: FoundryWork) {
        val quality = (p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY) - D.MISTAKE_PENALTY).coerceAtLeast(0)
        p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY, quality)
        w.sweetArmed = false
        if (quality == 0) {
            p.message("This sword is too damaged to keep working. You should give it to Kovac and try again.")
            p[WORK] = null
            p.stopInteraction()
            p.anim(-1)
        }
    }

    /**
     * A sweet spot gives back one mistake's worth of quality, up to the quality the sword started with (Wiki "Giants' Foundry"
     * > Sweet Spot).
     */
    private fun restore(p: PlayerRef) {
        val start = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_START_QUALITY)
        val quality = p.varbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY)
        if (quality < start) {
            p.setVarbit(Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY, (quality + D.MISTAKE_PENALTY).coerceAtMost(start))
        } else {
            p.message("Your quick reactions allow you to find the perfect position to work the preform!")
        }
    }

    // ---- the reward shop

    private suspend fun PlayerScript.shop(p: PlayerRef) {
        val offers = unlockable(p)
        val labels = D.REWARDS.map { "${p.objName(it.obj)} - ${it.cost}" } + offers.map { "${it.third.name} mould - ${it.third.cost}" }
        val choice = pick("Reputation: ${p.varp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS)}", labels)
        if (choice < 0) return
        if (choice < D.REWARDS.size) {
            val reward = D.REWARDS[choice]
            if (reward.stack) {
                p.countDialog("Enter amount:") { amount -> buy(this, reward, amount) }
            } else {
                buy(p, reward, 1)
            }
        } else {
            buyMould(p, offers[choice - D.REWARDS.size])
        }
    }

    /** The purchasable moulds the player has not unlocked: (type, index within the type, mould). */
    private fun unlockable(p: PlayerRef): List<Triple<Int, Int, Mould>> =
        (0..2).flatMap { type ->
            D.MOULDS[type]
                .withIndex()
                .filter { (i, _) -> !unlocked(p, type, i) }
                .map { (i, m) -> Triple(type, i, m) }
        }

    private fun buyMould(p: PlayerRef, entry: Triple<Int, Int, Mould>) {
        val (type, index, mould) = entry
        val points = p.varp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS)
        if (points < mould.cost) return p.message("You don't have enough foundry reputation.")
        p.setVarp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, points - mould.cost)
        p.setVarbit(UNLOCK_VARBITS[type][index - D.DEFAULT_MOULDS], 1)
        p.message("You unlock the ${mould.name} mould.")
    }

    private fun buy(p: PlayerRef, reward: D.Reward, amount: Int) {
        if (amount <= 0) return
        val cost = reward.cost.toLong() * amount
        val points = p.varp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS)
        if (points < cost) return p.message("You don't have enough foundry reputation.")
        if (!p.transaction(
                "giants foundry shop",
            ) { add(Invs.INV, reward.obj, amount) }
        ) {
            return p.message("You don't have enough inventory space.")
        }
        p.setVarp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, (points - cost).toInt())
    }

    private fun sellBack(p: PlayerRef, reward: D.Reward) {
        val refund = reward.cost * D.REFUND_PERCENT / PERCENT
        if (!p.transaction("giants foundry sell") { remove(Invs.INV, reward.obj, 1) }) return
        p.setVarp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS, p.varp(Varps.GIANTS_FOUNDRY_REWARD_SHOP_POINTS) + refund)
        p.message("Kovac gives you $refund foundry reputation.")
    }

    private companion object {
        const val OP_FIRST = 1
        const val OP_SECOND = 2
        const val OP_THIRD = 3
        const val OP_FOURTH = 4

        val WORK = AttrKey<FoundryWork>("giantsFoundry.work")
        val SWEET = AttrKey<FoundrySweet>("giantsFoundry.sweet")

        /**
         * Saved values: commission open, its start (epoch seconds), swords made and the personal best (Wiki "Giants' Foundry" >
         * Handing in).
         */
        const val COMMISSION_KEY = "giantsFoundry.commission"
        const val START_KEY = "giantsFoundry.start"
        const val SWORDS_KEY = "giantsFoundry.swords"
        const val PB_QUALITY_KEY = "giantsFoundry.pbQuality"
        const val PB_SECONDS_KEY = "giantsFoundry.pbSeconds"

        /** Station kinds beyond the three tools: the lava (heat) and the waterfall (cool), slow and fast options. */
        const val KIND_HEAT_SLOW = 3
        const val KIND_HEAT_FAST = 4
        const val KIND_COOL_SLOW = 5
        const val KIND_COOL_FAST = 6

        const val TOOLS = 3

        /** Mould jig states (varbit 13914; the loc ids 44625..44629 in order, OPEN-GF10): empty 0, set up, poured, cooling, cooled. */
        const val JIG_SET = 1
        const val JIG_POURED = 2
        const val JIG_COOLED = 4

        /** Cache enum 4372: commission descriptor value -> text (client scripts 6101, 6102). */
        const val WORD_ENUM = 4372

        /** Wiki "Giants' Foundry" > Sword quality: a ruined sword is worth 60 coins (30 xp is the formula at quality 0). */
        const val RUINED_COINS = 60

        const val RUN_MAX = 10_000
        const val SECONDS_PER_MINUTE = 60
        const val PAGE = 4
        const val PERCENT = 100
        const val ONE_PERCENT_OF_SCALE = 10
        const val UNIFORM_PIECES = 4
        const val FULL_PERCENT = 100

        /**
         * OPEN-GF11: the foundry's map square (52, 179) and where Kovac, the arrival below the cave and the cave mouth are
         * (cache locs 44636 / 44635).
         */
        val FOUNDRY_X = 3328..3391
        val FOUNDRY_Z = 11456..11519
        val ARRIVAL = CoordGrid(3365, 11484, 0)
        val OUTSIDE = CoordGrid(3360, 3151, 0)
        val KOVAC_AT = CoordGrid(3372, 11485, 0)

        val SWORD_VARBITS =
            intArrayOf(
                Varbits.GIANTS_FOUNDRY_PREFORM_DIFICULTY,
                Varbits.GIANTS_FOUNDRY_PREFORM_QUALITY,
                Varbits.GIANTS_FOUNDRY_PREFORM_START_QUALITY,
                Varbits.GIANTS_FOUNDRY_PREFORM_TEMPERATURE,
                Varbits.GIANTS_FOUNDRY_PREFORM_COMPLETION,
                Varbits.GIANTS_FOUNDRY_PREFORM_STORED,
                Varbits.GIANTS_FOUNDRY_MOULD_STATE,
                Varbits.GIANTS_FOUNDRY_CRUCIBLE_STATE,
            )
        val COUNT_VARBITS =
            intArrayOf(
                Varbits.GIANTS_FOUNDRY_BRONZE_COUNT,
                Varbits.GIANTS_FOUNDRY_IRON_COUNT,
                Varbits.GIANTS_FOUNDRY_STEEL_COUNT,
                Varbits.GIANTS_FOUNDRY_MITHRIL_COUNT,
                Varbits.GIANTS_FOUNDRY_ADAMANT_COUNT,
                Varbits.GIANTS_FOUNDRY_RUNE_COUNT,
            )
        val SEED_VARBITS =
            intArrayOf(
                Varbits.GIANTS_FOUNDRY_SEED_1,
                Varbits.GIANTS_FOUNDRY_SEED_2,
                Varbits.GIANTS_FOUNDRY_SEED_3,
                Varbits.GIANTS_FOUNDRY_SEED_4,
                Varbits.GIANTS_FOUNDRY_SEED_5,
                Varbits.GIANTS_FOUNDRY_SEED_6,
            )
        val MOULD_SELECTED =
            intArrayOf(
                Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_RICASSO,
                Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_BLADE,
                Varbits.GIANTS_FOUNDRY_MOULD_SELECTED_TIP,
            )
        val UNLOCK_VARBITS =
            arrayOf(
                intArrayOf(
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_7,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_8,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_9,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_10,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_RICASSO_11,
                ),
                intArrayOf(
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_7,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_8,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_9,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_10,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_BLADE_11,
                ),
                intArrayOf(
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_7,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_8,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_9,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_10,
                    Varbits.GIANTS_FOUNDRY_UNLOCKED_MOULD_TIP_11,
                ),
            )
    }
}
