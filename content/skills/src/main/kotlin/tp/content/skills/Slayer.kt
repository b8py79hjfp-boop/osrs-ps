package tp.content.skills

import tp.plugin.AttackCheck
import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.NpcRef
import tp.plugin.OpenShopRequest
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.SharedAttrs
import tp.plugin.Stats
import tp.plugin.chatNpc
import tp.plugin.chatPlayer
import tp.plugin.choice
import tp.symbols.Invs
import tp.symbols.Objs
import tp.symbols.Varbits
import tp.symbols.Varps
import tp.util.CoordGrid

/**
 * Slayer (docs/mechanics/slayer.md). A master's "Assignment" op (and "I need another assignment." in its conversation) rolls
 * a task from the master's weighted list ([SlayerData.TASKS]) among the tasks the player's levels, quests and bought rewards
 * allow ([SlayerAssign]), and stores the task in the cache's Slayer variables ([SlayerState]). A kill of a monster of the task's
 * group by the player credited with it ([DeathEvent.lootFor]) counts down the task and gives the monster's Slayer xp; the last
 * kill pays the task streak and the master's Slayer reward points. "Rewards" opens the Slayer Rewards interface
 * ([SlayerRewards]); superior monsters ([SlayerSuperiors]) and boss tasks are part of the rolls. Monsters need their Slayer level
 * to be attacked ([AttackCheck]). The enchanted gem "Check" op reports the task.
 */
public class Slayer : ContentPlugin {
    @Volatile
    private var loaded: SlayerRuntime? = null

    private fun runtime(): SlayerRuntime = loaded ?: error("Slayer data is loaded at world start")

    private val assign = SlayerAssign(::runtime)
    private val superiors = SlayerSuperiors(::runtime)
    private val rewards = SlayerRewards(::runtime, assign)

    override fun ContentScope.register() {
        onWorldStart {
            if (loaded == null) loaded = SlayerRuntime(SlayerCache(this))
            for (m in SlayerData.PLACED) spawnNpc(m.npc, CoordGrid(m.x, m.z, m.level), 0)
        }
        for (master in SlayerData.MASTERS) {
            for (name in master.names) {
                onOpNpcNamed(name, OP_ASSIGNMENT) { npc -> assignment(npc, master) }
                onOpNpcNamed(name, OP_TALK) { npc -> talk(npc, master) }
                onOpNpcNamed(name, OP_REWARDS) { rewards.open(player, master) }
            }
        }
        with(rewards) { register() }
        onItemOp(Objs.SLAYER_GEM, OP_CHECK) { click -> gemCheck(click) }
        events.on<DeathEvent> { death -> killed(death) }
        events.on<AttackCheck> { attack -> attackCheck(attack) }
        events.on<tp.plugin.SlayerTaskCheck> { check -> check.onTask = check.onTask || onTask(check.player, check.npc) }
    }

    // ---- conversation

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
                when (choice("Select an Option", LOOK_REWARDS, LOOK_SHOP, LOOK_CANCEL)) {
                    1 -> {
                        chatPlayer(LOOK_REWARDS)
                        rewards.open(player, master)
                    }

                    2 -> {
                        chatPlayer(LOOK_SHOP)
                        player.events.publish(OpenShopRequest(player, npc.type))
                    }

                    3 -> {
                        chatPlayer(LOOK_CANCEL)
                    }
                }
            }

            3 -> {
                chatPlayer(PICK_NOTHING)
            }
        }
    }

    private suspend fun PlayerScript.assignment(npc: NpcRef, master: SlayerMaster) {
        val p = player
        if (SlayerState.hasTask(p)) {
            if (pendingBoss(p)) return askBossAmount(npc)
            if (master.key == TURAEL) return changeTask(npc, master)
            val noun = assign.taskName(p)
            return chatNpc(
                npc,
                "You're still hunting $noun; you have ${SlayerState.left(p)} to go. Come back when you've finished your task.",
            )
        }
        val refusal = refusal(p, master)
        if (refusal != null) chatNpc(npc, refusal) else newTask(npc, master)
    }

    /** Why [master] will not give [p] a task (levels), or null. */
    private fun refusal(p: PlayerRef, master: SlayerMaster): String? =
        when {
            p.combatLevel < master.combat -> {
                "You need a combat level of at least ${master.combat} to be assigned tasks by me."
            }

            p.baseLevel(Stats.SLAYER) < master.slayer -> {
                "You need a Slayer level of at least ${master.slayer} to be assigned tasks by me."
            }

            master.key == SlayerAssign.WILDERNESS_MASTER && p.baseLevel(Stats.SLAYER) <= 1 -> {
                "You haven't learnt the basics of Slayer yet. Speak to Turael in Burthorpe first."
            }

            else -> {
                null
            }
        }

    /** Rolls and gives a task of [master] (the player has none). */
    private suspend fun PlayerScript.newTask(npc: NpcRef, master: SlayerMaster) {
        val p = player
        when (val pick = assign.roll(p, master) ?: return chatNpc(npc, "I have no tasks for you at the moment.")) {
            is SlayerPick.Regular -> {
                val amount = assign.amount(p, pick.task, pick.row)
                val where = assign.give(p, master, pick, amount)
                chatNpc(npc, "Your new task is to kill $amount ${pick.row.name}$where.")
            }

            is SlayerPick.Boss -> {
                SlayerState.set(p, SlayerCache.BOSS_TASK_ID, -1, pick.boss.id, master.id)
                askBossAmount(npc)
            }
        }
    }

    /**
     * A boss task waits for its amount (Wiki "Boss Slayer": the master "will ask the player to select the amount that they want to
     * kill", 3-35, Barrows 3-36; "if the player ... cancels out of the dialogue, the Slayer master will remind the player of it").
     * OPEN-SL12: the Combat Achievement tiers that raise the cap by 5 are not read.
     */
    private suspend fun PlayerScript.askBossAmount(npc: NpcRef) {
        val boss = SlayerData.BOSSES.firstOrNull { it.id == SlayerState.bossId(player) } ?: return
        val max = if (boss.id == BARROWS_BOSS) BARROWS_MAX else BOSS_MAX
        chatNpc(npc, "You have been assigned ${boss.name}. How many would you like to kill?")
        player.countDialog("How many would you like to kill? ($BOSS_MIN-$max)") { n -> bossAmount(this, n, max) }
    }

    private fun bossAmount(p: PlayerRef, n: Int, max: Int) {
        if (!pendingBoss(p)) return
        if (n !in BOSS_MIN..max) return p.message("Choose between $BOSS_MIN and $max kills; talk to the Slayer master again.")
        p.setVarp(Varps.SLAYER_COUNT, n)
        p.message("Your new task is to kill $n ${assign.taskName(p)}.")
        rewards.sync(p, force = false)
    }

    private fun pendingBoss(p: PlayerRef): Boolean = SlayerState.taskId(p) == SlayerCache.BOSS_TASK_ID && SlayerState.left(p) < 0

    /**
     * Turael changes a task of another master into one of his own (Wiki "Turael", Task changing): not one he could assign himself
     * and not his own, and "your task streak will also be reset to zero" (Krystilia's tasks reset the Wilderness streak). Spria cannot.
     * OPEN-SL4: the wording is not sourced.
     */
    private suspend fun PlayerScript.changeTask(npc: NpcRef, master: SlayerMaster) {
        val p = player
        val from = assign.masterOf(SlayerState.masterId(p))
        val mine = from?.key == master.key || assign.gives(p, master, SlayerState.taskId(p))
        if (mine) {
            val noun = assign.taskName(p)
            return chatNpc(
                npc,
                "You're still hunting $noun; you have ${SlayerState.left(p)} to go. Come back when you've finished your task.",
            )
        }
        val wild = from?.key == SlayerAssign.WILDERNESS_MASTER
        chatNpc(npc, "I can give you an easier task, but your streak of ${SlayerState.streak(p, wild)} tasks will be lost. Are you sure?")
        if (choice("Select an Option", CHANGE_YES, CHANGE_NO) != 1) return
        chatPlayer(CHANGE_YES)
        SlayerState.clear(p)
        SlayerState.setStreak(p, wild, 0)
        newTask(npc, master)
    }

    // ---- kills

    /** The monster row of the player's current task that [npc] belongs to, or null if [npc] is not on the task. */
    private fun taskMonster(player: PlayerRef, npc: NpcRef): SlayerMonster? {
        if (SlayerState.left(player) <= 0) return null
        val monsters = SlayerData.MONSTERS[npc.type] ?: return null
        val id = SlayerState.taskId(player)
        if (id == SlayerCache.BOSS_TASK_ID) {
            val boss = SlayerData.BOSSES.firstOrNull { it.id == SlayerState.bossId(player) } ?: return null
            return if (npc.displayName.lowercase() in boss.npcs) monsters.firstOrNull { it.group == SlayerAssign.BOSS_GROUP } else null
        }
        val groups = runtime().groupsOf[id] ?: return null
        return monsters.firstOrNull { it.group in groups }
    }

    /** Whether [npc] belongs to [player]'s current task (the same match as a kill uses). */
    private fun onTask(player: PlayerRef, npc: NpcRef): Boolean = taskMonster(player, npc) != null

    private fun killed(death: DeathEvent) {
        val npc = death.entity as? NpcRef ?: return
        val player = death.lootFor ?: return
        val monster = taskMonster(player, npc)
        val wild = SlayerState.masterId(player) == assign.idOf(SlayerAssign.WILDERNESS_MASTER)
        val counted = monster?.takeIf { !(wild && player[SharedAttrs.IN_WILDERNESS] != true) }
        if (counted != null) {
            player.addXp(Stats.SLAYER, counted.xp)
            val left = SlayerState.left(player) - 1
            if (left > 0) player.setVarp(Varps.SLAYER_COUNT, left) else complete(player)
            rewards.sync(player, force = false)
        }
        if (superiors.died(player, npc, counted != null)) return
        if (counted != null) superiors.rollSpawn(player, npc)
    }

    /** The task is done: clears it, counts the streak of its master and pays the points (Wiki "Slayer reward point", "Boss Slayer"). */
    private fun complete(p: PlayerRef) {
        val master = assign.masterOf(SlayerState.masterId(p))
        val boss = SlayerState.taskId(p) == SlayerCache.BOSS_TASK_ID
        SlayerState.clear(p)
        // Wiki "Boss Slayer": "Upon finishing a boss task, the player will get an additional 5,000 Slayer experience".
        if (boss) p.addXp(Stats.SLAYER, BOSS_BONUS_XP)
        if (master == null) return
        val wild = master.key == SlayerAssign.WILDERNESS_MASTER
        val streak = SlayerState.streak(p, wild) + 1
        SlayerState.setStreak(p, wild, streak)
        val points = pointsFor(p, master, streak)
        if (points <= 0) {
            p.message("You've completed $streak tasks in a row; return to a Slayer master.")
            return
        }
        val total = SlayerState.points(p) + points
        SlayerState.setPoints(p, total)
        p.message(
            "You've completed $streak tasks in a row and received $points points, giving you a total of ${SlayerState.points(p)}; " +
                "return to a Slayer master.",
        )
    }

    /**
     * Points of the [streak]th task: none before the fifth, then the largest interval it is a multiple of (Wiki "Slayer reward
     * point"); with the Elite diary of Nieve's or Konar's region the table is the diary's.
     */
    private fun pointsFor(p: PlayerRef, master: SlayerMaster, streak: Int): Int {
        if (streak < FIRST_POINTS_TASK) return 0
        val table = master.elite?.takeIf { p.varbit(master.eliteDiary) != 0 } ?: master.points
        for (i in SlayerData.INTERVALS.indices.reversed()) {
            if (streak % SlayerData.INTERVALS[i] == 0) return table[i]
        }
        return 0
    }

    // ---- attacks

    /**
     * A Slayer monster needs its Slayer level to be attacked (Wiki `slaylvl` of the monster's infobox), and a superior belongs to the
     * player whose kill spawned it (Wiki "Superior slayer monster").
     */
    private fun attackCheck(check: AttackCheck) {
        val player = check.attacker as? PlayerRef ?: return
        val npc = check.target as? NpcRef ?: return
        superiors.denial(player, npc)?.let { return check.deny(it) }
        val monsters = SlayerData.MONSTERS[npc.type] ?: return
        val need = monsters.maxOf { it.slayer }
        if (need > player.level(Stats.SLAYER)) check.deny("You need a Slayer level of $need to attack this monster.")
    }

    private fun PlayerRef.gemCheck(click: ButtonClick) {
        val p = this
        val inv = p.inv(Invs.INV)
        if (click.slot !in 0 until inv.size || inv.obj(click.slot) != click.obj) return
        val left = SlayerState.left(p)
        val wild = SlayerState.masterId(p) == assign.idOf(SlayerAssign.WILDERNESS_MASTER)
        val streak = SlayerState.streak(p, wild)
        if (!SlayerState.hasTask(p) || left <= 0) {
            p.message("You do not currently have a Slayer task. Your task streak is $streak.")
            return
        }
        p.message("You're assigned to kill ${assign.taskName(p)}; only $left more to go. Your task streak is $streak.")
    }

    private companion object {
        const val OP_ASSIGNMENT = "Assignment"
        const val OP_TALK = "Talk-to"
        const val OP_REWARDS = "Rewards"
        const val OP_CHECK = "Check"

        /** The only master who changes other masters' tasks (Wiki "Turael"; Aya is the same master, Spria cannot). */
        const val TURAEL = "Turael"

        /** Slayer reward points are not awarded for the first four tasks (Wiki "Slayer reward point": "rewarded for their fifth"). */
        const val FIRST_POINTS_TASK = 5

        /** Boss task amounts: "anywhere from 3-35 kills", Barrows "3-36" (Wiki "Boss Slayer"); the Barrows brothers are boss 17. */
        const val BOSS_MIN = 3
        const val BOSS_MAX = 35
        const val BARROWS_MAX = 36
        const val BARROWS_BOSS = 17

        /** Wiki "Boss Slayer": "an additional 5,000 Slayer experience regardless of task length". */
        const val BOSS_BONUS_XP = 5000.0

        /** The shared greeting of the Wiki transcript of Vannaka, Chaeldar, Mazchna and Nieve (npc-dialogue.tsv tree 40). */
        const val GREETING = "'Ello, and what are you after then?"
        const val PICK_ASSIGNMENT = "I need another assignment."
        const val PICK_REWARDS = "Have you any rewards for me, or anything to trade?"
        const val PICK_NOTHING = "Er... Nothing..."
        const val REWARDS_REPLY = "I have quite a few rewards you can earn, and a wide variety of Slayer equipment for sale."
        const val LOOK_REWARDS = "Look at rewards."
        const val LOOK_SHOP = "Look at shop."
        const val LOOK_CANCEL = "Cancel."
        const val CHANGE_YES = "Yes, give me an easier task."
        const val CHANGE_NO = "No, I'll keep my task."
    }
}
