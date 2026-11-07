package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.IfEvents
import tp.plugin.PlayerRef
import tp.plugin.Stats
import tp.symbols.Components
import tp.symbols.Interfaces
import tp.symbols.Objs

/**
 * The notice boards (interfaces 941 and 942) and the task list of the captain's log (docs/mechanics/sailing-port-tasks.md
 * PT4, PT9). The client builds both screens itself from cache data and from what the server tells it:
 *  - board 941: client script 8912 draws up to 8 tasks given as `Port_Task` db row ids; a task's "Select" op arrives as a
 *    button click on [Components.Port_Task_Board.CONTAINER] with the child index of the task's model,
 *  - info panel 942: client script 8901 fills it for one task row and labels its button "Accept task" or "Cancel task" by
 *    the slot varbits; the button arrives as a click on [Components.Port_Task_Info.SELECT],
 *  - captain's log 935: script 8707 lists the five slots from the slot varbits alone; a slot's "Select" op arrives as a click
 *    on [Components.Sailing_Log.TASKS_CLICK] with the slot number as child index, and its panel is 942 in the log's
 *    `TASK_WINDOW`.
 */
internal class PortBoards(private val state: SailingState, private val tasks: PortTasks) {
    /** What a player is looking at: a board's tasks or the log. */
    private class View(val tasks: List<PortTask>, val inLog: Boolean) {
        var selected: PortTask? = null
        var slot: Int = -1
        var infoOpen: Boolean = false
    }

    fun ContentScope.register() {
        for (loc in PortSites.BOARD_LOCS) {
            onOpLoc(loc, OP_INSPECT) {
                state.charting.boardOpened(player, it.coord)
                openBoard(player, state.ports.ofBoard(loc) ?: return@onOpLoc)
            }
        }
        onButton(Components.Port_Task_Board.CONTAINER) { click -> select(this, click.slot) }
        onButton(Components.Port_Task_Info.SELECT) { primary(this) }
        onButton(Components.Port_Task_Info.CANCEL) { back(this) }
        onModalClosed(Interfaces.PORT_TASK_BOARD) { this[VIEW] = null }
        for (log in listOf(Objs.SAILING_LOG, Objs.SAILING_LOG_INITIAL)) onItemOp(log, "View-tasks") { openLog(this) }
        onButton(Components.Sailing_Log.TASKS_CLICK) { click -> selectSlot(this, click.slot) }
        onModalClosed(Interfaces.SAILING_LOG) { this[VIEW] = null }
    }

    // ---- notice board

    private fun openBoard(p: PlayerRef, dock: Int) {
        val shown = state.catalog.boardFor(p, dock)
        p[VIEW] = View(shown, inLog = false)
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.PORT_TASK_BOARD)
        p.ifEvents(Components.Port_Task_Board.CONTAINER, 0 until PortCatalog.BOARD_SIZE * CHILDREN_PER_TASK, IfEvents.op(OP_SELECT))
        p.clientScript(CS_BUILD_BOARD, *IntArray(PortCatalog.BOARD_SIZE) { shown.getOrNull(it)?.row ?: -1 }.toTypedArray())
    }

    /**
     * A click on a board task. The client builds [CHILDREN_PER_TASK] components per task and the task's "Select" op is the first
     * of them (scripts 8916 and 8917 count their child ids up that way), so task n is child n * [CHILDREN_PER_TASK]
     * (OPEN-PT-IF1: derived from the script, not seen in a real client).
     */
    private fun select(p: PlayerRef, child: Int) {
        val view = p[VIEW]?.takeIf { !it.inLog } ?: return
        if (child < 0 || child % CHILDREN_PER_TASK != 0) return
        val task = view.tasks.getOrNull(child / CHILDREN_PER_TASK) ?: return
        view.selected = task
        showInfo(p, view, Components.Port_Task_Board.POPUP, task)
    }

    // ---- captain's log

    /** The log's "View-tasks" option: the log (interface 935) with the task panel mounted in its window. */
    private fun openLog(p: PlayerRef) {
        p[VIEW] = View(emptyList(), inLog = true)
        p.clientScript(CS_MAINMODAL_BACKGROUND, -1, -1)
        p.openModal(Components.Toplevel.MAINMODAL, Interfaces.SAILING_LOG)
        p.ifEvents(Components.Sailing_Log.TASKS_CLICK, 0 until PortTaskSave.SLOTS, IfEvents.op(OP_SELECT))
        mountInfo(p, Components.Sailing_Log.TASK_WINDOW)
        p[VIEW]?.infoOpen = true
    }

    private fun selectSlot(p: PlayerRef, slot: Int) {
        val view = p[VIEW]?.takeIf { it.inLog } ?: return
        val ts = PortTaskSave.get(p, slot)?.takeIf { slot < PortTaskSave.slotsAt(p.baseLevel(Stats.SAILING)) }
        val task = ts?.let { state.catalog.task(it.taskId) } ?: return
        view.slot = slot
        view.selected = task
    }

    // ---- info panel

    private fun showInfo(p: PlayerRef, view: View, window: Int, task: PortTask) {
        if (!view.infoOpen) {
            mountInfo(p, window)
            view.infoOpen = true
        }
        p.clientScript(CS_FILL_INFO, window, task.row)
    }

    /** Opens the info panel (942) inside [window] and enables its two buttons. */
    private fun mountInfo(p: PlayerRef, window: Int) {
        p.openOverlay(window, Interfaces.PORT_TASK_INFO)
        p.ifEvents(Components.Port_Task_Info.SELECT, -1..-1, IfEvents.op(OP_SELECT))
        p.ifEvents(Components.Port_Task_Info.CANCEL, -1..-1, IfEvents.op(OP_SELECT))
    }

    /** The info panel's main button: accept the selected board task, or cancel the selected task when it is already taken. */
    private fun primary(p: PlayerRef) {
        val view = p[VIEW] ?: return
        val task = view.selected ?: return
        val slot = tasks.slotOf(p, task.id)
        if (slot >= 0) {
            if (tasks.cancel(p, slot) && view.inLog) view.selected = null
        } else if (!view.inLog) {
            tasks.accept(p, task)
        }
        if (!view.inLog) p.clientScript(CS_FILL_INFO, Components.Port_Task_Board.POPUP, task.row)
    }

    private fun back(p: PlayerRef) {
        p[VIEW]?.selected = null
    }

    private companion object {
        val VIEW = AttrKey<View>("sailing.taskview")

        /** Inspect is op 1 of the notice board locs (cache locs 60287-60309). */
        const val OP_INSPECT = 1

        /** The "Select" / button op the board, log and info panel use (script 8917: `cc_setop 1 "Select"`). */
        const val OP_SELECT = 1

        /** Client script `toplevel_mainmodal_open`: sets the main modal background (colour, transparency; -1 = default). */
        const val CS_MAINMODAL_BACKGROUND = 2524

        /** Client script 8912: builds the board from 8 task db row ids (-1 = no task); rev 242 disassembly. */
        const val CS_BUILD_BOARD = 8912

        /** Client script 8901: fills the info panel for a task row inside a window component; rev 242 disassembly. */
        const val CS_FILL_INFO = 8901

        /** Components the client creates per board task: script 8917 makes the model, icon, label, model, hit box and name. */
        const val CHILDREN_PER_TASK = 6
    }
}
