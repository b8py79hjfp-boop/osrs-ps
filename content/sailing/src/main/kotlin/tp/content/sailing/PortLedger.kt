package tp.content.sailing

import tp.plugin.AttrKey
import tp.plugin.ContentScope
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.symbols.Invs

/**
 * The ledger tables (docs/mechanics/sailing-port-tasks.md PT5): "Take-cargo", "Take-last-cargo" and "Take-any-cargo" pick up a
 * crate of an active courier task whose cargo port is this dock, "Deposit-cargo" hands a carried crate in at the task's
 * destination. The client resolves a table to its take or deposit variant through varbit `SAILING_CARRYING_CARGO`; the server
 * sees the base loc id and the op number, so each op branches on what the player really carries. A carried crate that leaves
 * the port area is confiscated after [CONFISCATE_TICKS] ticks.
 */
internal class PortLedger(private val state: SailingState, private val tasks: PortTasks) {
    fun ContentScope.register() {
        for (loc in PortSites.LEDGER_LOCS) {
            onOpLoc(loc, OP_TAKE) {
                state.ports.ofLedger(loc)?.let {
                    if (Cargo.held(player) >=
                        0
                    ) {
                        deposit(player, it)
                    } else {
                        takeChoice(player, it)
                    }
                }
            }
            onOpLoc(loc, OP_TAKE_LAST) { state.ports.ofLedger(loc)?.let { takeLast(player, it) } }
            onOpLoc(loc, OP_TAKE_ANY) { state.ports.ofLedger(loc)?.let { takeAny(player, it) } }
        }
        onLogin {
            Cargo.refresh(this)
            if (Cargo.held(this) >= 0) startWatch(this)
        }
    }

    private fun quantity(task: PortTask): Int = task.cargo.firstOrNull()?.quantity ?: 0

    /** Active courier tasks that still have crates to collect at [dock]. */
    private fun collectable(p: PlayerRef, dock: Int): List<PortTasks.ActiveTask> =
        tasks.active(p).filter { it.task.isCourier && it.task.cargoDock == dock && it.state.taken < quantity(it.task) }

    private fun takeChoice(p: PlayerRef, dock: Int) {
        val options = collectable(p, dock)
        when {
            !checkHands(p) -> {
                Unit
            }

            options.isEmpty() -> {
                p.message("You have no cargo to collect here.")
            }

            options.size == 1 -> {
                take(p, options.first())
            }

            else -> {
                p.choiceDialog(
                    "Which cargo?",
                    options.map { it.task.name },
                ) { choice -> options.getOrNull(choice - 1)?.let { take(this, it) } }
            }
        }
    }

    private fun takeLast(p: PlayerRef, dock: Int) {
        if (!checkHands(p)) return
        val last = PortTaskSave.lastCargoTask(p)
        val match = collectable(p, dock).firstOrNull { it.task.id == last }
        if (match == null) p.message("You have no cargo of that kind to collect here.") else take(p, match)
    }

    private fun takeAny(p: PlayerRef, dock: Int) {
        if (!checkHands(p)) return
        val options = collectable(p, dock)
        if (options.isEmpty()) p.message("You have no cargo to collect here.") else take(p, options[p.random(options.size)])
    }

    private fun checkHands(p: PlayerRef): Boolean {
        if (Cargo.held(p) >= 0) {
            p.message("You are already carrying cargo.")
            return false
        }
        if (!Cargo.handsFree(p)) {
            p.message("You need both hands free to carry cargo.")
            return false
        }
        return true
    }

    private fun take(p: PlayerRef, pick: PortTasks.ActiveTask) {
        val crate =
            pick.task.cargo
                .firstOrNull()
                ?.obj ?: return
        if (!Cargo.handsFree(p) || !p.transaction("port cargo take") { setAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) }) return
        PortTaskSave.set(p, pick.slot, pick.state.copy(taken = pick.state.taken + 1))
        PortTaskSave.setLastCargoTask(p, pick.task.id)
        PortTaskSave.mirror(p)
        Cargo.refresh(p)
        startWatch(p)
        tasks.quest?.cargoTaken(p, pick.task.id)
    }

    // ---- deposit

    private fun deposit(p: PlayerRef, dock: Int) {
        val crate = Cargo.held(p)
        val target =
            tasks
                .active(p)
                .filter {
                    it.task.isCourier && it.task.endingDock == dock && it.task.cargo
                        .firstOrNull()
                        ?.obj == crate
                }.firstOrNull { it.state.delivered < quantity(it.task) }
        if (target == null) return p.message("That cargo isn't to be delivered here.")
        if (!p.transaction("port cargo deposit") { removeAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) }) return
        val delivered = target.state.delivered + 1
        PortTaskSave.set(p, target.slot, target.state.copy(delivered = delivered))
        Cargo.refresh(p)
        if (delivered >= quantity(target.task)) tasks.complete(p, target.slot) else PortTaskSave.mirror(p)
    }

    // ---- leaving the port with a crate

    private fun startWatch(p: PlayerRef) {
        if (!p.hasTimer(WATCH)) p.timer(WATCH, 1, soft = true) { watch(this) }
    }

    private fun watch(p: PlayerRef) {
        if (Cargo.held(p) < 0) {
            p.stopTimer(WATCH)
            p[AWAY] = null
            return
        }
        if (inPort(p)) {
            if (p[AWAY] != null) p[AWAY] = null
            return
        }
        val away = (p[AWAY] ?: 0) + 1
        p[AWAY] = away
        if (away == 1) p.message(LEAVING)
        if (away >= CONFISCATE_TICKS) confiscate(p)
    }

    /** On a boat, or within [PORT_RADIUS] tiles of a dock's landing (OPEN-PT3: the real port area is not in the cache). */
    private fun inPort(p: PlayerRef): Boolean =
        ActiveBoats.of(p) != null || state.sites.all.any { site -> site.land?.let { it.chebyshev(p.coord) <= PORT_RADIUS } == true }

    private fun confiscate(p: PlayerRef) {
        val crate = Cargo.held(p)
        p.stopTimer(WATCH)
        p[AWAY] = null
        if (crate < 0 || !p.transaction("port cargo confiscated") { removeAt(Invs.WORN, Cargo.WEAPON_SLOT, crate, 1) }) return
        // The crate is lost, so it can be collected again (OPEN-PT3).
        tasks
            .active(
                p,
            ).firstOrNull {
                it.task.isCourier && it.task.cargo
                    .firstOrNull()
                    ?.obj == crate && it.state.taken > it.state.delivered
            }?.let {
                PortTaskSave.set(p, it.slot, it.state.copy(taken = it.state.taken - 1))
            }
        PortTaskSave.mirror(p)
        Cargo.refresh(p)
        p.message(CONFISCATED)
    }

    private companion object {
        val WATCH = TimerId("sailing_cargo_watch")
        val AWAY = AttrKey<Int>("sailing.cargo.away")

        /** Ledger table options (cache locs 60320 / 60321): Take-cargo and Deposit-cargo are op 1, then Take-last and Take-any. */
        const val OP_TAKE = 1
        const val OP_TAKE_LAST = 2
        const val OP_TAKE_ANY = 3

        /** Ticks a crate may be outside the port before it is confiscated (Wiki Courier crate: 22 ticks, 13.2 s). */
        const val CONFISCATE_TICKS = 22

        /** Tiles from a dock's landing that count as the port area. No source: the Wiki only says "the port area" (OPEN-PT3). */
        const val PORT_RADIUS = 40

        /** Game messages of the Wiki Courier crate page (colours as quoted there). */
        const val LEAVING =
            "<col=ff3045>You're leaving port with some cargo. If you do not return it, it will be confiscated by the " +
                "shipwright wizard's autonomous tracking unit.</col>"
        const val CONFISCATED = "<col=ff0000>Your cargo has been confiscated by the S.W.A.T. team.</col>"
    }
}
