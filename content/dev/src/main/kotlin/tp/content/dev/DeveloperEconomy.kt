package tp.content.dev

import tp.plugin.AttackCheck
import tp.plugin.AttrKey
import tp.plugin.ContentPlugin
import tp.plugin.ContentScope
import tp.plugin.DamageRequest
import tp.plugin.HitStyle
import tp.plugin.PlayerRef
import tp.plugin.TimerId
import tp.plugin.commands.Category
import tp.plugin.commands.CommandBuilder
import tp.plugin.commands.CommandResult
import tp.plugin.commands.PlayerTarget
import tp.plugin.setXp
import tp.plugin.xp
import tp.plugin.xpForLevel
import tp.symbols.Invs
import tp.symbols.Varps
import tp.util.Rank
import tp.util.commands.Impact

/**
 * The developer commands of commands brief §6 that create value (ECONOMY: off on live worlds unless the file allows it)
 * or change gameplay (CONVENIENCE). Every item goes through the transaction API, labelled as a staff spawn.
 */
public class DeveloperEconomy : ContentPlugin {
    override fun ContentScope.register() {
        items()
        stats()
        convenience()
        // God mode (§6): never a valid target; refilled each tick by its timer.
        events.on<AttackCheck> { c ->
            val target = c.target as? PlayerRef ?: return@on
            if (target[GOD] == true) c.deny("That player can't be attacked.")
        }
    }

    private fun ContentScope.items() {
        dev("item", Impact.ECONOMY, "Spawns an item into your inventory (a staff spawn).", "::item abyssal whip", "::item 995 10m") {
            val item = item("item")
            val amount = long("amount", optional = true, min = 1, max = Int.MAX_VALUE.toLong())
            val noted = choice("noted", "noted", optional = true)
            signature(item, amount, noted) { ctx ->
                val p = ctx.player
                val pick = ctx[item]
                val count = (ctx.opt(amount) ?: 1L).toInt()
                val obj = if (pick.noted || ctx.opt(noted) != null) p.noted(pick.id).takeIf { it >= 0 } ?: pick.id else pick.id
                if (!p.transaction(
                        "staff spawn",
                    ) { add(Invs.INV, obj, count) }
                ) {
                    return@signature ctx.fail("No room for $count x ${p.objName(obj)}.")
                }
                ctx.done("Spawned $count x ${p.objName(obj)}.")
            }
        }
        // ECONOMY: a temporary loc can be a resource (rocks, trees), so it can create items.
        dev("loc", Impact.ECONOMY, "Puts a temporary loc on your tile (10 minutes, or until ::removeloc).", "::loc 10060 10 0") {
            val loc = loc("loc")
            val shape = int("shape", optional = true, min = 0, max = MAX_SHAPE.toLong())
            val rotation = int("rotation", optional = true, min = 0, max = MAX_ROTATION.toLong())
            signature(loc, shape, rotation) { ctx ->
                val p = ctx.player
                val placed = Placed(p.coord, ctx.opt(shape) ?: CENTREPIECE)
                p.addLoc(ctx[loc], placed.at, placed.shape, ctx.opt(rotation) ?: 0, LOC_TICKS)
                val list = p[PLACED] ?: ArrayList<Placed>().also { p[PLACED] = it }
                list += placed
                ctx.done("Loc ${ctx[loc]} placed for 10 minutes.")
            }
        }
        dev("removeloc", Impact.ECONOMY, "Removes the locs you placed with ::loc (the map's own loc comes back).", "::removeloc") {
            signature { ctx ->
                val p = ctx.player
                val list = p[PLACED].orEmpty()
                p[PLACED] = null
                // A one-tick delete reverts the layer to what the map had before ::loc.
                for (placed in list) p.deleteLoc(placed.at, placed.shape, 1)
                ctx.done("Removed ${list.size} loc(s).")
            }
        }
        dev("copy", Impact.ECONOMY, "Copies an online player's equipment, inventory and stats onto you.", "::copy Big_Bob") {
            target()
            val who = player("player")
            signature(who) { ctx ->
                val from = (ctx[who] as? PlayerTarget.Online)?.player ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                if (from == ctx.player) return@signature ctx.fail("That's you.")
                copy(ctx.player, from)
            }
        }
    }

    private fun ContentScope.stats() {
        dev("master", Impact.ECONOMY, "Sets every skill to 99.", "::master") {
            signature { ctx ->
                val p = ctx.player
                for (stat in 0 until STAT_COUNT) p.setXp(stat, p.xpForLevel(MAX_LEVEL))
                ctx.done("Every skill is 99.")
            }
        }
        dev("setlevel", Impact.ECONOMY, "Sets a skill's level (1-99), with the xp for it.", "::setlevel attack 70") {
            val skill = skill("skill")
            val level = int("level", min = 1, max = MAX_LEVEL.toLong())
            signature(skill, level) { ctx ->
                ctx.player.setXp(ctx[skill], ctx.player.xpForLevel(ctx[level]))
                ctx.done("Skill ${ctx[skill]} is level ${ctx[level]}.")
            }
        }
        dev("addxp", Impact.ECONOMY, "Adds xp to a skill.", "::addxp magic 100k") {
            val skill = skill("skill")
            val xp = long("xp", min = 1, max = MAX_XP)
            signature(skill, xp) { ctx ->
                val gained = ctx.player.addXp(ctx[skill], ctx[xp].toDouble())
                ctx.done("Added ${ctx[xp]} xp (+$gained levels).")
            }
        }
        dev("resetstats", Impact.ECONOMY, "Resets every skill to level 1 (Hitpoints 10).", "::resetstats") {
            confirm = true
            signature { ctx ->
                val p = ctx.player
                for (stat in 0 until STAT_COUNT) p.setXp(stat, if (stat == HITPOINTS) p.xpForLevel(HITPOINTS_START) else 0.0)
                ctx.done("Stats reset.")
            }
        }
    }

    private fun ContentScope.convenience() {
        dev("heal", Impact.CONVENIENCE, "Restores hitpoints, prayer, run energy and special attack (yours or a player's).", "::heal") {
            target()
            val who = player("player", optional = true)
            signature(who) { ctx ->
                val p = (ctx.opt(who) as? PlayerTarget.Online)?.player ?: ctx.player
                restore(p)
                ctx.done("Healed ${p.username}.")
            }
        }
        dev("spec", Impact.CONVENIENCE, "Fills your special attack energy.", "::spec") {
            signature { ctx ->
                ctx.player.setVarp(Varps.SA_ENERGY, FULL_SPEC)
                ctx.done("Special attack energy restored.")
            }
        }
        dev("god", Impact.CONVENIENCE, "God mode on or off: invulnerable, with unlimited run, prayer and special attack.", "::god") {
            signature { ctx ->
                val p = ctx.player
                if (p[GOD] == true) {
                    p[GOD] = null
                    p.stopTimer(GOD_TIMER)
                    return@signature ctx.done("God mode off.")
                }
                p[GOD] = true
                p.timer(GOD_TIMER, 1, soft = false) { restore(this) }
                ctx.done("God mode on.")
            }
        }
        dev("kill", Impact.CONVENIENCE, "Kills an online player, or the nearest NPC of a type.", "::kill Big_Bob", "::kill goblin") {
            target()
            val who = player("player")
            signature(who) { ctx ->
                val p = (ctx[who] as? PlayerTarget.Online)?.player ?: return@signature ctx.fail("${ctx[who].name} is not on this world.")
                kill(ctx.player, p)
            }
            val npc = npc("npc")
            signature(npc) { ctx ->
                val target =
                    ctx.player
                        .npcsWithin(ctx.player.coord, KILL_RADIUS)
                        .filter { it.type == ctx[npc] }
                        .minByOrNull { it.coord.chebyshev(ctx.player.coord) }
                        ?: return@signature ctx.fail("No such NPC within $KILL_RADIUS tiles.")
                kill(ctx.player, target)
            }
        }
    }

    // ---- helpers

    private fun ContentScope.dev(
        name: String,
        impact: Impact,
        description: String,
        vararg examples: String,
        block: CommandBuilder.() -> Unit,
    ) {
        command(name, Rank.DEVELOPER, Category.DEVELOPMENT, impact) {
            description(description)
            example(*examples)
            block()
        }
    }

    private fun restore(p: PlayerRef) {
        p.hitpoints = p.maxHitpoints
        p.setLevel(PRAYER, p.baseLevel(PRAYER))
        p.runEnergy = FULL_RUN
        p.setVarp(Varps.SA_ENERGY, FULL_SPEC)
    }

    private fun kill(by: PlayerRef, target: tp.plugin.EntityRef): CommandResult {
        val request = DamageRequest(target, by, target.hitpoints, HitStyle.TYPELESS)
        by.events.publish(request)
        return if (request.handled) CommandResult.Done("Killed.") else CommandResult.Failed("Nothing handled the damage.")
    }

    /** §6 `::copy`: one transaction empties your equipment and inventory and copies theirs, slot for slot. */
    private fun copy(to: PlayerRef, from: PlayerRef): CommandResult {
        val ok =
            to.transaction("staff spawn ::copy ${from.username}") {
                for (inv in listOf(Invs.WORN, Invs.INV)) {
                    val mine = to.inv(inv)
                    val theirs = from.inv(inv)
                    for (slot in 0 until mine.size) {
                        if (mine.obj(slot) >= 0 && !removeAt(inv, slot, mine.obj(slot), mine.count(slot))) return@transaction false
                        if (slot < theirs.size && theirs.obj(slot) >= 0 && !setAt(inv, slot, theirs.obj(slot), theirs.count(slot))) {
                            return@transaction false
                        }
                    }
                }
                true
            }
        if (!ok) return CommandResult.Failed("Couldn't copy ${from.username}'s items.")
        for (stat in 0 until STAT_COUNT) to.setXp(stat, from.xp(stat))
        return CommandResult.Done("Copied ${from.username}.")
    }

    private class Placed(val at: tp.util.CoordGrid, val shape: Int)

    private companion object {
        val PLACED = AttrKey<MutableList<Placed>>("dev.placed_locs")

        /** Loc shapes 0..22 and rotations 0..3 (cache loc model types); 10 is a centrepiece (most objects). */
        const val MAX_SHAPE = 22
        const val MAX_ROTATION = 3
        const val CENTREPIECE = 10

        /** `::loc` lifetime: 1000 ticks, 10 minutes. */
        const val LOC_TICKS = 1000
        val GOD = AttrKey<Boolean>("dev.god")
        val GOD_TIMER = TimerId("dev.god")

        /** Stats 0..23 (RuneLite `Skill`: attack .. sailing). */
        const val STAT_COUNT = 24
        const val HITPOINTS = 3
        const val PRAYER = 5
        const val MAX_LEVEL = 99
        const val HITPOINTS_START = 10

        /** 200M xp, the OSRS cap. */
        const val MAX_XP = 200_000_000L

        /** Special attack energy is 0..1000 (content/combat `Specials.FULL_ENERGY`). */
        const val FULL_SPEC = 1000

        /** Run energy is 0..10000 (run-energy.md). */
        const val FULL_RUN = 10_000

        /** `::kill <npc>` looks this far (the legacy `::fight` radius). */
        const val KILL_RADIUS = 15
    }
}
