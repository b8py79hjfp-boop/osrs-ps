package tp.content.diaries

import tp.plugin.AttrKey
import tp.plugin.ItemsCreated
import tp.plugin.LocInteraction
import tp.plugin.NpcInteraction
import tp.plugin.PlayerRef
import tp.plugin.SharedAttrs
import tp.plugin.XpGained
import tp.symbols.Invs
import java.util.concurrent.ConcurrentHashMap

/** A rectangle of tiles; [level] -1 = any level. */
internal class Box(val x1: Int, val z1: Int, val x2: Int, val z2: Int, val level: Int) {
    fun contains(x: Int, z: Int, plane: Int): Boolean = x in x1..x2 && z in z1..z2 && (level < 0 || level == plane)
}

/** What observes a part of a task (docs/mechanics/achievement-diaries.md, "Triggers"). */
internal enum class PartKind { LOC, NPC, ITEM, XP, AREA, CAST, KILL, SEQ }

/**
 * One observable step of a task. All filters that are set must hold when the event arrives; a task completes when all its parts
 * have happened (in any order, except [PartKind.SEQ] which is an ordered chain of loc interactions).
 */
internal class Part(
    val kind: PartKind,
    /** Name keys ([DiaryHooks.key]) of cache names that match (locs, npcs, objs). */
    val names: Set<String>,
    /** Config ids that match (locs, npcs, objs). */
    val ids: Set<Int>,
    /** Op number (locs, npcs) or op text (npcs) that must have been chosen; null = any op. */
    val opNumber: Int,
    val opText: String?,
    /** Stat id of an [PartKind.XP] / [PartKind.CAST] part; -1 otherwise. */
    val stat: Int,
    /** Exact experience of an [PartKind.XP] / [PartKind.CAST] part (a spell's or an action's Wiki experience); NaN = any. */
    val xp: Double,
    /** Places the player must stand in (empty = anywhere). */
    val boxes: List<Box>,
    /** Cast arrival: the tile the spell takes the player to ([Box]) . */
    val dest: Box?,
    /** Transaction reason prefix an [PartKind.ITEM] must come from; null = any. */
    val reason: String?,
    /** Item parts: total to collect over several events ([count]), and a single event that must bring at least [min]. */
    val count: Int,
    val min: Int,
    /** Cache names of worn items that must all be worn (area parts, "with ... equipped"). */
    val worn: List<String>,
    /** Cache name keys of the NPCs the player must have just interacted with (pickpocketing: the success is the item); empty = any. */
    val target: Set<String>,
    /** Ordered loc ids of a [PartKind.SEQ] part (a rooftop or agility lap); [alias] maps an alternative loc of a step to its id. */
    val seq: IntArray,
    val alias: Map<Int, Int>,
    /** The player must be in the Wilderness ([tp.plugin.SharedAttrs.IN_WILDERNESS]). */
    val wild: Boolean,
    /** An [PartKind.ITEM] part that any created item satisfies (pickpocket loot, whose obj depends on the NPC). */
    val any: Boolean,
)

internal class TriggerTask(val id: TaskId, val label: String, val parts: List<Part>) {
    /** Precomputed saved keys: no string building when an event arrives. */
    val doneKey: String = Diaries.tasksKey(id.area, id.tier)
    val partsKey: String = Diaries.partsKey(id)
    val countKeys: List<String> = parts.indices.map { "diary.count.${id.raw}.$it" }
    val doneBit: Long = 1L shl (id.index - 1)
    val allParts: Long = (1L shl parts.size) - 1
}

/** Index entry: part [part] of [task]. */
internal class Ref(val task: TriggerTask, val part: Int)

/**
 * Observes plugin-api events and completes the tasks whose trigger table (`diary-triggers.tsv`) rows they satisfy. Progress of
 * multi-part tasks is a bit per part in `diary.part.<task id>`; counting parts keep `diary.count.<task id>.<part>`.
 */
internal class DiaryTriggers(private val diaries: Diaries, val tasks: List<TriggerTask>) {
    private val locByName = HashMap<String, MutableList<Ref>>()
    private val locById = HashMap<Int, MutableList<Ref>>()
    private val npcByName = HashMap<String, MutableList<Ref>>()
    private val npcById = HashMap<Int, MutableList<Ref>>()
    private val itemByName = HashMap<String, MutableList<Ref>>()
    private val itemById = HashMap<Int, MutableList<Ref>>()
    private val killByName = HashMap<String, MutableList<Ref>>()
    private val xpRefs = HashMap<Int, MutableList<Ref>>()
    private val seqById = HashMap<Int, MutableList<Ref>>()
    private val itemAny = ArrayList<Ref>()

    /** Area and cast-arrival parts, polled by [poll]. */
    private val watched = ArrayList<Ref>()
    private val casts = ArrayList<Ref>()
    private val seqParts = ArrayList<Ref>()
    private val needsTarget: Boolean

    /** Item obj id to the refs its cache name matches; resolved once per obj (the cache is the same in every world). */
    private val itemMemo = ConcurrentHashMap<Int, List<Ref>>()

    init {
        var target = false
        for (task in tasks) {
            for ((i, part) in task.parts.withIndex()) {
                val ref = Ref(task, i)
                if (part.target.isNotEmpty()) target = true
                when (part.kind) {
                    PartKind.LOC -> {
                        index(locByName, part.names, ref)
                        for (id in part.ids) locById.getOrPut(id) { ArrayList() } += ref
                    }

                    PartKind.NPC -> {
                        index(npcByName, part.names, ref)
                        for (id in part.ids) npcById.getOrPut(id) { ArrayList() } += ref
                    }

                    PartKind.ITEM -> {
                        if (part.any) itemAny += ref
                        index(itemByName, part.names, ref)
                        for (id in part.ids) itemById.getOrPut(id) { ArrayList() } += ref
                    }

                    PartKind.KILL -> {
                        index(killByName, part.names, ref)
                    }

                    PartKind.XP -> {
                        xpRefs.getOrPut(part.stat) { ArrayList() } += ref
                    }

                    PartKind.AREA -> {
                        watched += ref
                    }

                    PartKind.CAST -> {
                        xpRefs.getOrPut(part.stat) { ArrayList() } += ref
                        casts += ref
                        watched += ref
                    }

                    PartKind.SEQ -> {
                        seqParts += ref
                        for (id in part.seq.toSet() + part.alias.keys) seqById.getOrPut(id) { ArrayList() } += ref
                    }
                }
            }
        }
        needsTarget = target
    }

    private fun index(into: MutableMap<String, MutableList<Ref>>, names: Set<String>, ref: Ref) {
        for (name in names) into.getOrPut(name) { ArrayList() } += ref
    }

    // ---- events

    fun loc(e: LocInteraction) {
        val player = e.player
        val seq = seqById[e.loc.id]
        if (seq != null) for (ref in seq) advanceSeq(player, ref, e.loc.id)
        val byId = locById[e.loc.id]
        if (byId != null) for (ref in byId) if (opMatches(ref.task.parts[ref.part], e.option, null)) hit(player, ref, 1)
        if (locByName.isEmpty()) return
        val byName = locByName[DiaryHooks.key(player.locName(e.loc.id))] ?: return
        for (ref in byName) if (opMatches(ref.task.parts[ref.part], e.option, null)) hit(player, ref, 1)
    }

    fun npc(e: NpcInteraction) {
        val player = e.player
        val npc = e.npc
        val opText = npc.opName(e.option)
        if (needsTarget) player[LAST_NPC] = LastNpc(DiaryHooks.key(npc.displayName), player.tick)
        npcById[npc.type]?.let { refs -> for (ref in refs) if (opMatches(ref.task.parts[ref.part], e.option, opText)) hit(player, ref, 1) }
        if (npcByName.isEmpty()) return
        val byName = npcByName[DiaryHooks.key(npc.displayName)] ?: return
        for (ref in byName) if (opMatches(ref.task.parts[ref.part], e.option, opText)) hit(player, ref, 1)
    }

    fun items(e: ItemsCreated) {
        val player = e.player
        for ((obj, amount) in e.objs) {
            val refs =
                itemMemo.getOrPut(obj) {
                    val out = ArrayList<Ref>()
                    itemById[obj]?.let { out += it }
                    itemByName[DiaryHooks.key(player.objName(obj))]?.let { out += it }
                    out += itemAny
                    out
                }
            for (ref in refs) {
                val part = ref.task.parts[ref.part]
                if (part.reason != null && !e.reason.startsWith(part.reason)) continue
                if (part.min > 0 && amount < part.min) continue
                hit(player, ref, if (part.min > 0) 1 else amount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            }
        }
    }

    fun xp(e: XpGained) {
        val refs = xpRefs[e.stat] ?: return
        for (ref in refs) {
            val part = ref.task.parts[ref.part]
            if (!part.xp.isNaN() && kotlin.math.abs(part.xp - e.xp) > XP_EPSILON) continue
            if (part.kind == PartKind.CAST) {
                if (!isDone(e.player, ref.task) && inBoxes(part, e.player)) startCast(e.player, ref)
            } else {
                hit(e.player, ref, 1)
            }
        }
    }

    /** A kill of an NPC called [name] at [at] by [player]. */
    fun kill(player: PlayerRef, name: String, x: Int, z: Int, plane: Int) {
        val refs = killByName[DiaryHooks.key(name)] ?: return
        for (ref in refs) {
            val part = ref.task.parts[ref.part]
            if (part.boxes.isNotEmpty() && part.boxes.none { it.contains(x, z, plane) }) continue
            hit(player, ref, 1, checkPlace = false)
        }
    }

    // ---- polling (enter / visit / arrive)

    /** Every [POLL_TICKS] ticks while online: areas entered and spells that have taken the player to their destination. */
    fun poll(player: PlayerRef) {
        val at = player.coord
        val x = at.x
        val z = at.z
        val plane = at.level
        for (ref in watched) {
            val part = ref.task.parts[ref.part]
            if (part.kind == PartKind.CAST) {
                val until = player[CAST_UNTIL]?.get(castIndex(ref)) ?: 0L
                if (until < player.tick) continue
                val dest = part.dest ?: continue
                if (!dest.contains(x, z, plane)) continue
                player[CAST_UNTIL]?.set(castIndex(ref), 0L)
                hit(player, ref, 1, checkPlace = false)
            } else {
                if (part.boxes.none { it.contains(x, z, plane) }) continue
                hit(player, ref, 1, checkPlace = false)
            }
        }
    }

    private fun castIndex(ref: Ref): Int = casts.indexOf(ref)

    private fun startCast(player: PlayerRef, ref: Ref) {
        val until = player[CAST_UNTIL] ?: LongArray(casts.size).also { player[CAST_UNTIL] = it }
        until[castIndex(ref)] = player.tick + CAST_WINDOW_TICKS
    }

    private fun advanceSeq(player: PlayerRef, ref: Ref, loc: Int) {
        val part = ref.task.parts[ref.part]
        val progress = player[SEQ_PROGRESS] ?: IntArray(seqParts.size).also { player[SEQ_PROGRESS] = it }
        val slot = seqParts.indexOf(ref)
        val next = progress[slot]
        val step = part.alias[loc] ?: loc
        progress[slot] =
            when {
                part.seq[next] == step -> next + 1
                part.seq[0] == step -> 1
                else -> 0
            }
        if (progress[slot] == part.seq.size) {
            progress[slot] = 0
            hit(player, ref, 1)
        }
    }

    // ---- matching and progress

    private fun isDone(player: PlayerRef, task: TriggerTask): Boolean = (player.saved(task.doneKey) ?: 0L) and task.doneBit != 0L

    private fun opMatches(part: Part, option: Int, opText: String?): Boolean =
        (part.opNumber < 0 || part.opNumber == option) && (part.opText == null || part.opText.equals(opText, ignoreCase = true))

    private fun inBoxes(part: Part, player: PlayerRef): Boolean {
        if (part.boxes.isEmpty()) return true
        val at = player.coord
        return part.boxes.any { it.contains(at.x, at.z, at.level) }
    }

    private fun wornOk(part: Part, player: PlayerRef): Boolean {
        if (part.worn.isEmpty()) return true
        val worn = player.inv(Invs.WORN)
        val names = HashSet<String>()
        for (slot in 0 until worn.size) {
            val obj = worn.obj(slot)
            if (obj >= 0) names += DiaryHooks.key(player.objName(obj))
        }
        return part.worn.all { it in names }
    }

    private fun targetOk(part: Part, player: PlayerRef): Boolean {
        if (part.target.isEmpty()) return true
        val last = player[LAST_NPC] ?: return false
        return last.name in part.target && player.tick - last.tick <= TARGET_WINDOW_TICKS
    }

    /**
     * Records that part [ref] happened [amount] times for [player]; completes the task when it was the last missing part. The
     * requirements (skills, quests) are checked first, so a player who lacks them keeps no progress for a task they cannot get.
     */
    private fun hit(player: PlayerRef, ref: Ref, amount: Int, checkPlace: Boolean = true) {
        val task = ref.task
        val part = task.parts[ref.part]
        if (isDone(player, task)) return
        if (task.parts.size > 1 && (player.saved(task.partsKey) ?: 0L) and (1L shl ref.part) != 0L) return
        if (checkPlace && !inBoxes(part, player)) return
        if (part.wild && player[SharedAttrs.IN_WILDERNESS] != true) return
        if (!wornOk(part, player) || !targetOk(part, player)) return
        if (!diaries.meetsRequirements(player, task.id)) return
        if (part.count > 1) {
            val key = task.countKeys[ref.part]
            val total = (player.saved(key) ?: 0L) + amount
            if (total < part.count) {
                player.setSaved(key, total)
                return
            }
            player.setSaved(key, null)
        }
        if (task.parts.size == 1) {
            diaries.complete(player, task.id)
            return
        }
        val parts = (player.saved(task.partsKey) ?: 0L) or (1L shl ref.part)
        player.setSaved(task.partsKey, parts)
        if (parts == task.allParts) diaries.complete(player, task.id)
    }

    private class LastNpc(val name: String, val tick: Long)

    companion object {
        /** Ticks between two polls of the areas (about 1.8 s: entering a place is noticed within a couple of steps). */
        const val POLL_TICKS = 3

        /** A spell's arrival is looked for this long after its experience arrived (the teleport animation is a few ticks). */
        const val CAST_WINDOW_TICKS = 20L

        /** A pickpocket's loot counts for the NPC interacted with at most this many ticks before. */
        const val TARGET_WINDOW_TICKS = 8L

        /** Experience compares equal within this (the Wiki lists spell and action experience to one decimal). */
        const val XP_EPSILON = 0.001

        private val LAST_NPC = AttrKey<LastNpc>("diary.last_npc")
        private val CAST_UNTIL = AttrKey<LongArray>("diary.cast_until")
        private val SEQ_PROGRESS = AttrKey<IntArray>("diary.seq_progress")
    }
}
