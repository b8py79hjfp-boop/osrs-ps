package tp.content.sailing

import tp.plugin.ContentPlugin
import tp.plugin.ContentScope

/**
 * The Sailing skill (docs/plans/sailing.md, docs/mechanics/sailing-*.md). At world start the cache tables are read into
 * [SailingData] and cross-checked, so a cache without the Sailing tables or with broken references stops the boot instead
 * of failing in a player's boat; then the boat factory is built. S1 content: boat ownership and spawning ([Boats]), docking
 * and boarding ([Docking]) and the Pandemonium quest ([Pandemonium]). S3 content: port tasks ([PortTasks]), notice boards and
 * the captain's log task list ([PortBoards]), ledger tables ([PortLedger]), cargo holds ([CargoHold]), port masters
 * ([PortMaster]) and the coin bags ([PortRewards]). Movement is [BoatMotion].
 */
public class Sailing : ContentPlugin {
    private val state = SailingState()
    private val docking = Docking(state)
    private val tasks = PortTasks(state)
    private val pandemonium = Pandemonium(state, docking, tasks)
    private val boards = PortBoards(state, tasks)
    private val ledger = PortLedger(state, tasks)
    private val hold = CargoHold(state, tasks)
    private val master = PortMaster(tasks)

    init {
        tasks.quest = pandemonium
    }

    override fun ContentScope.register() {
        onWorldStart {
            val data = SailingData(this)
            data.check()
            state.data = data
            state.boats = Boats(data, DockSites(data.docks))
            state.ports = PortSites(data.docks)
            state.catalog = PortCatalog(data)
        }
        with(docking) { register() }
        with(pandemonium) { register() }
        with(tasks) { register() }
        with(boards) { register() }
        with(ledger) { register() }
        with(hold) { register() }
        with(master) { register() }
        with(PortRewards) { registerBags() }
    }
}
