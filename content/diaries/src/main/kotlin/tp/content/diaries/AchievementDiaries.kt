package tp.content.diaries

import tp.plugin.ButtonClick
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DeathEvent
import tp.plugin.ItemsCreated
import tp.plugin.LocInteraction
import tp.plugin.NpcInteraction
import tp.plugin.NpcRef
import tp.plugin.PlayerRef
import tp.plugin.PlayerScript
import tp.plugin.TimerId
import tp.plugin.XpGained
import tp.plugin.chatNpc
import tp.plugin.choice
import tp.plugin.commands.economyToolsAllowed
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Npcs
import tp.symbols.Objs
import tp.symbols.Varbits

/**
 * Achievement diaries (docs/mechanics/achievement-diaries.md): twelve diaries of four tiers. Data from the Wiki
 * (`diary-*.tsv`), state in saved values and in the varbits the client's journal scripts read.
 *
 * - [Diaries.complete] is the one API content calls when a task's trigger happens; this plugin calls it for the kill tasks
 *   through [DeathEvent] ([DiaryHooks]).
 * - The side journal's diary list (interface `area_task`, drawn by the client) is answered here: a click on an area opens its
 *   task list (AD8). [open] shows that list for the account summary's row.
 * - The taskmaster's Talk-to claims a finished tier (AD5); the tier's Antique lamp is used through the experience
 *   interface (AD9, [DiaryLamps]).
 */
public class AchievementDiaries : ContentPlugin {
    @Volatile private var loaded: Diaries? = null

    @Volatile private var lamps: DiaryLamps? = null

    @Volatile private var triggers: DiaryTriggers? = null

    @Volatile private var dialogue: DiaryDialogue? = null

    @Volatile private var killIndex: Map<String, List<Pair<KillHook, Int>>> = emptyMap()

    override fun ContentScope.register() {
        onWorldStart {
            val diaries = Diaries(DiaryData.load(), QuestGate.load())
            DiaryHooks.verify(diaries.data)
            killIndex = DiaryHooks.index()
            triggers = DiaryTriggers(diaries, DiaryTriggerData.load(diaries.data))
            dialogue = DiaryDialogue.load()
            loaded = diaries
            lamps = DiaryLamps(diaries, enumInts(DiaryLamps.COMPONENT_STAT_ENUM)).also { it.verify() }
        }
        onLogin {
            loaded?.syncAll(this)
            timer(WATCH, DiaryTriggers.POLL_TICKS, soft = true) { triggers?.poll(this) }
        }
        events.on<DeathEvent> { e -> kill(e) }
        events.on<LocInteraction> { e -> triggers?.loc(e) }
        events.on<NpcInteraction> { e -> triggers?.npc(e) }
        events.on<ItemsCreated> { e -> triggers?.items(e) }
        events.on<XpGained> { e -> triggers?.xp(e) }
        // The rows are children of the list layer; which layer holds them is not in the cache's decoded fields, so every
        // layer of the interface answers (OPEN-AD10). The row index is the area's index in cache enum 595.
        for (component in LIST_COMPONENTS) onButton(component, JOURNAL_OP) { c -> journalClick(c) }
        for ((area, npcs) in TASKMASTERS.withIndex()) {
            for (npc in npcs) onOpNpc(npc, TALK_OP) { master -> talk(area, master) }
        }
        onCommand("diary") { args -> command(args) }
        registerLamps()
    }

    /** Lamp handlers: the "Rub" of each tier lamp, the skill buttons and Confirm of interface 240 (AD9). */
    private fun ContentScope.registerLamps() {
        for (obj in LAMP_OBJS) onItemOp(obj, RUB) { c -> lamps?.rub(this, c) }
        for (child in DiaryLamps.FIRST_SKILL_COMPONENT until DiaryLamps.FIRST_SKILL_COMPONENT + DiaryLamps.SKILL_COMPONENTS) {
            onButton(Components.Xpreward.UNIVERSE + child) { lamps?.select(this, child) }
        }
        onButton(Components.Xpreward.CONFIRM) { lamps?.confirm(this) }
        onModalClosed(Interfaces.XPREWARD) { lamps?.closed(this) }
    }

    /**
     * For the account summary's "Achievement Diaries" row (the caller routes the click): shows the journal's Tasks tab, which
     * the client fills with the twelve diaries and their progress from the varbits (AD8; same as the Tasks button of
     * `JournalTab`).
     */
    internal fun open(player: PlayerRef) {
        player.setVarbit(Varbits.SIDE_JOURNAL_TAB, TAB_TASKS)
        player.openOverlay(Components.Side_Journal.TAB_CONTAINER, Interfaces.AREA_TASK)
    }

    private fun kill(e: DeathEvent) {
        val diaries = loaded ?: return
        val npc = e.entity as? NpcRef ?: return
        val player = DiaryHooks.creditedTo(e) ?: return
        val at = npc.coord
        triggers?.kill(player, npc.displayName, at.x, at.z, at.level)
        val matches = killIndex[DiaryHooks.key(npc.displayName)] ?: return
        DiaryHooks.apply(diaries, player, matches)
    }

    // ---- journal

    private fun PlayerRef.journalClick(click: ButtonClick) {
        val diaries = loaded ?: return
        if (click.slot !in diaries.data.areas.indices) return
        openJournal(diaries, diaries.data.areas[click.slot])
    }

    /**
     * Opens one diary's task list as the quest journal window: the area as title, a heading per tier with its count, then the
     * tasks coloured red (open) or green (done), wrapped to the window (OPEN-AD11: the real list's layout is server-drawn and
     * not in the cache).
     */
    private fun PlayerRef.openJournal(diaries: Diaries, area: DiaryArea) {
        val lines = ArrayList<String>()
        for ((tier, tasks) in area.tasks.withIndex()) {
            val done = diaries.doneCount(this, area.index, tier)
            lines += "<col=ffffff>${TIER_NAMES[tier].replaceFirstChar(Char::uppercase)} tasks ($done/${tasks.size})</col>"
            for (task in tasks) {
                val colour = if (diaries.isDone(this, task.id)) DONE_COLOUR else OPEN_COLOUR
                for (line in wrap("${task.id.index}. ${task.text}")) lines += "<col=$colour>$line</col>"
            }
            lines += ""
        }
        openModal(Components.Toplevel.MAINMODAL, Interfaces.QUESTJOURNAL)
        ifText(Components.Questjournal.TITLE, "${area.name} Journal")
        for ((i, line) in lines.take(MAX_LINES).withIndex()) ifText(Components.Questjournal.QJ1 + i, line)
    }

    private fun wrap(text: String): List<String> {
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > WRAP_WIDTH) {
                out += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    // ---- taskmaster

    /**
     * Talk-to on a taskmaster (AD5, AD6, OPEN-AD12): with a tier to claim, the Wiki's "<Tier> tasks completed" conversation, which
     * hands the reward over where the transcript says so; otherwise the Wiki's standard conversation, whose "Can I have another"
     * option gives the tier item again. A taskmaster whose transcript has no such part (Karamja's claim) gets the claim menu.
     */
    private suspend fun PlayerScript.talk(areaIndex: Int, master: NpcRef) {
        val diaries = loaded ?: return
        val script = dialogue ?: return
        val claimable = diaries.claimable(player, areaIndex)
        val actions = TaskmasterActions(diaries, player, areaIndex, claimable)
        when {
            claimable != null && script.has(areaIndex, TIER_NAMES[claimable]) -> {
                with(script) { play(areaIndex, TIER_NAMES[claimable], master, actions) }
            }

            claimable == null && script.has(areaIndex, STANDARD) -> {
                with(script) { play(areaIndex, STANDARD, master, actions) }
            }

            else -> {
                menuTalk(diaries, areaIndex, master)
            }
        }
    }

    /** What one conversation can do to the diary: hand over the claimable tier, give the tier item again. */
    private class TaskmasterActions(
        private val diaries: Diaries,
        private val player: PlayerRef,
        private val area: Int,
        private val tier: Int?,
    ) : DiaryDialogue.Actions {
        override fun claim(): Boolean = tier != null && diaries.claim(player, area, tier)

        override fun replace(): String? {
            val owned = diaries.highestClaimed(player, area) ?: return NOTHING_TO_REPLACE
            val item =
                diaries.data.areas[area]
                    .rewards[owned]
                    .item
            return when {
                diaries.owns(player, area, owned) -> "You already have your $item."
                diaries.replace(player, area, owned) -> null
                else -> "You haven't got room for it. Make space in your backpack and come back."
            }
        }

        override fun hasClaimed(): Boolean = diaries.highestClaimed(player, area) != null
    }

    /** The claim menu (claim / another item / how to claim / bye) for a part the transcript does not have. */
    private suspend fun PlayerScript.menuTalk(diaries: Diaries, areaIndex: Int, master: NpcRef) {
        val area = diaries.data.areas[areaIndex]
        val claimable = diaries.claimable(player, areaIndex)
        val owned = diaries.highestClaimed(player, areaIndex)
        val options = ArrayList<String>()
        val actions = ArrayList<Int>()
        if (claimable != null) {
            options += "I'd like to claim my ${TIER_NAMES[claimable]} reward."
            actions += ACTION_CLAIM
        }
        if (owned != null) {
            options += "Can I have another ${area.rewards[owned].item}?"
            actions += ACTION_REPLACE
        }
        options += "How do I claim the rewards?"
        actions += ACTION_HELP
        options += "Bye!"
        actions += ACTION_BYE
        val pick = choice("Select an Option", *options.toTypedArray())
        if (pick == 0) return
        when (actions[pick - 1]) {
            ACTION_CLAIM -> {
                claim(diaries, area, claimable ?: return, master)
            }

            ACTION_REPLACE -> {
                replace(diaries, area, owned ?: return, master)
            }

            ACTION_HELP -> {
                chatNpc(
                    master,
                    "Just complete the tasks in ${area.name} so they're ticked off, then come and speak to me for your rewards.",
                )
            }
        }
    }

    private suspend fun PlayerScript.claim(diaries: Diaries, area: DiaryArea, tier: Int, master: NpcRef) {
        if (diaries.claim(player, area.index, tier)) {
            chatNpc(master, "Well done! Here is your ${area.rewards[tier].item} and an antique lamp.")
        } else {
            chatNpc(master, "You haven't got room for your reward. Make space in your backpack and come back.")
        }
    }

    private suspend fun PlayerScript.replace(diaries: Diaries, area: DiaryArea, tier: Int, master: NpcRef) {
        val item = area.rewards[tier].item
        when {
            diaries.owns(player, area.index, tier) -> chatNpc(master, "You already have your $item.")
            diaries.replace(player, area.index, tier) -> chatNpc(master, "Here is another $item. Try not to lose it.")
            else -> chatNpc(master, "You haven't got room for it. Make space in your backpack and come back.")
        }
    }

    // ---- staff

    /** `::diary` opens the diary list; `::diary <area> <tier> <task>` (administrators) completes a task, for testing. */
    private fun PlayerRef.command(args: List<String>) {
        val diaries = loaded ?: return
        if (args.size < COMMAND_TASK_ARGS) return open(this)
        if (!economyToolsAllowed) return
        val area = args[0].toIntOrNull()
        val tier = args[1].toIntOrNull()
        val index = args[2].toIntOrNull()
        if (area == null || tier == null || index == null) return message("::diary <area 0-11> <tier 0-3> <task number>")
        val ok = diaries.complete(this, TaskId.of(area, tier, index))
        if (!ok) message("Nothing completed (unknown, already done or requirements not met).")
    }

    private companion object {
        /** Op 1 of the rows ("Open <area> Journal", client script 709). */
        const val JOURNAL_OP = 1
        const val TALK_OP = 1
        const val COMMAND_TASK_ARGS = 3

        /** `side_journal_tab` value of the Tasks sub-tab (RS Mod `SideJournalTab`; same as `JournalTab.TAB_TASKS`). */
        const val TAB_TASKS = 2

        /** `questjournal` has the line components `qj1..qj199`. */
        const val MAX_LINES = 199

        /** Characters per journal line before wrapping (OPEN-AD11: the window is about 450 pixels of 6-pixel glyphs). */
        const val WRAP_WIDTH = 70
        const val DONE_COLOUR = "00ff00"
        const val OPEN_COLOUR = "ff0000"

        const val STANDARD = "standard"

        /** Said when "another item" is chosen before any tier was claimed (the option is not offered then). */
        const val NOTHING_TO_REPLACE = "You haven't claimed anything from me yet."

        /** Soft timer that looks for entered areas and arrived spells (docs/mechanics/achievement-diaries.md, "Triggers"). */
        val WATCH = TimerId("diary_watch")

        const val ACTION_CLAIM = 0
        const val ACTION_REPLACE = 1
        const val ACTION_HELP = 2
        const val ACTION_BYE = 3

        /** Layers of interface `area_task` that may hold the area rows (script 709 fills one of them). */
        val LIST_COMPONENTS =
            intArrayOf(
                Components.Area_Task.TASKBOX,
                Components.Area_Task.BORDER,
                Components.Area_Task.SCROLLBAR,
                Components.Area_Task.COM_5,
                Components.Area_Task.UNIVERSE,
            )

        /** Taskmaster NPC types per area (cache NPCs named `*_diary`; Karamja's is Pirate Jackie the Fruit). */
        val TASKMASTERS: List<IntArray> =
            listOf(
                intArrayOf(Npcs.AGILITYARENA_TICKETTRADER_2OPS, Npcs.AGILITYARENA_TICKETTRADER_3OPS),
                intArrayOf(Npcs.ARDY_TWOPINTS_DIARY),
                intArrayOf(Npcs.WHITE_KNIGHT_DIARY),
                intArrayOf(Npcs.VIKING_FREM_DIARY),
                intArrayOf(Npcs.SEERS_DIARY_WEDGE),
                intArrayOf(Npcs.JARR_DESERT_DIARY),
                intArrayOf(Npcs.MIKASI_LUMBRIDGE_DIARY),
                intArrayOf(Npcs.LESABRE_MORT_DIARY),
                intArrayOf(Npcs.TOBY_VARROCK_DIARY),
                intArrayOf(Npcs.LESSER_FANATIC_DIARY),
                intArrayOf(Npcs.WESTERN_GNOME_CHILD_DIARY),
                intArrayOf(Npcs.ELISE_KOUREND_KEBOS_DIARY),
            )

        /** Backpack action of the lamps (cache iop text). */
        const val RUB = "Rub"

        /** The lamp objs of the tiers: Karamja's three lower ones (`atjun_*_lamp`) and the four `diary_*_lamp`. */
        val LAMP_OBJS =
            intArrayOf(
                Objs.ATJUN_EASY_LAMP,
                Objs.ATJUN_MED_LAMP,
                Objs.ATJUN_HARD_LAMP,
                Objs.DIARY_EASY_LAMP,
                Objs.DIARY_MED_LAMP,
                Objs.DIARY_HARD_LAMP,
                Objs.DIARY_ELITE_LAMP,
            )
    }
}
