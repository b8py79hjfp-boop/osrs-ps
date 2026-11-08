package tp.plugin.commands

import tp.plugin.PlayerRef

/**
 * Writes a staff action taken in the `::commands` interface that is not itself a command (claiming or closing a help ticket)
 * to the append-only audit log (commands brief section 4, section 8), with the same detail fields as a command: rank, world,
 * tick and source `interface`. No-op on a world without an account server.
 */
public fun PlayerRef.auditStaffAction(action: String, target: String, detail: String) {
    val world = p.world
    world.accounts?.audit(
        username,
        action,
        target,
        "rank=${rank.name} world=${world.config.id} tick=${world.tick} source=interface $detail",
    )
}
