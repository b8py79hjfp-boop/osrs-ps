package tp.content.skills

import tp.plugin.AttackCheck
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.util.CoordGrid

/**
 * Slayer (docs/mechanics/slayer.md). A master's "Assignment" op (and "I need another assignment." in its conversation) rolls
 * a task from the master's weighted list ([SlayerData.TASKS]) among the tasks the player's levels allow, and stores the task
 * (monster group, kills left, master) as persisted values. A kill of a monster of the task's group by the player credited
 * with it ([DeathEvent.lootFor]) counts down the task and gives the monster's Slayer xp; the last kill pays the task streak
 * and the master's Slayer reward points. Monsters need their Slayer level to be attacked ([AttackCheck]). The enchanted gem
 * "Check" op reports the task.
 */
public class Slayer : ContentPlugin {
    override fun ContentScope.register() {
        onWorldStart {
            for (m in SlayerData.PLACED) spawnNpc(m.npc, CoordGrid(m.x, m.z, m.level), 0)
        }
        for (master in SlayerData.MASTERS) {
            for (name in master.names) {
                onOpNpcNamed(name, OP_ASSIGNMENT) { npc -> assignment(npc, master) }
                onOpNpcNamed(name, OP_TALK) { npc -> talk(npc, master) }
            }
        }
        onItemOp(Objs.SLAYER_GEM, OP_CHECK) { click -> gemCheck(click) }
        events.on<DeathEvent> { death -> killed(death) }
        events.on<AttackCheck> { attack -> attackCheck(attack) }
        events.on<tp.plugin.SlayerTaskCheck> { check -> check.onTask = check.onTask || onTask(check.player, check.npc) }
    }

    private suspend fun PlayerScript.talk(npc: NpcRef, master: SlayerMaster) {
        chatNpc(npc, GREETING)
        when (choice("Select an Option", PICK_ASSIGNMENT, PICK_REWARDS, PICK_NOTHING)) {
            1 -> {
                chatPlayer(PICK_ASSIGNMENT)
                assignment(npc, master)
            }

            2 -> {
                chatPlayer(PICK_REWARDS)
                chatNpc(npc, REWARDS_REPLY)
            }

            3 -> {
                chatPlayer(PICK_NOTHING)
            }
        }
    }

    private suspend fun PlayerScript.assignment(npc: NpcRef, master: SlayerMaster) {
        val p = player
        val left = p.saved(LEFT_KEY) ?: 0
        val current = p.saved(GROUP_KEY)
        if (current != null && left > 0) {
            val noun = (TASK_BY_GROUP[current]?.name ?: "monsters").lowercase()
            return chatNpc(npc, "You're still hunting $noun; you have $left to go. Come back when you've finished your task.")
        }
        if (p.combatLevel < master.combat) {
            return chatNpc(npc, "You need a combat level of at least ${master.combat} to be assigned tasks by me.")
        }
        if (p.baseLevel(Stats.SLAYER) < master.slayer) {
            return chatNpc(npc, "You need a Slayer level of at least ${master.slayer} to be assigned tasks by me.")
        }
        if (master.key == WILDERNESS_MASTER && p.baseLevel(Stats.SLAYER) <= 1) {
            return chatNpc(npc, "You haven't learnt the basics of Slayer yet. Speak to Turael in Burthorpe first.")
        }
        val task = roll(p, master) ?: return chatNpc(npc, "I have no tasks for you at the moment.")
        val amount = task.min + p.random(task.max - task.min + 1)
        p.setSaved(GROUP_KEY, key(task.group))
        p.setSaved(LEFT_KEY, amount.toLong())
        p.setSaved(MASTER_KEY, key(master.key))
        chatNpc(npc, "Your new task is to kill $amount ${task.name.lowercase()}.")
    }

    /** One weighted pick among the tasks of [master] that [p] may be assigned (Wiki "Slayer task": ineligible tasks add no weight). */
    private fun roll(p: PlayerRef, master: SlayerMaster): SlayerTask? {
        val pool = (SlayerData.TASKS[master.key] ?: return null).filter { eligible(p, it) }
        val total = pool.sumOf { it.weight }
        if (total <= 0) return null
        var pick = p.random(total)
        for (task in pool) {
            pick -= task.weight
            if (pick < 0) return task
        }
        return pool.last()
    }

    /**
     * Whether [p] may be assigned [t]: the levels the Wiki lists, no unverifiable quest or unlock ([QUESTS_ASSUMED_COMPLETE],
     * docs/mechanics/slayer.md SL3) and at least one monster to kill for it.
     */
    private fun eligible(p: PlayerRef, t: SlayerTask): Boolean {
        if (t.other && !QUESTS_ASSUMED_COMPLETE) return false
        if (p.combatLevel < t.combat || p.baseLevel(Stats.SLAYER) < t.slayer || t.max <= 0) return false
        return t.skills.all { (stat, level) -> p.baseLevel(stat) >= level }
    }

    /** Whether [npc] belongs to [player]'s current task (the same match as a kill uses). */
    private fun onTask(player: PlayerRef, npc: NpcRef): Boolean {
        val group = player.saved(GROUP_KEY) ?: return false
        return SlayerData.MONSTERS[npc.type]?.any { key(it.group) == group } == true
    }

    private fun killed(death: DeathEvent) {
        val npc = death.entity as? NpcRef ?: return
        val player = death.lootFor ?: return
        val monsters = SlayerData.MONSTERS[npc.type] ?: return
        val group = player.saved(GROUP_KEY) ?: return
        val monster = monsters.firstOrNull { key(it.group) == group } ?: return
        if (player.saved(MASTER_KEY) == key(WILDERNESS_MASTER) && player[SharedAttrs.IN_WILDERNESS] != true) return
        player.addXp(Stats.SLAYER, monster.xp)
        val left = (player.saved(LEFT_KEY) ?: 0) - 1
        if (left > 0) player.setSaved(LEFT_KEY, left) else complete(player)
    }

    /** The task is done: clears it, counts the streak of its master and pays the points (Wiki "Slayer reward point"). */
    private fun complete(p: PlayerRef) {
        val master = SlayerData.MASTERS.firstOrNull { key(it.key) == p.saved(MASTER_KEY) }
        p.setSaved(GROUP_KEY, null)
        p.setSaved(LEFT_KEY, null)
        p.setSaved(MASTER_KEY, null)
        if (master == null) return
        val streakKey = if (master.key == WILDERNESS_MASTER) WILDERNESS_STREAK_KEY else STREAK_KEY
        val streak = (p.saved(streakKey) ?: 0) + 1
        p.setSaved(streakKey, streak)
        val points = pointsFor(master, streak)
        if (points <= 0) {
            p.message("You've completed $streak tasks in a row; return to a Slayer master.")
            return
        }
        val total = (p.saved(POINTS_KEY) ?: 0) + points
        p.setSaved(POINTS_KEY, total)
        p.message(
            "You've completed $streak tasks in a row and received $points points, giving you a total of $total; return to a Slayer master.",
        )
    }

    /** Points of the [streak]th task: none before the fifth, then the largest interval it is a multiple of (Wiki "Slayer reward point"). */
    private fun pointsFor(master: SlayerMaster, streak: Long): Int {
        if (streak < FIRST_POINTS_TASK) return 0
        for (i in SlayerData.INTERVALS.indices.reversed()) {
            if (streak % SlayerData.INTERVALS[i] == 0L) return master.points[i]
        }
        return 0
    }

    /** A Slayer monster needs its Slayer level to be attacked (Wiki `slaylvl` of the monster's infobox). */
    private fun attackCheck(check: AttackCheck) {
        val player = check.attacker as? PlayerRef ?: return
        val npc = check.target as? NpcRef ?: return
        val monsters = SlayerData.MONSTERS[npc.type] ?: return
        val need = monsters.maxOf { it.slayer }
        if (need > player.level(Stats.SLAYER)) check.deny("You need a Slayer level of $need to attack this monster.")
    }

    private fun PlayerRef.gemCheck(click: ButtonClick) {
        val p = this
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val left = p.saved(LEFT_KEY) ?: 0
        val group = p.saved(GROUP_KEY)
        val streak = p.saved(STREAK_KEY) ?: 0
        if (group == null || left <= 0) {
            p.message("You do not currently have a Slayer task. Your task streak is $streak.")
            return
        }
        val noun = (TASK_BY_GROUP[group]?.name ?: "monsters").lowercase()
        p.message("You're assigned to kill $noun; only $left more to go. Your task streak is $streak.")
    }

    private companion object {
        const val OP_ASSIGNMENT = "Assignment"
        const val OP_TALK = "Talk-to"
        const val OP_CHECK = "Check"

        /** Persisted values (docs/mechanics/slayer.md SL1): the task's group key hash, kills left, the master key hash. */
        const val GROUP_KEY = "slayer.group"
        const val LEFT_KEY = "slayer.left"
        const val MASTER_KEY = "slayer.master"
        const val STREAK_KEY = "slayer.streak"
        const val WILDERNESS_STREAK_KEY = "slayer.wstreak"
        const val POINTS_KEY = "slayer.points"

        /** The master whose tasks count only in the Wilderness and have their own streak (Wiki "Krystilia"). */
        const val WILDERNESS_MASTER = "Krystilia"

        /** Slayer reward points are not awarded for the first four tasks (Wiki "Slayer reward point": "rewarded for their fifth"). */
        const val FIRST_POINTS_TASK = 5L

        /**
         * OPEN-SL2: the server has no quest or reward-unlock state, so a task whose Wiki requirement is a quest or an unlock
         * (Priest in Peril, Dragon Slayer I, "Watch the birdie", ...) is never assigned. Set true to assign them anyway.
         */
        const val QUESTS_ASSUMED_COMPLETE = false

        /** The shared greeting of the Wiki transcript of Vannaka, Chaeldar, Mazchna and Nieve (npc-dialogue.tsv tree 40). */
        const val GREETING = "'Ello, and what are you after then?"
        const val PICK_ASSIGNMENT = "I need another assignment."
        const val PICK_REWARDS = "Have you any rewards for me, or anything to trade?"
        const val PICK_NOTHING = "Er... Nothing..."
        const val REWARDS_REPLY = "I have quite a few rewards you can earn, and a wide variety of Slayer equipment for sale."

        /** Stable key of a name for the persisted values (`String.hashCode` is specified, so it survives restarts). */
        fun key(name: String): Long = name.hashCode().toLong()

        /** A task row of any master by group key hash (for the task's name in messages). */
        val TASK_BY_GROUP: Map<Long, SlayerTask> by lazy {
            val out = HashMap<Long, SlayerTask>()
            for (task in SlayerData.TASKS.values.flatten()) {
                val previous = out.put(key(task.group), task)
                check(previous == null || previous.group == task.group) { "slayer group key collision: ${task.group}" }
            }
            out
        }
    }
}
